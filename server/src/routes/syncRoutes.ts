import { safeError } from '../lib/errors.js';
/**
 * Sync Routes (Hono)
 * Batch sync queue processing, cloud pull with keyset pagination, and guest conversion.
 * Strictly adheres to docs/04_DATABASE.md Section 23 & docs/05_API.md Section 8.
 */

import crypto from 'node:crypto';
import { Hono } from 'hono';
import { z } from 'zod';
import { zValidator } from '@hono/zod-validator';
import { uploadLocalSchema, syncPayloadSchema, validationHook } from '../lib/validators.js';
import { eq, and, or, gte, inArray, sql } from 'drizzle-orm';

import {
  userLikes,
  userPlaylists,
  userPlaylistTracks,
  userPlayHistory,
  userPreferences,
  syncOperations,
  guestMergeLog,
  importedPlaylists,
  importedPlaylistTracks,
} from '../db/schema.js';
import { requireAuth } from '../middleware/honoAuth.js';
import { AppEnv } from '../types.js';

export const syncRoutes = new Hono<AppEnv>();

syncRoutes.use('*', requireAuth);

export interface SyncOperationInput {
  id?: string;
  type: 'create' | 'update' | 'delete';
  entityType:
    | 'like'
    | 'playlist'
    | 'playlist_track'
    | 'history'
    | 'preference'
    | 'playlist_reorder'
    | 'import_playlist'
    | 'imported_playlist'
    | 'import_playlist_track'
    | 'imported_playlist_track';
  entityId: string;
  payload?: any;
  createdAt?: string;
}

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

async function withUserTx<T>(db: any, userId: string, fn: (tx: any) => Promise<T>): Promise<T> {
  const client = (db as any)?.session?.client || (db as any)?.client;
  if (client && typeof client.begin === 'function') {
    return db.transaction(async (tx: any) => {
      await tx.execute(sql`SELECT set_config('request.jwt.claim.sub', ${userId}, true)`);
      return fn(tx);
    });
  }
  return fn(db);
}

