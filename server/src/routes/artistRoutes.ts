/**
 * Dynamic Artist Discovery Routes (Hono)
 * Provides cached, real-time artist discovery for onboarding and category tuning.
 */

import { Hono } from 'hono';
import { eq, and, desc } from 'drizzle-orm';
import { db } from '../db/connection.js';
import { followedArtists } from '../db/schema.js';
import { requireAuth } from '../middleware/honoAuth.js';
import { AppEnv } from '../types.js';

export const artistRoutes = new Hono<AppEnv>();

interface CachedArtistResponse {
  artists: Array<{
    id: string;
    name: string;
    artworkUrl: string;
    genre: string;
    language: string;
    isFamous: boolean;
  }>;
  expiresAt: number;
}

const artistCache = new Map<string, CachedArtistResponse>();
const CACHE_TTL_MS = 60 * 60 * 1000; // 1 hour

artistRoutes.get('/top', async (c) => {
  const mode = c.req.query('mode') || 'initial';
  const languages = c.req.query('languages') || (mode === 'initial' ? 'hindi,english' : '');
  const genres = c.req.query('genres') || (mode === 'initial' ? 'pop,romantic' : '');
  const limit = Math.min(Math.max(Number(c.req.query('limit')) || 24, 1), 50);

  const cacheKey = `${mode}:::${languages.toLowerCase()}:::${genres.toLowerCase()}:::${limit}`;
  const cached = artistCache.get(cacheKey);
  const now = Date.now();

  if (cached && cached.expiresAt > now) {
    return c.json({ artists: cached.artists, cached: true });
  }

  const cleanLangs = languages
    .split(',')
    .map((l) => l.trim())
    .filter(Boolean);
  const cleanGenres = genres
    .split(',')
    .map((g) => g.trim())
    .filter(Boolean);

  // Construct queries across requested languages and genres
  const queries: string[] = [];
  if (mode === 'global_lang' && cleanLangs.length > 0) {
    queries.push(...cleanLangs.map((lang) => `top ${lang} artists`));
  } else if (mode === 'global_genre' && cleanGenres.length > 0) {
    queries.push(...cleanGenres.map((genre) => `top ${genre} artists`));
  } else {
    // initial mode
    if (cleanLangs.length > 0 && cleanGenres.length > 0) {
      for (const lang of cleanLangs) {
        for (const genre of cleanGenres) {
          queries.push(`top ${lang} ${genre} artists`);
        }
      }
    } else if (cleanLangs.length > 0) {
      queries.push(...cleanLangs.map((lang) => `top ${lang} artists`));
    } else if (cleanGenres.length > 0) {
      queries.push(...cleanGenres.map((genre) => `top ${genre} artists`));
    }
    if (queries.length === 0) {
      queries.push('top artists');
    }
  }

  const artists: Array<{
    id: string;
    name: string;
    artworkUrl: string;
    genre: string;
    language: string;
    isFamous: boolean;
  }> = [];

  const seenNames = new Set<string>();

  for (const q of queries.slice(0, 4)) {
    try {
      const targetUrl = `https://www.jiosaavn.com/api.php?__call=search.getArtistResults&_format=json&api_version=4&n=${limit}&p=1&q=${encodeURIComponent(
        q
      )}`;

      const res = await fetch(targetUrl, {
        headers: {
          'User-Agent':
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
          Referer: 'https://www.jiosaavn.com/',
        },
      });

      if (res.ok) {
        const data = (await res.json().catch(() => null)) as any;
        const results = data?.results || [];

        for (const item of results) {
          const rawName = item.name
            ? String(item.name)
                .replace(/&amp;/g, '&')
                .replace(/&#039;/g, "'")
                .trim()
            : '';
          const normName = rawName.toLowerCase();
          if (!normName || seenNames.has(normName)) continue;
          seenNames.add(normName);

          const rawImg = item.image || '';
          const artworkUrl = rawImg
            ? rawImg.replace(/50x50/g, '500x500').replace(/150x150/g, '500x500')
            : '';

          artists.push({
            id: `art-${item.id || normName.replace(/[^a-z0-9]/g, '-')}`,
            name: rawName,
            artworkUrl,
            genre: cleanGenres[0] || 'Pop',
            language: cleanLangs[0] || 'hindi',
            isFamous: true,
          });

          if (artists.length >= limit) break;
        }
      }
    } catch (err) {
      console.warn(`[artistRoutes] Artist search failed for query "${q}":`, err);
    }

    if (artists.length >= limit) break;
  }

  // Cache response for 1 hour
  artistCache.set(cacheKey, {
    artists,
    expiresAt: now + CACHE_TTL_MS,
  });

  return c.json({ artists, cached: false });
});

// ==========================================
// Followed Artists Endpoints
// ==========================================

artistRoutes.post('/follow', requireAuth, async (c) => {
  const userId = c.get('userId');
  if (!userId) {
    return c.json({ error: 'unauthorized' }, 401);
  }
  const body = await c.req.json().catch(() => null);
  if (!body || !body.artistId || !body.artistName) {
    return c.json({ error: 'invalid_payload', message: 'artistId and artistName are required' }, 400);
  }

  try {
    await db
      .insert(followedArtists)
      .values({
        userId,
        artistId: String(body.artistId),
        artistName: String(body.artistName),
        thumbnailUrl: body.thumbnailUrl ? String(body.thumbnailUrl) : null,
        followedAt: new Date(),
      })
      .onConflictDoUpdate({
        target: [followedArtists.userId, followedArtists.artistId],
        set: {
          artistName: String(body.artistName),
          thumbnailUrl: body.thumbnailUrl ? String(body.thumbnailUrl) : null,
          followedAt: new Date(),
        },
      });

    return c.json({ success: true, artistId: body.artistId, isFollowing: true });
  } catch (err: any) {
    console.error('[artistRoutes] Failed to follow artist:', err);
    return c.json({ error: 'failed_to_follow', message: err?.message }, 500);
  }
});

artistRoutes.delete('/unfollow/:artistId', requireAuth, async (c) => {
  const userId = c.get('userId');
  if (!userId) {
    return c.json({ error: 'unauthorized' }, 401);
  }
  const artistId = c.req.param('artistId');
  if (!artistId) {
    return c.json({ error: 'missing_artist_id' }, 400);
  }

  try {
    await db
      .delete(followedArtists)
      .where(and(eq(followedArtists.userId, userId), eq(followedArtists.artistId, artistId)));

    return c.json({ success: true, artistId, isFollowing: false });
  } catch (err: any) {
    console.error('[artistRoutes] Failed to unfollow artist:', err);
    return c.json({ error: 'failed_to_unfollow', message: err?.message }, 500);
  }
});

artistRoutes.get('/followed', requireAuth, async (c) => {
  const userId = c.get('userId');
  if (!userId) {
    return c.json({ error: 'unauthorized' }, 401);
  }

  try {
    const list = await db
      .select({
        artistId: followedArtists.artistId,
        artistName: followedArtists.artistName,
        thumbnailUrl: followedArtists.thumbnailUrl,
        followedAt: followedArtists.followedAt,
      })
      .from(followedArtists)
      .where(eq(followedArtists.userId, userId))
      .orderBy(desc(followedArtists.followedAt));

    return c.json({ artists: list });
  } catch (err: any) {
    console.error('[artistRoutes] Failed to fetch followed artists:', err);
    return c.json({ error: 'failed_to_fetch', message: err?.message }, 500);
  }
});

artistRoutes.get('/is-following/:artistId', requireAuth, async (c) => {
  const userId = c.get('userId');
  if (!userId) {
    return c.json({ isFollowing: false });
  }
  const artistId = c.req.param('artistId');
  if (!artistId) {
    return c.json({ isFollowing: false });
  }

  try {
    const [row] = await db
      .select({ artistId: followedArtists.artistId })
      .from(followedArtists)
      .where(and(eq(followedArtists.userId, userId), eq(followedArtists.artistId, artistId)))
      .limit(1);

    return c.json({ isFollowing: !!row });
  } catch {
    return c.json({ isFollowing: false });
  }
});

