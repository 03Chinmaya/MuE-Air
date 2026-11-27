/**
 * Playlist Import Routes (Hono)
 * Connects to the YouTube Music sidecar strictly for playlist imports.
 * The sidecar is used ONLY for playlist import, never for metadata browsing or streaming.
 */

import { Hono } from 'hono';
import { and, eq, isNull, sql } from 'drizzle-orm';
import { db } from '../db/connection.js';
import { importedPlaylists, users } from '../db/schema.js';
import { AppEnv } from '../types.js';
import { validateImportTarget } from '../util/validateImportTarget.js';

export const importRoutes = new Hono<AppEnv>();

if (process.env.NODE_ENV === 'production' && !process.env.SIDECAR_SECRET) {
  throw new Error('SIDECAR_SECRET is required in production');
}

export interface ImportedTrack {
  videoId: string;
  title: string;
  artists: string[] | string;
  album: string | null;
  durationMs: number;
  thumbnail?: string | null;
}

export interface ImportedPlaylist {
  title: string;
  tracks: ImportedTrack[];
  thumbnail?: string | null;
}

export class SidecarHttpError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

export function detectSource(url: string): 'spotify' | 'youtube' {
  const lower = url.toLowerCase();
  if (
    lower.includes('open.spotify.com/') ||
    lower.includes('spotify.link/') ||
    lower.includes('spotify:playlist:')
  ) {
    return 'spotify';
  }
  return 'youtube';
}

function normalizeSidecarTracks(tracks: any[]) {
  return tracks.map((t: any) => ({
    videoId: t.videoId || '',
    title: t.title || 'Unknown',
    artists: Array.isArray(t.artists) ? t.artists.join(', ') : (t.artists || 'Unknown Artist'),
    album: t.album || null,
    durationMs: t.durationMs || 0,
    artworkUrl: t.artworkUrl || t.thumbnail || null,
  }));
}

async function fetchFromSidecar<T>(endpoint: string, body?: any): Promise<T> {
  const sidecarBaseUrl = process.env.YTMUSIC_SIDECAR_URL;
  if (!sidecarBaseUrl) {
    throw new Error('YTMUSIC_SIDECAR_URL not configured for playlist import');
  }

  const targetUrl = `${sidecarBaseUrl.replace(/\/$/, '')}${endpoint}`;
  let lastError: unknown;

  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const res = await fetch(targetUrl, {
        method: body ? 'POST' : 'GET',
        headers: {
          'Content-Type': 'application/json',
          'X-Sidecar-Secret': process.env.SIDECAR_SECRET ?? '',
        },
        body: body ? JSON.stringify(body) : undefined,
        signal: (AbortSignal as any).timeout(90_000),
      });

      if (!res.ok) {
        throw new SidecarHttpError(res.status, `Sidecar import failed: HTTP ${res.status}`);
      }

      return (await res.json()) as T;
    } catch (err: any) {
      if (err instanceof SidecarHttpError) {
        throw err;
      }
      lastError = err;
      const isTimeoutOrNetwork =
        err?.name === 'TimeoutError' ||
        err?.name === 'AbortError' ||
        err?.code === 'ECONNRESET' ||
        err?.code === 'ETIMEDOUT' ||
        err?.cause?.code === 'ECONNRESET' ||
        err?.cause?.code === 'ETIMEDOUT';
      if (attempt === 0 && isTimeoutOrNetwork) {
        continue;
      }
      throw err;
    }
  }

  throw lastError;
}

// Import public playlist by URL or ID
importRoutes.post('/playlist', async (c) => {
  try {
    const tokenPayload = c.get('tokenPayload');
    const userId = c.get('userId');
    if (tokenPayload?.is_guest === true && userId) {
      const reqDb = c.get('db') || db;
      const [guestRow] = await reqDb
        .select({ deviceFingerprint: users.deviceFingerprint })
        .from(users)
        .where(eq(users.id, userId))
        .limit(1);

      const deviceFp = guestRow?.deviceFingerprint;
      const countRows = deviceFp
        ? await reqDb
            .select({ count: sql<number>`count(*)::int` })
            .from(importedPlaylists)
            .innerJoin(users, eq(users.id, importedPlaylists.userId))
            .where(
              and(
                eq(users.deviceFingerprint, deviceFp),
                eq(users.isGuest, true),
                isNull(users.mergedInto)
              )
            )
        : await reqDb
            .select({ count: sql<number>`count(*)::int` })
            .from(importedPlaylists)
            .where(eq(importedPlaylists.userId, userId));

      const importCount = countRows[0]?.count ?? 0;
      if (importCount >= 5) {
        return c.json({ error: 'LOGIN_REQUIRED' }, 403);
      }
    }

    const { url, playlistId, spDc } = await c.req.json<{ url?: string; playlistId?: string; spDc?: string }>();
    const target = url || playlistId;
    if (!target) {
      return c.json({ error: 'url or playlistId required' }, 400);
    }

    const v = validateImportTarget(target);
    if (!v.ok) {
      return c.json({ error: 'INVALID_URL', reason: v.reason }, 400);
    }
    const normalized = v.target;
    const source = normalized.kind;
    const sidecarTarget = normalized.cleanedUrl ?? normalized.playlistId;

    if (source === 'spotify') {
      if (!spDc) {
        return c.json({ error: 'SPOTIFY_COOKIE_REQUIRED' }, 400);
      }
      try {
        const data = await fetchFromSidecar<{
          title?: string;
          tracks?: ImportedTrack[];
          thumbnail?: string | null;
        }>('/spotify/private-playlist', { playlist_url: sidecarTarget, sp_dc_cookie: spDc });

        return c.json({
          title: data.title || 'Imported Spotify Playlist',
          tracks: normalizeSidecarTracks(data.tracks || []),
          thumbnail: data.thumbnail || null,
        });
      } catch (err: any) {
        if (err instanceof SidecarHttpError) {
          if (err.status === 400 || err.status === 422) {
            return c.json({ error: 'SPOTIFY_AUTH_FAILED' }, 400);
          }
          if (err.status >= 500) {
            return c.json({ error: 'SIDECAR_ERROR' }, 502);
          }
        }
        return c.json({ error: 'SIDECAR_ERROR' }, 502);
      }
    }

    const data = await fetchFromSidecar<{
      title?: string;
      tracks?: ImportedTrack[];
      thumbnail?: string | null;
    }>('/playlist/public', { playlist_url_or_id: sidecarTarget });

    return c.json({
      title: data.title || 'Imported Playlist',
      tracks: normalizeSidecarTracks(data.tracks || []),
      thumbnail: data.thumbnail || null,
    });
  } catch (err: any) {
    return c.json({ error: err?.message || 'Playlist import failed' }, 500);
  }
});

// Import user playlists via OAuth refresh token
importRoutes.post('/library/playlists', async (c) => {
  try {
    const { refreshToken } = await c.req.json<{ refreshToken: string }>();
    if (!refreshToken) {
      return c.json({ error: 'refreshToken required' }, 400);
    }

    const data = await fetchFromSidecar<{ playlists: any[] }>('/library/playlists', {
      refresh_token: refreshToken,
      client_id: process.env.GOOGLE_CLIENT_ID,
      client_secret: process.env.GOOGLE_CLIENT_SECRET,
    });

    return c.json({ playlists: data.playlists || [] });
  } catch (err: any) {
    return c.json({ error: err?.message || 'Failed to import user playlists' }, 500);
  }
});
