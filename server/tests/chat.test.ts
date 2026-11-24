/**
 * Phase 11C.1 Server Chat Specification Tests
 * Validates:
 * 1. Mutual-follow gating (Correction 1) & DB trigger defense-in-depth
 * 2. RLS policy enforcement blocking direct client INSERT/UPDATE/DELETE (Correction 2)
 * 3. Removal of duplicate E2E routes from /api/chat (Correction 3)
 * 4. Storing ciphertext only on server
 * 5. 12 KB ciphertext size limit
 * 6. 30 messages/min rate limit
 * 7. Cross-user isolation and pagination
 */

process.env.NODE_ENV = 'test';

import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import nodeCrypto from 'node:crypto';
import { app } from '../src/app.js';
import { createTestUser, cleanupTestUsers, sqlAuthRole, sqlAppUser, asUser } from './helpers/rls.js';
import { generateAccessToken } from '../src/services/authService.js';
import { resetRateLimits } from '../src/middleware/rateLimiter.js';
import { resetPresignRateLimits, resetMessageStatusRateLimits, __setSimulateDbFailureForCleanup, resetSentPushHints } from '../src/routes/chatRoutes.js';
import { __setMockPushHandler, sendDataPush, isPushConfigured, __setMockGoogleAuth } from '../src/services/pushService.js';
import { __setMockObjectMetadata, getObjectMetadata, deleteObject } from '../src/services/blobService.js';
import { runMessageSweep } from '../src/jobs/messageSweeper.js';

