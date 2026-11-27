import { safeError } from '../lib/errors.js';
/**
 * Auth Routes (Hono)
 * Endpoints for signup, login, refresh, logout, profile, and devices.
 * Implements docs/05_API.md Section 16 & docs/07_AUTHENTICATION.md.
 */

import crypto from 'node:crypto';
import net from 'node:net';
import { Hono } from 'hono';
import { z } from 'zod';
import { zValidator } from '@hono/zod-validator';
import {
  guestSetupSchema,
  mergeGuestSchema,
  signupSchema,
  loginSchema,
  refreshSchema,
  deviceRegisterSchema,
  validationHook,
} from '../lib/validators.js';
import { eq, or, and, asc, sql, lt, isNull } from 'drizzle-orm';
import { authDb } from '../db/connection.js';
import { users, userSessions, userDevices, signupVerificationOtps, passwordResetOtps } from '../db/schema.js';
import {
  hashCredential,
  verifyCredential,
  generateAccessToken,
  generateGuestToken,
  generateRefreshToken,
  hashToken,
  DUMMY_HASH,
} from '../services/authService.js';
import {
  generateOtp,
  hashOtp,
  verifyOtp,
  isOtpExpired,
  OTP_EXPIRY_MS,
  MAX_OTP_ATTEMPTS,
} from '../services/otpService.js';
import { sendWelcomeEmail, sendOtpEmail } from '../services/emailService.js';
import { honoAuthMiddleware, requireAuth, requireAuthOrGuest } from '../middleware/honoAuth.js';
import { mergeGuestIntoAuth } from '../services/guestMergeService.js';
import { getClientIp } from '../lib/rateLimit.js';
import { validateUsername } from '../utils/username.js';
import { AppEnv } from '../types.js';

export const authRoutes = new Hono<AppEnv>();

// In-memory rate limiting fallback
const authAttempts = new Map<string, { count: number; resetTime: number }>();

export async function checkRateLimit(
  ip: string,
  maxAttempts = 5,
  windowSec = 60,
  forceInMemory = false
): Promise<{ allowed: boolean; retryAfter: number }> {
  const redisUrl = process.env.REDIS_URL;
  const redisToken = process.env.REDIS_TOKEN;

  if (redisUrl && redisToken && !forceInMemory) {
    try {
      const key = `rl:auth:${ip}`;
      const res = await fetch(`${redisUrl}/pipeline`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${redisToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify([
          ['INCR', key],
          ['EXPIRE', key, windowSec],
        ]),
      });
      if (res.ok) {
        const results = (await res.json()) as any[];
        const count = results[0]?.result;
        if (typeof count === 'number') {
          return {
            allowed: count <= maxAttempts,
            retryAfter: windowSec,
          };
        }
      }
    } catch {
      // Fallback to in-memory on redis error
    }
  }

  const now = Date.now();
  const record = authAttempts.get(ip);
  if (record && now < record.resetTime) {
    record.count++;
    const retryAfter = Math.max(1, Math.ceil((record.resetTime - now) / 1000));
    return {
      allowed: record.count <= maxAttempts,
      retryAfter,
    };
  } else {
    authAttempts.set(ip, { count: 1, resetTime: now + windowSec * 1000 });
    return { allowed: true, retryAfter: windowSec };
  }
}

export function clearRateLimit(ip: string) {
  authAttempts.delete(ip);
}


// GET /api/auth/check-username?username=...
authRoutes.get('/check-username', async (c) => {
  const forwarded = c.req.header('x-forwarded-for');
  const ip = forwarded ? forwarded.split(',').pop()?.trim() : c.req.header('cf-connecting-ip');
  if (ip) {
    const rl = await checkRateLimit(`check-username:${ip}`, 20, 60);
    if (!rl.allowed) {
      return c.json(
        { error: 'Too many username checks. Please try again in 1 minute.' },
        429,
        { 'Retry-After': String(rl.retryAfter) }
      );
    }
  }

  const username = c.req.query('username');
  const validation = validateUsername(username);
  if (!validation.ok) {
    return c.json({ error: validation.reason, code: 'invalid_username' }, 400);
  }

  const clean = validation.value;

  try {
    const existing = await authDb
      .select({ id: users.id })
      .from(users)
      .where(eq(users.username, clean))
      .limit(1);

    return c.json({ available: existing.length === 0 });
  } catch {
    return c.json({ available: true });
  }
});