async function processOperations(db: any, userId: string, operations: SyncOperationInput[]) {
  const accepted: string[] = [];
  const failed: Array<{ id: string; reason: string }> = [];

  for (const op of operations) {
    const opId = op.id || op.entityId || 'unknown';
    try {
      await withUserTx(db, userId, async (tx) => {
        // 1. Idempotency check: if op.id is a UUID and already in sync_operations for this user
        if (op.id && UUID_REGEX.test(op.id)) {
          const existing = await tx
            .select({ id: syncOperations.id })
            .from(syncOperations)
            .where(and(eq(syncOperations.id, op.id), eq(syncOperations.userId, userId)))
            .limit(1);

          if (existing.length > 0) {
            return;
          }
        }

        const payload = op.payload || {};

        // 2. Entity handling
        if (op.entityType === 'like') {
          if (op.type === 'create') {
            const trackId = payload.providerTrackId || payload.trackId || op.entityId;
            await tx
              .insert(userLikes)
              .values({
                userId,
                provider: payload.provider || 'youtube_music',
                providerTrackId: String(trackId),
                title: payload.title || 'Unknown Title',
                artistName: payload.artistName || (Array.isArray(payload.artists) ? payload.artists[0] : payload.artist),
                albumTitle: payload.albumTitle || payload.album,
                artworkUrl: payload.artworkUrl,
                durationMs: payload.durationMs,
                likedAt: payload.likedAt ? new Date(payload.likedAt) : new Date(),
              })
              .onConflictDoNothing();
          } else if (op.type === 'delete') {
            const trackId = payload.providerTrackId || payload.trackId || op.entityId;
            await tx
              .delete(userLikes)
              .where(
                and(
                  eq(userLikes.userId, userId),
                  or(
                    eq(userLikes.providerTrackId, String(trackId)),
                    UUID_REGEX.test(String(op.entityId)) ? eq(userLikes.id, op.entityId) : undefined
                  )
                )
              );
          }
        } else if (op.entityType === 'playlist') {
          if (op.type === 'create') {
            await tx
              .insert(userPlaylists)
              .values({
                id: UUID_REGEX.test(op.entityId) ? op.entityId : undefined,
                userId,
                name: payload.name || 'Untitled Playlist',
                description: payload.description,
                artworkUrl: payload.artworkUrl,
                visibility: payload.visibility || 'public',
                createdAt: payload.createdAt ? new Date(payload.createdAt) : new Date(),
                updatedAt: payload.updatedAt ? new Date(payload.updatedAt) : new Date(),
              })
              .onConflictDoNothing();
          } else if (op.type === 'update') {
            if (UUID_REGEX.test(op.entityId)) {
              await tx
                .update(userPlaylists)
                .set({
                  name: payload.name,
                  description: payload.description,
                  artworkUrl: payload.artworkUrl,
                  visibility: payload.visibility,
                  updatedAt: payload.updatedAt ? new Date(payload.updatedAt) : new Date(),
                })
                .where(and(eq(userPlaylists.userId, userId), eq(userPlaylists.id, op.entityId)));
            }
          } else if (op.type === 'delete') {
            if (UUID_REGEX.test(op.entityId)) {
              await tx
                .delete(userPlaylists)
                .where(and(eq(userPlaylists.userId, userId), eq(userPlaylists.id, op.entityId)));
            }
          }
        } else if (op.entityType === 'playlist_track') {
          const playlistId = payload.playlistId;
          if (playlistId && UUID_REGEX.test(playlistId)) {
            if (op.type === 'create') {
              await tx
                .insert(userPlaylistTracks)
                .values({
                  id: UUID_REGEX.test(op.entityId) ? op.entityId : undefined,
                  playlistId,
                  position: typeof payload.position === 'number' ? payload.position : 0,
                  provider: payload.provider || 'youtube_music',
                  providerTrackId: String(payload.providerTrackId || payload.trackId || op.entityId),
                  title: payload.title || 'Track',
                  artistName: payload.artistName || (Array.isArray(payload.artists) ? payload.artists[0] : payload.artist),
                  albumTitle: payload.albumTitle || payload.album,
                  artworkUrl: payload.artworkUrl,
                  durationMs: payload.durationMs,
                  addedAt: payload.addedAt ? new Date(payload.addedAt) : new Date(),
                })
                .onConflictDoNothing();
            } else if (op.type === 'update') {
              if (UUID_REGEX.test(op.entityId)) {
                await tx
                  .update(userPlaylistTracks)
                  .set({
                    position: payload.position,
                  })
                  .where(
                    and(
                      eq(userPlaylistTracks.playlistId, playlistId),
                      eq(userPlaylistTracks.id, op.entityId)
                    )
                  );
              }
            } else if (op.type === 'delete') {
              await tx
                .delete(userPlaylistTracks)
                .where(
                  and(
                    eq(userPlaylistTracks.playlistId, playlistId),
                    UUID_REGEX.test(op.entityId)
                      ? eq(userPlaylistTracks.id, op.entityId)
                      : eq(userPlaylistTracks.providerTrackId, String(op.entityId))
                  )
                );
            }
          }
        } else if (op.entityType === 'history') {
          if (op.type === 'create') {
            await tx.insert(userPlayHistory).values({
              userId,
              provider: payload.provider || 'youtube_music',
              providerTrackId: String(payload.providerTrackId || payload.trackId || op.entityId),
              title: payload.title,
              artistName: payload.artistName || (Array.isArray(payload.artists) ? payload.artists[0] : payload.artist),
              albumTitle: payload.albumTitle || payload.album,
              playedAt: payload.playedAt ? new Date(payload.playedAt) : new Date(),
              durationPlayedMs: payload.durationPlayedMs,
              completed: !!payload.completed,
              source: payload.source,
            });
          } else if (op.type === 'delete') {
            if (UUID_REGEX.test(op.entityId)) {
              await tx
                .delete(userPlayHistory)
                .where(and(eq(userPlayHistory.userId, userId), eq(userPlayHistory.id, op.entityId)));
            }
          }
        } else if (op.entityType === 'preference') {
          if (op.type === 'create' || op.type === 'update') {
            await tx
              .insert(userPreferences)
              .values({
                userId,
                favoriteGenres: Array.isArray(payload.favoriteGenres) ? payload.favoriteGenres : [],
                favoriteArtists: Array.isArray(payload.favoriteArtists) ? payload.favoriteArtists : [],
                preferredLanguages: Array.isArray(payload.preferredLanguages) ? payload.preferredLanguages : [],
                updatedAt: payload.updatedAt ? new Date(payload.updatedAt) : new Date(),
              })
              .onConflictDoUpdate({
                target: userPreferences.userId,
                set: {
                  favoriteGenres: Array.isArray(payload.favoriteGenres) ? payload.favoriteGenres : [],
                  favoriteArtists: Array.isArray(payload.favoriteArtists) ? payload.favoriteArtists : [],
                  preferredLanguages: Array.isArray(payload.preferredLanguages) ? payload.preferredLanguages : [],
                  updatedAt: payload.updatedAt ? new Date(payload.updatedAt) : new Date(),
                },
              });
          } else if (op.type === 'delete') {
            await tx.delete(userPreferences).where(eq(userPreferences.userId, userId));
          }
        } else if (op.entityType === 'playlist_reorder') {
          const playlistId = payload.playlistId || op.entityId;
          const orderedTrackIds: string[] = Array.isArray(payload.orderedTrackIds) ? payload.orderedTrackIds : [];

          if (!playlistId || !UUID_REGEX.test(playlistId)) {
            const err: any = new Error('Bad Request: Invalid playlist ID');
            err.status = 400;
            throw err;
          }

          // 1. Verify user owns playlistId (via user_playlists.user_id)
          const playlist = await tx
            .select({ id: userPlaylists.id, userId: userPlaylists.userId })
            .from(userPlaylists)
            .where(eq(userPlaylists.id, playlistId))
            .limit(1);

          if (playlist.length === 0 || playlist[0].userId !== userId) {
            const err: any = new Error('Forbidden: Playlist not owned by user');
            err.status = 403;
            throw err;
          }

          // 2. Query existing tracks for playlist
          const currentTracks = await tx
            .select({ id: userPlaylistTracks.id, providerTrackId: userPlaylistTracks.providerTrackId })
            .from(userPlaylistTracks)
            .where(eq(userPlaylistTracks.playlistId, playlistId));

          const currentIds = new Set(currentTracks.map((t: any) => t.id));
          const currentProviderTrackIds = new Set(currentTracks.map((t: any) => t.providerTrackId));

          const matchesRowIds =
            new Set(orderedTrackIds).size === currentTracks.length &&
            orderedTrackIds.length === currentTracks.length &&
            orderedTrackIds.every((id: string) => currentIds.has(id));

          const matchesProviderTrackIds =
            new Set(orderedTrackIds).size === currentTracks.length &&
            orderedTrackIds.length === currentTracks.length &&
            orderedTrackIds.every((id: string) => currentProviderTrackIds.has(id));

          if (!matchesRowIds && !matchesProviderTrackIds) {
            const err: any = new Error('Bad Request: Set mismatch for playlist tracks');
            err.status = 400;
            throw err;
          }

          // 3. Atomically update positions in single transaction: shift negative first, then set sequential positions (1,000,000 offset)
          await tx
            .update(userPlaylistTracks)
            .set({ position: sql`-(position + 1000000)` })
            .where(eq(userPlaylistTracks.playlistId, playlistId));

          for (let pos = 0; pos < orderedTrackIds.length; pos++) {
            const trackId = orderedTrackIds[pos];
            if (UUID_REGEX.test(trackId)) {
              await tx
                .update(userPlaylistTracks)
                .set({ position: pos })
                .where(
                  and(
                    eq(userPlaylistTracks.playlistId, playlistId),
                    eq(userPlaylistTracks.id, trackId)
                  )
                );
            } else {
              await tx
                .update(userPlaylistTracks)
                .set({ position: pos })
                .where(
                  and(
                    eq(userPlaylistTracks.playlistId, playlistId),
                    eq(userPlaylistTracks.providerTrackId, trackId)
                  )
                );
            }
          }
        } else if (op.entityType === 'import_playlist' || op.entityType === 'imported_playlist') {
          if (op.type === 'create') {
            const externalId = String(payload.externalId || op.entityId);
            const provider = payload.provider || payload.source || 'youtube_music';
            const title = payload.title || payload.name || 'Untitled Imported Playlist';
            const trackCount = typeof payload.trackCount === 'number' ? payload.trackCount : 0;
            const matchedCount = typeof payload.matchedCount === 'number' ? payload.matchedCount : 0;
            const lastSyncedAt = payload.lastSyncedAt ? new Date(payload.lastSyncedAt) : new Date();
            const createdAt = payload.createdAt ? new Date(payload.createdAt) : new Date();

            await tx
              .insert(importedPlaylists)
              .values({
                id: UUID_REGEX.test(op.entityId) ? op.entityId : undefined,
                userId,
                provider,
                externalId,
                title,
                description: payload.description || null,
                thumbnailUrl: payload.thumbnailUrl || payload.artworkUrl || null,
                trackCount,
                matchedCount,
                lastSyncedAt,
                createdAt,
              })
              .onConflictDoUpdate({
                target: [importedPlaylists.userId, importedPlaylists.provider, importedPlaylists.externalId],
                set: {
                  title,
                  description: payload.description || null,
                  thumbnailUrl: payload.thumbnailUrl || payload.artworkUrl || null,
                  trackCount,
                  matchedCount,
                  lastSyncedAt,
                },
              });
          } else if (op.type === 'update') {
            if (UUID_REGEX.test(op.entityId)) {
              await tx
                .update(importedPlaylists)
                .set({
                  title: payload.title || payload.name,
                  description: payload.description,
                  thumbnailUrl: payload.thumbnailUrl || payload.artworkUrl,
                  trackCount: payload.trackCount,
                  matchedCount: payload.matchedCount,
                  lastSyncedAt: payload.lastSyncedAt ? new Date(payload.lastSyncedAt) : new Date(),
                })
                .where(and(eq(importedPlaylists.userId, userId), eq(importedPlaylists.id, op.entityId)));
            }
          } else if (op.type === 'delete') {
            await tx
              .delete(importedPlaylists)
              .where(
                and(
                  eq(importedPlaylists.userId, userId),
                  or(
                    UUID_REGEX.test(op.entityId) ? eq(importedPlaylists.id, op.entityId) : undefined,
                    eq(importedPlaylists.externalId, String(op.entityId))
                  )
                )
              );
          }
        } else if (op.entityType === 'import_playlist_track' || op.entityType === 'imported_playlist_track') {
          const importedPlaylistId = payload.importedPlaylistId || payload.playlistId;
          if (importedPlaylistId && UUID_REGEX.test(importedPlaylistId)) {
            if (op.type === 'create') {
              let artists: string[] = [];
              if (Array.isArray(payload.artists)) {
                artists = payload.artists.filter((a: any): a is string => typeof a === 'string');
              } else if (typeof payload.artist === 'string') {
                artists = [payload.artist];
              }
              if (artists.length === 0) {
                artists = ['Unknown Artist'];
              }

              await tx
                .insert(importedPlaylistTracks)
                .values({
                  id: UUID_REGEX.test(op.entityId) ? op.entityId : undefined,
                  importedPlaylistId,
                  position: typeof payload.position === 'number' ? payload.position : 0,
                  externalVideoId: String(payload.externalVideoId || payload.trackId || op.entityId),
                  title: payload.title || 'Track',
                  artists,
                  durationMs: typeof payload.durationMs === 'number' ? payload.durationMs : null,
                  thumbnailUrl: payload.thumbnailUrl || payload.artworkUrl || null,
                  jiosaavnTrackId: payload.jiosaavnTrackId || null,
                  matchConfidence: payload.matchConfidence ? String(payload.matchConfidence) : null,
                  matchStatus: payload.matchStatus || 'pending',
                  createdAt: payload.createdAt ? new Date(payload.createdAt) : new Date(),
                })
                .onConflictDoNothing();
            } else if (op.type === 'update') {
              if (UUID_REGEX.test(op.entityId)) {
                await tx
                  .update(importedPlaylistTracks)
                  .set({
                    position: payload.position,
                    matchStatus: payload.matchStatus,
                    jiosaavnTrackId: payload.jiosaavnTrackId,
                  })
                  .where(
                    and(
                      eq(importedPlaylistTracks.importedPlaylistId, importedPlaylistId),
                      eq(importedPlaylistTracks.id, op.entityId)
                    )
                  );
              }
            } else if (op.type === 'delete') {
              await tx
                .delete(importedPlaylistTracks)
                .where(
                  and(
                    eq(importedPlaylistTracks.importedPlaylistId, importedPlaylistId),
                    UUID_REGEX.test(op.entityId)
                      ? eq(importedPlaylistTracks.id, op.entityId)
                      : eq(importedPlaylistTracks.externalVideoId, String(op.entityId))
                  )
                );
            }
          }
        } else {
          throw new Error(`Unsupported entity type: ${op.entityType}`);
        }

        // 3. Record in sync_operations log table
        await tx
          .insert(syncOperations)
          .values({
            id: op.id && UUID_REGEX.test(op.id) ? op.id : undefined,
            userId,
            type: op.type,
            entityType: op.entityType,
            entityId: String(op.entityId),
            payload,
            processedAt: new Date(),
            status: 'applied',
          })
          .onConflictDoNothing();
      });

      accepted.push(opId);
    } catch (err: any) {
      const failReason = process.env.NODE_ENV === 'production' ? 'operation_failed' : (err.message || 'Operation failed');
      failed.push({ id: opId, reason: failReason, status: err.status } as any);
    }
  }

  return { accepted, failed, serverTime: new Date().toISOString() };
}

