import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import {
  sqlAppUser,
  sqlAuthRole,
  createTestUser,
  cleanupTestUsers,
  asUser,
} from './helpers/rls.js';

describe('Supabase RLS & Role Isolation Tests', () => {
  let userA: { id: string; username: string; email: string };
  let userB: { id: string; username: string; email: string };
  const userIds: string[] = [];

  before(async () => {
    const timestamp = Date.now();
    userA = await createTestUser(`user_a_${timestamp}`, `user_a_${timestamp}@example.com`);
    userB = await createTestUser(`user_b_${timestamp}`, `user_b_${timestamp}@example.com`);
    userIds.push(userA.id, userB.id);

    // Seed devices for User A and User B
    await sqlAuthRole`
      INSERT INTO user_devices (user_id, device_name, platform)
      VALUES
        (${userA.id}, 'Phone A', 'android'),
        (${userB.id}, 'Phone B', 'android')
    `;

    // Seed e2e prekey for User B
    await sqlAuthRole`
      INSERT INTO e2e_signed_prekeys (user_id, device_id, key_id, public_key, signature)
      SELECT ${userB.id}, id, 1, 'test_pub_key', 'test_sig'
      FROM user_devices WHERE user_id = ${userB.id}
    `;
  });

  after(async () => {
    try {
      await cleanupTestUsers(userIds);
    } catch {}
  });

  it('user A sees only own devices, user B devices are hidden', async () => {
    await asUser(userA.id, async (tx) => {
      const devices = await tx`SELECT id, user_id, device_name FROM user_devices`;
      assert.strictEqual(devices.length, 1);
      assert.strictEqual(devices[0].user_id, userA.id);
      assert.strictEqual(devices[0].device_name, 'Phone A');
    });
  });

  it('user A querying user B devices explicitly returns 0 rows', async () => {
    await asUser(userA.id, async (tx) => {
      const devices = await tx`SELECT id, user_id FROM user_devices WHERE user_id = ${userB.id}`;
      assert.strictEqual(devices.length, 0);
    });
  });

  it('user A attempting to update user B row affects 0 rows', async () => {
    await asUser(userA.id, async (tx) => {
      const result = await tx`UPDATE users SET username = 'hacked' WHERE id = ${userB.id}`;
      assert.strictEqual(result.count, 0);
    });
  });

  it('app_user cannot SELECT password_hash directly from users table (permission denied)', async () => {
    let denied = false;
    try {
      await sqlAppUser`SELECT password_hash FROM users LIMIT 1`;
    } catch (err: any) {
      denied = err.code === '42501'; // Postgres error code for permission denied
    }
    assert.strictEqual(denied, true, 'Direct SELECT on users should be denied to app_user');
  });

  it('app_user can query users_public view without exposing password_hash', async () => {
    const publicUsers = await sqlAppUser`SELECT * FROM users_public WHERE id = ${userA.id}`;
    assert.strictEqual(publicUsers.length, 1);
    assert.strictEqual(publicUsers[0].username, userA.username);
    assert.strictEqual((publicUsers[0] as any).password_hash, undefined);
  });

  it('auth_role (authDb) can query users table directly', async () => {
    const rows = await sqlAuthRole`SELECT id, email FROM users WHERE id = ${userA.id}`;
    assert.strictEqual(rows.length, 1);
    assert.strictEqual(rows[0].email, userA.email);
  });

  it('prekey SELECT is allowed for any authenticated user', async () => {
    await asUser(userA.id, async (tx) => {
      const prekeys = await tx`SELECT * FROM e2e_signed_prekeys WHERE user_id = ${userB.id}`;
      assert.strictEqual(prekeys.length, 1);
      assert.strictEqual(prekeys[0].public_key, 'test_pub_key');
    });
  });
});