// POST /api/auth/signup
authRoutes.post(
  '/signup',
  zValidator('json', signupSchema, validationHook('Username and either password or PIN are required.')),
  async (c) => {
    const ip = getClientIp(c);
    const rl = await checkRateLimit(ip);
    if (!rl.allowed) {
      return c.json(
        { error: 'Too many authentication attempts. Please try again in 1 minute.' },
        429,
        { 'Retry-After': String(rl.retryAfter) }
      );
    }

    try {
      const body = c.req.valid('json');
      const username = body.username || body.identifier;
      const password = body.password || body.secret;
      const { email, displayName, pin } = body;

    // PIN path retained for legacy users. Remove in Phase 14 after migration.
    if (!username || (!password && !pin)) {
      return c.json({ error: 'Username and either password or PIN are required.' }, 400);
    }

    const userVal = validateUsername(username);
    if (!userVal.ok) {
      return c.json({ error: userVal.reason, code: 'invalid_username' }, 400);
    }
    const cleanUsername = userVal.value;
    const cleanEmail = email ? email.trim().toLowerCase() : null;

    // Check if user already exists
    try {
      const existing = await authDb
        .select()
        .from(users)
        .where(
          cleanEmail
            ? or(eq(users.username, cleanUsername), eq(users.email, cleanEmail))
            : eq(users.username, cleanUsername)
        )
        .limit(1);

      if (existing.length > 0) {
        return c.json({ error: 'Username or email already in use.' }, 409);
      }
    } catch {
      // Allow fallback if DB connection fails in local test
    }

    const passwordHash = password ? await hashCredential(password) : null;
    const pinHash = pin ? await hashCredential(pin) : null;

    const [newUser] = await authDb
      .insert(users)
      .values({
        username: cleanUsername,
        email: cleanEmail,
        displayName: displayName || cleanUsername,
        passwordHash,
        pinHash,
      })
      .returning();

    const accessToken = generateAccessToken(newUser.id, newUser.username);
    const { token: refreshToken, hash: refreshHash, expiresAt } = generateRefreshToken();

    try {
      await authDb.insert(userSessions).values({
        userId: newUser.id,
        refreshTokenHash: refreshHash,
        expiresAt,
      });
    } catch {
      // DB session record
    }

    // Trigger welcome email asynchronously upon successful account creation
    if (cleanEmail) {
      sendWelcomeEmail(cleanEmail, newUser.displayName || cleanUsername, cleanUsername).catch(() => {});
    }

    const guestIdHeader = c.req.header('x-guest-id')?.trim();
    const isUuid = (val?: string): val is string => !!val && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(val);

    let mergeResult: { merged: boolean; mergeId?: string; mergeStatus?: string } = { merged: false };
    if (isUuid(guestIdHeader) && process.env.ENABLE_GUEST_MERGE !== 'false') {
      try {
        const res = await mergeGuestIntoAuth(guestIdHeader, newUser.id);
        mergeResult = { merged: res.merged, mergeId: guestIdHeader };
      } catch (mergeErr) {
        console.warn('[Auth] Guest merge on signup failed:', mergeErr);
        mergeResult = { merged: false, mergeStatus: 'failed' };
      }
    }

    return c.json(
      {
        user: {
          id: newUser.id,
          username: newUser.username,
          displayName: newUser.displayName,
          email: newUser.email,
          avatarUrl: newUser.avatarUrl,
        },
        accessToken,
        refreshToken,
        merged: mergeResult.merged,
        ...(mergeResult.mergeId ? { mergeId: mergeResult.mergeId } : {}),
        ...(mergeResult.mergeStatus ? { mergeStatus: mergeResult.mergeStatus } : {}),
      },
      201
    );
  } catch (err: any) {
    console.error('[Auth Error]', err);
    return c.json(safeError(err, c, 500), 500);
  }
});

// POST /api/auth/login
authRoutes.post(
  '/login',
  zValidator('json', loginSchema, validationHook('Username/email and credentials required.')),
  async (c) => {
    const ip = getClientIp(c);
    const rl = await checkRateLimit(ip);
    if (!rl.allowed) {
      return c.json(
        { error: 'Too many authentication attempts. Please try again in 1 minute.' },
        429,
        { 'Retry-After': String(rl.retryAfter) }
      );
    }

    try {
      const body = c.req.valid('json');
      const usernameOrEmail = body.usernameOrEmail || body.identifier || body.username;
      const password = body.password || body.secret;
      const { pin, deviceName } = body;

    // PIN path retained for legacy users. Remove in Phase 14 after migration.
    if (!usernameOrEmail || (!password && !pin)) {
      return c.json({ error: 'Username/email and credentials required.' }, 400);
    }

    const query = usernameOrEmail.trim().toLowerCase();

    const [user] = await authDb
      .select()
      .from(users)
      .where(or(eq(users.username, query), eq(users.email, query)))
      .limit(1);

    if (!user) {
      await verifyCredential(DUMMY_HASH, password || pin || '');
      return c.json({ error: 'Incorrect credentials' }, 401);
    }

    let isValid = false;
    if (password && user.passwordHash) {
      isValid = await verifyCredential(user.passwordHash, password);
    }
    if (!isValid && pin && user.pinHash) {
      isValid = await verifyCredential(user.pinHash, pin);
    }

    if (!isValid) {
      return c.json({ error: 'Incorrect credentials' }, 401);
    }

    const accessToken = generateAccessToken(user.id, user.username);
    const { token: refreshToken, hash: refreshHash, expiresAt } = generateRefreshToken();

    let deviceId: string | undefined;
    if (deviceName) {
      try {
        const [device] = await authDb
          .insert(userDevices)
          .values({
            userId: user.id,
            deviceName,
            platform: 'android',
            lastSeenAt: new Date(),
          })
          .returning();
        deviceId = device.id;
      } catch {
        // Device registration optional
      }
    }

    try {
      await authDb.insert(userSessions).values({
        userId: user.id,
        deviceId: deviceId || null,
        refreshTokenHash: refreshHash,
        expiresAt,
        lastUsedAt: new Date(),
      });

      await authDb
        .update(users)
        .set({ lastLoginAt: new Date() })
        .where(eq(users.id, user.id));
    } catch {
      // Session save
    }

    const guestIdHeader = c.req.header('x-guest-id')?.trim();
    const isUuid = (val?: string): val is string => !!val && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(val);

    let mergeResult: { merged: boolean; mergeId?: string; mergeStatus?: string } = { merged: false };
    if (isUuid(guestIdHeader) && process.env.ENABLE_GUEST_MERGE !== 'false') {
      try {
        const res = await mergeGuestIntoAuth(guestIdHeader, user.id, deviceId);
        mergeResult = { merged: res.merged, mergeId: guestIdHeader };
      } catch (mergeErr) {
        console.warn('[Auth] Guest merge on login failed:', mergeErr);
        mergeResult = { merged: false, mergeStatus: 'failed' };
      }
    }

    return c.json({
      user: {
        id: user.id,
        username: user.username,
        displayName: user.displayName,
        email: user.email,
        avatarUrl: user.avatarUrl,
        bio: user.bio,
      },
      accessToken,
      refreshToken,
      merged: mergeResult.merged,
      ...(mergeResult.mergeId ? { mergeId: mergeResult.mergeId } : {}),
      ...(mergeResult.mergeStatus ? { mergeStatus: mergeResult.mergeStatus } : {}),
    });
  } catch (err: any) {
    console.error('[Auth Error]', err);
    return c.json(safeError(err, c, 500), 500);
  }
});


