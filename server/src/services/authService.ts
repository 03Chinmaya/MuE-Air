/**
 * Auth Service
 * Argon2id hashing and Supabase-compatible JWT token generation per:
 * docs/00_STACK_LOCK.md Section 4 & docs/07_AUTHENTICATION.md.
 */

import argon2 from 'argon2';
import jwt from 'jsonwebtoken';
import crypto from 'node:crypto';
import { eq, inArray, ilike, or, sql } from 'drizzle-orm';
import { authDb } from '../db/connection.js';
import { users, userDevices } from '../db/schema.js';

// Required for security fix: dynamically resolve JWT secret at runtime to avoid static ESM import race
export function getJwtSecret(): string {
  const secret = process.env.JWT_SECRET || process.env.SUPABASE_JWT_SECRET;
  if (!secret) {
    throw new Error('FATAL: JWT_SECRET or SUPABASE_JWT_SECRET must be set in environment variables.');
  }
  return secret;
}
const ACCESS_TOKEN_EXPIRY = '1h';
const GUEST_ACCESS_TOKEN_EXPIRY = '24h';
const REFRESH_TOKEN_EXPIRY_DAYS = 30;

/**
 * Test mode detection.
 * Strictly requires both NODE_ENV === 'test' AND SOMU_TEST_MODE === '1'.
 * Impossible to trigger accidentally in production.
 */
export const isTestEnv =
  process.env.NODE_ENV === 'test' &&
  process.env.SOMU_TEST_MODE === '1';

/**
 * Argon2id parameters.
 * Production parameters strictly adhere to docs/15_SECURITY.md §5.1 (m = 65536 KB / 64 MB, t = 3, p = 1).
 * Test environment parameters preserve cryptographic correctness while eliminating
 * artificial wall-clock CPU overhead in high-iteration test suites.
 */
const ARGON2_PARAMS = isTestEnv
  ? { memoryCost: 1024, timeCost: 2, parallelism: 1 }
  : { memoryCost: 65536, timeCost: 3, parallelism: 1 };

export const PROD_DUMMY_HASH = '$argon2id$v=19$m=65536,t=3,p=1$PnMLss+DsVWZxb0SnWPwLw$jrs+H3cq1qsQECQ2oVeok8kUvq12NeTfP4O4tKuYU7g';
export const TEST_DUMMY_HASH = '$argon2id$v=19$m=1024,t=2,p=1$rj/ep/vZ4vW3GCl/3BF4Rw$zOiYWJHywE7XOBE1d71Es4JH4L9mQR8VmaWlXteMr6E';

export const DUMMY_HASH = isTestEnv ? TEST_DUMMY_HASH : PROD_DUMMY_HASH;

/**
 * Parses the m= memory cost factor from an Argon2 encoded hash string.
 */
export function parseArgonCost(hash: string): number {
  const match = hash.match(/m=(\d+)/);
  return match ? parseInt(match[1], 10) : 0;
}

export interface TokenPayload {
  sub: string;
  role: 'guest' | 'authenticated' | string;
  is_guest: boolean;
  device_id?: string;
  aud: string;
  username?: string;
  iat?: number;
  exp?: number;
}

export async function hashCredential(plain: string): Promise<string> {
  return argon2.hash(plain, {
    type: argon2.argon2id,
    ...ARGON2_PARAMS,
  });
}

export async function verifyCredential(hash: string, plain: string): Promise<boolean> {
  try {
    return await argon2.verify(hash, plain);
  } catch {
    return false;
  }
}

export function generateAccessToken(
  userId: string,
  username?: string,
  deviceId?: string,
  isGuest: boolean = false
): string {
  const payload: TokenPayload = {
    sub: userId,
    role: isGuest ? 'guest' : 'authenticated',
    is_guest: isGuest,
    aud: isGuest ? 'guest' : 'authenticated',
    ...(username ? { username } : {}),
    ...(deviceId ? { device_id: deviceId } : {}),
  };
  return jwt.sign(payload, getJwtSecret(), {
    expiresIn: isGuest ? GUEST_ACCESS_TOKEN_EXPIRY : ACCESS_TOKEN_EXPIRY,
  });
}

export function generateGuestToken(userId: string, deviceId?: string): string {
  return generateAccessToken(userId, undefined, deviceId, true);
}

export function generateRefreshToken(): { token: string; hash: string; expiresAt: Date } {
  const token = crypto.randomBytes(40).toString('hex');
  const hash = crypto.createHash('sha256').update(token).digest('hex');
  const expiresAt = new Date(Date.now() + REFRESH_TOKEN_EXPIRY_DAYS * 24 * 60 * 60 * 1000);
  return { token, hash, expiresAt };
}