// POST /api/sync
syncRoutes.post(
  '/',
  zValidator('json', syncPayloadSchema, validationHook('Invalid operations format')),
  async (c) => {
    const db = c.get('db');
    const userId = c.get('userId');
    try {
      const body = c.req.valid('json');
      let rawOps: SyncOperationInput[] = [];

      if (Array.isArray(body)) {
        rawOps = body as SyncOperationInput[];
      } else if (Array.isArray((body as any)?.operations)) {
        rawOps = (body as any).operations;
      } else if ((body as any)?.entityType === 'playlist_reorder') {
        const reorder = body as any;
        rawOps = [
          {
            type: 'update',
            entityType: 'playlist_reorder',
            entityId: reorder.playlistId,
            payload: {
              playlistId: reorder.playlistId,
              orderedTrackIds: reorder.orderedTrackIds,
            },
          },
        ];
      } else {
        return c.json({ error: 'Invalid operations format' }, 400);
      }

      // Spec lock: batch max 50 operations. Never more.
      if (rawOps.length > 50) {
        return c.json({ error: 'Batch exceeds maximum limit of 50 operations' }, 400);
      }

    const result = await processOperations(db, userId, rawOps);
    if (result.failed.length > 0) {
      const firstFail = result.failed[0] as any;
      if (firstFail.status === 403 || firstFail.reason.includes('Forbidden')) {
        return c.json({ error: firstFail.reason }, 403);
      }
      if (
        firstFail.status === 400 ||
        firstFail.reason.includes('Bad Request') ||
        firstFail.reason.includes('Set mismatch')
      ) {
        return c.json({ error: firstFail.reason }, 400);
      }
    }
    return c.json(result);
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// GET /api/sync/pull?since=&cursor=
syncRoutes.get('/pull', async (c) => {
  const db = c.get('db');
  const userId = c.get('userId');
  try {
    const sinceParam = c.req.query('since');
    const cursorParam = c.req.query('cursor');
    const limit = Math.min(parseInt(c.req.query('limit') || '500', 10), 500);

    const sinceDate = sinceParam ? new Date(sinceParam) : null;

    // Decode keyset cursor: { timestamp: string, id: string }
    let cursor: { timestamp: string; id: string } | null = null;
    if (cursorParam) {
      try {
        const decoded = JSON.parse(Buffer.from(cursorParam, 'base64').toString('utf8'));
        if (decoded.timestamp && decoded.id) {
          cursor = decoded;
        }
      } catch {
        // Invalid cursor ignored
      }
    }

    const { pageItems, nextCursor } = await withUserTx(db, userId, async (tx) => {
      // 1. Fetch likes
      const likes = await tx
        .select()
        .from(userLikes)
        .where(
          and(
            eq(userLikes.userId, userId),
            sinceDate ? gte(userLikes.likedAt, sinceDate) : undefined
          )
        );

      // 2. Fetch playlists
      const playlists = await tx
        .select()
        .from(userPlaylists)
        .where(
          and(
            eq(userPlaylists.userId, userId),
            sinceDate ? gte(userPlaylists.updatedAt, sinceDate) : undefined
          )
        );

      // 3. Fetch playlist tracks (only for playlists owned by this user)
      const userPlaylistIds = playlists.map((p: any) => p.id);
      let playlistTracks: any[] = [];
      if (userPlaylistIds.length > 0) {
        playlistTracks = await tx
          .select()
          .from(userPlaylistTracks)
          .where(
            and(
              inArray(userPlaylistTracks.playlistId, userPlaylistIds),
              sinceDate ? gte(userPlaylistTracks.addedAt, sinceDate) : undefined
            )
          );
      }

      // 4. Fetch play history
      const history = await tx
        .select()
        .from(userPlayHistory)
        .where(
          and(
            eq(userPlayHistory.userId, userId),
            sinceDate ? gte(userPlayHistory.playedAt, sinceDate) : undefined
          )
        );

      // 5. Fetch preferences
      const preferences = await tx
        .select()
        .from(userPreferences)
        .where(
          and(
            eq(userPreferences.userId, userId),
            sinceDate ? gte(userPreferences.updatedAt, sinceDate) : undefined
          )
        );

      // 6. Fetch imported playlists
      const userImportedPlaylists = await tx
        .select()
        .from(importedPlaylists)
        .where(
          and(
            eq(importedPlaylists.userId, userId),
            sinceDate ? gte(importedPlaylists.createdAt, sinceDate) : undefined
          )
        );

      // 7. Fetch imported playlist tracks
      const userImportedPlaylistIds = userImportedPlaylists.map((p: any) => p.id);
      let userImportedTracks: any[] = [];
      if (userImportedPlaylistIds.length > 0) {
        userImportedTracks = await tx
          .select()
          .from(importedPlaylistTracks)
          .where(
            and(
              inArray(importedPlaylistTracks.importedPlaylistId, userImportedPlaylistIds),
              sinceDate ? gte(importedPlaylistTracks.createdAt, sinceDate) : undefined
            )
          );
      }

      // 8. Fetch delete tombstones (only when sinceDate is provided, for incremental sync)
      let deleteOps: any[] = [];
      if (sinceDate) {
        deleteOps = await tx
          .select()
          .from(syncOperations)
          .where(
            and(
              eq(syncOperations.userId, userId),
              eq(syncOperations.type, 'delete'),
              gte(syncOperations.createdAt, sinceDate)
            )
          )
          .limit(500);
      }

      // 9. Fetch playlist_reorder ops (only when sinceDate is provided, for incremental sync)
      let reorderOps: any[] = [];
      if (sinceDate) {
        reorderOps = await tx
          .select()
          .from(syncOperations)
          .where(
            and(
              eq(syncOperations.userId, userId),
              eq(syncOperations.entityType, 'playlist_reorder'),
              gte(syncOperations.createdAt, sinceDate)
            )
          )
          .limit(500);
      }

      // Flatten into unified items array
      type PullItem = {
        entity:
          | 'like'
          | 'playlist'
          | 'playlist_track'
          | 'history'
          | 'preference'
          | 'playlist_reorder'
          | 'import_playlist'
          | 'imported_playlist'
          | 'import_playlist_track'
          | 'imported_playlist_track';
        type?: 'create' | 'update' | 'delete';
        payload: any;
        updatedAt: string;
        id: string;
      };

      const allItems: PullItem[] = [
        ...likes.map((l: any) => ({
          entity: 'like' as const,
          type: 'create' as const,
          payload: l,
          updatedAt: l.likedAt.toISOString(),
          id: l.id,
        })),
        ...playlists.map((p: any) => ({
          entity: 'playlist' as const,
          type: 'create' as const,
          payload: p,
          updatedAt: p.updatedAt.toISOString(),
          id: p.id,
        })),
        ...playlistTracks.map((pt: any) => ({
          entity: 'playlist_track' as const,
          type: 'create' as const,
          payload: pt,
          updatedAt: pt.addedAt.toISOString(),
          id: pt.id,
        })),
        ...history.map((h: any) => ({
          entity: 'history' as const,
          type: 'create' as const,
          payload: h,
          updatedAt: h.playedAt.toISOString(),
          id: h.id,
        })),
        ...preferences.map((pr: any) => ({
          entity: 'preference' as const,
          type: 'create' as const,
          payload: pr,
          updatedAt: pr.updatedAt.toISOString(),
          id: pr.userId,
        })),
        ...userImportedPlaylists.map((ip: any) => ({
          entity: 'import_playlist' as const,
          type: 'create' as const,
          payload: ip,
          updatedAt: (ip.lastSyncedAt || ip.createdAt).toISOString(),
          id: ip.id,
        })),
        ...userImportedTracks.map((it: any) => ({
          entity: 'import_playlist_track' as const,
          type: 'create' as const,
          payload: it,
          updatedAt: it.createdAt.toISOString(),
          id: it.id,
        })),
        ...deleteOps.map((op: any) => ({
          entity: op.entityType as any,
          type: 'delete' as const,
          payload: {
            providerTrackId: op.entityId,
            id: op.entityId,
            playlistId: op.payload?.playlistId || op.payload?.importedPlaylistId,
            importedPlaylistId: op.payload?.importedPlaylistId || op.payload?.playlistId,
            provider: op.payload?.provider || op.payload?.source || 'youtube_music',
          },
          updatedAt: op.createdAt.toISOString(),
          id: op.id,
        })),
        ...reorderOps.map((op: any) => ({
          entity: 'playlist_reorder' as const,
          type: 'update' as const,
          payload: {
            playlistId: op.entityId,
            orderedTrackIds: op.payload?.orderedTrackIds || [],
          },
          updatedAt: op.createdAt.toISOString(),
          id: op.id,
        })),
      ];

      // Keyset ordering: (updatedAt ASC, id ASC)
      allItems.sort((a, b) => {
        const timeDiff = new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime();
        if (timeDiff !== 0) return timeDiff;
        return a.id.localeCompare(b.id);
      });

      // Keyset filtering: strictly after cursor
      let filtered = allItems;
      if (cursor) {
        const cursorTime = new Date(cursor.timestamp).getTime();
        const cursorId = cursor.id;
        filtered = allItems.filter((item) => {
          const itemTime = new Date(item.updatedAt).getTime();
          if (itemTime > cursorTime) return true;
          if (itemTime === cursorTime && item.id > cursorId) return true;
          return false;
        });
      }

      // Page slice
      const items = filtered.slice(0, limit);
      const hasMore = filtered.length > limit;

      let next: string | null = null;
      if (hasMore && items.length > 0) {
        const lastItem = items[items.length - 1];
        next = Buffer.from(
          JSON.stringify({ timestamp: lastItem.updatedAt, id: lastItem.id }),
          'utf8'
        ).toString('base64');
      }

      return { pageItems: items, nextCursor: next };
    });

    return c.json({
      items: pageItems.map(({ entity, type, payload, updatedAt }) => ({
        entity,
        type: type || 'create',
        payload,
        updatedAt,
      })),
      nextCursor,
    });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

async function handleUploadLocal(c: any, rawData: any) {
  const userId = c.get('userId');
  const db = c.get('db');

  let data = rawData;
  if (!data?.clientBatchId || !Array.isArray(data?.operations)) {
    const parseResult = uploadLocalSchema.safeParse(rawData);
    if (!parseResult.success) {
      return c.json({ error: 'invalid_payload', details: parseResult.error.format() }, 400);
    }
    data = parseResult.data;
  }
  const { clientBatchId, operations } = data;

  // 1. Idempotency check against guest_merge_log
  const [existing] = await db
    .select()
    .from(guestMergeLog)
    .where(eq(guestMergeLog.clientBatchId, clientBatchId))
    .limit(1);

  if (existing) {
    return c.json({ success: true, status: 'duplicate', processed: 0, acceptedCount: 1 });
  }

  // 2. Process operations in transaction
  const result = await processOperations(db, userId, operations);

  // 3. Insert completion record in guest_merge_log
  await db.insert(guestMergeLog).values({
    guestId: userId,
    authId: userId,
    clientBatchId,
    status: 'upload_local_complete',
  });

  return c.json({
    success: true,
    status: 'ok',
    processed: result.accepted.length,
    acceptedCount: result.accepted.length,
    failedCount: result.failed.length,
    serverTime: result.serverTime,
  });
}

// POST /api/sync/upload-local
syncRoutes.post(
  '/upload-local',
  zValidator('json', uploadLocalSchema, validationHook('invalid_payload')),
  async (c) => {
    try {
      const body = c.req.valid('json');
      return await handleUploadLocal(c, body);
    } catch (err: any) {
      return c.json(safeError(err, c, 500), 500);
    }
  }
);

// POST /api/sync/guest-convert (backwards-compatible alias)
syncRoutes.post('/guest-convert', async (c) => {
  try {
    const rawBody = await c.req.json().catch(() => ({}));
    if (!rawBody.clientBatchId) {
      rawBody.clientBatchId = 'legacy-' + crypto.randomUUID();
    }
    return await handleUploadLocal(c, rawBody);
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});