// POST /api/auth/guest-setup
authRoutes.post(
  '/guest-setup',
  zValidator('json', guestSetupSchema, validationHook('invalid_payload')),
  async (c) => {
    try {
      const { deviceId, deviceName, platform } = c.req.valid('json');
      const deviceFingerprintHash = crypto.createHash('sha256').update(deviceId).digest('hex');

      const randomSuffix = crypto.randomBytes(4).toString('hex');
      const guestUsername = `guest_${randomSuffix}`;

      let clientIp: string | null =
        c.req.header('x-forwarded-for')?.split(',')[0].trim() ||
        c.req.header('x-real-ip')?.trim() ||
        c.req.header('cf-connecting-ip')?.trim() ||
        null;

      if (clientIp) {
        if (clientIp.startsWith('[') && clientIp.includes(']')) {
          clientIp = clientIp.substring(1, clientIp.indexOf(']'));
        } else if (clientIp.includes(':') && clientIp.indexOf(':') === clientIp.lastIndexOf(':')) {
          clientIp = clientIp.split(':')[0];
        }
        if (!net.isIP(clientIp)) {
          clientIp = null;
        }
      }
      if (clientIp === '') clientIp = null;

      let [guestUser] = await authDb
        .select()
        .from(users)
        .where(
          and(
            eq(users.deviceFingerprint, deviceFingerprintHash),
            eq(users.isGuest, true),
            isNull(users.mergedInto)
          )
        )
        .limit(1);

      if (guestUser) {
        await authDb
          .update(users)
          .set({ lastSeenAt: new Date(), updatedAt: new Date() })
          .where(eq(users.id, guestUser.id));
      } else {
        try {
          const [inserted] = await authDb
            .insert(users)
            .values({
              username: guestUsername,
              displayName: 'Guest',
              isGuest: true,
              deviceFingerprint: deviceFingerprintHash,
              ipAtCreation: clientIp,
              lastSeenAt: new Date(),
            })
            .returning();
          guestUser = inserted;
        } catch (insertErr: any) {
          if (insertErr?.code === '23505') {
            const [raced] = await authDb
              .select()
              .from(users)
              .where(
                and(
                  eq(users.deviceFingerprint, deviceFingerprintHash),
                  eq(users.isGuest, true),
                  isNull(users.mergedInto)
                )
              )
              .limit(1);
            if (!raced) throw insertErr;
            guestUser = raced;
            await authDb
              .update(users)
              .set({ lastSeenAt: new Date(), updatedAt: new Date() })
              .where(eq(users.id, guestUser.id));
          } else {
            throw insertErr;
          }
        }
      }

      const [device] = await authDb
        .insert(userDevices)
        .values({
          userId: guestUser.id,
          deviceName,
          platform,
          deviceFingerprintHash,
        })
        .returning();

      const accessToken = generateGuestToken(guestUser.id, device.id);
      const { token: refreshToken, hash: refreshHash, expiresAt: refreshExpiresAt } = generateRefreshToken();

      await authDb.insert(userSessions).values({
        userId: guestUser.id,
        deviceId: device.id,
        refreshTokenHash: refreshHash,
        expiresAt: refreshExpiresAt,
      });

      return c.json({
        accessToken,
        refreshToken,
        expiresIn: 86400,
        userId: guestUser.id,
        isGuest: true,
      });
    } catch (err: any) {
      console.error('[Auth Error]', err);
      return c.json(safeError(err, c, 500), 500);
    }
  }
);

