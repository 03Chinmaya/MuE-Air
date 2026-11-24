import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';
import { createTestUser, cleanupTestUsers, sqlAuthRole, asUser } from './helpers/rls.js';
import { generateAccessToken } from '../src/services/authService.js';
import { resetRateLimits } from '../src/middleware/rateLimiter.js';

describe('Phase 10A — Backend Social Specification Tests', () => {
  let userA: { id: string; username: string; email: string };
  let userB: { id: string; username: string; email: string };
  let userC: { id: string; username: string; email: string }; // Private user
  let userD: { id: string; username: string; email: string }; // Blocked by A
  let userE: { id: string; username: string; email: string }; // Blocks A

  let tokenA: string;
  let tokenB: string;
  let tokenC: string;
  let tokenD: string;
  let tokenE: string;

  let createdRequestId: string = '';

  before(async () => {
    const ts = Date.now();
    userA = await createTestUser(`soc_a_${ts}`, `soc_a_${ts}@test.com`, false);
    userB = await createTestUser(`soc_b_${ts}`, `soc_b_${ts}@test.com`, false);
    userC = await createTestUser(`soc_c_${ts}`, `soc_c_${ts}@test.com`, true); // private account
    userD = await createTestUser(`soc_d_${ts}`, `soc_d_${ts}@test.com`, false);
    userE = await createTestUser(`soc_e_${ts}`, `soc_e_${ts}@test.com`, false);

    tokenA = generateAccessToken(userA.id, userA.username);
    tokenB = generateAccessToken(userB.id, userB.username);
    tokenC = generateAccessToken(userC.id, userC.username);
    tokenD = generateAccessToken(userD.id, userD.username);
    tokenE = generateAccessToken(userE.id, userE.username);
  });

  after(async () => {
    try {
      await cleanupTestUsers([userA.id, userB.id, userC.id, userD.id, userE.id]);
    } catch {}
  });

  // 1. Follow public -> transaction commits, counts ±1, notification inserted
  it('1. Follow public -> transaction commits, counts ±1, notification inserted', async () => {
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/social/follow/${userB.id}`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );

    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.strictEqual(body.status, 'following');

    // Verify database counts and follow edge
    const [dbFollow] = await sqlAuthRole`
      SELECT * FROM follows WHERE follower_id = ${userA.id} AND followee_id = ${userB.id}
    `;
    assert.ok(dbFollow, 'Follow edge must exist');

    const [dbUserA] = await sqlAuthRole<{ following_count: number }[]>`
      SELECT following_count FROM users WHERE id = ${userA.id}
    `;
    const [dbUserB] = await sqlAuthRole<{ follower_count: number }[]>`
      SELECT follower_count FROM users WHERE id = ${userB.id}
    `;
    assert.strictEqual(Number(dbUserA.following_count), 1, 'Follower following_count should be incremented');
    assert.strictEqual(Number(dbUserB.follower_count), 1, 'Followee follower_count should be incremented');

    // Verify notification was created
    const notifs = await sqlAuthRole<{ type: string; actor_id: string }[]>`
      SELECT type, actor_id FROM notifications WHERE user_id = ${userB.id} AND type = 'new_follower'
    `;
    assert.ok(notifs.length > 0, 'new_follower notification should be inserted');
    assert.strictEqual(notifs[0].actor_id, userA.id);
  });

  // 2. Follow private -> follow_request created, expires_at = now + 30 days
  it('2. Follow private -> follow_request created, expires_at = now + 30 days', async () => {
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/social/follow/${userC.id}`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );

    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.strictEqual(body.status, 'requested');
    assert.ok(body.requestId, 'requestId must be returned');
    createdRequestId = body.requestId;

    // Follow edge must NOT exist
    const [edge] = await sqlAuthRole`
      SELECT * FROM follows WHERE follower_id = ${userA.id} AND followee_id = ${userC.id}
    `;
    assert.strictEqual(edge, undefined, 'Follow edge must not exist before approval');

    // Verify follow_request in DB
    const [reqRow] = await sqlAuthRole<{ id: string; status: string; created_at: string; expires_at: string }[]>`
      SELECT id, status, created_at, expires_at FROM follow_requests WHERE id = ${createdRequestId}
    `;
    assert.ok(reqRow, 'Follow request row must exist');
    assert.strictEqual(reqRow.status, 'pending');

    const createdTime = new Date(reqRow.created_at).getTime();
    const expiresTime = new Date(reqRow.expires_at).getTime();
    const diffDays = (expiresTime - createdTime) / (1000 * 60 * 60 * 24);
    assert.ok(diffDays >= 29 && diffDays <= 31, `Expires at should be ~30 days, got ${diffDays.toFixed(2)}`);

    // Verify follow_request notification
    const [notif] = await sqlAuthRole<{ type: string; actor_id: string }[]>`
      SELECT type, actor_id FROM notifications WHERE user_id = ${userC.id} AND type = 'follow_request'
    `;
    assert.ok(notif, 'follow_request notification must exist');
    assert.strictEqual(notif.actor_id, userA.id);
  });

  // 3. Accept request -> transaction: follow inserted, request deleted, counts updated, notification to sender
  it('3. Accept request -> transaction: follow inserted, request deleted, counts updated, notification to sender', async () => {
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/social/follow-requests/${createdRequestId}/accept`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${tokenC}`,
        },
      })
    );

    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.strictEqual(body.status, 'accepted');

    // Verify follow edge created
    const [dbFollow] = await sqlAuthRole`
      SELECT * FROM follows WHERE follower_id = ${userA.id} AND followee_id = ${userC.id}
    `;
    assert.ok(dbFollow, 'Follow edge must exist after accept');

    // Verify request deleted
    const [reqRow] = await sqlAuthRole`
      SELECT * FROM follow_requests WHERE id = ${createdRequestId}
    `;
    assert.strictEqual(reqRow, undefined, 'Follow request must be deleted after accept');

    // Verify counts updated
    const [dbUserA] = await sqlAuthRole<{ following_count: number }[]>`
      SELECT following_count FROM users WHERE id = ${userA.id}
    `;
    const [dbUserC] = await sqlAuthRole<{ follower_count: number }[]>`
      SELECT follower_count FROM users WHERE id = ${userC.id}
    `;
    assert.strictEqual(Number(dbUserA.following_count), 2, 'Follower following_count should be 2 (follows B and C)');
    assert.strictEqual(Number(dbUserC.follower_count), 1, 'Private followee follower_count should be 1');

    // Verify notification sent to sender (userA)
    const [notif] = await sqlAuthRole<{ type: string; actor_id: string }[]>`
      SELECT type, actor_id FROM notifications WHERE user_id = ${userA.id} AND type = 'follow_accepted'
    `;
    assert.ok(notif, 'follow_accepted notification must be sent to requester');
    assert.strictEqual(notif.actor_id, userC.id);
  });

  // 4. Reject request -> status = rejected
  it('4. Reject request -> status = rejected', async () => {
    // User B requests to follow private user C
    const reqRes = await app.fetch(
      new Request(`http://localhost:4000/api/social/follow/${userC.id}`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${tokenB}`,
        },
      })
    );
    const reqBody = (await reqRes.json()) as any;
    const reqId = reqBody.requestId;
    assert.ok(reqId, 'RequestId must be returned');

    // User C rejects
    const rejectRes = await app.fetch(
      new Request(`http://localhost:4000/api/social/follow-requests/${reqId}/reject`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${tokenC}`,
        },
      })
    );

    assert.strictEqual(rejectRes.status, 200);
    const rejectBody = (await rejectRes.json()) as any;
    assert.strictEqual(rejectBody.status, 'rejected');

    // Check request status in DB
    const [reqRow] = await sqlAuthRole<{ status: string }[]>`
      SELECT status FROM follow_requests WHERE id = ${reqId}
    `;
    assert.strictEqual(reqRow.status, 'rejected');

    // Confirm no follow edge
    const [edge] = await sqlAuthRole`
      SELECT * FROM follows WHERE follower_id = ${userB.id} AND followee_id = ${userC.id}
    `;
    assert.strictEqual(edge, undefined, 'No follow edge should exist on rejected request');
  });

  // 5. Block -> follows preserved (NOT deleted)
  it('5. Block -> follows preserved (NOT deleted per Stack Lock §18.9)', async () => {
    // User A already follows User B. Now User A blocks User B.
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/social/block/${userB.id}`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );

    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.strictEqual(body.status, 'blocked');

    // Verify block row exists
    const [blockRow] = await sqlAuthRole`
      SELECT * FROM blocks WHERE blocker_id = ${userA.id} AND blocked_id = ${userB.id}
    `;
    assert.ok(blockRow, 'Block record must exist');

    // CRITICAL: Verify follow edge was NOT deleted
    const [followEdge] = await sqlAuthRole`
      SELECT * FROM follows WHERE follower_id = ${userA.id} AND followee_id = ${userB.id}
    `;
    assert.ok(followEdge, 'Follow edge must NOT be deleted when user is blocked (Stack Lock §18.9)');
  });

  // 6. Unblock -> existing follows still present
  it('6. Unblock -> existing follows still present', async () => {
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/social/block/${userB.id}`, {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );

    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.strictEqual(body.status, 'unblocked');

    // Block row must be gone
    const [blockRow] = await sqlAuthRole`
      SELECT * FROM blocks WHERE blocker_id = ${userA.id} AND blocked_id = ${userB.id}
    `;
    assert.strictEqual(blockRow, undefined, 'Block record must be removed');

    // Follow edge must still exist
    const [followEdge] = await sqlAuthRole`
      SELECT * FROM follows WHERE follower_id = ${userA.id} AND followee_id = ${userB.id}
    `;
    assert.ok(followEdge, 'Follow edge must still be preserved after unblocking');
  });

  // 7. Search excludes self
  it('7. Search excludes self', async () => {
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/social/search/users?q=${encodeURIComponent(userA.username)}`, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );

    assert.strictEqual(res.status, 200);
    const results = (await res.json()) as any[];
    const selfFound = results.find((u) => u.id === userA.id || u.username === userA.username);
    assert.strictEqual(selfFound, undefined, 'Search results must exclude the requesting viewer');
  });

  // 8. Search excludes blocked users (both directions)
  it('8. Search excludes blocked users (both directions)', async () => {
    // Setup blocks: User A blocks User D; User E blocks User A
    await sqlAuthRole`
      INSERT INTO blocks (blocker_id, blocked_id)
      VALUES (${userA.id}, ${userD.id}), (${userE.id}, ${userA.id})
      ON CONFLICT DO NOTHING
    `;

    // 1. Search for userD (viewer blocked them)
    const resD = await app.fetch(
      new Request(`http://localhost:4000/api/social/search/users?q=${encodeURIComponent(userD.username)}`, {
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    const resultsD = (await resD.json()) as any[];
    assert.strictEqual(resultsD.find((u) => u.id === userD.id), undefined, 'Blocked user D must not appear in search');

    // 2. Search for userE (userE blocked viewer)
    const resE = await app.fetch(
      new Request(`http://localhost:4000/api/social/search/users?q=${encodeURIComponent(userE.username)}`, {
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    const resultsE = (await resE.json()) as any[];
    assert.strictEqual(resultsE.find((u) => u.id === userE.id), undefined, 'User E who blocked viewer must not appear');
  });

  // 9. Search never returns email/phone
  it('9. Search never returns email/phone or password hash', async () => {
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/social/search/users?q=soc_`, {
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );

    assert.strictEqual(res.status, 200);
    const results = (await res.json()) as any[];
    assert.ok(results.length > 0, 'Should find matching test users');

    for (const item of results) {
      assert.strictEqual(item.email, undefined, 'Email must never be exposed');
      assert.strictEqual(item.phone, undefined, 'Phone must never be exposed');
      assert.strictEqual(item.passwordHash, undefined, 'Password hash must never be exposed');
      assert.strictEqual(item.password_hash, undefined, 'password_hash must never be exposed');
      assert.ok(item.id, 'User ID should be present');
      assert.ok(item.username, 'Username should be present');
    }
  });

  // 10. RLS: user A cannot read user B's follow edges for private accounts
  it("10. RLS: user A cannot read private account's follow edges if not an approved follower", async () => {
    // User C is private. Create a separate userX who follows User C.
    const ts = Date.now();
    const userX = await createTestUser(`soc_x_${ts}`, `soc_x_${ts}@test.com`, false);
    await sqlAuthRole`
      INSERT INTO follows (follower_id, followee_id) VALUES (${userX.id}, ${userC.id})
    `;

    // Now, User B (who is NOT a follower of private User C) queries follows via app_user role
    const visibleEdges = await asUser(userB.id, async (tx) => {
      return tx`SELECT * FROM follows WHERE followee_id = ${userC.id}`;
    });

    // Clean up userX
    await cleanupTestUsers([userX.id]);

    // User B should NOT be able to see userX's follow edge to private User C
    const seenX = visibleEdges.find((e: any) => e.follower_id === userX.id);
    assert.strictEqual(seenX, undefined, "User B must not see userX's follow edge to private user C");
  });

  // 11. RLS: user cannot spoof follower in follows INSERT
  it('11. RLS: user B cannot INSERT into follows with spoofed follower_id (user A) via app_user', async () => {
    await assert.rejects(
      async () => {
        await asUser(userB.id, async (tx) => {
          await tx`
            INSERT INTO follows (follower_id, followee_id)
            VALUES (${userA.id}, ${userD.id})
          `;
        });
      },
      /violates row-level security policy|permission denied/i,
      'Direct client INSERT on follows with spoofed follower_id must be blocked by RLS'
    );
  });

  // 12. Rate limit: 6th follow in window -> 429
  it('12. Rate limit: 6th follow in window returns 429 with Retry-After header', async () => {
    process.env.RATE_LIMIT_FOLLOW_MAX = '5';
    resetRateLimits();

    const ts = Date.now();
    const rateUser = await createTestUser(`soc_rate_${ts}`, `soc_rate_${ts}@test.com`);
    const rateToken = generateAccessToken(rateUser.id, rateUser.username);

    try {
      // 5 calls should succeed (or be processed by business logic)
      for (let i = 0; i < 5; i++) {
        const res = await app.fetch(
          new Request(`http://localhost:4000/api/social/follow/${userB.id}`, {
            method: 'POST',
            headers: { Authorization: `Bearer ${rateToken}` },
          })
        );
        assert.ok(res.status === 200 || res.status === 404, `Request ${i + 1} should be processed, got ${res.status}`);
      }

      // 6th call should be blocked by rate limiter
      const blockedRes = await app.fetch(
        new Request(`http://localhost:4000/api/social/follow/${userB.id}`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${rateToken}` },
        })
      );

      assert.strictEqual(blockedRes.status, 429, '6th follow request must return 429 Too Many Requests');
      const retryAfter = blockedRes.headers.get('Retry-After');
      assert.ok(retryAfter, 'Retry-After header must be present on 429');
    } finally {
      delete process.env.RATE_LIMIT_FOLLOW_MAX;
      resetRateLimits();
      await cleanupTestUsers([rateUser.id]);
    }
  });

  // 13. Avatar signed URL: 500 without SUPABASE_SERVICE_ROLE_KEY (no mock fake) or real signed URL
  it('13. Avatar signed URL: 500 without SUPABASE_SERVICE_ROLE_KEY or real signed URL if configured', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/profile/me/avatar', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );

    if (!process.env.SUPABASE_SERVICE_ROLE_KEY) {
      assert.strictEqual(res.status, 500, 'Must throw 500 when SUPABASE_SERVICE_ROLE_KEY is missing (no fake success)');
      const body = (await res.json()) as any;
      assert.ok(body.error?.includes('missing SUPABASE_SERVICE_ROLE_KEY'), 'Should return missing key error');
    } else {
      assert.strictEqual(res.status, 200);
      const body = (await res.json()) as any;
      assert.ok(body.signedUrl, 'signedUrl must exist');
      assert.ok(body.path, 'path must exist');

      // Must match pattern: avatars/{userId}/{timestamp}.jpg
      const expectedPrefix = `avatars/${userA.id}/`;
      assert.ok(body.path.startsWith(expectedPrefix), `Path must start with ${expectedPrefix}, got ${body.path}`);
      assert.ok(body.path.endsWith('.jpg'), `Path must end with .jpg, got ${body.path}`);
      assert.ok(body.signedUrl.includes(expectedPrefix), 'signedUrl must contain user avatars prefix');
      assert.strictEqual(body.expiresIn, 300, 'TTL must be 300s (5 min)');
    }
  });

  // 14. Real Supabase Storage upload flow (or skipped with warning if key missing)
  it('14. Real Supabase Storage upload flow (or skipped with warning if key missing)', async () => {
    if (!process.env.SUPABASE_SERVICE_ROLE_KEY) {
      console.warn('⚠️ WARNING: SUPABASE_SERVICE_ROLE_KEY is absent. Skipping real Supabase Storage upload test.');
      return;
    }

    // 1. Request real signed URL
    const signRes = await app.fetch(
      new Request('http://localhost:4000/api/profile/me/avatar', {
        method: 'POST',
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(signRes.status, 200);
    const { signedUrl, path } = (await signRes.json()) as any;

    // 2. PUT a 100 KB JPEG to the signed URL
    const fakeJpeg = Buffer.alloc(100 * 1024, 0xff);
    const uploadRes = await fetch(signedUrl, {
      method: 'PUT',
      headers: { 'Content-Type': 'image/jpeg' },
      body: fakeJpeg,
    });
    assert.ok(uploadRes.ok, `Upload to Supabase Storage signed URL must succeed, got ${uploadRes.status}`);

    // 3. Complete avatar upload
    const completeRes = await app.fetch(
      new Request('http://localhost:4000/api/profile/me/avatar/complete', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ path }),
      })
    );
    assert.strictEqual(completeRes.status, 200);
    const completeBody = (await completeRes.json()) as any;
    assert.ok(completeBody.avatarUrl);

    // 4. Verify CDN URL returns 200
    const cdnRes = await fetch(completeBody.avatarUrl, { method: 'HEAD' });
    assert.strictEqual(cdnRes.status, 200, 'Avatar CDN URL must return 200');

    // 5. Delete avatar
    const deleteRes = await app.fetch(
      new Request('http://localhost:4000/api/profile/me/avatar', {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(deleteRes.status, 200);
  });

  // 15. Bug 1 Verification: /api/social/blocks returns blocked user after block and empty after unblock
  it('15. Bug 1: /api/social/blocks returns blocked user after block and empty after unblock', async () => {
    // 1. Block user D
    const blockRes = await app.fetch(
      new Request(`http://localhost:4000/api/social/block/${userD.id}`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(blockRes.status, 200);

    // 2. Query blocks list
    const listRes = await app.fetch(
      new Request('http://localhost:4000/api/social/blocks', {
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(listRes.status, 200);
    const list = (await listRes.json()) as any[];
    assert.ok(Array.isArray(list), 'Blocks must return an array');
    const blockedEntry = list.find((b) => b.blockedId === userD.id);
    assert.ok(blockedEntry, 'Blocked user D must be present in blocks list');
    assert.strictEqual(blockedEntry.username, userD.username);

    // 3. Unblock user D
    const unblockRes = await app.fetch(
      new Request(`http://localhost:4000/api/social/block/${userD.id}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(unblockRes.status, 200);

    // 4. Query blocks list again -> user D must be gone
    const emptyListRes = await app.fetch(
      new Request('http://localhost:4000/api/social/blocks', {
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(emptyListRes.status, 200);
    const emptyList = (await emptyListRes.json()) as any[];
    assert.strictEqual(emptyList.find((b) => b.blockedId === userD.id), undefined);
  });

  // 16. Bug 2 Verification: /api/social/follow-requests returns outgoing requests for requester and incoming for recipient
  it('16. Bug 2: /api/social/follow-requests returns outgoing requests for requester and incoming for recipient', async () => {
    const ts = Date.now();
    const sender = await createTestUser(`req_s_${ts}`, `req_s_${ts}@test.com`, false);
    const privateTarget = await createTestUser(`req_t_${ts}`, `req_t_${ts}@test.com`, true);
    const sToken = generateAccessToken(sender.id, sender.username);
    const tToken = generateAccessToken(privateTarget.id, privateTarget.username);

    try {
      // 1. Sender requests to follow private target
      const followRes = await app.fetch(
        new Request(`http://localhost:4000/api/social/follow/${privateTarget.id}`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${sToken}` },
        })
      );
      assert.strictEqual(followRes.status, 200);
      const followData = (await followRes.json()) as any;
      assert.strictEqual(followData.status, 'requested');

      // 2. Sender queries outgoing requests -> must see 1 request
      const outgoingRes = await app.fetch(
        new Request('http://localhost:4000/api/social/follow-requests?direction=outgoing', {
          headers: { Authorization: `Bearer ${sToken}` },
        })
      );
      assert.strictEqual(outgoingRes.status, 200);
      const outgoingList = (await outgoingRes.json()) as any[];
      assert.ok(Array.isArray(outgoingList), 'Outgoing must return array');
      const reqForSender = outgoingList.find((r) => r.toUserId === privateTarget.id);
      assert.ok(reqForSender, 'Sender must see outgoing follow request to private target');
      assert.strictEqual(reqForSender.status, 'pending');

      // 3. Recipient queries incoming requests -> must see 1 request
      const incomingRes = await app.fetch(
        new Request('http://localhost:4000/api/social/follow-requests?direction=incoming', {
          headers: { Authorization: `Bearer ${tToken}` },
        })
      );
      assert.strictEqual(incomingRes.status, 200);
      const incomingList = (await incomingRes.json()) as any[];
      assert.ok(Array.isArray(incomingList), 'Incoming must return array');
      const reqForRecipient = incomingList.find((r) => r.fromUserId === sender.id);
      assert.ok(reqForRecipient, 'Recipient must see incoming follow request from sender');
      assert.strictEqual(reqForRecipient.status, 'pending');
    } finally {
      await cleanupTestUsers([sender.id, privateTarget.id]);
    }
  });

  // 17. Bug 3 Verification: follow request does NOT increment followingCount, only acceptance does
  it('17. Bug 3: follow request to private user does NOT increment followingCount, only acceptance does', async () => {
    const ts = Date.now();
    const freshAlice = await createTestUser(`alice_${ts}`, `alice_${ts}@test.com`, false);
    const publicBob = await createTestUser(`bob_${ts}`, `bob_${ts}@test.com`, false);
    const privateCharlie = await createTestUser(`charlie_${ts}`, `charlie_${ts}@test.com`, true);

    const aTok = generateAccessToken(freshAlice.id, freshAlice.username);
    const cTok = generateAccessToken(privateCharlie.id, privateCharlie.username);

    try {
      // 1. Initial followingCount is 0
      const prof0Res = await app.fetch(
        new Request('http://localhost:4000/api/profile/me', {
          headers: { Authorization: `Bearer ${aTok}` },
        })
      );
      const prof0 = (await prof0Res.json()) as any;
      assert.strictEqual(Number(prof0.followingCount), 0);

      // 2. Alice follows public Bob -> followingCount becomes 1
      const fBobRes = await app.fetch(
        new Request(`http://localhost:4000/api/social/follow/${publicBob.id}`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${aTok}` },
        })
      );
      assert.strictEqual(fBobRes.status, 200);

      const prof1Res = await app.fetch(
        new Request('http://localhost:4000/api/profile/me', {
          headers: { Authorization: `Bearer ${aTok}` },
        })
      );
      const prof1 = (await prof1Res.json()) as any;
      assert.strictEqual(Number(prof1.followingCount), 1, 'followingCount must be 1 after public follow');

      // 3. Alice requests private Charlie -> followingCount MUST STILL BE 1
      const fCharlieRes = await app.fetch(
        new Request(`http://localhost:4000/api/social/follow/${privateCharlie.id}`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${aTok}` },
        })
      );
      assert.strictEqual(fCharlieRes.status, 200);
      const charlieData = (await fCharlieRes.json()) as any;
      assert.strictEqual(charlieData.status, 'requested');
      const reqId = charlieData.requestId;

      // Assert Alice's followingCount is STILL 1
      const prof2Res = await app.fetch(
        new Request('http://localhost:4000/api/profile/me', {
          headers: { Authorization: `Bearer ${aTok}` },
        })
      );
      const prof2 = (await prof2Res.json()) as any;
      assert.strictEqual(Number(prof2.followingCount), 1, 'followingCount MUST NOT increment on pending follow request');

      // Assert Charlie's followerCount is STILL 0
      const charlieProfRes = await app.fetch(
        new Request('http://localhost:4000/api/profile/me', {
          headers: { Authorization: `Bearer ${cTok}` },
        })
      );
      const charlieProf = (await charlieProfRes.json()) as any;
      assert.strictEqual(Number(charlieProf.followerCount), 0, 'followerCount MUST NOT increment on pending follow request');

      // 4. Charlie accepts request -> now followingCount becomes 2 and followerCount becomes 1
      const acceptRes = await app.fetch(
        new Request(`http://localhost:4000/api/social/follow-requests/${reqId}/accept`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${cTok}` },
        })
      );
      assert.strictEqual(acceptRes.status, 200);

      const prof3Res = await app.fetch(
        new Request('http://localhost:4000/api/profile/me', {
          headers: { Authorization: `Bearer ${aTok}` },
        })
      );
      const prof3 = (await prof3Res.json()) as any;
      assert.strictEqual(Number(prof3.followingCount), 2, 'followingCount increments to 2 only after request acceptance');
    } finally {
      await cleanupTestUsers([freshAlice.id, publicBob.id, privateCharlie.id]);
    }
  });

  // 18. Private account followers list: non-follower receives 403 Forbidden
  it('18. GET /api/social/followers/:userId returns 403 for private account when viewer does not follow', async () => {
    // User C is a private account. User B is not an approved follower (request was rejected in test 4).
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/social/followers/${userC.id}`, {
        headers: { Authorization: `Bearer ${tokenB}` },
      })
    );

    assert.strictEqual(res.status, 403, 'Non-follower must receive 403 when requesting private user followers');
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'This account is private');
  });

  it('19. GET /api/social/search/users returns 401 when called without auth token', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/social/search/users?q=test')
    );
    assert.strictEqual(res.status, 401);
  });

  it('20. POST /api/social/follow/invalid-uuid returns 400 Bad Request', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/social/follow/invalid-uuid', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );
    assert.strictEqual(res.status, 400);
  });
});

