/**
 * Playlist Import Routes (Hono)
 * Connects to the YouTube Music sidecar strictly for playlist imports.
 * The sidecar is used ONLY for playlist import, never for metadata browsing or streaming.
 */

import { Hono } from 'hono';
import { AppEnv } from '../types.js';

export const importRoutes = new Hono<AppEnv>();

export interface ImportedTrack {
  videoId: string;
  title: string;
  artists: string[];
  album: string | null;
  durationMs: number;
  thumbnail?: string | null;
}

export interface ImportedPlaylist {
  title: string;
  tracks: ImportedTrack[];
  thumbnail?: string | null;
}

async function fetchFromSidecar<T>(endpoint: string, body?: any): Promise<T> {
  const sidecarBaseUrl = process.env.YTMUSIC_SIDECAR_URL;
  if (!sidecarBaseUrl) {
    throw new Error('YTMUSIC_SIDECAR_URL not configured for playlist import');
  }

  const res = await fetch(`${sidecarBaseUrl.replace(/\/$/, '')}${endpoint}`, {
    method: body ? 'POST' : 'GET',
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
    signal: (AbortSignal as any).timeout(30000),
  });

  if (!res.ok) {
    throw new Error(`Sidecar import failed: HTTP ${res.status}`);
  }

  return res.json() as Promise<T>;
}

// Import public playlist by URL or ID
importRoutes.post('/playlist', async (c) => {
  try {
    const { url, playlistId } = await c.req.json<{ url?: string; playlistId?: string }>();
    const target = url || playlistId;
    if (!target) {
      return c.json({ error: 'url or playlistId required' }, 400);
    }

    const data = await fetchFromSidecar<{
      title?: string;
      tracks?: ImportedTrack[];
      thumbnail?: string | null;
    }>('/playlist/public', { playlist_url_or_id: target });

    return c.json({
      title: data.title || 'Imported Playlist',
      tracks: data.tracks || [],
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