// POST /api/auth/merge-guest (requires authenticated user)
authRoutes.post(
  '/merge-guest',
  requireAuth,
  zValidator('json', mergeGuestSchema, validationHook('invalid_payload')),
  async (c) => {
    try {
      const { guestId, guestUserId, clientBatchId } = c.req.valid('json');
      const effectiveGuestId = (guestId || guestUserId)!;
      const userId = c.get('userId');

      const res = await mergeGuestIntoAuth(effectiveGuestId, userId, undefined, clientBatchId);
      if (res.alreadyMerged) {
        return c.json({ status: 'already_merged', guestId: effectiveGuestId, authId: userId });
      }
      return c.json({ status: 'merged', guestId: effectiveGuestId, authId: userId });
    } catch (err: any) {
      return c.json(safeError(err, c, 500), 500);
    }
  }
);

// GET /api/auth/guest-status (requires auth or guest)
authRoutes.get('/guest-status', requireAuthOrGuest, async (c) => {
  try {
    const userId = c.get('userId');
    const [user] = await authDb
      .select({
        isGuest: users.isGuest,
        mergedInto: users.mergedInto,
      })
      .from(users)
      .where(eq(users.id, userId))
      .limit(1);

    if (!user) {
      return c.json({ error: 'User not found' }, 404);
    }

    const isGuest = !!user.isGuest;
    const mergedInto = user.mergedInto || null;
    const needsMerge = isGuest && !mergedInto;

    return c.json({
      isGuest,
      mergedInto,
      needsMerge,
    });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// POST /api/auth/refresh
authRoutes.post(
  '/refresh',
  zValidator('json', refreshSchema, validationHook('Refresh token is required.')),
  async (c) => {
    try {
      const { refreshToken } = c.req.valid('json');

      const tokenHash = hashToken(refreshToken);

    const [session] = await authDb
      .select()
      .from(userSessions)
      .where(eq(userSessions.refreshTokenHash, tokenHash))
      .limit(1);

    if (!session) {
      // Reuse detection: check if this was a previous token that was already rotated
      const [reusedSession] = await authDb
        .select()
        .from(userSessions)
        .where(eq(userSessions.previousTokenHash, tokenHash))
        .limit(1);

      if (reusedSession) {
        // TOKEN REUSE DETECTED! Revoke ALL sessions for this compromised user
        await authDb.delete(userSessions).where(eq(userSessions.userId, reusedSession.userId));
        return c.json({ error: 'Token reuse detected. All sessions revoked.' }, 401);
      }

      return c.json({ error: 'Invalid or expired refresh token.' }, 401);
    }

    if (session.expiresAt && new Date() > session.expiresAt) {
      return c.json({ error: 'Invalid or expired refresh token.' }, 401);
    }

    const [user] = await authDb.select().from(users).where(eq(users.id, session.userId)).limit(1);
    if (!user) {
      return c.json({ error: 'User no longer exists.' }, 401);
    }

    const newAccessToken = generateAccessToken(user.id, user.username, session.deviceId || undefined, user.isGuest);
    const { token: newRefreshToken, hash: newRefreshHash, expiresAt: newExpiresAt } = generateRefreshToken();

    // Rotate refresh token
    await authDb
      .update(userSessions)
      .set({
        refreshTokenHash: newRefreshHash,
        previousTokenHash: tokenHash,
        expiresAt: newExpiresAt,
        lastUsedAt: new Date(),
      })
      .where(eq(userSessions.id, session.id));

    return c.json({
      accessToken: newAccessToken,
      refreshToken: newRefreshToken,
    });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// ==========================================
// OTP-based Signup & Verification Endpoints
// ==========================================

// POST /api/auth/signup-init
authRoutes.post('/signup-init', async (c) => {
  try {
    const body = await c.req.json().catch(() => ({}));
    const { username, email, password, displayName } = body;

    if (!username || typeof username !== 'string' || !email || typeof email !== 'string' || !password || typeof password !== 'string') {
      return c.json({ error: 'Username, email, and password are required.' }, 400);
    }

    const userVal = validateUsername(username);
    if (!userVal.ok) {
      return c.json({ error: userVal.reason, code: 'invalid_username' }, 400);
    }
    const cleanUsername = userVal.value;
    const cleanEmail = email.trim().toLowerCase();
    const cleanDisplayName = typeof displayName === 'string' && displayName.trim() ? displayName.trim() : cleanUsername;

    // Checkpoint 3: Rate limit signup-init (3/hr per email, 5/hr per IP)
    const emailRl = await checkRateLimit(`signup:${cleanEmail}`, 3, 3600);
    if (!emailRl.allowed) {
      return c.json({ error: 'Too many signup attempts. Try again later.' }, 429, {
        'Retry-After': String(emailRl.retryAfter),
      });
    }

    const forwarded = c.req.header('x-forwarded-for');
    const rawIp = forwarded ? forwarded.split(',').pop()?.trim() : c.req.header('cf-connecting-ip');
    if (rawIp && rawIp !== 'unknown') {
      const ipRl = await checkRateLimit(`signup:ip:${rawIp}`, 5, 3600);
      if (!ipRl.allowed) {
        return c.json({ error: 'Too many signup attempts from this network. Try again later.' }, 429, {
          'Retry-After': String(ipRl.retryAfter),
        });
      }
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) {
      return c.json({ error: 'A valid email address is required.' }, 400);
    }

    if (password.length < 8 || !/[a-zA-Z]/.test(password) || !/[0-9]/.test(password)) {
      return c.json({ error: 'Password must be at least 8 characters long and contain both letters and numbers.' }, 400);
    }

    // Check if email or username is already registered in users table
    const [existing] = await authDb
      .select({ id: users.id, email: users.email, username: users.username })
      .from(users)
      .where(or(eq(users.username, cleanUsername), eq(users.email, cleanEmail)))
      .limit(1);

    if (existing) {
      if (existing.email === cleanEmail) {
        return c.json({ error: 'Email is already registered' }, 409);
      }
      return c.json({ error: 'Username is already taken' }, 409);
    }

    // Hash password ONCE
    const passwordHash = await hashCredential(password);
    const otp = generateOtp();
    const otpHash = hashOtp(otp);
    const expiresAt = new Date(Date.now() + OTP_EXPIRY_MS);

    const [record] = await authDb
      .insert(signupVerificationOtps)
      .values({
        email: cleanEmail,
        username: cleanUsername,
        passwordHash,
        displayName: cleanDisplayName,
        otpHash,
        expiresAt,
      })
      .returning({ id: signupVerificationOtps.id });

    // Send OTP email (fire-and-forget)
    sendOtpEmail(cleanEmail, otp, 'signup').catch(() => {});

    return c.json({ verificationId: record.id, email: cleanEmail }, 200);
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// POST /api/auth/signup-verify
authRoutes.post('/signup-verify', async (c) => {
  try {
    const body = await c.req.json().catch(() => ({}));
    const { verificationId, otp } = body;

    if (!verificationId || typeof verificationId !== 'string' || !otp || typeof otp !== 'string') {
      return c.json({ error: 'Verification ID and OTP are required.' }, 400);
    }

    const [record] = await authDb
      .update(signupVerificationOtps)
      .set({ attempts: sql`${signupVerificationOtps.attempts} + 1` })
      .where(
        and(
          eq(signupVerificationOtps.id, verificationId.trim()),
          lt(signupVerificationOtps.attempts, MAX_OTP_ATTEMPTS),
          isNull(signupVerificationOtps.usedAt)
        )
      )
      .returning();

    if (!record) {
      const [existing] = await authDb
        .select({ attempts: signupVerificationOtps.attempts })
        .from(signupVerificationOtps)
        .where(eq(signupVerificationOtps.id, verificationId.trim()))
        .limit(1);

      if (existing && existing.attempts >= MAX_OTP_ATTEMPTS) {
        return c.json({ error: 'Too many failed attempts. Please request a new code.' }, 400);
      }
      return c.json({ error: 'Invalid or expired verification session.' }, 400);
    }

    if (isOtpExpired(record.expiresAt)) {
      return c.json({ error: 'Verification code has expired. Please request a new code.' }, 400);
    }

    const isValid = verifyOtp(otp, record.otpHash);
    if (!isValid) {
      return c.json({ error: 'Incorrect verification code. Please try again.' }, 400);
    }

    // Mark OTP used
    await authDb
      .update(signupVerificationOtps)
      .set({ usedAt: new Date() })
      .where(eq(signupVerificationOtps.id, record.id));

    // Race-condition check: ensure username/email wasn't registered in the interim
    const [existing] = await authDb
      .select({ id: users.id })
      .from(users)
      .where(or(eq(users.username, record.username), eq(users.email, record.email)))
      .limit(1);

    if (existing) {
      return c.json({ error: 'User already exists.' }, 409);
    }

    // Insert user into users table with pre-computed passwordHash
    const [newUser] = await authDb
      .insert(users)
      .values({
        username: record.username,
        email: record.email,
        displayName: record.displayName,
        passwordHash: record.passwordHash,
        emailVerified: true,
        emailVerifiedAt: new Date(),
      })
      .returning({
        id: users.id,
        username: users.username,
        email: users.email,
        displayName: users.displayName,
        createdAt: users.createdAt,
      });

    // Generate tokens and session
    const accessToken = generateAccessToken(newUser.id, newUser.username);
    const { token: refreshToken, hash: refreshTokenHash, expiresAt } = generateRefreshToken();

    await authDb.insert(userSessions).values({
      userId: newUser.id,
      refreshTokenHash,
      expiresAt,
    });

    // Send welcome email (fire-and-forget)
    sendWelcomeEmail(newUser.email!, newUser.displayName, newUser.username).catch(() => {});

    return c.json(
      {
        user: {
          id: newUser.id,
          username: newUser.username,
          email: newUser.email,
          displayName: newUser.displayName,
          emailVerified: true,
          createdAt: newUser.createdAt,
        },
        accessToken,
        refreshToken,
      },
      200
    );
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// POST /api/auth/signup-resend-otp
authRoutes.post('/signup-resend-otp', async (c) => {
  try {
    const body = await c.req.json().catch(() => ({}));
    const { verificationId } = body;

    if (!verificationId || typeof verificationId !== 'string') {
      return c.json({ error: 'Verification ID is required.' }, 400);
    }

    const [record] = await authDb
      .select()
      .from(signupVerificationOtps)
      .where(eq(signupVerificationOtps.id, verificationId.trim()))
      .limit(1);

    if (!record || record.usedAt !== null) {
      return c.json({ error: 'Verification session not found or already completed.' }, 400);
    }

    const rl = await checkRateLimit(`resend:signup:${record.email}`, 3, 3600);
    if (!rl.allowed) {
      return c.json({ error: 'Too many resend attempts. Please try again later.' }, 429, {
        'Retry-After': String(rl.retryAfter),
      });
    }

    const newOtp = generateOtp();
    const otpHash = hashOtp(newOtp);
    const expiresAt = new Date(Date.now() + OTP_EXPIRY_MS);

    await authDb
      .update(signupVerificationOtps)
      .set({
        otpHash,
        attempts: 0,
        expiresAt,
      })
      .where(eq(signupVerificationOtps.id, record.id));

    sendOtpEmail(record.email, newOtp, 'signup').catch(() => {});

    return c.json({ ok: true, email: record.email }, 200);
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// ==========================================
// OTP-based Password Reset Endpoints
// ==========================================

const handleForgotPasswordInit = async (c: any) => {
  try {
    const body = await c.req.json().catch(() => ({}));
    const email = body.email;

    if (!email || typeof email !== 'string') {
      return c.json({ resetId: crypto.randomUUID(), email: '' });
    }

    const cleanEmail = email.trim().toLowerCase();

    // Rate limit: 3/hr per email
    const rl = await checkRateLimit(`reset:${cleanEmail}`, 3, 3600);
    if (!rl.allowed) {
      return c.json(
        { error: 'Too many password reset requests. Please try again in 1 hour.' },
        429,
        { 'Retry-After': String(rl.retryAfter) }
      );
    }

    const forwarded = c.req.header('x-forwarded-for');
    const rawIp = forwarded ? forwarded.split(',').pop()?.trim() : c.req.header('cf-connecting-ip');
    if (rawIp && rawIp !== 'unknown') {
      const ipRl = await checkRateLimit(`reset:ip:${rawIp}`, 5, 3600);
      if (!ipRl.allowed) {
        return c.json(
          { error: 'Too many password reset requests from this network. Please try again in 1 hour.' },
          429,
          { 'Retry-After': String(ipRl.retryAfter) }
        );
      }
    }

    const [user] = await authDb
      .select({ id: users.id, email: users.email })
      .from(users)
      .where(eq(users.email, cleanEmail))
      .limit(1);

    if (!user || !user.email) {
      return c.json({ error: 'No account found with this email address.' }, 404);
    }

    const otp = generateOtp();
    const otpHash = hashOtp(otp);
    const expiresAt = new Date(Date.now() + OTP_EXPIRY_MS);

    const [record] = await authDb
      .insert(passwordResetOtps)
      .values({
        userId: user.id,
        email: user.email,
        otpHash,
        expiresAt,
      })
      .returning({ id: passwordResetOtps.id });

    sendOtpEmail(user.email, otp, 'reset').catch(() => {});

    return c.json({ resetId: record.id, email: cleanEmail }, 200);
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
};

// POST /api/auth/forgot-password-init
authRoutes.post('/forgot-password-init', handleForgotPasswordInit);

// POST /api/auth/forgot-password (alias for forgot-password-init)
authRoutes.post('/forgot-password', handleForgotPasswordInit);

// POST /api/auth/reset-password
authRoutes.post('/reset-password', async (c) => {
  try {
    const body = await c.req.json().catch(() => ({}));
    const { resetId, otp, newPassword } = body;

    if (!resetId || typeof resetId !== 'string' || !otp || typeof otp !== 'string') {
      return c.json({ error: 'Reset ID and verification code are required.' }, 400);
    }

    if (
      !newPassword ||
      typeof newPassword !== 'string' ||
      newPassword.length < 8 ||
      !/[a-zA-Z]/.test(newPassword) ||
      !/[0-9]/.test(newPassword)
    ) {
      return c.json(
        { error: 'Password must be at least 8 characters long and contain both letters and numbers.' },
        400
      );
    }

    const cleanResetId = (resetId || '').trim();
    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(cleanResetId);
    if (!isUuid) {
      return c.json({ error: 'Invalid or expired reset session.' }, 400);
    }

    const [record] = await authDb
      .update(passwordResetOtps)
      .set({ attempts: sql`${passwordResetOtps.attempts} + 1` })
      .where(
        and(
          eq(passwordResetOtps.id, cleanResetId),
          lt(passwordResetOtps.attempts, MAX_OTP_ATTEMPTS),
          isNull(passwordResetOtps.usedAt)
        )
      )
      .returning();

    if (!record) {
      const [existing] = await authDb
        .select({ attempts: passwordResetOtps.attempts })
        .from(passwordResetOtps)
        .where(eq(passwordResetOtps.id, cleanResetId))
        .limit(1);

      if (existing && existing.attempts >= MAX_OTP_ATTEMPTS) {
        return c.json({ error: 'Too many failed attempts. Please request a new reset code.' }, 400);
      }
      return c.json({ error: 'Invalid or expired reset session.' }, 400);
    }

    if (isOtpExpired(record.expiresAt)) {
      return c.json({ error: 'This reset code has expired. Please request a new code.' }, 400);
    }

    const isValid = verifyOtp(otp, record.otpHash);
    if (!isValid) {
      return c.json({ error: 'Incorrect verification code. Please try again.' }, 400);
    }

    // Mark used
    await authDb
      .update(passwordResetOtps)
      .set({ usedAt: new Date() })
      .where(eq(passwordResetOtps.id, record.id));

    if (!record.userId) {
      return c.json({ error: 'Invalid reset record.' }, 400);
    }

    // Verify user still exists
    const [user] = await authDb
      .select({ id: users.id, email: users.email })
      .from(users)
      .where(eq(users.id, record.userId))
      .limit(1);

    if (!user) {
      return c.json({ error: 'No account found with this email address.' }, 404);
    }

    // Hash new password
    const passwordHash = await hashCredential(newPassword);

    // Update user password and updatedAt timestamp
    await authDb
      .update(users)
      .set({
        passwordHash,
        updatedAt: new Date(),
      })
      .where(eq(users.id, record.userId));

    // Revoke all active sessions for this user (force re-login)
    await authDb
      .delete(userSessions)
      .where(eq(userSessions.userId, record.userId));

    return c.json({ ok: true });
  } catch (err: any) {
    return c.json({ error: 'Failed to reset password. Please try again.' }, 500);
  }
});

// POST /api/auth/reset-password-resend-otp
authRoutes.post('/reset-password-resend-otp', async (c) => {
  try {
    const body = await c.req.json().catch(() => ({}));
    const { resetId } = body;

    if (!resetId || typeof resetId !== 'string') {
      return c.json({ error: 'Reset ID is required.' }, 400);
    }

    const cleanResetId = resetId.trim();
    const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(cleanResetId);
    if (!isUuid) {
      return c.json({ error: 'Reset session not found or already completed.' }, 400);
    }

    const [record] = await authDb
      .select()
      .from(passwordResetOtps)
      .where(eq(passwordResetOtps.id, cleanResetId))
      .limit(1);

    if (!record || record.usedAt !== null || !record.userId) {
      return c.json({ error: 'Reset session not found or already completed.' }, 400);
    }

    // Verify user still exists
    const [user] = await authDb
      .select({ id: users.id, email: users.email })
      .from(users)
      .where(eq(users.id, record.userId))
      .limit(1);

    if (!user) {
      return c.json({ error: 'No account found with this email address.' }, 404);
    }

    const rl = await checkRateLimit(`resend:reset:${record.email}`, 3, 3600);
    if (!rl.allowed) {
      return c.json({ error: 'Too many resend attempts. Please try again later.' }, 429, {
        'Retry-After': String(rl.retryAfter),
      });
    }

    const newOtp = generateOtp();
    const otpHash = hashOtp(newOtp);
    const expiresAt = new Date(Date.now() + OTP_EXPIRY_MS);

    await authDb
      .update(passwordResetOtps)
      .set({
        otpHash,
        attempts: 0,
        expiresAt,
      })
      .where(eq(passwordResetOtps.id, record.id));

    sendOtpEmail(record.email, newOtp, 'reset').catch(() => {});

    return c.json({ ok: true, email: record.email }, 200);
  } catch (err: any) {
    return c.json({ error: 'Failed to resend reset code.' }, 500);
  }
});

// POST /api/auth/logout (Protected)
authRoutes.post('/logout', honoAuthMiddleware, async (c) => {
  const userId = c.get('userId');
  try {
    const body = await c.req.json().catch(() => ({}));
    if (body.refreshToken) {
      const tokenHash = hashToken(body.refreshToken);
      await authDb.delete(userSessions).where(eq(userSessions.refreshTokenHash, tokenHash));
    } else {
      await authDb.delete(userSessions).where(eq(userSessions.userId, userId));
    }
    return c.json({ success: true, message: 'Logged out successfully.' });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// GET /api/auth/me (Protected)
authRoutes.get('/me', honoAuthMiddleware, async (c) => {
  const userId = c.get('userId');
  try {
    const [user] = await authDb.select().from(users).where(eq(users.id, userId)).limit(1);
    if (!user) {
      return c.json({ error: 'User not found.' }, 404);
    }

    return c.json({
      id: user.id,
      username: user.username,
      displayName: user.displayName,
      email: user.email,
      avatarUrl: user.avatarUrl,
      bio: user.bio,
      isPrivate: user.isPrivate,
      followerCount: user.followerCount,
      followingCount: user.followingCount,
    });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// PATCH /api/auth/me (Protected)
authRoutes.patch('/me', honoAuthMiddleware, async (c) => {
  const userId = c.get('userId');
  try {
    const body = await c.req.json();
    const allowedUpdates: Partial<typeof users.$inferInsert> = {};

    if (typeof body.displayName === 'string') allowedUpdates.displayName = body.displayName.trim();
    if (typeof body.bio === 'string') allowedUpdates.bio = body.bio.trim();
    if (typeof body.avatarUrl === 'string') allowedUpdates.avatarUrl = body.avatarUrl.trim();
    if (typeof body.isPrivate === 'boolean') allowedUpdates.isPrivate = body.isPrivate;

    allowedUpdates.updatedAt = new Date();

    const [updated] = await authDb
      .update(users)
      .set(allowedUpdates)
      .where(eq(users.id, userId))
      .returning();

    return c.json({
      id: updated.id,
      username: updated.username,
      displayName: updated.displayName,
      avatarUrl: updated.avatarUrl,
      bio: updated.bio,
      isPrivate: updated.isPrivate,
    });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// GET /api/auth/devices (Protected)
authRoutes.get('/devices', honoAuthMiddleware, async (c) => {
  const userId = c.get('userId');
  try {
    const devices = await authDb.select().from(userDevices).where(eq(userDevices.userId, userId));
    return c.json(devices);
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// POST /api/auth/devices/register (Protected)
authRoutes.post(
  '/devices/register',
  honoAuthMiddleware,
  zValidator('json', deviceRegisterSchema, validationHook('invalid_request')),
  async (c) => {
    const userId = c.get('userId');
    try {
      const body = c.req.valid('json');
      const { deviceName, platform, pushToken, pushEndpoint } = body;
      const endpoint = pushEndpoint || pushToken;

    // Enforce max 10 devices per user: if 10 or more, delete oldest
    const existingDevices = await authDb
      .select()
      .from(userDevices)
      .where(eq(userDevices.userId, userId))
      .orderBy(asc(userDevices.createdAt));

    if (existingDevices.length >= 10) {
      const excess = existingDevices.length - 10 + 1;
      const oldestDevices = existingDevices.slice(0, excess);
      for (const d of oldestDevices) {
        // Deleting device cascades to user_sessions via device_id foreign key
        await authDb.delete(userDevices).where(eq(userDevices.id, d.id));
      }
    }

    const [device] = await authDb
      .insert(userDevices)
      .values({
        userId,
        deviceName: deviceName || 'Android Device',
        platform: platform || 'android',
        pushToken: endpoint || null,
        lastSeenAt: new Date(),
      })
      .returning();

    return c.json(device, 201);
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// DELETE /api/auth/devices/:id (Protected)
authRoutes.delete('/devices/:id', honoAuthMiddleware, async (c) => {
  const userId = c.get('userId');
  const deviceId = c.req.param('id');
  if (!deviceId) {
    return c.json({ error: 'Device ID required' }, 400);
  }
  try {
    await authDb.delete(userDevices).where(and(eq(userDevices.id, deviceId), eq(userDevices.userId, userId)));
    return c.json({ success: true, message: 'Device revoked.' });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// POST /api/auth/logout-all (Protected)
authRoutes.post('/logout-all', honoAuthMiddleware, async (c) => {
  const userId = c.get('userId');
  try {
    await authDb.delete(userSessions).where(eq(userSessions.userId, userId));
    return c.json({ success: true, message: 'All sessions revoked.' });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// DELETE /api/auth/account (Protected - Full GDPR Wipe)
authRoutes.delete('/account', honoAuthMiddleware, async (c) => {
  const userId = c.get('userId');
  try {
    // Cascades delete sessions, devices, likes, playlists, follows, messages
    await authDb.delete(users).where(eq(users.id, userId));
    return c.json({ success: true, message: 'Account permanently deleted.' });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// POST /api/auth/terms/accept (Protected)
authRoutes.post('/terms/accept', honoAuthMiddleware, async (c) => {
  const userId = c.get('userId');
  try {
    const body = await c.req.json().catch(() => ({}));
    const version = typeof body?.version === 'string' && body.version.trim() ? body.version.trim() : '1.0';
    const now = new Date();
    await authDb
      .update(users)
      .set({
        termsAcceptedAt: now,
        termsVersion: version,
        updatedAt: now,
      })
      .where(eq(users.id, userId));
    return c.json({ acceptedAt: now.toISOString(), version }, 200);
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// GET /api/auth/terms/status (Protected)
authRoutes.get('/terms/status', honoAuthMiddleware, async (c) => {
  const userId = c.get('userId');
  try {
    const [user] = await authDb
      .select({
        termsAcceptedAt: users.termsAcceptedAt,
        termsVersion: users.termsVersion,
      })
      .from(users)
      .where(eq(users.id, userId))
      .limit(1);

    if (!user) {
      return c.json({ error: 'User not found' }, 404);
    }

    return c.json(
      {
        acceptedAt: user.termsAcceptedAt ? user.termsAcceptedAt.toISOString() : null,
        version: user.termsVersion || null,
        currentVersion: '1.0',
      },
      200
    );
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});


