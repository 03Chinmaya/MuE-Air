import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import crypto from 'node:crypto';
import dotenv from 'dotenv';
dotenv.config();

import { pgClientAuth, authDb } from '../src/db/connection.js';
import { users } from '../src/db/schema.js';
import { mergeGuestIntoAuth } from '../src/services/guestMergeService.js';

describe('Guest Merge Service Tests', () => {
  let guestId: string;
  let authUserId: string;
  let secondGuestId: string;

  before(async () => {
    // 1. Create a guest user
    const [g] = await authDb
      .insert(users)
      .values({
        username: `guest_merge_test_${crypto.randomBytes(3).toString('hex')}`,
        displayName: 'Guest User',
        isGuest: true,
      })
      .returning();
    guestId = g.id;

    // 2. Create another guest user
    const [g2] = await authDb
      .insert(users)
      .values({
        username: `guest_merge_test2_${crypto.randomBytes(3).toString('hex')}`,
        displayName: 'Guest User 2',
        isGuest: true,
      })
      .returning();
    secondGuestId = g2.id;

    // 3. Create an authenticated user
    const [u] = await authDb
      .insert(users)
      .values({
        username: `auth_merge_test_${crypto.randomBytes(3).toString('hex')}`,
        displayName: 'Auth User',
        email: `auth_test_${Date.now()}@example.com`,
        isGuest: false,
      })
      .returning();
    authUserId = u.id;

    // Seed guest user data:
    // 3 history entries
    await pgClientAuth`
      INSERT INTO user_play_history (user_id, provider, provider_track_id, title)
      VALUES 
        (${guestId}, 'jiosaavn', 'track_1', 'Song 1'),
        (${guestId}, 'jiosaavn', 'track_2', 'Song 2'),
        (${guestId}, 'jiosaavn', 'track_3', 'Song 3');
    `;

    // 2 likes (one unique, one colliding with auth user)
    await pgClientAuth`
      INSERT INTO user_likes (user_id, provider, provider_track_id, title)
      VALUES 
        (${guestId}, 'jiosaavn', 'track_1', 'Song 1'),
        (${guestId}, 'jiosaavn', 'track_col', 'Colliding Song');
    `;
    await pgClientAuth`
      INSERT INTO user_likes (user_id, provider, provider_track_id, title)
      VALUES (${authUserId}, 'jiosaavn', 'track_col', 'Colliding Song');
    `;

    // 1 playlist
    await pgClientAuth`
      INSERT INTO user_playlists (user_id, name)
      VALUES (${guestId}, 'My Guest Playlist');
    `;

    // Stats collision test: both have stats for 'stat_track'
    await pgClientAuth`
      INSERT INTO song_play_stats (user_id, provider, provider_track_id, play_count)
      VALUES (${guestId}, 'jiosaavn', 'stat_track', 5);
    `;
    await pgClientAuth`
      INSERT INTO song_play_stats (user_id, provider, provider_track_id, play_count)
      VALUES (${authUserId}, 'jiosaavn', 'stat_track', 10);
    `;

    // Music integration collision: both connected to youtube
    await pgClientAuth`
      INSERT INTO user_music_integrations (user_id, provider, encrypted_refresh_token)
      VALUES (${guestId}, 'youtube', 'guest_encrypted_token');
    `;
    await pgClientAuth`
      INSERT INTO user_music_integrations (user_id, provider, encrypted_refresh_token)
      VALUES (${authUserId}, 'youtube', 'auth_encrypted_token');
    `;
  });

  after(async () => {
    try {
      if (guestId) await pgClientAuth`DELETE FROM users WHERE id = ${guestId};`;
      if (secondGuestId) await pgClientAuth`DELETE FROM users WHERE id = ${secondGuestId};`;
      if (authUserId) await pgClientAuth`DELETE FROM users WHERE id = ${authUserId};`;
    } catch {}
  });

  it('rejects merging guest into another guest account', async () => {
    await assert.rejects(
      async () => {
        await mergeGuestIntoAuth(guestId, secondGuestId);
      },
      /Destination account cannot be a guest account/
    );
  });

  it('rejects merging an authenticated account into a guest account', async () => {
    await assert.rejects(
      async () => {
        await mergeGuestIntoAuth(authUserId, guestId);
      },
      /Target source user is not a guest account/
    );
  });

  it('rejects merging an account into itself', async () => {
    await assert.rejects(
      async () => {
        await mergeGuestIntoAuth(guestId, guestId);
      },
      /Cannot merge user into themselves/
    );
  });

  it('successfully executes happy path merge and resolves collisions', async () => {
    const testDeviceId = crypto.randomUUID();
    const res = await mergeGuestIntoAuth(guestId, authUserId, testDeviceId);
    assert.strictEqual(res.merged, true);
    assert.strictEqual(res.alreadyMerged, false);
    assert.strictEqual(res.authId, authUserId);

    // 1. Verify history moved
    const history = await pgClientAuth`SELECT * FROM user_play_history WHERE user_id = ${authUserId};`;
    assert.strictEqual(history.length, 3);

    // 2. Verify likes deduped (should have track_1 and track_col, total 2)
    const likes = await pgClientAuth`SELECT * FROM user_likes WHERE user_id = ${authUserId};`;
    assert.strictEqual(likes.length, 2);

    // 3. Verify song stats summed (5 + 10 = 15)
    const [stats] = await pgClientAuth`SELECT play_count FROM song_play_stats WHERE user_id = ${authUserId} AND provider_track_id = 'stat_track';`;
    assert.strictEqual(stats?.play_count, 15);

    // 4. Verify playlist moved
    const playlists = await pgClientAuth`SELECT * FROM user_playlists WHERE user_id = ${authUserId};`;
    assert.strictEqual(playlists.length, 1);
    assert.strictEqual(playlists[0].name, 'My Guest Playlist');

    // 5. Verify integration conflict: auth user's token kept, guest's deleted
    const integrations = await pgClientAuth`SELECT encrypted_refresh_token FROM user_music_integrations WHERE user_id = ${authUserId};`;
    assert.strictEqual(integrations.length, 1);
    assert.strictEqual(integrations[0].encrypted_refresh_token, 'auth_encrypted_token');

    // 6. Verify guest marked as merged
    const [guestRow] = await pgClientAuth`SELECT is_guest, merged_into, merged_at FROM users WHERE id = ${guestId};`;
    assert.strictEqual(guestRow?.is_guest, false);
    assert.strictEqual(guestRow?.merged_into, authUserId);
    assert.ok(guestRow?.merged_at);

    // 7. Verify guest_merge_log
    const [log] = await pgClientAuth`SELECT status FROM guest_merge_log WHERE guest_id = ${guestId} AND auth_id = ${authUserId};`;
    assert.strictEqual(log?.status, 'complete');
  });

  it('is idempotent on repeat call for already merged guest', async () => {
    const res = await mergeGuestIntoAuth(guestId, authUserId);
    assert.strictEqual(res.merged, true);
    assert.strictEqual(res.alreadyMerged, true);
    assert.strictEqual(res.authId, authUserId);
  });
});
