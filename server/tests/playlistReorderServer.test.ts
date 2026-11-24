import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import crypto from 'node:crypto';
import { eq, and } from 'drizzle-orm';
import { app } from '../src/app.js';
import { createTestUser, cleanupTestUsers } from './helpers/rls.js';
import { generateAccessToken } from '../src/services/authService.js';
import { authDb } from '../src/db/connection.js';
import { syncOperations, userPlaylists, userPlaylistTracks } from '../src/db/schema.js';

describe('Phase 12 Part 1b — Server Atomic Playlist Reorder Tests', () => {
  let userA: { id: string; username: string; email: string };
  let userB: { id: string; username: string; email: string };
  let tokenA: string;
  let tokenB: string;

  let playlistId = '';
  let trackId1 = '';
  let trackId2 = '';
  let trackId3 = '';

  before(async () => {
    playlistId = crypto.randomUUID();
    trackId1 = crypto.randomUUID();
    trackId2 = crypto.randomUUID();
    trackId3 = crypto.randomUUID();

    const ts = Date.now();
    userA = await createTestUser(`reorder_user_a_${ts}`, `reorder_a_${ts}@test.com`);
    userB = await createTestUser(`reorder_user_b_${ts}`, `reorder_b_${ts}@test.com`);

    tokenA = generateAccessToken(userA.id, userA.username);
    tokenB = generateAccessToken(userB.id, userB.username);

    // Seed playlist and 3 tracks via POST /api/sync
    const seedOps = [
      {
        id: crypto.randomUUID(),
        type: 'create',
        entityType: 'playlist',
        entityId: playlistId,
        payload: {
          name: 'Reorder Test Playlist',
          description: 'Testing atomic reordering',
          visibility: 'public',
        },
      },
      {
        id: trackId1,
        type: 'create',
        entityType: 'playlist_track',
        entityId: trackId1,
        payload: {
          playlistId,
          position: 0,
          provider: 'youtube_music',
          providerTrackId: 'yt-track-1',
          title: 'Track One',
        },
      },
      {
        id: trackId2,
        type: 'create',
        entityType: 'playlist_track',
        entityId: trackId2,
        payload: {
          playlistId,
          position: 1,
          provider: 'youtube_music',
          providerTrackId: 'yt-track-2',
          title: 'Track Two',
        },
      },
      {
        id: trackId3,
        type: 'create',
        entityType: 'playlist_track',
        entityId: trackId3,
        payload: {
          playlistId,
          position: 2,
          provider: 'youtube_music',
          providerTrackId: 'yt-track-3',
          title: 'Track Three',
        },
      },
    ];

    const seedRes = await app.fetch(
      new Request('http://localhost:4000/api/sync', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ operations: seedOps }),
      })
    );

    assert.strictEqual(seedRes.status, 200);
  });

  after(async () => {
    try {
      await authDb.delete(userPlaylistTracks).where(eq(userPlaylistTracks.playlistId, playlistId));
      await authDb.delete(userPlaylists).where(eq(userPlaylists.id, playlistId));
      await authDb.delete(syncOperations).where(eq(syncOperations.entityId, playlistId));
      await authDb.delete(syncOperations).where(eq(syncOperations.id, '00000000-0000-4000-8000-000000000001'));
      await authDb.delete(syncOperations).where(eq(syncOperations.id, trackId1));
      await authDb.delete(syncOperations).where(eq(syncOperations.id, trackId2));
      await authDb.delete(syncOperations).where(eq(syncOperations.id, trackId3));
      await cleanupTestUsers([userA.id, userB.id]);
    } catch {}
  });

  // Test 1 — reorder updates all positions in single transaction
  it('Test 1 — reorder updates all positions in single transaction', async () => {
    // Reorder from [track1, track2, track3] to [track3, track1, track2]
    const reorderedIds = [trackId3, trackId1, trackId2];

    const res = await app.fetch(
      new Request('http://localhost:4000/api/sync', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          entityType: 'playlist_reorder',
          playlistId,
          orderedTrackIds: reorderedIds,
        }),
      })
    );

    assert.strictEqual(res.status, 200);

    // Verify positions via pull
    const pullRes = await app.fetch(
      new Request('http://localhost:4000/api/sync/pull', {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );

    assert.strictEqual(pullRes.status, 200);
    const pullData = (await pullRes.json()) as any;
    const tracks = (pullData.items || []).filter(
      (item: any) => item.entity === 'playlist_track' && item.payload.playlistId === playlistId
    );

    const posMap = new Map(tracks.map((t: any) => [t.payload.id || t.id, t.payload.position]));
    assert.strictEqual(posMap.get(trackId3), 0);
    assert.strictEqual(posMap.get(trackId1), 1);
    assert.strictEqual(posMap.get(trackId2), 2);
  });

  // Test 2 — non-owner rejected 403
  it('Test 2 — non-owner rejected 403', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/sync', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenB}`, // User B does not own playlist
        },
        body: JSON.stringify({
          entityType: 'playlist_reorder',
          playlistId,
          orderedTrackIds: [trackId1, trackId2, trackId3],
        }),
      })
    );

    assert.strictEqual(res.status, 403);
    const body = (await res.json()) as any;
    assert.match(body.error, /Forbidden/i);
  });

  // Test 3 — set mismatch rejected 400
  it('Test 3 — set mismatch rejected 400', async () => {
    // Missing one track: 2 instead of 3
    const resSubset = await app.fetch(
      new Request('http://localhost:4000/api/sync', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          entityType: 'playlist_reorder',
          playlistId,
          orderedTrackIds: [trackId1, trackId2],
        }),
      })
    );

    assert.strictEqual(resSubset.status, 400);

    // Unknown track ID
    const resUnknown = await app.fetch(
      new Request('http://localhost:4000/api/sync', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          entityType: 'playlist_reorder',
          playlistId,
          orderedTrackIds: [trackId1, trackId2, '99999999-9999-4999-8999-999999999999'],
        }),
      })
    );

    assert.strictEqual(resUnknown.status, 400);
  });

  // Test 4 — one sync_operations row written (not N)
  it('Test 4 — one sync_operations row written (not N)', async () => {
    const ops = await authDb
      .select()
      .from(syncOperations)
      .where(
        and(
          eq(syncOperations.entityType, 'playlist_reorder'),
          eq(syncOperations.entityId, playlistId)
        )
      );

    assert.strictEqual(ops.length, 1);
    assert.strictEqual(ops[0].type, 'update');
    assert.strictEqual(ops[0].entityType, 'playlist_reorder');
  });

  // Test 5 — 0-indexed positions stored
  it('Test 5 — 0-indexed positions stored', async () => {
    const pullRes = await app.fetch(
      new Request('http://localhost:4000/api/sync/pull', {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );

    assert.strictEqual(pullRes.status, 200);
    const pullData = (await pullRes.json()) as any;
    const tracks = (pullData.items || []).filter(
      (item: any) => item.entity === 'playlist_track' && item.payload.playlistId === playlistId
    );

    const positions = tracks.map((t: any) => t.payload.position).sort((a: number, b: number) => a - b);
    assert.deepStrictEqual(positions, [0, 1, 2]);
    assert.strictEqual(Math.min(...positions), 0);
    assert.strictEqual(Math.max(...positions), 2);
  });
});
