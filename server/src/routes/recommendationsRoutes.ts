/**
 * Recommendations Routes (Hono)
 * Shelves, feedback loop, and radio candidates.
 * Strictly adheres to docs/05_API.md Section 13.
 */

import { Hono } from 'hono';
import { songPlayStats } from '../db/schema.js';
import { honoAuthMiddleware } from '../middleware/honoAuth.js';
import { AppEnv } from '../types.js';
import { fetchYtmData } from './youtubeRoutes.js';

export const recommendationsRoutes = new Hono<AppEnv>();

// GET /api/recommendations/home
recommendationsRoutes.get('/home', async (c) => {
  let trendingTracks: any[] = [];
  let newReleasesItems: any[] = [];

  try {
    const [trendingRes, newReleasesRes] = await Promise.allSettled([
      fetchYtmData('trending'),
      fetchYtmData('new-releases'),
    ]);

    if (trendingRes.status === 'fulfilled' && trendingRes.value?.tracks) {
      trendingTracks = trendingRes.value.tracks;
    }

    if (newReleasesRes.status === 'fulfilled') {
      const val = newReleasesRes.value;
      newReleasesItems = [
        ...(Array.isArray(val?.trending) ? val.trending : []),
        ...(Array.isArray(val?.releases) ? val.releases : []),
      ];
    }
  } catch (err) {
    console.warn('[recommendationsRoutes] Failed to fetch home data:', err);
  }

  const shelves = [
    {
      key: 'quick_picks',
      title: 'Quick Picks',
      subtitle: 'Based on your recent listening',
      source: 'aggregated',
      items: trendingTracks.slice(0, 10),
    },
    {
      key: 'trending_now',
      title: 'Trending Now',
      subtitle: 'Popular across MuE',
      source: 'jiosaavn',
      items: trendingTracks.slice(10, 25),
    },
    {
      key: 'for_you',
      title: 'For You',
      subtitle: 'Handpicked favorites and new discoveries',
      source: 'aggregated',
      items: newReleasesItems.slice(0, 15),
    },
  ];

  return c.json({
    shelves,
    generatedAt: Date.now(),
    ttlSeconds: 1800,
  });
});

// POST /api/recommendations/feedback (Protected, fire-and-forget)
recommendationsRoutes.post('/feedback', honoAuthMiddleware, async (c) => {
  const db = c.get('db');
  const userId = c.get('userId');
  try {
    const body = await c.req.json();
    const { trackId, action, provider = 'youtube_music' } = body;

    if (!trackId || !action) {
      return c.json({ error: 'Missing trackId or action' }, 400);
    }

    // Fire and forget stats increment
    if (action === 'complete' || action === 'play') {
      try {
        await db
          .insert(songPlayStats)
          .values({
            userId,
            provider,
            providerTrackId: trackId,
            playCount: 1,
            completionCount: action === 'complete' ? 1 : 0,
            updatedAt: new Date(),
          })
          .onConflictDoNothing();
      } catch {
        // Non-blocking
      }
    }

    return c.json({ recorded: true });
  } catch {
    return c.json({ recorded: false }, 500);
  }
});