export function hashToken(token: string): string {
  return crypto.createHash('sha256').update(token).digest('hex');
}

export function verifyAccessToken(token: string): TokenPayload | null {
  try {
    const raw = jwt.verify(token, getJwtSecret(), { algorithms: ['HS256'] }) as any;
    if (!raw || !raw.sub) return null;
    return {
      sub: raw.sub,
      role: raw.role || 'authenticated',
      is_guest: raw.is_guest === true || raw.role === 'guest',
      aud: raw.aud || 'authenticated',
      username: raw.username,
      device_id: raw.device_id,
      iat: raw.iat,
      exp: raw.exp,
    };
  } catch {
    return null;
  }
}

// ==========================================
// User & Profile Query/Mutation Helpers (authDb)
// Strictly encapsulated per BLK-02
// ==========================================

export async function getUserByEmail(email: string) {
  const [user] = await authDb
    .select()
    .from(users)
    .where(eq(users.email, email.toLowerCase().trim()))
    .limit(1);
  return user ?? null;
}

export async function getUserById(id: string) {
  const [user] = await authDb
    .select()
    .from(users)
    .where(eq(users.id, id))
    .limit(1);
  return user ?? null;
}

export async function getUserByUsername(username: string) {
  const [user] = await authDb
    .select()
    .from(users)
    .where(eq(users.username, username.trim()))
    .limit(1);
  return user ?? null;
}

export async function getUsersByIds(ids: string[]) {
  if (ids.length === 0) return [];
  return await authDb
    .select()
    .from(users)
    .where(inArray(users.id, ids));
}

export async function searchUsers(query: string, limit = 20, offset = 0) {
  const trimmed = query.trim();
  if (!trimmed) return [];
  return await authDb
    .select()
    .from(users)
    .where(
      or(
        ilike(users.username, `%${trimmed}%`),
        ilike(users.displayName, `%${trimmed}%`)
      )
    )
    .limit(limit)
    .offset(offset);
}

export async function updateUserProfile(id: string, data: Partial<typeof users.$inferInsert>) {
  const [updated] = await authDb
    .update(users)
    .set({
      ...data,
      updatedAt: new Date(),
    })
    .where(eq(users.id, id))
    .returning();
  return updated ?? null;
}

export async function upsertProfile(userId: string, data: Partial<typeof users.$inferInsert>) {
  const existing = await getUserById(userId);
  if (existing) {
    return await updateUserProfile(userId, data);
  } else {
    const [created] = await authDb
      .insert(users)
      .values({
        id: userId,
        username: data.username ?? `user_${userId.slice(0, 8)}`,
        displayName: data.displayName ?? 'New User',
        ...data,
      })
      .returning();
    return created ?? null;
  }
}

export async function resolveUser(identifier: string) {
  const trimmed = identifier.trim();
  const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(trimmed);
  if (isUuid) {
    const user = await getUserById(trimmed);
    if (user) return user;
  }
  if (trimmed.includes('@')) {
    return await getUserByEmail(trimmed);
  }
  return await getUserByUsername(trimmed);
}

export async function incrementFollowCounts(followerId: string, followeeId: string) {
  await authDb.transaction(async (tx) => {
    await tx
      .update(users)
      .set({ followingCount: sql`following_count + 1` })
      .where(eq(users.id, followerId));

    await tx
      .update(users)
      .set({ followerCount: sql`follower_count + 1` })
      .where(eq(users.id, followeeId));
  });
}

export async function decrementFollowCounts(followerId: string, followeeId: string) {
  await authDb.transaction(async (tx) => {
    await tx
      .update(users)
      .set({ followingCount: sql`GREATEST(0, following_count - 1)` })
      .where(eq(users.id, followerId));

    await tx
      .update(users)
      .set({ followerCount: sql`GREATEST(0, follower_count - 1)` })
      .where(eq(users.id, followeeId));
  });
}

export async function getUserDevices(userId: string) {
  return await authDb
    .select()
    .from(userDevices)
    .where(eq(userDevices.userId, userId));
}

/**
 * Executes a privileged maintenance query (e.g. background data retention sweepers)
 * using the authDb connection. Strictly limited to maintenance jobs.
 */
export async function executePrivilegedQuery<T>(fn: (db: typeof authDb) => Promise<T>): Promise<T> {
  return await fn(authDb);
}