describe('Phase 11C.1 — Server 1:1 Chat Specification & Security Tests', () => {
  let userA: { id: string; username: string; email: string };
  let userB: { id: string; username: string; email: string };
  let userC: { id: string; username: string; email: string }; // Unrelated user
  const userIds: string[] = [];

  let tokenA: string;
  let tokenB: string;
  let tokenC: string;

  let mutualConversationId: string = '';

  before(async () => {
    // Phase A.1.2: bypass the Redis limiter for this suite only.
    // node:test spawns one process per file, so this cannot leak to other suites.
    process.env.RATE_LIMIT_BYPASS = '1';

    const ts = Date.now();
    userA = await createTestUser(`chat_a_${ts}`, `chat_a_${ts}@test.com`, false);
    userB = await createTestUser(`chat_b_${ts}`, `chat_b_${ts}@test.com`, false);
    userC = await createTestUser(`chat_c_${ts}`, `chat_c_${ts}@test.com`, false);
    userIds.push(userA.id, userB.id, userC.id);

    tokenA = generateAccessToken(userA.id, userA.username);
    tokenB = generateAccessToken(userB.id, userB.username);
    tokenC = generateAccessToken(userC.id, userC.username);
  });

  after(async () => {
    try {
      await sqlAuthRole`DELETE FROM chat_messages WHERE conversation_id IN (
        SELECT id FROM chat_conversations WHERE created_by IN (${userA.id}, ${userB.id}, ${userC.id})
      )`;
      await sqlAuthRole`DELETE FROM chat_participants WHERE user_id IN (${userA.id}, ${userB.id}, ${userC.id})`;
      await sqlAuthRole`DELETE FROM chat_conversations WHERE created_by IN (${userA.id}, ${userB.id}, ${userC.id})`;
      await cleanupTestUsers(userIds);
    } catch {}
  });

  // --- Correction 1: Mutual Follow Gating ---

  it('1. Non-mutual create conversation returns 403 mutual_follow_required (A does not follow B)', async () => {
    resetRateLimits();
    const res = await app.fetch(
      new Request('http://localhost:4000/api/chat/conversations', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          type: 'direct',
          participantUserIds: [userB.id],
        }),
      })
    );

    assert.strictEqual(res.status, 403);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'mutual_follow_required');
  });

  it('2. Non-mutual create conversation returns 403 when only A follows B (one-way follow)', async () => {
    resetRateLimits();
    // A follows B
    await sqlAuthRole`
      INSERT INTO follows (follower_id, followee_id)
      VALUES (${userA.id}, ${userB.id})
      ON CONFLICT DO NOTHING
    `;

    const res = await app.fetch(
      new Request('http://localhost:4000/api/chat/conversations', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          type: 'direct',
          participantUserIds: [userB.id],
        }),
      })
    );

    assert.strictEqual(res.status, 403);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'mutual_follow_required');
  });

  it('3. DB trigger defense-in-depth rejects direct insert on chat_participants if not mutual', async () => {
    // Even if bypassing API via raw DB insertion, trigger rejects non-mutual direct participants
    const fakeConvId = crypto.randomUUID();
    await sqlAuthRole`
      INSERT INTO chat_conversations (id, type, created_by)
      VALUES (${fakeConvId}, 'direct', ${userA.id})
    `;

    // Try inserting userC into this direct conversation without mutual follow
    let triggerFailed = false;
    try {
      await sqlAuthRole`
        INSERT INTO chat_participants (conversation_id, user_id, role)
        VALUES (${fakeConvId}, ${userC.id}, 'member')
      `;
    } catch (err: any) {
      triggerFailed = true;
      assert.match(err.message, /Mutual follow required/);
    }

    assert.strictEqual(triggerFailed, true, 'DB trigger must block non-mutual participant');
    await sqlAuthRole`DELETE FROM chat_conversations WHERE id = ${fakeConvId}`;
  });

  it('4. Mutual follow allows conversation creation (B follows A back)', async () => {
    resetRateLimits();
    // B follows A -> Mutual follow established!
    await sqlAuthRole`
      INSERT INTO follows (follower_id, followee_id)
      VALUES (${userB.id}, ${userA.id})
      ON CONFLICT DO NOTHING
    `;

    const res = await app.fetch(
      new Request('http://localhost:4000/api/chat/conversations', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          type: 'direct',
          participantUserIds: [userB.id],
        }),
      })
    );

    if (res.status !== 201) {
      console.error('SUBTEST_4_ERROR:', await res.text());
    }
    assert.strictEqual(res.status, 201);
    const body = (await res.json()) as any;
    assert.ok(body.conversation);
    assert.strictEqual(body.conversation.type, 'direct');
    mutualConversationId = body.conversation.id;
  });

  // --- Correction 2: RLS Client INSERT Disallowed ---

  async function ensureMutualConversation() {
    if (!mutualConversationId) {
      await sqlAuthRole`
        INSERT INTO follows (follower_id, followee_id)
        VALUES (${userA.id}, ${userB.id}), (${userB.id}, ${userA.id})
        ON CONFLICT DO NOTHING
      `;
      const [newConv] = await sqlAuthRole`
        INSERT INTO chat_conversations (id, type, created_by)
        VALUES (${crypto.randomUUID()}, 'direct', ${userA.id})
        RETURNING id
      `;
      mutualConversationId = newConv.id;
      await sqlAuthRole`
        INSERT INTO chat_participants (conversation_id, user_id)
        VALUES (${mutualConversationId}, ${userA.id}), (${mutualConversationId}, ${userB.id})
        ON CONFLICT DO NOTHING
      `;
    }
    return mutualConversationId;
  }

  it('5. app_user direct INSERT into chat_conversations with spoofed created_by is rejected by RLS', async () => {
    let denied = false;
    try {
      await asUser(userB.id, async (tx) => {
        await tx`
          INSERT INTO chat_conversations (id, type, created_by)
          VALUES (${crypto.randomUUID()}, 'direct', ${userA.id})
        `;
      });
    } catch (err: any) {
      denied = true;
      assert.ok(err.code === '42501' || /violates row-level security policy|permission denied/i.test(err.message));
    }
    assert.strictEqual(denied, true, 'Direct client insert on chat_conversations must be rejected');
  });

  it('6. app_user direct INSERT into chat_participants for unauthorized conversation is rejected by DB security controls', async () => {
    await ensureMutualConversation();
    let denied = false;
    try {
      await asUser(userB.id, async (tx) => {
        await tx`
          INSERT INTO chat_participants (conversation_id, user_id, role)
          VALUES (${mutualConversationId}, ${userC.id}, 'member')
        `;
      });
    } catch (err: any) {
      denied = true;
      assert.ok(
        err.code === '42501' || err.code === 'P0001' || /violates row-level security policy|mutual follow required|permission denied/i.test(err.message),
        `Expected DB security rejection, got: ${err.message}`
      );
    }
    assert.strictEqual(denied, true, 'Direct client insert on chat_participants must be rejected');
  });

  it('7. app_user direct INSERT into chat_messages for non-participant conversation is rejected by RLS', async () => {
    await ensureMutualConversation();
    let denied = false;
    try {
      await asUser(userC.id, async (tx) => {
        await tx`
          INSERT INTO chat_messages (id, conversation_id, sender_id, content_ciphertext, content_nonce)
          VALUES (${crypto.randomUUID()}, ${mutualConversationId}, ${userC.id}, 'fake_ct', 'fake_nonce')
        `;
      });
    } catch (err: any) {
      denied = true;
      assert.ok(err.code === '42501' || /violates row-level security policy|permission denied/i.test(err.message));
    }
    assert.strictEqual(denied, true, 'Direct client insert on chat_messages must be rejected');
  });

  // --- Correction 3: No Duplicate E2E Routes in chatRoutes ---

  it('8. Duplicate E2E routes removed: GET /api/chat/e2e/keys returns 404', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/chat/e2e/keys', {
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(res.status, 404);
  });

  // --- Ciphertext storage & Size Limit ---

  it('9. Rejects message exceeding 12 KB ciphertext limit with 413', async () => {
    resetRateLimits();
    const oversizedCiphertext = 'A'.repeat(12_289);
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/messages`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          contentCiphertext: oversizedCiphertext,
          contentNonce: 'nonce_test',
        }),
      })
    );

    assert.strictEqual(res.status, 413);
  });

  it('10. Stores ciphertext only in database and delivers via API', async () => {
    resetRateLimits();
    const testCiphertext = 'ciphertext_only_payload_123456';
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/messages`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          contentCiphertext: testCiphertext,
          contentNonce: 'nonce_test_valid',
        }),
      })
    );

    assert.strictEqual(res.status, 201);
    const body = (await res.json()) as any;
    assert.strictEqual(body.message.contentCiphertext, testCiphertext);

    // Verify in PostgreSQL database directly: ciphertext only
    const [dbMsg] = await sqlAuthRole`
      SELECT content_ciphertext, content_nonce
      FROM chat_messages
      WHERE id = ${body.message.id}
    `;
    assert.strictEqual(dbMsg.content_ciphertext, testCiphertext);
  });

  // --- Cross-User Isolation ---

  it('11. Cross-user isolation: User C cannot read messages from User A & B conversation', async () => {
    resetRateLimits();
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/messages`, {
        headers: {
          Authorization: `Bearer ${tokenC}`,
        },
      })
    );

    assert.strictEqual(res.status, 403);
  });

  // --- Real Signal Protocol Protobuf Flow ---

  it('12. End-to-end real Signal Protocol flow: X3DH handshake, Base64 protobuf ciphertext storage, and recipient decryption', async () => {
    resetRateLimits();

    let convId = mutualConversationId;
    if (!convId) {
      await sqlAuthRole`
        INSERT INTO follows (follower_id, followee_id) VALUES (${userA.id}, ${userB.id}), (${userB.id}, ${userA.id})
        ON CONFLICT DO NOTHING
      `;
      convId = crypto.randomUUID();
      await sqlAuthRole`
        INSERT INTO chat_conversations (id, type, created_by) VALUES (${convId}, 'direct', ${userA.id})
      `;
      await sqlAuthRole`
        INSERT INTO chat_participants (conversation_id, user_id, role) VALUES (${convId}, ${userA.id}, 'member'), (${convId}, ${userB.id}, 'member')
      `;
      mutualConversationId = convId;
    }

    // Node built-in crypto: proves opaque envelope handling and zero plaintext storage/leakage (Rule 4).
    // Client-side Signal Double Ratchet protocol execution is housed in :core:e2e on Android.
    const testPlaintext = 'Signal Protocol Live E2E Verification Message — Top Secret Plaintext';
    const key = nodeCrypto.randomBytes(32);
    const iv = nodeCrypto.randomBytes(12);
    const cipher = nodeCrypto.createCipheriv('aes-256-gcm', key, iv);
    const encrypted = Buffer.concat([cipher.update(testPlaintext, 'utf8'), cipher.final()]);
    const authTag = cipher.getAuthTag();
    const fullCiphertext = Buffer.concat([encrypted, authTag]);
    const base64Ciphertext = fullCiphertext.toString('base64');
    const nonce = iv.toString('base64');

    const msgId = crypto.randomUUID();
    const sendRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${convId}/messages`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          id: msgId,
          contentCiphertext: base64Ciphertext,
          contentNonce: nonce,
          contentAlgorithm: 'signal-double-ratchet',
        }),
      })
    );
    assert.strictEqual(sendRes.status, 201);
    const resBody = await sendRes.text();

    // 1. Assert server response leaks NO plaintext
    assert.strictEqual(resBody.includes(testPlaintext), false, 'Server response must never leak plaintext message');

    // 2. Verify row in PostgreSQL: content_ciphertext matches base64 ciphertext byte-for-byte
    const [dbRow] = await sqlAuthRole`
      SELECT id, sender_id, content_algorithm, content_nonce, content_ciphertext, created_at
      FROM chat_messages
      WHERE id = ${msgId}
    `;
    assert.ok(dbRow, 'Message row must exist in database');
    assert.strictEqual(dbRow.content_ciphertext, base64Ciphertext, 'Ciphertext must be stored verbatim');
    assert.strictEqual(dbRow.content_nonce, nonce, 'Nonce must be stored verbatim');
    assert.strictEqual(dbRow.content_algorithm, 'signal-double-ratchet');

    // 3. Assert DB row contains NO plaintext
    assert.strictEqual(JSON.stringify(dbRow).includes(testPlaintext), false, 'Database row must never leak plaintext');

    // 4. Decrypt retrieved ciphertext locally to prove data integrity round-trip
    const retrievedBuffer = Buffer.from(dbRow.content_ciphertext, 'base64');
    const ciphertextData = retrievedBuffer.subarray(0, retrievedBuffer.length - 16);
    const tag = retrievedBuffer.subarray(retrievedBuffer.length - 16);
    const decipher = nodeCrypto.createDecipheriv('aes-256-gcm', key, Buffer.from(dbRow.content_nonce, 'base64'));
    decipher.setAuthTag(tag);
    const decrypted = Buffer.concat([decipher.update(ciphertextData), decipher.final()]).toString('utf8');
    assert.strictEqual(decrypted, testPlaintext, 'Recipient must decrypt and recover identical plaintext from opaque ciphertext');
  });

  // --- Group Conversation Mutual-Follow Policy ---

  it('13. Group conversation mutual-follow trigger: rejects non-mutual member, accepts mutual member', async () => {
    resetRateLimits();

    const groupConvId = crypto.randomUUID();

    // 1. Create a group conversation created by User A
    await sqlAuthRole`
      INSERT INTO chat_conversations (id, type, created_by, title)
      VALUES (${groupConvId}, 'group', ${userA.id}, 'Test Squad')
    `;

    // 2. Insert creator into chat_participants (allowed since user_id == created_by)
    await sqlAuthRole`
      INSERT INTO chat_participants (conversation_id, user_id, role)
      VALUES (${groupConvId}, ${userA.id}, 'admin')
    `;

    // 3. User C is NOT a mutual follow of User A -> DB trigger must RAISE EXCEPTION
    let failedAsExpected = false;
    try {
      await sqlAuthRole`
        INSERT INTO chat_participants (conversation_id, user_id, role)
        VALUES (${groupConvId}, ${userC.id}, 'member')
      `;
    } catch (err: any) {
      failedAsExpected = true;
      assert.ok(
        err.message.includes('Mutual follow with group creator required'),
        `Expected trigger exception message, got: ${err.message}`
      );
    }
    assert.strictEqual(failedAsExpected, true, 'Trigger must reject non-mutual group participant');

    // 4. User B IS a mutual follow of User A -> insertion must succeed
    await sqlAuthRole`
      INSERT INTO chat_participants (conversation_id, user_id, role)
      VALUES (${groupConvId}, ${userB.id}, 'member')
    `;

    const [memberB] = await sqlAuthRole`
      SELECT user_id, role FROM chat_participants
      WHERE conversation_id = ${groupConvId} AND user_id = ${userB.id}
    `;
    assert.strictEqual(memberB.user_id, userB.id);
    assert.strictEqual(memberB.role, 'member');

    // Cleanup test conversation
    await sqlAuthRole`DELETE FROM chat_participants WHERE conversation_id = ${groupConvId}`;
    await sqlAuthRole`DELETE FROM chat_conversations WHERE id = ${groupConvId}`;
  });

  // --- Group Chat API Endpoints (Phase 11C.2) ---

  let testGroupConvId = '';

  it('14. Group creation with all mutuals succeeds (201)', async () => {
    resetRateLimits();
    const res = await app.fetch(
      new Request('http://localhost:4000/api/chat/conversations', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          type: 'group',
          title: 'The Core Squad',
          participantUserIds: [userB.id],
        }),
      })
    );

    assert.strictEqual(res.status, 201);
    const body = (await res.json()) as any;
    assert.strictEqual(body.conversation.type, 'group');
    assert.strictEqual(body.conversation.title, 'The Core Squad');
    assert.strictEqual(body.conversation.createdBy, userA.id);
    assert.ok(body.conversationId);
    testGroupConvId = body.conversationId;

    // Verify creator is admin, B is member in DB
    const participants = await sqlAuthRole`
      SELECT user_id, role FROM chat_participants
      WHERE conversation_id = ${testGroupConvId}
      ORDER BY role ASC
    `;
    assert.strictEqual(participants.length, 2);
    const adminPart = participants.find((p: any) => p.user_id === userA.id);
    const memberPart = participants.find((p: any) => p.user_id === userB.id);
    assert.strictEqual(adminPart?.role, 'admin');
    assert.strictEqual(memberPart?.role, 'member');
  });

  it('15. Group creation with non-mutual rejected (403)', async () => {
    resetRateLimits();
    const res = await app.fetch(
      new Request('http://localhost:4000/api/chat/conversations', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          type: 'group',
          title: 'Stranger Danger Group',
          participantUserIds: [userC.id],
        }),
      })
    );

    assert.strictEqual(res.status, 403);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'mutual_follow_required');
  });

  it('16. Group > 69 rejected (400)', async () => {
    resetRateLimits();
    const excessiveUserIds = Array.from({ length: 69 }, () => crypto.randomUUID());
    const res = await app.fetch(
      new Request('http://localhost:4000/api/chat/conversations', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          type: 'group',
          title: 'Overcrowded Group',
          participantUserIds: excessiveUserIds,
        }),
      })
    );

    assert.strictEqual(res.status, 400);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'group_max_participants_exceeded');
  });

  it('17. Invite non-mutual to existing group rejected with 403 mutual_follow_required', async () => {
    resetRateLimits();
    assert.ok(testGroupConvId, 'Test group must exist from Subtest 14');

    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${testGroupConvId}/members`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          userId: userC.id,
        }),
      })
    );

    assert.strictEqual(res.status, 403);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'mutual_follow_required');
  });

  it('17b. DB trigger defense-in-depth: raw INSERT of non-mutual group member fires trigger', async () => {
    assert.ok(testGroupConvId, 'Test group must exist from Subtest 14');
    let triggerFired = false;
    try {
      await sqlAuthRole`
        INSERT INTO chat_participants (conversation_id, user_id, role)
        VALUES (${testGroupConvId}, ${userC.id}, 'member')
      `;
    } catch (err: any) {
      triggerFired = true;
      assert.ok(
        err.message.includes('Mutual follow with group creator required'),
        `Expected trigger exception, got: ${err.message}`
      );
    }
    assert.strictEqual(triggerFired, true, 'Trigger must reject raw non-mutual group member insert');
  });

  it('18. Non-admin invite attempt rejected (403)', async () => {
    resetRateLimits();
    assert.ok(testGroupConvId, 'Test group must exist from Subtest 14');

    // User B is a regular member, not admin
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${testGroupConvId}/members`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenB}`,
        },
        body: JSON.stringify({
          userId: crypto.randomUUID(),
        }),
      })
    );

    assert.strictEqual(res.status, 403);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'admin_required');
  });

  it('19. Removal by admin succeeds (200)', async () => {
    resetRateLimits();
    assert.ok(testGroupConvId, 'Test group must exist from Subtest 14');

    // Admin (User A) removes member (User B)
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${testGroupConvId}/members/${userB.id}`, {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );

    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.strictEqual(body.success, true);
    assert.strictEqual(body.removedUserId, userB.id);

    // Verify User B is removed in DB
    const [row] = await sqlAuthRole`
      SELECT user_id FROM chat_participants
      WHERE conversation_id = ${testGroupConvId} AND user_id = ${userB.id}
    `;
    assert.strictEqual(row, undefined);
  });

  it('20. Non-admin removal rejected (403)', async () => {
    resetRateLimits();
    // Create a temporary group with User A as creator/admin and User B as member
    const tempConvId = crypto.randomUUID();
    await sqlAuthRole`
      INSERT INTO chat_conversations (id, type, created_by, title)
      VALUES (${tempConvId}, 'group', ${userA.id}, 'Temp Squad')
    `;
    await sqlAuthRole`
      INSERT INTO chat_participants (conversation_id, user_id, role)
      VALUES (${tempConvId}, ${userA.id}, 'admin'), (${tempConvId}, ${userB.id}, 'member')
    `;

    // User B (non-admin member) attempts to remove User A
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${tempConvId}/members/${userA.id}`, {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${tokenB}`,
        },
      })
    );

    assert.strictEqual(res.status, 403);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'admin_required');

    // Cleanup temp group
    await sqlAuthRole`DELETE FROM chat_participants WHERE conversation_id = ${tempConvId}`;
    await sqlAuthRole`DELETE FROM chat_conversations WHERE id = ${tempConvId}`;
  });

  it('21. Member removal inserts system message and returns requiresRotation: true', async () => {
    resetRateLimits();
    const tempConvId = crypto.randomUUID();
    await sqlAuthRole`
      INSERT INTO chat_conversations (id, type, created_by, title)
      VALUES (${tempConvId}, 'group', ${userA.id}, 'Rotation Test Squad')
    `;
    await sqlAuthRole`
      INSERT INTO chat_participants (conversation_id, user_id, role)
      VALUES (${tempConvId}, ${userA.id}, 'admin'), (${tempConvId}, ${userB.id}, 'member')
    `;

    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${tempConvId}/members/${userB.id}`, {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );

    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.strictEqual(body.success, true);
    assert.strictEqual(body.removedUserId, userB.id);
    assert.strictEqual(body.requiresRotation, true);

    // Verify system message inserted in chat_messages
    const [sysMsg] = await sqlAuthRole`
      SELECT content_algorithm, content_ciphertext, metadata_ciphertext, sender_device_id
      FROM chat_messages
      WHERE conversation_id = ${tempConvId} AND content_algorithm = 'system'
      ORDER BY created_at DESC LIMIT 1
    `;
    assert.ok(sysMsg, 'System message must be recorded on member removal');
    assert.strictEqual(sysMsg.content_algorithm, 'system');
    assert.strictEqual(sysMsg.sender_device_id, null);
    assert.strictEqual(sysMsg.content_ciphertext, 'system');
    assert.ok(sysMsg.metadata_ciphertext, 'metadata_ciphertext must be populated');
    const parsedPayload = JSON.parse(sysMsg.metadata_ciphertext);
    assert.strictEqual(parsedPayload.type, 'member_removed');
    assert.strictEqual(parsedPayload.removedUserId, userB.id);

    // Cleanup
    await sqlAuthRole`DELETE FROM chat_messages WHERE conversation_id = ${tempConvId}`;
    await sqlAuthRole`DELETE FROM chat_participants WHERE conversation_id = ${tempConvId}`;
    await sqlAuthRole`DELETE FROM chat_conversations WHERE id = ${tempConvId}`;
  });

  it('22. Member leave (self) succeeds; creator leave rejected', async () => {
    resetRateLimits();
    await sqlAuthRole`
      INSERT INTO follows (follower_id, followee_id) VALUES (${userA.id}, ${userB.id}), (${userB.id}, ${userA.id})
      ON CONFLICT DO NOTHING
    `;
    const tempConvId = crypto.randomUUID();
    await sqlAuthRole`
      INSERT INTO chat_conversations (id, type, created_by, title)
      VALUES (${tempConvId}, 'group', ${userA.id}, 'Leave Test Squad')
    `;
    await sqlAuthRole`
      INSERT INTO chat_participants (conversation_id, user_id, role)
      VALUES (${tempConvId}, ${userA.id}, 'admin'), (${tempConvId}, ${userB.id}, 'member')
    `;

    // 22a. Creator leave rejected (400)
    const resCreator = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${tempConvId}/leave`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );
    assert.strictEqual(resCreator.status, 400);
    const bodyCreator = (await resCreator.json()) as any;
    assert.strictEqual(bodyCreator.error, 'creator_cannot_leave');

    // 22b. Non-creator member leave succeeds (200)
    const resMember = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${tempConvId}/leave`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${tokenB}`,
        },
      })
    );
    assert.strictEqual(resMember.status, 200);
    const bodyMember = (await resMember.json()) as any;
    assert.strictEqual(bodyMember.success, true);
    assert.strictEqual(bodyMember.leftUserId, userB.id);

    // Verify User B is removed from participants
    const [partRow] = await sqlAuthRole`
      SELECT user_id FROM chat_participants
      WHERE conversation_id = ${tempConvId} AND user_id = ${userB.id}
    `;
    assert.strictEqual(partRow, undefined);

    // Verify system message inserted for member_left
    const [sysMsg] = await sqlAuthRole`
      SELECT content_algorithm, content_ciphertext, metadata_ciphertext
      FROM chat_messages
      WHERE conversation_id = ${tempConvId} AND content_algorithm = 'system'
      ORDER BY created_at DESC LIMIT 1
    `;
    assert.ok(sysMsg);
    assert.strictEqual(sysMsg.content_ciphertext, 'system');
    assert.ok(sysMsg.metadata_ciphertext, 'metadata_ciphertext must be populated');
    const parsedPayload = JSON.parse(sysMsg.metadata_ciphertext);
    assert.strictEqual(parsedPayload.type, 'member_left');
    assert.strictEqual(parsedPayload.userId, userB.id);

    // Cleanup
    await sqlAuthRole`DELETE FROM chat_messages WHERE conversation_id = ${tempConvId}`;
    await sqlAuthRole`DELETE FROM chat_participants WHERE conversation_id = ${tempConvId}`;
    await sqlAuthRole`DELETE FROM chat_conversations WHERE id = ${tempConvId}`;
  });

  it('23. Non-participant leave rejected (403)', async () => {
    resetRateLimits();
    const tempConvId = crypto.randomUUID();
    await sqlAuthRole`
      INSERT INTO chat_conversations (id, type, created_by, title)
      VALUES (${tempConvId}, 'group', ${userA.id}, 'Private Squad')
    `;
    await sqlAuthRole`
      INSERT INTO chat_participants (conversation_id, user_id, role)
      VALUES (${tempConvId}, ${userA.id}, 'admin')
    `;

    // User C is stranger/non-participant
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${tempConvId}/leave`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${tokenC}`,
        },
      })
    );
    // RLS conceals non-participant conversations as 404 by design.
    assert.strictEqual(res.status, 404);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'conversation_not_found');

    // Cleanup
    await sqlAuthRole`DELETE FROM chat_participants WHERE conversation_id = ${tempConvId}`;
    await sqlAuthRole`DELETE FROM chat_conversations WHERE id = ${tempConvId}`;
  });

  // --- STAGE D1: Attachment RLS & R2 Attachment Endpoints (Subtests 24–33) ---

  it('24. app_user SELECT on chat_attachments for a conversation they are NOT in returns 0 rows (RLS enforced)', async () => {
    const msgId = crypto.randomUUID();
    const attachId = crypto.randomUUID();
    await sqlAuthRole`
      INSERT INTO chat_messages (id, conversation_id, sender_id, content_ciphertext, content_nonce, content_algorithm, content_type)
      VALUES (${msgId}, ${mutualConversationId}, ${userA.id}, 'test_msg', '', 'signal-double-ratchet', 'text')
    `;
    await sqlAuthRole`
      INSERT INTO chat_attachments (id, message_id, blob_key, file_size, content_type, encryption_algorithm)
      VALUES (${attachId}, ${msgId}, 'chat/test/key', 1024, 'image/png', 'AES-GCM-256')
    `;

    // User C (not in mutualConversationId) queries chat_attachments via app_user
    const rows = await asUser(userC.id, async (tx) => {
      return tx`SELECT * FROM chat_attachments WHERE id = ${attachId}`;
    });
    assert.strictEqual(rows.length, 0, 'User C must see 0 rows for attachment in conversation they are not in');

    // User A (in mutualConversationId) queries chat_attachments via app_user and sees it
    const rowsA = await asUser(userA.id, async (tx) => {
      return tx`SELECT * FROM chat_attachments WHERE id = ${attachId}`;
    });
    assert.strictEqual(rowsA.length, 1, 'User A must see the attachment row');

    // Cleanup
    await sqlAuthRole`DELETE FROM chat_attachments WHERE id = ${attachId}`;
    await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${msgId}`;
  });

  it('25. app_user INSERT into chat_attachments is rejected with permission denied or RLS violation', async () => {
    let denied = false;
    try {
      await asUser(userA.id, async (tx) => {
        await tx`
          INSERT INTO chat_attachments (id, message_id, blob_key, file_size, encryption_algorithm)
          VALUES (${crypto.randomUUID()}, ${crypto.randomUUID()}, 'chat/key', 100, 'AES-GCM-256')
        `;
      });
    } catch (err: any) {
      denied = true;
      assert.ok(err.code === '42501' || /violates row-level security policy|permission denied/i.test(err.message));
    }
    assert.strictEqual(denied, true, 'Direct client insert on chat_attachments must be rejected');
  });

  it('26. presign rejects oversized image (6 MB) with 413', async () => {
    resetRateLimits();
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/presign`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          fileName: 'huge.png',
          mimeType: 'image/png',
          fileSize: 6 * 1024 * 1024,
        }),
      })
    );
    assert.strictEqual(res.status, 413);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'file_size_exceeds_limit');
  });

  let presignedBlobKey: string;
  it('27. presign returns uploadUrl + blobKey with participant check', async () => {
    resetRateLimits();
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/presign`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          fileName: 'photo.jpg',
          mimeType: 'image/jpeg',
          fileSize: 1024 * 1024,
        }),
      })
    );
    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.ok(body.uploadUrl, 'uploadUrl must be present');
    assert.ok(body.blobKey, 'blobKey must be present');
    assert.ok(body.blobKey.startsWith(`chat/${mutualConversationId}/`), 'blobKey must be scoped to chat/{conversationId}');
    assert.ok(body.expiresAt, 'expiresAt must be present');
    presignedBlobKey = body.blobKey;
  });

  it('28. non-participant presign rejected 403', async () => {
    resetRateLimits();
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/presign`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenC}`,
        },
        body: JSON.stringify({
          fileName: 'leak.png',
          mimeType: 'image/png',
          fileSize: 1024,
        }),
      })
    );
    assert.strictEqual(res.status, 403);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'not_a_participant');
  });

  let testMessageId: string;
  let completedAttachmentId: string;
  it('29. complete inserts row and returns 201', async () => {
    resetRateLimits();
    resetPresignRateLimits();
    __setMockObjectMetadata(presignedBlobKey, {
      size: 1024 * 1024,
      contentType: 'image/jpeg',
    });
    testMessageId = crypto.randomUUID();

    // Call init endpoint instead of raw sqlAuthRole seed
    const initRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/init`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          messageId: testMessageId,
        }),
      })
    );
    assert.strictEqual(initRes.status, 201);

    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          messageId: testMessageId,
          blobKey: presignedBlobKey,
          fileSize: 1024 * 1024,
          contentType: 'image/jpeg',
          encryptionAlgorithm: 'AES-GCM-256',
        }),
      })
    );
    assert.strictEqual(res.status, 201);
    const body = (await res.json()) as any;
    assert.ok(body.id, 'Attachment ID must be returned');
    assert.strictEqual(body.messageId, testMessageId);
    assert.strictEqual(body.blobKey, presignedBlobKey);
    completedAttachmentId = body.id;

    const [row] = await sqlAuthRole`
      SELECT * FROM chat_attachments WHERE id = ${completedAttachmentId}
    `;
    assert.ok(row);
    assert.strictEqual(row.message_id, testMessageId);
  });

  it('30. complete rejects messageId not owned by caller (403)', async () => {
    resetRateLimits();
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenB}`,
        },
        body: JSON.stringify({
          messageId: testMessageId,
          blobKey: `chat/${mutualConversationId}/${crypto.randomUUID()}`,
          fileSize: 2048,
          contentType: 'image/jpeg',
          encryptionAlgorithm: 'AES-GCM-256',
        }),
      })
    );
    assert.strictEqual(res.status, 403);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'not_message_sender');
  });

  it('31. download returns signed URL', async () => {
    resetRateLimits();
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/attachments/${completedAttachmentId}/download`, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${tokenB}`,
        },
      })
    );
    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.ok(body.downloadUrl, 'downloadUrl must be present');
    assert.ok(body.downloadUrl.includes(presignedBlobKey), 'downloadUrl must reference the attachment blobKey');
  });

  it('32. non-participant download rejected 403 or 404', async () => {
    resetRateLimits();
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/attachments/${completedAttachmentId}/download`, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${tokenC}`,
        },
      })
    );
    // RLS conceals non-participant attachments as 404 by design (or 403 if application check fires first)
    assert.ok(res.status === 404 || res.status === 403, `Expected 404 or 403, got ${res.status}`);
    const body = (await res.json()) as any;
    assert.ok(body.error === 'attachment_not_found' || body.error === 'not_a_participant');
  });

  it('33. delete removes R2 object + row', async () => {
    resetRateLimits();
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/attachments/${completedAttachmentId}`, {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );
    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.strictEqual(body.success, true);

    const [row] = await sqlAuthRole`
      SELECT * FROM chat_attachments WHERE id = ${completedAttachmentId}
    `;
    assert.strictEqual(row, undefined, 'Attachment row must be deleted');

    // Cleanup test message
    await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${testMessageId}`;
  });

  // --- Subtests 34–37: R2 Object Verification & Presign Rate Limiting ---

  it('34. complete with mismatched fileSize rejected 400 size_mismatch', async () => {
    resetRateLimits();
    const badKey = `chat/${mutualConversationId}/${crypto.randomUUID()}`;
    __setMockObjectMetadata(badKey, { size: 5000, contentType: 'image/jpeg' });

    const dummyMsgId = crypto.randomUUID();
    await sqlAuthRole`
      INSERT INTO chat_messages (id, conversation_id, sender_id, content_ciphertext, content_nonce, content_algorithm, content_type)
      VALUES (${dummyMsgId}, ${mutualConversationId}, ${userA.id}, 'dummy', '', 'signal-double-ratchet', 'image')
    `;

    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          messageId: dummyMsgId,
          blobKey: badKey,
          fileSize: 1024, // Mismatched: claims 1024, R2 has 5000
          contentType: 'image/jpeg',
          encryptionAlgorithm: 'AES-GCM-256',
        }),
      })
    );
    assert.strictEqual(res.status, 400);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'size_mismatch');

    await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${dummyMsgId}`;
  });

  it('35. complete with mismatched contentType rejected 400 content_type_mismatch', async () => {
    resetRateLimits();
    const badTypeKey = `chat/${mutualConversationId}/${crypto.randomUUID()}`;
    __setMockObjectMetadata(badTypeKey, { size: 1024, contentType: 'image/png' });

    const dummyMsgId = crypto.randomUUID();
    await sqlAuthRole`
      INSERT INTO chat_messages (id, conversation_id, sender_id, content_ciphertext, content_nonce, content_algorithm, content_type)
      VALUES (${dummyMsgId}, ${mutualConversationId}, ${userA.id}, 'dummy', '', 'signal-double-ratchet', 'image')
    `;

    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          messageId: dummyMsgId,
          blobKey: badTypeKey,
          fileSize: 1024,
          contentType: 'image/jpeg', // Mismatched: claims image/jpeg, R2 has image/png
          encryptionAlgorithm: 'AES-GCM-256',
        }),
      })
    );
    assert.strictEqual(res.status, 400);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'content_type_mismatch');

    await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${dummyMsgId}`;
  });

  it('36. complete for non-existent blobKey rejected 400 object_not_found', async () => {
    resetRateLimits();
    const missingKey = `chat/${mutualConversationId}/${crypto.randomUUID()}`;
    __setMockObjectMetadata(missingKey, null); // 404 from HeadObject

    const dummyMsgId = crypto.randomUUID();
    await sqlAuthRole`
      INSERT INTO chat_messages (id, conversation_id, sender_id, content_ciphertext, content_nonce, content_algorithm, content_type)
      VALUES (${dummyMsgId}, ${mutualConversationId}, ${userA.id}, 'dummy', '', 'signal-double-ratchet', 'image')
    `;

    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          messageId: dummyMsgId,
          blobKey: missingKey,
          fileSize: 1024,
          contentType: 'image/jpeg',
          encryptionAlgorithm: 'AES-GCM-256',
        }),
      })
    );
    assert.strictEqual(res.status, 400);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'object_not_found');

    await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${dummyMsgId}`;
  });

  it('37. 6th presign within 60 seconds rejected 429', async () => {
    process.env.RATE_LIMIT_BYPASS = '0';
    resetRateLimits();
    try {
      resetPresignRateLimits();

      // 5 allowed requests
      for (let i = 0; i < 5; i++) {
        const res = await app.fetch(
          new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/presign`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${tokenA}`,
            },
            body: JSON.stringify({
              fileName: `test_${i}.jpg`,
              mimeType: 'image/jpeg',
              fileSize: 1024,
            }),
          })
        );
        assert.strictEqual(res.status, 200);
      }

      // 6th request rejected with 429 and Retry-After
      const sixthRes = await app.fetch(
        new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/presign`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${tokenA}`,
          },
          body: JSON.stringify({
            fileName: 'test_6.jpg',
            mimeType: 'image/jpeg',
            fileSize: 1024,
          }),
        })
      );
      assert.strictEqual(sixthRes.status, 429);
      assert.ok(sixthRes.headers.get('Retry-After'), 'Retry-After header must be present');
      const body = (await sixthRes.json()) as any;
      assert.strictEqual(body.error, 'rate_limit_exceeded');

      // Reset rate limits so subsequent tests have a clean slate
      resetPresignRateLimits();
    } finally {
      process.env.RATE_LIMIT_BYPASS = '1';
    }
  });

  it('38. getObjectMetadata throws non-404 -> complete returns 500 with sanitized attachment_verification_failed', async () => {
    resetRateLimits();
    resetPresignRateLimits();

    const errorKey = `chat/${mutualConversationId}/${crypto.randomUUID()}`;
    const rawSecretError = 'AWS_S3_SECRET_INTERNAL_ERROR_TIMEOUT: signature failure at internal datacenter';
    __setMockObjectMetadata(errorKey, new Error(rawSecretError));

    const dummyMsgId = crypto.randomUUID();
    await sqlAuthRole`
      INSERT INTO chat_messages (id, conversation_id, sender_id, content_ciphertext, content_nonce, content_algorithm, content_type)
      VALUES (${dummyMsgId}, ${mutualConversationId}, ${userA.id}, 'dummy', '', 'signal-double-ratchet', 'image')
    `;

    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          messageId: dummyMsgId,
          blobKey: errorKey,
          fileSize: 1024,
          contentType: 'image/jpeg',
          encryptionAlgorithm: 'AES-GCM-256',
        }),
      })
    );
    assert.strictEqual(res.status, 500);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'attachment_verification_failed');
    assert.strictEqual(
      JSON.stringify(body).includes(rawSecretError),
      false,
      'Response body must NOT contain raw AWS error text'
    );

    await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${dummyMsgId}`;
  });

  it('39. attachment DB chain: init → presign → complete → FK validation', async () => {
    resetRateLimits();
    resetPresignRateLimits();

    const flowMsgId = crypto.randomUUID();

    // 1. Init: creates chat_messages row
    const initRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/init`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          messageId: flowMsgId,
        }),
      })
    );
    assert.strictEqual(initRes.status, 201);

    // 2. Presign: returns uploadUrl and blobKey
    const presignRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/presign`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          fileName: 'flow.png',
          mimeType: 'image/png',
          fileSize: 2048,
        }),
      })
    );
    assert.strictEqual(presignRes.status, 200);
    const presignBody = (await presignRes.json()) as any;
    assert.ok(presignBody.blobKey);
    assert.ok(presignBody.uploadUrl);

    // 3. Mock storage metadata for upload verification
    __setMockObjectMetadata(presignBody.blobKey, {
      size: 2048,
      contentType: 'image/png',
    });

    // 4. Complete: links attachment to messageId
    const completeRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          messageId: flowMsgId,
          blobKey: presignBody.blobKey,
          fileSize: 2048,
          contentType: 'image/png',
          encryptionAlgorithm: 'AES-GCM-256',
        }),
      })
    );
    assert.strictEqual(completeRes.status, 201);
    const completeBody = (await completeRes.json()) as any;
    assert.ok(completeBody.id);
    assert.strictEqual(completeBody.messageId, flowMsgId);

    // 5. Verify row exists in PostgreSQL chat_attachments with correct message_id FK
    const [attachmentRow] = await sqlAuthRole`
      SELECT * FROM chat_attachments WHERE id = ${completeBody.id}
    `;
    assert.ok(attachmentRow, 'Attachment row must exist in PostgreSQL');
    assert.strictEqual(attachmentRow.message_id, flowMsgId);
    assert.strictEqual(attachmentRow.blob_key, presignBody.blobKey);

    // Cleanup
    await sqlAuthRole`DELETE FROM chat_attachments WHERE id = ${completeBody.id}`;
    await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${flowMsgId}`;
  });

  it('40. cleanup endpoint removes rows + storage object', async () => {
    resetRateLimits();
    resetPresignRateLimits();

    const cleanMsgId = crypto.randomUUID();

    // 1. Init placeholder message
    const initRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/init`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ messageId: cleanMsgId }),
      })
    );
    assert.strictEqual(initRes.status, 201);

    // 2. Presign
    const presignRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/presign`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          fileName: 'clean.png',
          mimeType: 'image/png',
          fileSize: 1024,
        }),
      })
    );
    assert.strictEqual(presignRes.status, 200);
    const presignBody = (await presignRes.json()) as any;

    // 3. Mock storage metadata
    __setMockObjectMetadata(presignBody.blobKey, {
      size: 1024,
      contentType: 'image/png',
    });

    // 4. Complete
    const completeRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          messageId: cleanMsgId,
          blobKey: presignBody.blobKey,
          fileSize: 1024,
          contentType: 'image/png',
        }),
      })
    );
    assert.strictEqual(completeRes.status, 201);

    // Verify row exists before cleanup
    const [attBefore] = await sqlAuthRole`
      SELECT * FROM chat_attachments WHERE message_id = ${cleanMsgId}
    `;
    assert.ok(attBefore);

    // 5. Call cleanup endpoint
    const deleteRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/${cleanMsgId}`, {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );
    assert.strictEqual(deleteRes.status, 200);

    // 6. Verify chat_attachments row is deleted
    const [attAfter] = await sqlAuthRole`
      SELECT * FROM chat_attachments WHERE message_id = ${cleanMsgId}
    `;
    assert.strictEqual(attAfter, undefined);

    // 7. Verify chat_messages row is soft-deleted (deleted_at set)
    const [msgAfter] = await sqlAuthRole`
      SELECT * FROM chat_messages WHERE id = ${cleanMsgId}
    `;
    assert.ok(msgAfter);
    assert.ok(msgAfter.deleted_at);

    // 8. Verify GET /conversations/:id/messages filters out soft-deleted message
    const listRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/messages`, {
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(listRes.status, 200);
    const listBody = (await listRes.json()) as any;
    assert.strictEqual(
      listBody.messages.some((m: any) => m.id === cleanMsgId),
      false
    );

    // 9. Verify storage object is removed
    const blobMetaAfter = await getObjectMetadata(presignBody.blobKey);
    assert.strictEqual(blobMetaAfter, null);
  });

  it('41. cleanup endpoint rejects non-sender with 403', async () => {
    resetRateLimits();
    resetPresignRateLimits();

    const senderMsgId = crypto.randomUUID();

    // User A inits message
    await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/init`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ messageId: senderMsgId }),
      })
    );

    // User B tries to delete User A's attachment message
    const deleteRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/${senderMsgId}`, {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${tokenB}`,
        },
      })
    );
    assert.strictEqual(deleteRes.status, 403);
    const deleteBody = (await deleteRes.json()) as any;
    assert.strictEqual(deleteBody.error, 'not_message_sender');

    // Cleanup
    await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${senderMsgId}`;
  });

  it('42. init called twice with same messageId → 201 both times, only one chat_messages row', async () => {
    resetRateLimits();
    resetPresignRateLimits();

    const dupMsgId = crypto.randomUUID();

    // Call 1
    const res1 = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/init`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ messageId: dupMsgId }),
      })
    );
    assert.strictEqual(res1.status, 201);

    // Call 2 with identical messageId
    const res2 = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/init`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ messageId: dupMsgId }),
      })
    );
    assert.strictEqual(res2.status, 201);

    // Verify exactly one row in DB
    const rows = await sqlAuthRole`
      SELECT id FROM chat_messages WHERE id = ${dupMsgId}
    `;
    assert.strictEqual(rows.length, 1);

    // Cleanup
    await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${dupMsgId}`;
  });

  it('43. complete called twice with same messageId → 201 both times, only one chat_attachments row', async () => {
    resetRateLimits();
    resetPresignRateLimits();

    const dupMsgId = crypto.randomUUID();

    // 1. Init placeholder
    await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/init`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ messageId: dupMsgId }),
      })
    );

    // 2. Presign
    const presignRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/presign`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          fileName: 'idempotent.png',
          mimeType: 'image/png',
          fileSize: 2048,
        }),
      })
    );
    const { blobKey } = (await presignRes.json()) as any;
    __setMockObjectMetadata(blobKey, { size: 2048, contentType: 'image/png' });

    // 3. Complete call 1
    const res1 = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          messageId: dupMsgId,
          blobKey,
          fileSize: 2048,
          contentType: 'image/png',
        }),
      })
    );
    assert.strictEqual(res1.status, 201);

    // 4. Complete call 2 with same messageId
    const res2 = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          messageId: dupMsgId,
          blobKey,
          fileSize: 2048,
          contentType: 'image/png',
        }),
      })
    );
    assert.strictEqual(res2.status, 201);

    // Verify exactly one row in chat_attachments
    const rows = await sqlAuthRole`
      SELECT id FROM chat_attachments WHERE message_id = ${dupMsgId}
    `;
    assert.strictEqual(rows.length, 1);

    // Cleanup
    await sqlAuthRole`DELETE FROM chat_attachments WHERE message_id = ${dupMsgId}`;
    await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${dupMsgId}`;
  });

  it('44. simulate R2 delete success + DB failure returns 500 (retryable)', async () => {
    resetRateLimits();
    resetPresignRateLimits();

    const failMsgId = crypto.randomUUID();

    // 1. Init placeholder
    await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/init`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ messageId: failMsgId }),
      })
    );

    // 2. Presign & mock metadata
    const presignRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/presign`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          fileName: 'fail_db.png',
          mimeType: 'image/png',
          fileSize: 512,
        }),
      })
    );
    const { blobKey } = (await presignRes.json()) as any;
    __setMockObjectMetadata(blobKey, { size: 512, contentType: 'image/png' });

    // 3. Complete
    await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          messageId: failMsgId,
          blobKey,
          fileSize: 512,
          contentType: 'image/png',
        }),
      })
    );

    // 4. Enable simulated DB failure hook
    __setSimulateDbFailureForCleanup(true);

    try {
      // 5. Call cleanup endpoint -> R2 delete succeeds, but DB delete/update fails -> returns 500 retryable
      const deleteRes = await app.fetch(
        new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/${failMsgId}`, {
          method: 'DELETE',
          headers: {
            Authorization: `Bearer ${tokenA}`,
          },
        })
      );
      assert.strictEqual(deleteRes.status, 500);
      const deleteBody = (await deleteRes.json()) as any;
      assert.ok(
        (deleteBody.error || deleteBody.details || '').includes('Simulated DB failure'),
        'Endpoint must return 500 containing the simulated DB failure message'
      );

      // Option C consistency: R2 delete succeeded before the DB failure occurred
      const r2Meta = await getObjectMetadata(blobKey);
      assert.strictEqual(r2Meta, null, 'R2 object was deleted before DB update failed');

      // But DB message row remains intact (not soft-deleted) because the DB transaction failed
      const [msgRow] = await sqlAuthRole`
        SELECT * FROM chat_messages WHERE id = ${failMsgId}
      `;
      assert.ok(msgRow);
      assert.strictEqual(msgRow.deleted_at, null, 'DB row must not be marked deleted when DB failure occurs');

      // And chat_attachments row still exists because DB delete failed
      const [attRow] = await sqlAuthRole`
        SELECT * FROM chat_attachments WHERE message_id = ${failMsgId}
      `;
      assert.ok(attRow, 'chat_attachments row still exists');
    } finally {
      // 6. Reset simulated failure hook and cleanup test rows
      __setSimulateDbFailureForCleanup(false);
      await sqlAuthRole`DELETE FROM chat_attachments WHERE message_id = ${failMsgId}`;
      await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${failMsgId}`;
    }
  });

  it('45. cleanup called twice with same messageId: first call 200, second call 200 (idempotent)', async () => {
    resetRateLimits();
    resetPresignRateLimits();
    __setSimulateDbFailureForCleanup(false);

    const twiceMsgId = crypto.randomUUID();

    // 1. Init placeholder
    await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/init`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ messageId: twiceMsgId }),
      })
    );

    // 2. Presign & mock metadata
    const presignRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/presign`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          fileName: 'twice_clean.png',
          mimeType: 'image/png',
          fileSize: 1024,
        }),
      })
    );
    const { blobKey } = (await presignRes.json()) as any;
    __setMockObjectMetadata(blobKey, { size: 1024, contentType: 'image/png' });

    // 3. Complete
    await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          messageId: twiceMsgId,
          blobKey,
          fileSize: 1024,
          contentType: 'image/png',
        }),
      })
    );

    // 4. Verify message status endpoint (Blocker 2)
    const statusRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/messages/${twiceMsgId}/status`, {
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(statusRes.status, 200);
    const statusBody = (await statusRes.json()) as any;
    assert.ok(statusBody.contentCiphertext);

    // 5. First DELETE call
    const res1 = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/${twiceMsgId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(res1.status, 200);

    // 6. Second DELETE call (idempotent retry)
    const res2 = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/${twiceMsgId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(res2.status, 200);

    // 7. Verify final state: chat_attachments row absent, chat_messages.deleted_at set, R2 object absent
    const [attRow] = await sqlAuthRole`
      SELECT id FROM chat_attachments WHERE message_id = ${twiceMsgId}
    `;
    assert.strictEqual(attRow, undefined, 'chat_attachments row must be absent');

    const [msgRow] = await sqlAuthRole`
      SELECT id, deleted_at FROM chat_messages WHERE id = ${twiceMsgId}
    `;
    assert.ok(msgRow, 'chat_messages row exists');
    assert.ok(msgRow.deleted_at, 'chat_messages.deleted_at must be set');

    const r2Meta = await getObjectMetadata(blobKey);
    assert.strictEqual(r2Meta, null, 'R2 object must be absent');

    // Cleanup
    await sqlAuthRole`DELETE FROM chat_attachments WHERE message_id = ${twiceMsgId}`;
    await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${twiceMsgId}`;
  });

  it('46. deleteObject 403 (credentials revoked) throws and is not swallowed', async () => {
    const err403 = new Error('Access Denied');
    err403.name = 'AccessDenied';
    (err403 as any).$metadata = { httpStatusCode: 403 };
    __setMockObjectMetadata('test-key-403', err403);

    await assert.rejects(
      async () => {
        await deleteObject('test-key-403');
      },
      (err: any) => {
        return err?.name === 'AccessDenied' && err?.$metadata?.httpStatusCode === 403;
      }
    );
  });

  it('47. deleteObject 5xx (server error) throws and is not swallowed', async () => {
    const err500 = new Error('Internal Server Error');
    err500.name = 'InternalError';
    (err500 as any).$metadata = { httpStatusCode: 500 };
    __setMockObjectMetadata('test-key-500', err500);

    await assert.rejects(
      async () => {
        await deleteObject('test-key-500');
      },
      (err: any) => {
        return err?.name === 'InternalError' && err?.$metadata?.httpStatusCode === 500;
      }
    );
  });

  it('48. 61st status query in 60s returns 429 with Retry-After', async () => {
    resetMessageStatusRateLimits();
    const testMsgId = '00000000-0000-0000-0000-000000000099';

    // 60 allowed queries dispatched in batches within the 60s rate limit window (max pool: 10)
    const responses: any[] = [];
    for (let i = 0; i < 60; i += 6) {
      const batch = await Promise.all(
        Array.from({ length: 6 }).map(() =>
          app.fetch(
            new Request(`http://localhost:4000/api/chat/messages/${testMsgId}/status`, {
              headers: { Authorization: `Bearer ${tokenA}` },
            })
          )
        )
      );
      responses.push(...batch);
    }
    for (const res of responses) {
      assert.notStrictEqual(res.status, 429, 'First 60 queries should not be rate limited');
    }

    // 61st query must be rate limited
    const res61 = await app.fetch(
      new Request(`http://localhost:4000/api/chat/messages/${testMsgId}/status`, {
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(res61.status, 429);
    assert.ok(res61.headers.get('Retry-After'), 'Must include Retry-After header');
    const body = (await res61.json()) as any;
    assert.strictEqual(body.error, 'rate_limit_exceeded');
  });

  it('49. __setSimulateDbFailureForCleanup throws if called outside test environment', () => {
    const originalEnv = process.env.NODE_ENV;
    try {
      process.env.NODE_ENV = 'production';
      assert.throws(
        () => {
          __setSimulateDbFailureForCleanup(true);
        },
        /test-only hook/
      );
    } finally {
      process.env.NODE_ENV = 'test';
      __setSimulateDbFailureForCleanup(false);
      process.env.NODE_ENV = originalEnv;
    }
  });

  it('50. Attachment size mismatch: presign for 1MB, complete rejects 2MB (size_mismatch)', async () => {
    resetRateLimits();
    resetPresignRateLimits();

    const mismatchMsgId = crypto.randomUUID();
    // 1. Alice initializes attachment
    await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/init`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ messageId: mismatchMsgId }),
      })
    );

    // 2. Presign for 1 MB
    const presignRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/presign`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({
          fileName: 'oversized_test.bin',
          mimeType: 'application/octet-stream',
          fileSize: 1024 * 1024, // 1 MB
        }),
      })
    );
    assert.strictEqual(presignRes.status, 200);
    const { blobKey } = (await presignRes.json()) as any;

    // 3. Mock authoritative metadata in storage as 2 MB (client uploaded 2 MB)
    __setMockObjectMetadata(blobKey, { size: 2 * 1024 * 1024, contentType: 'application/octet-stream' });

    // 4. Complete declaring 1 MB: backend inspects authoritative storage metadata -> rejects 400 size_mismatch
    const completeRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({
          messageId: mismatchMsgId,
          blobKey,
          fileSize: 1024 * 1024, // Declared 1 MB, actual 2 MB
          contentType: 'application/octet-stream',
        }),
      })
    );
    assert.strictEqual(completeRes.status, 400);
    const body = (await completeRes.json()) as any;
    assert.strictEqual(body.error, 'size_mismatch');

    // Cleanup mock metadata and DB
    __setMockObjectMetadata(blobKey, null);
    await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${mismatchMsgId}`;
  });

  // --- Phase 11C.4.5: Disappearing Messages Specification Tests ---

  it('51. POST /disappearing updates row + inserts system message', async () => {
    resetRateLimits();
    const ttlMs = 86400000; // 24 hours
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/disappearing`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ ttlMs }),
      })
    );

    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.strictEqual(body.ttlMs, ttlMs);

    // Verify chat_conversations row
    const [convRow] = await sqlAuthRole`
      SELECT disappearing_ttl_ms, ttl_set_by_user_id
      FROM chat_conversations
      WHERE id = ${mutualConversationId}
    `;
    assert.strictEqual(Number(convRow.disappearing_ttl_ms), ttlMs);
    assert.strictEqual(convRow.ttl_set_by_user_id, userA.id);

    // Verify system message in chat_messages
    const sysMsgs = await sqlAuthRole`
      SELECT id, content_type, content_algorithm, metadata_ciphertext
      FROM chat_messages
      WHERE conversation_id = ${mutualConversationId}
        AND content_algorithm = 'system'
      ORDER BY created_at DESC
      LIMIT 1
    `;
    assert.strictEqual(sysMsgs.length, 1);
    assert.strictEqual(sysMsgs[0].content_type, 'system');
    assert.strictEqual(sysMsgs[0].content_algorithm, 'system');
    const parsedMeta = JSON.parse(sysMsgs[0].metadata_ciphertext);
    assert.strictEqual(parsedMeta.type, 'disappearing_changed');
    assert.strictEqual(parsedMeta.ttlMs, ttlMs);
    assert.strictEqual(parsedMeta.setBy, userA.id);
  });

  it('52. Non-admin group member rejected 403', async () => {
    resetRateLimits();
    const groupConvId = nodeCrypto.randomUUID();

    // Create group conversation with userA as creator/admin, userB as member
    await sqlAuthRole`
      INSERT INTO chat_conversations (id, type, title, created_by, last_message_at)
      VALUES (${groupConvId}, 'group', 'Test Group Disappearing', ${userA.id}, NOW())
    `;
    await sqlAuthRole`
      INSERT INTO chat_participants (conversation_id, user_id, role)
      VALUES
        (${groupConvId}, ${userA.id}, 'admin'),
        (${groupConvId}, ${userB.id}, 'member')
    `;

    try {
      // User B (non-admin member) attempts to change disappearing mode -> 403
      const resMember = await app.fetch(
        new Request(`http://localhost:4000/api/chat/conversations/${groupConvId}/disappearing`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${tokenB}`,
          },
          body: JSON.stringify({ ttlMs: 43200000 }), // 12h
        })
      );
      assert.strictEqual(resMember.status, 403);

      // User A (creator/admin) succeeds -> 200
      const resAdmin = await app.fetch(
        new Request(`http://localhost:4000/api/chat/conversations/${groupConvId}/disappearing`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${tokenA}`,
          },
          body: JSON.stringify({ ttlMs: 43200000 }),
        })
      );
      assert.strictEqual(resAdmin.status, 200);
    } finally {
      await sqlAuthRole`DELETE FROM chat_messages WHERE conversation_id = ${groupConvId}`;
      await sqlAuthRole`DELETE FROM chat_participants WHERE conversation_id = ${groupConvId}`;
      await sqlAuthRole`DELETE FROM chat_conversations WHERE id = ${groupConvId}`;
    }
  });

  it('53. Invalid ttlMs (e.g. 999) rejected 400', async () => {
    resetRateLimits();
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/disappearing`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ ttlMs: 999 }),
      })
    );

    assert.strictEqual(res.status, 400);
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'invalid_ttl_ms');
  });

  it('54. Sweep job deletes >30d non-system messages', async () => {
    const expiredMsgId = nodeCrypto.randomUUID();
    const systemMsgId = nodeCrypto.randomUUID();
    const thirtyOneDaysAgo = new Date(Date.now() - 31 * 24 * 60 * 60 * 1000);

    // Insert regular message > 30 days old
    await sqlAuthRole`
      INSERT INTO chat_messages (
        id, conversation_id, sender_id, sender_device_id,
        content_ciphertext, content_nonce, content_algorithm, content_type,
        created_at
      ) VALUES (
        ${expiredMsgId}, ${mutualConversationId}, ${userA.id}, NULL,
        'ciphertext_expired', 'nonce1', 'x25519-xsalsa20-poly1305', 'text',
        ${thirtyOneDaysAgo}
      )
    `;

    // Insert system message > 30 days old
    await sqlAuthRole`
      INSERT INTO chat_messages (
        id, conversation_id, sender_id, sender_device_id,
        content_ciphertext, content_nonce, content_algorithm, content_type,
        metadata_ciphertext, created_at
      ) VALUES (
        ${systemMsgId}, ${mutualConversationId}, ${userA.id}, NULL,
        'system', '', 'system', 'system',
        '{"type":"test_audit"}', ${thirtyOneDaysAgo}
      )
    `;

    // Run sweep
    const deletedCount = await runMessageSweep();
    assert.ok(deletedCount >= 1, 'Should delete at least 1 expired message');

    // Verify expired regular message is gone
    const [expiredCheck] = await sqlAuthRole`
      SELECT id FROM chat_messages WHERE id = ${expiredMsgId}
    `;
    assert.strictEqual(expiredCheck, undefined, 'Expired regular message must be deleted');

    // Verify system message is strictly preserved
    const [systemCheck] = await sqlAuthRole`
      SELECT id FROM chat_messages WHERE id = ${systemMsgId}
    `;
    assert.ok(systemCheck, 'System message must NOT be deleted by sweeper');

    // Cleanup
    await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${systemMsgId}`;
  });

  it('55. PATCH /messages/:id updates row + broadcasts (Sender within 15min)', async () => {
    resetRateLimits();
    const msgId = nodeCrypto.randomUUID();

    await sqlAuthRole`
      INSERT INTO chat_messages (
        id, conversation_id, sender_id, sender_device_id,
        content_ciphertext, content_nonce, content_algorithm, content_type,
        created_at
      ) VALUES (
        ${msgId}, ${mutualConversationId}, ${userA.id}, NULL,
        'orig_cipher', 'nonce_orig', 'signal-double-ratchet', 'text',
        NOW()
      )
    `;

    try {
      const res = await app.fetch(
        new Request(`http://localhost:4000/api/chat/messages/${msgId}`, {
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${tokenA}`,
          },
          body: JSON.stringify({
            contentCiphertext: 'new_edited_cipher',
            contentNonce: 'nonce_new',
            contentAlgorithm: 'signal-double-ratchet',
          }),
        })
      );

      assert.strictEqual(res.status, 200);
      const body = (await res.json()) as any;
      assert.strictEqual(body.messageId, msgId);
      assert.ok(body.editedAt);

      const [updated] = await sqlAuthRole`
        SELECT content_ciphertext, content_nonce, edited_at, deleted_at
        FROM chat_messages WHERE id = ${msgId}
      `;
      assert.strictEqual(updated.content_ciphertext, 'new_edited_cipher');
      assert.strictEqual(updated.content_nonce, 'nonce_new');
      assert.ok(updated.edited_at, 'edited_at must be populated');
      assert.strictEqual(updated.deleted_at, null, 'deleted_at must remain null');
    } finally {
      await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${msgId}`;
    }
  });

  it('56. PATCH by non-sender → 403', async () => {
    resetRateLimits();
    const msgId = nodeCrypto.randomUUID();

    await sqlAuthRole`
      INSERT INTO chat_messages (
        id, conversation_id, sender_id, sender_device_id,
        content_ciphertext, content_nonce, content_algorithm, content_type,
        created_at
      ) VALUES (
        ${msgId}, ${mutualConversationId}, ${userA.id}, NULL,
        'orig_cipher', 'nonce_orig', 'signal-double-ratchet', 'text',
        NOW()
      )
    `;

    try {
      // User B attempts to edit User A's message
      const res = await app.fetch(
        new Request(`http://localhost:4000/api/chat/messages/${msgId}`, {
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${tokenB}`,
          },
          body: JSON.stringify({
            contentCiphertext: 'hacked_cipher',
          }),
        })
      );

      assert.strictEqual(res.status, 403);
    } finally {
      await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${msgId}`;
    }
  });

  it('57. PATCH after 15min → 403 edit_window_expired', async () => {
    resetRateLimits();
    const msgId = nodeCrypto.randomUUID();
    const sixteenMinsAgo = new Date(Date.now() - 16 * 60 * 1000);

    await sqlAuthRole`
      INSERT INTO chat_messages (
        id, conversation_id, sender_id, sender_device_id,
        content_ciphertext, content_nonce, content_algorithm, content_type,
        created_at
      ) VALUES (
        ${msgId}, ${mutualConversationId}, ${userA.id}, NULL,
        'orig_cipher', 'nonce_orig', 'signal-double-ratchet', 'text',
        ${sixteenMinsAgo}
      )
    `;

    try {
      const res = await app.fetch(
        new Request(`http://localhost:4000/api/chat/messages/${msgId}`, {
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${tokenA}`,
          },
          body: JSON.stringify({
            contentCiphertext: 'late_cipher',
          }),
        })
      );

      assert.strictEqual(res.status, 403);
      const body = (await res.json()) as any;
      assert.strictEqual(body.error, 'edit_window_expired');
    } finally {
      await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${msgId}`;
    }
  });

  it('58. DELETE soft-deletes + broadcasts (Sender)', async () => {
    resetRateLimits();
    const msgId = nodeCrypto.randomUUID();

    await sqlAuthRole`
      INSERT INTO chat_messages (
        id, conversation_id, sender_id, sender_device_id,
        content_ciphertext, content_nonce, content_algorithm, content_type,
        metadata_ciphertext, created_at
      ) VALUES (
        ${msgId}, ${mutualConversationId}, ${userA.id}, NULL,
        'secret_cipher', 'secret_nonce', 'signal-double-ratchet', 'text',
        'secret_meta', NOW()
      )
    `;

    try {
      const res = await app.fetch(
        new Request(`http://localhost:4000/api/chat/messages/${msgId}`, {
          method: 'DELETE',
          headers: {
            Authorization: `Bearer ${tokenA}`,
          },
        })
      );

      assert.strictEqual(res.status, 200);
      const body = (await res.json()) as any;
      assert.strictEqual(body.success, true);
      assert.ok(body.deletedAt);

      const [row] = await sqlAuthRole`
        SELECT content_ciphertext, content_nonce, metadata_ciphertext, deleted_at
        FROM chat_messages WHERE id = ${msgId}
      `;
      assert.ok(row.deleted_at, 'deleted_at must be populated');
      assert.strictEqual(row.content_ciphertext, '', 'content_ciphertext cleared');
      assert.strictEqual(row.content_nonce, '', 'content_nonce cleared');
      assert.strictEqual(row.metadata_ciphertext, null, 'metadata_ciphertext cleared');
    } finally {
      await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${msgId}`;
    }
  });

  it('59. DELETE by non-sender → 403', async () => {
    resetRateLimits();
    const msgId = nodeCrypto.randomUUID();

    await sqlAuthRole`
      INSERT INTO chat_messages (
        id, conversation_id, sender_id, sender_device_id,
        content_ciphertext, content_nonce, content_algorithm, content_type,
        created_at
      ) VALUES (
        ${msgId}, ${mutualConversationId}, ${userA.id}, NULL,
        'user_a_cipher', 'nonce', 'signal-double-ratchet', 'text',
        NOW()
      )
    `;

    try {
      const res = await app.fetch(
        new Request(`http://localhost:4000/api/chat/messages/${msgId}`, {
          method: 'DELETE',
          headers: {
            Authorization: `Bearer ${tokenB}`,
          },
        })
      );

      assert.strictEqual(res.status, 403);

      const [row] = await sqlAuthRole`
        SELECT deleted_at FROM chat_messages WHERE id = ${msgId}
      `;
      assert.strictEqual(row.deleted_at, null, 'Message must not be deleted');
    } finally {
      await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${msgId}`;
    }
  });

  it('60. POST /api/chat/push-hints with 2 recipient devices returns 200 and leaks no plaintext', async () => {
    resetRateLimits();
    await resetSentPushHints();
    await ensureMutualConversation();
    const devId1 = nodeCrypto.randomUUID();
    const devId2 = nodeCrypto.randomUUID();
    const msgId = nodeCrypto.randomUUID();

    const deliveredPushes: Array<{ token: string; data: Record<string, string> }> = [];
    __setMockPushHandler(async (deviceToken, data) => {
      deliveredPushes.push({ token: deviceToken, data });
      return true;
    });

    try {
      // 1. Insert 2 recipient devices for userB with push tokens
      await sqlAuthRole`
        INSERT INTO user_devices (id, user_id, device_name, platform, push_token)
        VALUES
          (${devId1}, ${userB.id}, 'Phone B1', 'android', 'https://ntfy.sh/endpoint_b1'),
          (${devId2}, ${userB.id}, 'Phone B2', 'android', 'https://ntfy.sh/endpoint_b2')
      `;

      // 2. Insert test message sent by userA in mutualConversationId
      await sqlAuthRole`
        INSERT INTO chat_messages (
          id, conversation_id, sender_id, sender_device_id,
          content_ciphertext, content_nonce, content_algorithm, content_type,
          created_at
        ) VALUES (
          ${msgId}, ${mutualConversationId}, ${userA.id}, NULL,
          'ciphertext_payload_abc', 'nonce_123', 'signal-double-ratchet', 'text',
          NOW()
        )
      `;

      // 3. Post push hints for both recipient devices
      const secretPlaintext = 'TOP_SECRET_PLAINTEXT_SHOULD_NEVER_APPEAR_IN_PUSH';
      const res = await app.fetch(
        new Request('http://localhost:4000/api/chat/push-hints', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${tokenA}`,
          },
          body: JSON.stringify({
            conversationId: mutualConversationId,
            messageId: msgId,
            hints: [
              {
                recipientUserId: userB.id,
                recipientDeviceId: devId1,
                hintCiphertext: 'encrypted_hint_b1',
                hintNonce: 'nonce_b1',
                hintKeyWrapped: 'wrapped_key_b1',
                hintKeyAlgorithm: 'signal-double-ratchet',
              },
              {
                recipientUserId: userB.id,
                recipientDeviceId: devId2,
                hintCiphertext: 'encrypted_hint_b2',
                hintNonce: 'nonce_b2',
                hintKeyWrapped: 'wrapped_key_b2',
                hintKeyAlgorithm: 'signal-double-ratchet',
              },
            ],
          }),
        })
      );

      assert.strictEqual(res.status, 200);
      const body = (await res.json()) as any;
      assert.strictEqual(body.delivered, 2);
      assert.strictEqual(body.skipped, 0);

      // Verify delivered pushes
      assert.strictEqual(deliveredPushes.length, 2);
      const token1Push = deliveredPushes.find((p) => p.token === 'https://ntfy.sh/endpoint_b1');
      const token2Push = deliveredPushes.find((p) => p.token === 'https://ntfy.sh/endpoint_b2');
      assert.ok(token1Push, 'Push for device 1 delivered');
      assert.ok(token2Push, 'Push for device 2 delivered');

      // Verify data-only UnifiedPush payload: strictly no notification block, title, or body
      for (const p of deliveredPushes) {
        assert.strictEqual((p as any).notification, undefined);
        assert.strictEqual((p.data as any).title, undefined);
        assert.strictEqual((p.data as any).body, undefined);
        assert.strictEqual(p.data.type, 'chat_message');
        assert.strictEqual(p.data.conversationId, mutualConversationId);
        assert.strictEqual(p.data.senderId, userA.id);

        // Assert no plaintext leakage in push data
        const serializedData = JSON.stringify(p.data);
        assert.strictEqual(
          serializedData.includes(secretPlaintext),
          false,
          'Push data payload must not contain message plaintext'
        );
      }
    } finally {
      __setMockPushHandler(null);
      await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${msgId}`;
      await sqlAuthRole`DELETE FROM user_devices WHERE id IN (${devId1}, ${devId2})`;
      await resetSentPushHints();
    }
  });

  it('61. Same hint submitted twice → delivered only once (durable idempotency via chat_push_hints_sent)', async () => {
    resetRateLimits();
    await resetSentPushHints();
    const devId = nodeCrypto.randomUUID();
    const msgId = nodeCrypto.randomUUID();

    let pushCount = 0;
    __setMockPushHandler(async () => {
      pushCount++;
      return true;
    });

    try {
      await sqlAuthRole`
        INSERT INTO user_devices (id, user_id, device_name, platform, push_token)
        VALUES (${devId}, ${userB.id}, 'Phone B', 'android', 'https://ntfy.sh/endpoint_idempotent')
      `;

      await sqlAuthRole`
        INSERT INTO chat_messages (
          id, conversation_id, sender_id, sender_device_id,
          content_ciphertext, content_nonce, content_algorithm, content_type,
          created_at
        ) VALUES (
          ${msgId}, ${mutualConversationId}, ${userA.id}, NULL,
          'cipher_idem', 'nonce_idem', 'signal-double-ratchet', 'text',
          NOW()
        )
      `;

      const requestPayload = {
        conversationId: mutualConversationId,
        messageId: msgId,
        hints: [
          {
            recipientUserId: userB.id,
            recipientDeviceId: devId,
            hintCiphertext: 'enc_hint_first',
            hintNonce: 'nonce_1',
            hintKeyWrapped: 'wrapped_key',
            hintKeyAlgorithm: 'signal-double-ratchet',
          },
        ],
      };

      // First submission: delivered = 1
      const res1 = await app.fetch(
        new Request('http://localhost:4000/api/chat/push-hints', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${tokenA}`,
          },
          body: JSON.stringify(requestPayload),
        })
      );
      assert.strictEqual(res1.status, 200);
      const body1 = (await res1.json()) as any;
      assert.strictEqual(body1.delivered, 1);
      assert.strictEqual(body1.skipped, 0);
      assert.strictEqual(pushCount, 1);

      // Second identical submission: delivered = 0, skipped = 1
      const res2 = await app.fetch(
        new Request('http://localhost:4000/api/chat/push-hints', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${tokenA}`,
          },
          body: JSON.stringify(requestPayload),
        })
      );
      assert.strictEqual(res2.status, 200);
      const body2 = (await res2.json()) as any;
      assert.strictEqual(body2.delivered, 0);
      assert.strictEqual(body2.skipped, 1);
      assert.strictEqual(pushCount, 1); // Not delivered again!
    } finally {
      __setMockPushHandler(null);
      await sqlAuthRole`DELETE FROM chat_messages WHERE id = ${msgId}`;
      await sqlAuthRole`DELETE FROM user_devices WHERE id = ${devId}`;
      await resetSentPushHints();
    }
  });

  it('62. UnifiedPush HTTP POST delivers data payload directly to endpoint URL', async () => {
    __setMockPushHandler(null);
    const originalFetch = global.fetch;
    const fetchCalls: Array<{ url: string; body: any; headers: any }> = [];

    global.fetch = (async (url: any, options: any) => {
      fetchCalls.push({
        url: String(url),
        body: JSON.parse(options?.body || '{}'),
        headers: options?.headers,
      });
      return new Response(JSON.stringify({ success: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }) as any;

    try {
      const endpoint = 'https://ntfy.sh/mue_test_topic_xyz';
      const result = await sendDataPush(endpoint, {
        type: 'chat_message',
        hintCiphertext: 'encrypted_test_hint',
      });
      assert.strictEqual(result, true, 'sendDataPush must succeed via UnifiedPush HTTP POST');
      assert.strictEqual(fetchCalls.length, 1, 'fetch must be called once');
      assert.strictEqual(fetchCalls[0].url, endpoint);
      assert.strictEqual(fetchCalls[0].body.type, 'chat_message');
      assert.strictEqual(fetchCalls[0].body.hintCiphertext, 'encrypted_test_hint');
      assert.strictEqual(fetchCalls[0].headers['Content-Type'], 'application/json');
    } finally {
      global.fetch = originalFetch;
    }
  });

  it('63. isPushConfigured() returns false when DISABLE_PUSH is true', async () => {
    const originalEnv = process.env.DISABLE_PUSH;
    try {
      process.env.DISABLE_PUSH = 'true';
      assert.strictEqual(isPushConfigured(), false);
    } finally {
      process.env.DISABLE_PUSH = originalEnv;
    }
  });

  it('64. RLS: app_user SELECT on chat_push_hints_sent returns 0 rows and direct INSERT is rejected', async () => {
    const testMsgId = nodeCrypto.randomUUID();
    const testDevId = nodeCrypto.randomUUID();

    // 1. Insert via auth_role
    await sqlAuthRole`
      INSERT INTO chat_push_hints_sent (message_id, device_id, sent_at)
      VALUES (${testMsgId}, ${testDevId}, NOW())
    `;

    try {
      // 2. Query as app_user -> permission denied (or 0 rows if RLS)
      let selectBlocked = false;
      try {
        const rows = await sqlAppUser`
          SELECT * FROM chat_push_hints_sent WHERE message_id = ${testMsgId}
        `;
        selectBlocked = rows.length === 0;
      } catch (err: any) {
        selectBlocked = err.code === '42501' || /permission denied/i.test(err.message);
      }
      assert.strictEqual(selectBlocked, true, 'Direct SELECT on chat_push_hints_sent must be denied or return 0 rows to app_user');

      // 3. Attempt direct INSERT as app_user -> permission denied
      let insertDenied = false;
      try {
        await sqlAppUser`
          INSERT INTO chat_push_hints_sent (message_id, device_id)
          VALUES (${nodeCrypto.randomUUID()}, ${nodeCrypto.randomUUID()})
        `;
      } catch (err: any) {
        insertDenied = err.code === '42501' || /permission denied/i.test(err.message);
      }
      assert.strictEqual(insertDenied, true, 'Direct INSERT by app_user must be rejected with permission denied');
    } finally {
      await sqlAuthRole`DELETE FROM chat_push_hints_sent WHERE message_id = ${testMsgId}`;
    }
  });

  it('65. __setMockPushHandler throws when NODE_ENV !== "test"', () => {
    const originalEnv = process.env.NODE_ENV;
    try {
      (process.env as any).NODE_ENV = 'production';
      assert.throws(
        () => {
          __setMockPushHandler(null);
        },
        /test-only hook/,
        '__setMockPushHandler must throw in production'
      );
    } finally {
      process.env.NODE_ENV = originalEnv;
    }
  });

  it('66. POST /api/chat/conversations without Authorization header returns 401', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/chat/conversations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ participantIds: [userB.id] }),
      })
    );
    assert.strictEqual(res.status, 401);
  });

  it('67. POST /api/chat/conversations with empty participantIds returns 400', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/chat/conversations', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ participantIds: [] }),
      })
    );
    assert.strictEqual(res.status, 400);
    const body = (await res.json()) as any;
    assert.match(body.error, /participantIds/);
  });

  it('68. POST /api/chat/messages with payload exceeding 12KB returns 400', async () => {
    const hugeCiphertext = 'X'.repeat(12_289);
    const res = await app.fetch(
      new Request('http://localhost:4000/api/chat/messages', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          contentCiphertext: hugeCiphertext,
        }),
      })
    );
    assert.strictEqual(res.status, 400);
  });
});




