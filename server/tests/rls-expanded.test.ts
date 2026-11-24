import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import crypto from 'node:crypto';
import {
  sqlAppUser,
  sqlAuthRole,
  createTestUser,
  cleanupTestUsers,
  asUser,
} from './helpers/rls.js';

function makeJoinCode(): string {
  const A = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
  const bytes = crypto.randomBytes(6);
  return Array.from(bytes, (b) => A[b % A.length]).join('');
}

describe('Phase 8 — Expanded Cross-Tenant RLS Isolation Tests', () => {
  let userA: { id: string; username: string; email: string };
  let userB: { id: string; username: string; email: string };
  const userIds: string[] = [];
  let userBPlaylistId: string;
  let userBSessionId: string;
  let userBDeviceId: string;

  before(async () => {
    const timestamp = Date.now();
    userA = await createTestUser(`rls_a_${timestamp}`, `rls_a_${timestamp}@example.com`);
    userB = await createTestUser(`rls_b_${timestamp}`, `rls_b_${timestamp}@example.com`);
    userIds.push(userA.id, userB.id);

    // Seed a device for User B (required foreign key for handoff_sessions)
    const [deviceB] = await sqlAuthRole<{ id: string }[]>`
      INSERT INTO user_devices (user_id, device_name, platform)
      VALUES (${userB.id}, 'Phone B', 'android')
      RETURNING id
    `;
    userBDeviceId = deviceB.id;

    // 1. Seed user_likes for User B
    await sqlAuthRole`
      INSERT INTO user_likes (user_id, provider, provider_track_id, title)
      VALUES (${userB.id}, 'jiosaavn', 'track_b_like_123', 'Song B')
    `;

    // 2. Seed imported_playlists for User B
    const [playlistB] = await sqlAuthRole<{ id: string }[]>`
      INSERT INTO imported_playlists (user_id, provider, external_id, title)
      VALUES (${userB.id}, 'spotify', 'spotify_pl_b_456', 'User B Private Import')
      RETURNING id
    `;
    userBPlaylistId = playlistB.id;

    // 3. Seed user_play_history for User B
    await sqlAuthRole`
      INSERT INTO user_play_history (user_id, provider, provider_track_id, title)
      VALUES (${userB.id}, 'youtube_music', 'track_b_history_789', 'History B')
    `;

    // 4. Seed sync_operations for User B
    await sqlAuthRole`
      INSERT INTO sync_operations (user_id, type, entity_type, entity_id, payload)
      VALUES (${userB.id}, 'upsert', 'playlist', 'entity_b_999', '{"name":"sync_b"}'::jsonb)
    `;

    // 5. Seed listen_sessions for User B
    const sessionBJoinCode = makeJoinCode();
    const [sessionB] = await sqlAuthRole<{ id: string }[]>`
      INSERT INTO listen_sessions (host_user_id, control_mode, join_code)
      VALUES (${userB.id}, 'host_only', ${sessionBJoinCode})
      RETURNING id
    `;
    userBSessionId = sessionB.id;

    // 6. Seed handoff_sessions for User B
    await sqlAuthRole`
      INSERT INTO handoff_sessions (user_id, source_device_id, status)
      VALUES (${userB.id}, ${userBDeviceId}, 'pending')
    `;
  });

  after(async () => {
    try {
      await cleanupTestUsers(userIds);
    } catch {}
  });

  it('1. user_likes: User A cannot see User B likes', async () => {
    await asUser(userA.id, async (tx) => {
      const likes = await tx`SELECT id, user_id, provider_track_id FROM user_likes WHERE user_id = ${userB.id}`;
      assert.strictEqual(likes.length, 0, 'User A should see 0 rows for User B likes');
    });
  });

  it('2. imported_playlists: User A cannot see User B imported playlists', async () => {
    await asUser(userA.id, async (tx) => {
      const playlists = await tx`SELECT id, user_id, title FROM imported_playlists WHERE user_id = ${userB.id}`;
      assert.strictEqual(playlists.length, 0, 'User A should see 0 rows for User B imported playlists');
    });
  });

  it('3. user_play_history: User A cannot see User B play history', async () => {
    await asUser(userA.id, async (tx) => {
      const history = await tx`SELECT id, user_id, provider_track_id FROM user_play_history WHERE user_id = ${userB.id}`;
      assert.strictEqual(history.length, 0, 'User A should see 0 rows for User B play history');
    });
  });

  it('4. sync_operations: User A cannot see User B sync operations', async () => {
    await asUser(userA.id, async (tx) => {
      const ops = await tx`SELECT id, user_id, entity_id FROM sync_operations WHERE user_id = ${userB.id}`;
      assert.strictEqual(ops.length, 0, 'User A should see 0 rows for User B sync operations');
    });
  });

  it('5. handoff_sessions: User A cannot see User B handoff sessions', async () => {
    await asUser(userA.id, async (tx) => {
      const sessions = await tx`SELECT id, user_id FROM handoff_sessions WHERE user_id = ${userB.id}`;
      assert.strictEqual(sessions.length, 0, 'User A should see 0 rows for User B handoff sessions');
    });
  });

  it('6. listen_sessions: User A cannot delete or hijack User B listen session', async () => {
    await asUser(userA.id, async (tx) => {
      const deleteResult = await tx`DELETE FROM listen_sessions WHERE id = ${userBSessionId}`;
      assert.strictEqual(deleteResult.count, 0, 'User A cannot delete User B listen session');

      const updateResult = await tx`
        UPDATE listen_sessions
        SET control_mode = 'open'
        WHERE id = ${userBSessionId}
      `;
      assert.strictEqual(updateResult.count, 0, 'User A cannot update User B listen session without authorization');
    });
  });
});
