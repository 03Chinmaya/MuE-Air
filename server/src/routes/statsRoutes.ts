import { safeError } from '../lib/errors.js';
/**
 * Stats Routes (Hono)
 * Aggregate play statistics and rankings.
 * Strictly adheres to docs/05_API.md Section 13.6 and docs/04_DATABASE.md Section 28.
 */

import { Hono } from 'hono';
import { eq, desc } from 'drizzle-orm';
import { songPlayStats, artistPlayStats, genrePlayStats } from '../db/schema.js';
import { honoAuthMiddleware } from '../middleware/honoAuth.js';
import { AppEnv } from '../types.js';

export const statsRoutes = new Hono<AppEnv>();

statsRoutes.use('*', honoAuthMiddleware);

// POST /api/stats/aggregate
statsRoutes.post('/aggregate', async (c) => {
  const db = c.get('db');
  const userId = c.get('userId');
  try {
    const body = await c.req.json();
    const { tracks = [], artists = [], genres = [] } = body;

    // Batch upsert tracks
    for (const t of tracks) {
      if (t.provider && t.providerTrackId) {
        await db
          .insert(songPlayStats)
          .values({
            userId,
            provider: t.provider,
            providerTrackId: t.providerTrackId,
            playCount: t.playCount || 1,
            completionCount: t.completionCount || 0,
            skipCount: t.skipCount || 0,
            updatedAt: new Date(),
          })
          .onConflictDoNothing();
      }
    }

    // Batch upsert artists
    for (const a of artists) {
      if (a.artistName) {
        await db
          .insert(artistPlayStats)
          .values({
            userId,
            artistName: a.artistName,
            playCount: a.playCount || 1,
            updatedAt: new Date(),
          })
          .onConflictDoNothing();
      }
    }

    // Batch upsert genres
    for (const g of genres) {
      if (g.genre) {
        await db
          .insert(genrePlayStats)
          .values({
            userId,
            genre: g.genre,
            playCount: g.playCount || 1,
            updatedAt: new Date(),
          })
          .onConflictDoNothing();
      }
    }

    return c.json({ success: true, count: tracks.length + artists.length + genres.length });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// GET /api/stats/top-tracks
statsRoutes.get('/top-tracks', async (c) => {
  const db = c.get('db');
  const userId = c.get('userId');
  try {
    const top = await db
      .select()
      .from(songPlayStats)
      .where(eq(songPlayStats.userId, userId))
      .orderBy(desc(songPlayStats.playCount))
      .limit(50);

    return c.json(top);
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});
