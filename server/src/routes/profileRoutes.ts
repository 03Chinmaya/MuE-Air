import { safeError } from '../lib/errors.js';
/**
 * Profile Routes (Hono)
 * Manages user profile updates and Supabase Storage signed avatar uploads.
 * Implements docs/00_STACK_LOCK.md, docs/05_API.md Section 16B & docs/29_SOCIAL.md.
 *
 * Rules:
 * - Public reads from users_public view.
 * - Profile mutations use authService.
 * - Avatar signed URL generated via Supabase admin storage client.
 * - Service role is strictly isolated to Storage; never used for DB queries.
 */

import { Hono } from 'hono';
import { eq, and, sql } from 'drizzle-orm';
import { z } from 'zod';
import { zValidator } from '@hono/zod-validator';
import { patchProfileSchema, validationHook } from '../lib/validators.js';
import { createClient } from '@supabase/supabase-js';
import * as authService from '../services/authService.js';
import { usersPublic, follows } from '../db/schema.js';
import { honoAuthMiddleware } from '../middleware/honoAuth.js';
import { checkRateLimit } from '../middleware/rateLimiter.js';
import { validateUsername } from '../utils/username.js';
import { AppEnv } from '../types.js';

export const profileRoutes = new Hono<AppEnv>();

profileRoutes.use('*', honoAuthMiddleware);

const SUPABASE_URL = (() => {
  const url = process.env.SUPABASE_URL?.trim();
  if (!url) {
    throw new Error(
      'SUPABASE_URL is not configured. Set it in the server environment (Render dashboard → Environment).'
    );
  }
  return url;
})();
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY || '';
const AVATARS_BUCKET = 'avatars';

function getStorageClient() {
  if (!SUPABASE_SERVICE_ROLE_KEY) return null;
  return createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false },
  });
}

// ==========================================
// 1. PROFILE DETAILS
// ==========================================

// GET /api/profile/me
profileRoutes.get('/me', async (c) => {
  const db = c.get('db');
  const userId = c.get('userId');

  const [profile] = await db
    .select()
    .from(usersPublic)
    .where(eq(usersPublic.id, userId))
    .limit(1);

  if (!profile) {
    return c.json({ error: 'User profile not found' }, 404);
  }

  return c.json(profile);
});

// GET /api/profile/:userId
profileRoutes.get('/:userId', async (c) => {
  const db = c.get('db');
  const viewerId = c.get('userId');
  const targetUserId = c.req.param('userId');

  if (targetUserId === 'me') {
    const [profile] = await db
      .select()
      .from(usersPublic)
      .where(eq(usersPublic.id, viewerId))
      .limit(1);
    return c.json(profile);
  }

  // Check if either user has blocked the other via is_blocked helper
  const [blockCheck] = await db.execute<{ is_blocked: boolean }>(
    sql`SELECT is_blocked(${viewerId}::uuid, ${targetUserId}::uuid) AS is_blocked`
  );

  if (blockCheck?.is_blocked) {
    return c.json({ error: 'User not found' }, 404);
  }

  const [targetUser] = await db
    .select()
    .from(usersPublic)
    .where(eq(usersPublic.id, targetUserId))
    .limit(1);

  if (!targetUser) {
    return c.json({ error: 'User not found' }, 404);
  }

  const [followEdge] = await db
    .select()
    .from(follows)
    .where(and(eq(follows.followerId, viewerId), eq(follows.followeeId, targetUserId)))
    .limit(1);

  return c.json({
    ...targetUser,
    isFollowing: Boolean(followEdge),
  });
});

const NINETY_DAYS_MS = 90 * 24 * 60 * 60 * 1000;

