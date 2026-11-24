import { Hono } from 'hono';
import { eq, sql } from 'drizzle-orm';
import { db } from '../db/connection.js';
import { userPlaylists, userPlaylistTracks, importedPlaylists } from '../db/schema.js';
import { requireAuthOrGuest } from '../middleware/honoAuth.js';
import { AppEnv } from '../types.js';

export const playlistRoutes = new Hono<AppEnv>();

export interface UnifiedPlaylistDto {
  id: string;
  name: string;
  artworkUrl: string | null;
  trackCount: number;
  source: string;
  updatedAt: string;
  isImported: boolean;
}

playlistRoutes.get('/', requireAuthOrGuest, async (c) => {
  const userId = c.get('userId');
  if (!userId) {
    return c.json({ error: 'unauthorized' }, 401);
  }

  const reqDb = c.get('db') || db;

  // 1. Fetch user-created playlists
  const userPls = await reqDb
    .select()
    .from(userPlaylists)
    .where(eq(userPlaylists.userId, userId));

  // Count tracks per user playlist
  const trackCountMap = new Map<string, number>();
  if (userPls.length > 0) {
    const trackCounts = await reqDb
      .select({
        playlistId: userPlaylistTracks.playlistId,
        count: sql<number>`count(*)::int`,
      })
      .from(userPlaylistTracks)
      .groupBy(userPlaylistTracks.playlistId);

    trackCounts.forEach((tc) => trackCountMap.set(tc.playlistId, Number(tc.count) || 0));
  }

  // 2. Fetch imported playlists
  const importedPls = await reqDb
    .select()
    .from(importedPlaylists)
    .where(eq(importedPlaylists.userId, userId));

  // 3. Map both to unified PlaylistDto
  const mappedUser: UnifiedPlaylistDto[] = userPls.map((pl) => ({
    id: pl.id,
    name: pl.name,
    artworkUrl: pl.artworkUrl || null,
    trackCount: trackCountMap.get(pl.id) || 0,
    source: 'local',
    updatedAt: (pl.updatedAt || pl.createdAt || new Date()).toISOString(),
    isImported: false,
  }));

  const mappedImported: UnifiedPlaylistDto[] = importedPls.map((pl) => ({
    id: pl.id,
    name: pl.title,
    artworkUrl: pl.thumbnailUrl || null,
    trackCount: pl.trackCount || 0,
    source: pl.provider,
    updatedAt: (pl.lastSyncedAt || pl.createdAt || new Date()).toISOString(),
    isImported: true,
  }));

  // 4. Sort by updatedAt DESC
  const playlists = [...mappedUser, ...mappedImported].sort(
    (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
  );

  return c.json({ playlists });
});
