import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';
import { createTestUser, cleanupTestUsers, sqlAuthRole } from './helpers/rls.js';
import { generateAccessToken } from '../src/services/authService.js';
import { closeConnections } from '../src/db/connection.js';

describe('RLS Connection Concurrency & Claim Isolation Tests', () => {
  let userA: { id: string; username: string; email: string };
  let userB: { id: string; username: string; email: string };
  let userTargetA: { id: string; username: string; email: string };
  let userTargetB: { id: string; username: string; email: string };

  let tokenA: string;
  let tokenB: string;

  before(async () => {
    const ts = Date.now();
    userA = await createTestUser(`conc_a_${ts}`, `conc_a_${ts}@test.com`, false);
    userB = await createTestUser(`conc_b_${ts}`, `conc_b_${ts}@test.com`, false);
    userTargetA = await createTestUser(`conc_ta_${ts}`, `conc_ta_${ts}@test.com`, false);
    userTargetB = await createTestUser(`conc_tb_${ts}`, `conc_tb_${ts}@test.com`, false);

    tokenA = generateAccessToken(userA.id, userA.username);
    tokenB = generateAccessToken(userB.id, userB.username);

    // Seed distinct data for User A and User B
    // 1. Notifications: User A has notif from Target A; User B has notif from Target B
    await sqlAuthRole`
      INSERT INTO notifications (user_id, actor_id, type)
      VALUES 
        (${userA.id}, ${userTargetA.id}, 'new_follower'),
        (${userB.id}, ${userTargetB.id}, 'follow_accepted');
    `;

    // 2. Blocks: User A blocks Target A; User B blocks Target B
    await sqlAuthRole`
      INSERT INTO blocks (blocker_id, blocked_id)
      VALUES 
        (${userA.id}, ${userTargetA.id}),
        (${userB.id}, ${userTargetB.id});
    `;

  });

  after(async () => {
    try {
      await cleanupTestUsers([userA.id, userB.id, userTargetA.id, userTargetB.id]);
    } catch {
      // Best-effort cleanup
    }
    await sqlAuthRole.end();
    await closeConnections();
  });

  it('50 concurrent requests mixing User A and User B tokens maintain strict RLS isolation', async () => {
    const TOTAL_REQUESTS = 50;

    // Fire 50 concurrent requests simultaneously across the pool
    const promises = Array.from({ length: TOTAL_REQUESTS }, async (_, i) => {
      const isUserA = i % 2 === 0;
      const token = isUserA ? tokenA : tokenB;
      const expectedUser = isUserA ? userA : userB;
      const unexpectedUser = isUserA ? userB : userA;

      // 1. Check /api/profile/me
      const profileRes = await app.fetch(
        new Request('http://localhost:4000/api/profile/me', {
          headers: { Authorization: `Bearer ${token}` },
        })
      );
      assert.strictEqual(profileRes.status, 200, `Profile request ${i} must return 200`);
      const profile = (await profileRes.json()) as any;
      assert.strictEqual(
        profile.id,
        expectedUser.id,
        `Req ${i}: Expected profile ID ${expectedUser.id}, got ${profile.id}`
      );
      assert.notStrictEqual(
        profile.id,
        unexpectedUser.id,
        `Req ${i}: Cross-user claim leak detected! Returned profile of other user`
      );

      // 2. Check /api/notifications (RLS: user_id = auth.uid())
      const notifRes = await app.fetch(
        new Request('http://localhost:4000/api/notifications', {
          headers: { Authorization: `Bearer ${token}` },
        })
      );
      assert.strictEqual(notifRes.status, 200, `Notifications request ${i} must return 200`);
      const notifData = (await notifRes.json()) as any;
      const items = notifData.items as any[];
      assert.ok(Array.isArray(items), `Req ${i}: Notifications items must be an array`);

      const expectedActor = isUserA ? userTargetA.id : userTargetB.id;
      const unexpectedActor = isUserA ? userTargetB.id : userTargetA.id;

      const sawExpectedNotif = items.some((n) => n.actorId === expectedActor);
      const sawUnexpectedNotif = items.some((n) => n.actorId === unexpectedActor);

      assert.ok(sawExpectedNotif, `Req ${i}: User must see their own notifications`);
      assert.strictEqual(
        sawUnexpectedNotif,
        false,
        `Req ${i}: Cross-user leak! User saw notifications belonging to different user`
      );

      // 3. Check /api/social/blocks (RLS: blocker_id = auth.uid())
      const blocksRes = await app.fetch(
        new Request('http://localhost:4000/api/social/blocks', {
          headers: { Authorization: `Bearer ${token}` },
        })
      );
      assert.strictEqual(blocksRes.status, 200, `Blocks request ${i} must return 200`);
      const blocksList = (await blocksRes.json()) as any[];
      assert.ok(Array.isArray(blocksList), `Req ${i}: Blocks list must be an array`);

      const expectedBlockedTarget = isUserA ? userTargetA.id : userTargetB.id;
      const unexpectedBlockedTarget = isUserA ? userTargetB.id : userTargetA.id;

      const hasExpected = blocksList.some((b) => b.blockedId === expectedBlockedTarget);
      const hasUnexpected = blocksList.some((b) => b.blockedId === unexpectedBlockedTarget);

      assert.ok(hasExpected, `Req ${i}: User must see their own blocked target`);
      assert.strictEqual(
        hasUnexpected,
        false,
        `Req ${i}: Cross-user leak! User saw blocked target belonging to different user`
      );

      return { requestIndex: i, success: true };
    });

    const results = await Promise.all(promises);
    assert.strictEqual(results.length, TOTAL_REQUESTS, `All ${TOTAL_REQUESTS} concurrent requests must complete`);
    for (const res of results) {
      assert.strictEqual(res.success, true);
    }
  });
});
