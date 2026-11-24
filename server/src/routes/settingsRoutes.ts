import { Hono } from 'hono';
import { eq, sql } from 'drizzle-orm';
import { z } from 'zod';
import { zValidator } from '@hono/zod-validator';
import { validationHook } from '../lib/validators.js';
import { requireAuth } from '../middleware/honoAuth.js';
import { userSettings } from '../db/schema.js';
import { AppEnv } from '../types.js';

export const settingsRoutes = new Hono<AppEnv>();

settingsRoutes.use('*', requireAuth);

const postSettingsSchema = z.object({
  settings: z.array(
    z.object({
      key: z.string().min(1).max(128),
      value: z.any(),
      deviceId: z.string().nullable().optional(),
      updatedAt: z.string().optional(),
    })
  ),
});

// GET /api/settings - retrieve all cloud settings for the current user
settingsRoutes.get('/', async (c) => {
  const db = c.get('db');
  const userId = c.get('userId');

  const rows = await db
    .select()
    .from(userSettings)
    .where(eq(userSettings.userId, userId));

  return c.json({
    settings: rows.map((r) => ({
      key: r.key,
      value: r.value,
      deviceId: r.deviceId,
      updatedAt: r.updatedAt.toISOString(),
    })),
  });
});

// POST /api/settings - batch upsert settings with last-write-wins conflict resolution
settingsRoutes.post('/', zValidator('json', postSettingsSchema, validationHook()), async (c) => {
  const db = c.get('db');
  const userId = c.get('userId');
  const body = c.req.valid('json');

  if (body.settings.length > 0) {
    for (const item of body.settings) {
      const incomingDate = item.updatedAt ? new Date(item.updatedAt) : new Date();

      await db
        .insert(userSettings)
        .values({
          userId,
          key: item.key,
          value: item.value,
          deviceId: item.deviceId ?? null,
          updatedAt: incomingDate,
        })
        .onConflictDoUpdate({
          target: [userSettings.userId, userSettings.key],
          set: {
            value: sql`EXCLUDED.value`,
            deviceId: sql`EXCLUDED.device_id`,
            updatedAt: sql`EXCLUDED.updated_at`,
          },
          where: sql`user_settings.updated_at <= EXCLUDED.updated_at`,
        });
    }
  }

  const allSettings = await db
    .select()
    .from(userSettings)
    .where(eq(userSettings.userId, userId));

  return c.json({
    settings: allSettings.map((r) => ({
      key: r.key,
      value: r.value,
      deviceId: r.deviceId,
      updatedAt: r.updatedAt.toISOString(),
    })),
  });
});