// PATCH /api/profile/me
profileRoutes.patch(
  '/me',
  zValidator('json', patchProfileSchema, validationHook('Invalid profile data')),
  async (c) => {
    const userId = c.get('userId');
    try {
      const body = c.req.valid('json');
      const updates: Record<string, any> = { updatedAt: new Date() };

    if (body.displayName !== undefined) {
      const dn = body.displayName.trim();
      if (dn.length < 1 || dn.length > 40) {
        return c.json({ error: 'Display name must be between 1 and 40 characters' }, 400);
      }
      updates.displayName = dn;
    }

    if (body.username !== undefined) {
      const cleanUsername = body.username.trim();

      const currentUser = await authService.getUserById(userId);

      if (!currentUser) {
        return c.json({ error: 'User not found' }, 404);
      }

      if (cleanUsername !== currentUser.username) {
        const changedAtMs = currentUser.usernameChangedAt
          ? new Date(currentUser.usernameChangedAt).getTime()
          : 0;
        const timeSinceLastChange = Date.now() - changedAtMs;
        const changeCount = currentUser.usernameChangeCount ?? 0;

        if (changedAtMs > 0 && timeSinceLastChange < NINETY_DAYS_MS && changeCount >= 2) {
          return c.json(
            { error: 'Username can only be changed twice every 90 days.' },
            403
          );
        }

        const existing = await authService.getUserByUsername(cleanUsername);

        if (existing && existing.id !== userId) {
          return c.json({ error: 'Username is already taken.' }, 400);
        }

        const isWindowExpired = changedAtMs > 0 && timeSinceLastChange >= NINETY_DAYS_MS;
        updates.username = cleanUsername;
        updates.usernameChangeCount = isWindowExpired ? 1 : changeCount + 1;
        updates.usernameChangedAt = new Date();
      }
    }

    if (body.bio !== undefined) {
      const bio = body.bio.trim();
      if (bio.length > 160) {
        return c.json({ error: 'Bio must be at most 160 characters' }, 400);
      }
      updates.bio = bio;
    }

    if (body.avatarUrl !== undefined) {
      updates.avatarUrl = body.avatarUrl ? body.avatarUrl.trim() : null;
    }

    if (body.isPrivate !== undefined) {
      updates.isPrivate = Boolean(body.isPrivate);
    }

    const updated = await authService.updateUserProfile(userId, updates);
    if (!updated) {
      return c.json({ error: 'Failed to update profile' }, 500);
    }

    return c.json({
      id: updated.id,
      username: updated.username,
      displayName: updated.displayName,
      bio: updated.bio,
      avatarUrl: updated.avatarUrl,
      isPrivate: updated.isPrivate,
    });
  } catch (err: any) {
    if (
      err?.code === '23505' ||
      err?.message?.includes('duplicate key') ||
      err?.message?.includes('users_username_unique')
    ) {
      return c.json({ error: 'Username is already taken.' }, 400);
    }
    return c.json(safeError(err, c, 500), 500);
  }
});

// ==========================================
// 2. AVATAR UPLOAD & REMOVAL
// ==========================================

// POST /api/profile/me/avatar (Generate signed upload URL)
profileRoutes.post('/me/avatar', async (c) => {
  const userId = c.get('userId');

  // Rate limit: 10/day per user
  const rl = await checkRateLimit('avatar', userId, 10, 86400);
  if (!rl.allowed) {
    c.header('Retry-After', String(rl.retryAfter));
    return c.json({ error: 'Daily avatar upload limit exceeded.' }, 429);
  }

  const timestamp = Date.now();
  const filePath = `avatars/${userId}/${timestamp}.jpg`;
  const storage = getStorageClient();

  let signedUrl = '';
  let publicUrl = `${SUPABASE_URL}/storage/v1/object/public/${AVATARS_BUCKET}/${filePath}`;

  if (!storage) {
    return c.json({ error: 'Supabase Storage is not configured (missing SUPABASE_SERVICE_ROLE_KEY)' }, 500);
  }

  const { data, error } = await storage.storage
    .from(AVATARS_BUCKET)
    .createSignedUploadUrl(filePath);

  if (error || !data) {
    return c.json({ error: error?.message || 'Failed to create signed upload URL' }, 500);
  }
  signedUrl = data.signedUrl;

  return c.json({
    signedUrl,
    uploadUrl: signedUrl,
    path: filePath,
    publicUrl,
    expiresIn: 300, // 5 min TTL
  });
});

// POST /api/profile/me/avatar/complete
profileRoutes.post('/me/avatar/complete', async (c) => {
  const userId = c.get('userId');
  try {
    const body = await c.req.json();
    const { publicUrl, path } = body;

    const resolvedUrl =
      publicUrl || (path ? `${SUPABASE_URL}/storage/v1/object/public/${AVATARS_BUCKET}/${path}` : null);

    if (!resolvedUrl) {
      return c.json({ error: 'Avatar URL or path is required' }, 400);
    }

    // Fetch previous avatar to clean up old object
    const current = await authService.getUserById(userId);

    if (current?.avatarUrl && current.avatarUrl !== resolvedUrl) {
      const storage = getStorageClient();
      if (storage) {
        try {
          const oldPath = current.avatarUrl.split(`/${AVATARS_BUCKET}/`)[1];
          if (oldPath) {
            await storage.storage.from(AVATARS_BUCKET).remove([oldPath]);
          }
        } catch {
          // Non-blocking cleanup
        }
      }
    }

    const updated = await authService.updateUserProfile(userId, { avatarUrl: resolvedUrl });
    if (!updated) {
      return c.json({ error: 'Failed to update avatar' }, 500);
    }

    return c.json({
      id: updated.id,
      avatarUrl: updated.avatarUrl,
    });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// DELETE /api/profile/me/avatar
profileRoutes.delete('/me/avatar', async (c) => {
  const userId = c.get('userId');
  try {
    const current = await authService.getUserById(userId);

    if (current?.avatarUrl) {
      const storage = getStorageClient();
      if (storage) {
        try {
          const oldPath = current.avatarUrl.split(`/${AVATARS_BUCKET}/`)[1];
          if (oldPath) {
            await storage.storage.from(AVATARS_BUCKET).remove([oldPath]);
          }
        } catch {
          // Non-blocking
        }
      }
    }

    await authService.updateUserProfile(userId, { avatarUrl: null });

    return c.json({ success: true, message: 'Avatar removed.' });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});
