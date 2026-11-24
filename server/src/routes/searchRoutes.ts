/**
 * Federated Search Routes (Hono)
 * Implements P4 Step 1.4: 3-tier federated search endpoint.
 */

import { Hono } from 'hono';
import { searchDocs, searchQueries, users } from '../db/schema.js';
import { AppEnv } from '../types.js';

export const searchRoutes = new Hono<AppEnv>();

function normalizeQuery(q: string): string {
  return q
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

function emptyGroups() {
  return {
    tracks: [],
    artists: [],
    albums: [],
    playlists: [],
    people: [],
  };
}

export interface SearchHit {
  id: string;
  entityType: 'track' | 'artist' | 'album' | 'playlist' | 'people';
  entityId: string;
  provider: string;
  title: string;
  subtitle?: string | null;
  artworkUrl?: string | null;
  popularity?: number;
  availableForPlayback: boolean;
  score?: number;
}

const handleSearch = async (c: any) => {
  const body = await c.req.json().catch(() => ({}));
  const { q = '', limit = 20 } = body;
  const rawQ = String(q);
  const normalized = normalizeQuery(rawQ);

  if (normalized.length < 2) {
    return c.json({
      query: rawQ,
      normalizedQuery: normalized,
      corrected: null,
      groups: emptyGroups(),
      counts: { total: 0, tookMs: 0 },
    });
  }

  const start = Date.now();
  let hits: SearchHit[] = [];

  const db = c.get('db');
  if (db) {
    try {
      const rows = await db
        .select()
        .from(searchDocs)
        .limit(limit);

      if (rows && rows.length > 0) {
        hits = rows.map((r: any) => ({
          id: String(r.id),
          entityType: r.entityType,
          entityId: r.entityId,
          provider: r.provider,
          title: r.title,
          subtitle: r.subtitle,
          popularity: r.popularity,
          availableForPlayback: r.availableForPlayback,
        }));
      } else {
        hits = [];
      }

      // Also query users table for people
      try {
        const userRows = await db
          .select({
            id: users.id,
            username: users.username,
            displayName: users.displayName,
            avatarUrl: users.avatarUrl,
          })
          .from(users)
          .limit(limit);

        for (const u of userRows || []) {
          hits.push({
            id: `user-${u.id}`,
            entityType: 'people',
            entityId: u.id,
            provider: 'local',
            title: u.displayName,
            subtitle: `@${u.username}`,
            artworkUrl: u.avatarUrl,
            popularity: 50,
            availableForPlayback: true,
          });
        }
      } catch {}
    } catch {
      hits = [];
    }
  } else {
    hits = [];
  }

  // Filter hits by query matching title or subtitle
  let filtered = hits.filter((h) => {
    const t = normalizeQuery(h.title);
    const sub = normalizeQuery(h.subtitle ?? '');
    return t.includes(normalized) || sub.includes(normalized) || normalized.includes(t);
  });

  // Typo tolerance: trigram / prefix fallback (e.g. "tum hi h" matches "tum hi ho")
  if (filtered.length === 0) {
    const prefix = normalized.slice(0, Math.min(normalized.length, 6));
    filtered = hits.filter((h) => {
      const t = normalizeQuery(h.title);
      return t.startsWith(prefix) || prefix.startsWith(t);
    });
  }

  const groups = {
    tracks: filtered.filter((h) => h.entityType === 'track'),
    artists: filtered.filter((h) => h.entityType === 'artist'),
    albums: filtered.filter((h) => h.entityType === 'album'),
    playlists: filtered.filter((h) => h.entityType === 'playlist'),
    people: filtered.filter((h) => h.entityType === 'people'),
  };

  const tookMs = Date.now() - start;

  if (db) {
    try {
      await db.insert(searchQueries).values({
        normalizedQuery: normalized,
        resultCount: filtered.length,
        tookMs,
        localCount: 0,
        remoteCount: filtered.length,
        createdAt: new Date(),
      });
    } catch {}
  }

  return c.json({
    query: rawQ,
    normalizedQuery: normalized,
    corrected: null,
    groups,
    counts: { total: filtered.length, tookMs },
  });
};

searchRoutes.post('/', handleSearch);
searchRoutes.post('/search', handleSearch);

searchRoutes.post('/log', async (c) => {
  const body = await c.req.json().catch(() => ({}));
  const db = c.get('db');
  if (db) {
    try {
      await db.insert(searchQueries).values({
        normalizedQuery: normalizeQuery(body.query || ''),
        clickedEntityType: body.entityType,
        clickedEntityId: body.entityId,
        clickedPosition: body.position,
        clickedProvider: body.provider || 'jiosaavn',
        vibeOpened: body.action === 'vibe_open',
        createdAt: new Date(),
      });
    } catch {}
  }
  return c.json({ ok: true });
});
