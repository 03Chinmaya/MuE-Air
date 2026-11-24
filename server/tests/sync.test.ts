import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';
import { createTestUser, cleanupTestUsers } from './helpers/rls.js';
import { generateAccessToken } from '../src/services/authService.js';

describe('Sync API Routes & Cross-Device Sync Specification Tests', () => {
  let userA: { id: string; username: string; email: string };
  let userB: { id: string; username: string; email: string };
  let tokenA: string;
  let tokenB: string;

  before(async () => {
    const ts = Date.now();
    userA = await createTestUser(`sync_user_a_${ts}`, `sync_a_${ts}@test.com`);
    userB = await createTestUser(`sync_user_b_${ts}`, `sync_b_${ts}@test.com`);

    tokenA = generateAccessToken(userA.id, userA.username);
    tokenB = generateAccessToken(userB.id, userB.username);
  });

  after(async () => {
    try {
      await cleanupTestUsers([userA.id, userB.id]);
    } catch {}
  });

  it('POST /api/sync returns 401 without auth token', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/sync', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ operations: [] }),
      })
    );
    assert.strictEqual(res.status, 401);
  });

  it('POST /api/sync rejects batches exceeding 50 operations with 400 Bad Request', async () => {
    const ops = Array.from({ length: 51 }, (_, i) => ({
      id: `00000000-0000-4000-8000-${String(i).padStart(12, '0')}`,
      type: 'create',
      entityType: 'like',
      entityId: `track_${i}`,
      payload: { title: `Track ${i}` },
    }));

    const res = await app.fetch(
      new Request('http://localhost:4000/api/sync', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ operations: ops }),
      })
    );

    assert.strictEqual(res.status, 400);
    const data = (await res.json()) as any;
    assert.match(data.error, /50 operations/i);
  });

  it('POST /api/sync processes a valid batch (<50 ops) and returns accepted IDs and serverTime', async () => {
    const opId1 = '11111111-1111-4111-8111-111111111111';
    const opId2 = '22222222-2222-4222-8222-222222222222';
    const opId3 = '33333333-3333-4333-8333-333333333333';

    const ops = [
      {
        id: opId1,
        type: 'create',
        entityType: 'like',
        entityId: 'yt-song-1',
        payload: {
          provider: 'youtube_music',
          providerTrackId: 'yt-song-1',
          title: 'Sync Test Song 1',
          artistName: 'Test Artist',
        },
      },
      {
        id: opId2,
        type: 'create',
        entityType: 'playlist',
        entityId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        payload: {
          name: 'My Cloud Playlist',
          description: 'A test playlist',
          visibility: 'public',
        },
      },
      {
        id: opId3,
        type: 'create',
        entityType: 'preference',
        entityId: userA.id,
        payload: {
          favoriteGenres: ['Electronic', 'Ambient'],
          favoriteArtists: ['Artist 1'],
        },
      },
    ];

    const res = await app.fetch(
      new Request('http://localhost:4000/api/sync', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ operations: ops }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    if (data.accepted.length !== 3) {
      console.error('SYNC TEST 2 FAILED OPS:', data.failed);
    }
    assert.strictEqual(data.accepted.length, 3);
    assert.strictEqual(data.failed.length, 0);
    assert.ok(data.serverTime);
    assert.ok(data.accepted.includes(opId1));
    assert.ok(data.accepted.includes(opId2));
    assert.ok(data.accepted.includes(opId3));
  });

  it('POST /api/sync is idempotent: re-submitting the same batch does not error or duplicate', async () => {
    const opId1 = '11111111-1111-4111-8111-111111111111';

    const ops = [
      {
        id: opId1,
        type: 'create',
        entityType: 'like',
        entityId: 'yt-song-1',
        payload: {
          provider: 'youtube_music',
          providerTrackId: 'yt-song-1',
          title: 'Sync Test Song 1',
          artistName: 'Test Artist',
        },
      },
    ];

    const res = await app.fetch(
      new Request('http://localhost:4000/api/sync', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ operations: ops }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.strictEqual(data.accepted.length, 1);
    assert.ok(data.accepted.includes(opId1));
  });

  it('GET /api/sync/pull returns user items and supports keyset pagination', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/sync/pull?limit=2', {
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.ok(Array.isArray(data.items));
    assert.ok(data.items.length <= 2);
    assert.ok('nextCursor' in data);

    // If nextCursor exists, fetch page 2
    if (data.nextCursor) {
      const resPage2 = await app.fetch(
        new Request(`http://localhost:4000/api/sync/pull?limit=2&cursor=${encodeURIComponent(data.nextCursor)}`, {
          headers: {
            Authorization: `Bearer ${tokenA}`,
          },
        })
      );
      assert.strictEqual(resPage2.status, 200);
      const dataPage2 = (await resPage2.json()) as any;
      assert.ok(Array.isArray(dataPage2.items));
    }
  });

  it('POST /api/sync/guest-convert converts local guest operations on signup', async () => {
    const guestOps = [
      {
        id: '44444444-4444-4444-8444-444444444444',
        type: 'create' as const,
        entityType: 'like' as const,
        entityId: 'yt-guest-like-1',
        payload: {
          provider: 'youtube_music',
          providerTrackId: 'yt-guest-like-1',
          title: 'Guest Liked Song',
          artistName: 'Guest Artist',
        },
      },
    ];

    const res = await app.fetch(
      new Request('http://localhost:4000/api/sync/guest-convert', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenB}`,
        },
        body: JSON.stringify({ operations: guestOps }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.strictEqual(data.success, true);
    assert.strictEqual(data.acceptedCount, 1);
  });

  it('Cross-user isolation: User B pulling changes never sees User A data', async () => {
    const resB = await app.fetch(
      new Request('http://localhost:4000/api/sync/pull', {
        headers: {
          Authorization: `Bearer ${tokenB}`,
        },
      })
    );

    assert.strictEqual(resB.status, 200);
    const dataB = (await resB.json()) as any;

    // None of user A's items should appear in user B's pull
    const userASongFound = dataB.items.find((item: any) => item.payload?.title === 'Sync Test Song 1');
    assert.strictEqual(userASongFound, undefined);
  });

  it('POST /api/sync/guest-convert is idempotent: re-submitting guest operations does not duplicate or fail', async () => {
    const guestOps = [
      {
        id: '44444444-4444-4444-8444-444444444444',
        type: 'create' as const,
        entityType: 'like' as const,
        entityId: 'yt-guest-like-1',
        payload: {
          provider: 'youtube_music',
          providerTrackId: 'yt-guest-like-1',
          title: 'Guest Liked Song',
          artistName: 'Guest Artist',
        },
      },
    ];

    const res = await app.fetch(
      new Request('http://localhost:4000/api/sync/guest-convert', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenB}`,
        },
        body: JSON.stringify({ operations: guestOps }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.strictEqual(data.success, true);
    assert.strictEqual(data.acceptedCount, 1);
  });

  it('Failed operation returns reason: invalid operation reports in failed array with reason', async () => {
    const invalidOp = {
      id: '55555555-5555-4555-8555-555555555555',
      type: 'create',
      entityType: 'unsupported_entity',
      entityId: 'unknown-id',
      payload: {},
    };

    const res = await app.fetch(
      new Request('http://localhost:4000/api/sync', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ operations: [invalidOp] }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.strictEqual(data.accepted.length, 0);
    assert.strictEqual(data.failed.length, 1);
    assert.strictEqual(data.failed[0].id, invalidOp.id);
    assert.match(data.failed[0].reason, /unsupported entity type/i);
  });

  it('Sync supports import_playlist and import_playlist_track upload and pull', async () => {
    const playlistId = '99999999-9999-4999-8999-999999999999';
    const trackId = '88888888-8888-4888-8888-888888888888';

    const ops = [
      {
        id: '77777777-7777-4777-8777-777777777777',
        type: 'create',
        entityType: 'import_playlist',
        entityId: playlistId,
        payload: {
          provider: 'spotify',
          externalId: 'spotify-test-playlist-1',
          title: 'My Imported Spotify Hits',
          trackCount: 1,
        },
      },
      {
        id: '66666666-6666-4666-8666-666666666666',
        type: 'create',
        entityType: 'import_playlist_track',
        entityId: trackId,
        payload: {
          importedPlaylistId: playlistId,
          position: 0,
          externalVideoId: 'spotify-track-1',
          title: 'Imported Song 1',
          artists: ['Test Artist'],
        },
      },
    ];

    const resUpload = await app.fetch(
      new Request('http://localhost:4000/api/sync', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ operations: ops }),
      })
    );

    assert.strictEqual(resUpload.status, 200);
    const dataUpload = (await resUpload.json()) as any;
    assert.strictEqual(dataUpload.accepted.length, 2);
    assert.strictEqual(dataUpload.failed.length, 0);

    // Now pull as userA
    const resPull = await app.fetch(
      new Request('http://localhost:4000/api/sync/pull', {
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );
    assert.strictEqual(resPull.status, 200);
    const dataPull = (await resPull.json()) as any;
    const importedPlItem = dataPull.items.find(
      (it: any) => it.entity === 'import_playlist' && it.payload.id === playlistId
    );
    assert.ok(importedPlItem, 'Expected imported_playlist item in pull response');
    assert.strictEqual(importedPlItem.payload.title, 'My Imported Spotify Hits');

    const importedTrackItem = dataPull.items.find(
      (it: any) => it.entity === 'import_playlist_track' && it.payload.id === trackId
    );
    assert.ok(importedTrackItem, 'Expected imported_playlist_track item in pull response');
    assert.strictEqual(importedTrackItem.payload.title, 'Imported Song 1');
  });
});
