/**
 * Live Attachment Storage End-to-End Integration Tests (Upstash Blob)
 * Split from default test suite per FIX D5.1.
 *
 * Verifies real round-trip cryptographic upload, download, decryption,
 * byte-identical fidelity, metadata verification, and cleanup against Upstash Blob.
 */

process.env.NODE_ENV = 'test';

import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import nodeCrypto from 'node:crypto';
import { app } from '../../src/app.js';
import { createTestUser, cleanupTestUsers, sqlAuthRole } from '../helpers/rls.js';
import { generateAccessToken } from '../../src/services/authService.js';
import { getObjectMetadata, deleteObject, __clearMockObjectMetadata } from '../../src/services/blobService.js';
import { AttachmentEncryption } from '../../../src/e2e/AttachmentEncryption.js';

describe('Phase 11C.3 — Live Storage E2E Integration Tests', () => {
  let userA: { id: string; username: string; email: string };
  let userB: { id: string; username: string; email: string };
  const userIds: string[] = [];
  let tokenA: string;
  let tokenB: string;
  let mutualConversationId: string = '';
  let liveTestExecuted = false;

  before(async () => {
    // Clear any mock metadata so all calls hit live storage
    __clearMockObjectMetadata();

    const ts = Date.now();
    userA = await createTestUser(`live_a_${ts}`, `live_a_${ts}@test.com`, false);
    userB = await createTestUser(`live_b_${ts}`, `live_b_${ts}@test.com`, false);
    userIds.push(userA.id, userB.id);

    tokenA = generateAccessToken(userA.id, userA.username);
    tokenB = generateAccessToken(userB.id, userB.username);

    // Mutual follow
    await sqlAuthRole`
      INSERT INTO follows (follower_id, followee_id)
      VALUES (${userA.id}, ${userB.id}), (${userB.id}, ${userA.id})
      ON CONFLICT DO NOTHING
    `;

    // 1:1 Conversation
    const convRes = await app.fetch(
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
    assert.strictEqual(convRes.status, 201);
    const convData = (await convRes.json()) as any;
    mutualConversationId = convData.conversation.id;
  });

  after(async () => {
    try {
      if (mutualConversationId) {
        await sqlAuthRole`DELETE FROM chat_attachments WHERE message_id IN (
          SELECT id FROM chat_messages WHERE conversation_id = ${mutualConversationId}
        )`;
        await sqlAuthRole`DELETE FROM chat_messages WHERE conversation_id = ${mutualConversationId}`;
        await sqlAuthRole`DELETE FROM chat_participants WHERE conversation_id = ${mutualConversationId}`;
        await sqlAuthRole`DELETE FROM chat_conversations WHERE id = ${mutualConversationId}`;
      }
      if (userIds.length > 0) {
        await cleanupTestUsers(userIds);
      }
    } catch {}
  });

  it('1. Live Storage E2E: 100 KB payload upload → download → decrypt → byte-identical', async (t) => {
    const { UPSTASH_BLOB_TOKEN } = process.env;
    if (!UPSTASH_BLOB_TOKEN) {
      if (process.env.REQUIRE_LIVE_STORAGE_E2E === '1') {
        assert.fail('REQUIRE_LIVE_STORAGE_E2E=1 is set but UPSTASH_BLOB_TOKEN is missing');
      }
      t.skip('Skipping Live Storage E2E: UPSTASH_BLOB_TOKEN missing in test environment');
      return;
    }

    liveTestExecuted = true;

    // 1. Generate 100 KB payload and encrypt
    const originalPlaintext = new Uint8Array(nodeCrypto.randomBytes(100 * 1024));
    const encrypted = await AttachmentEncryption.encrypt(originalPlaintext);

    // 2. Alice initializes attachment
    const messageId = nodeCrypto.randomUUID();
    const initRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/init`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({ messageId }),
      })
    );
    assert.strictEqual(initRes.status, 201);

    // 3. Alice presigns upload URL
    const presignRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/presign`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          fileName: 'storage-live-100kb.bin',
          mimeType: 'application/octet-stream',
          fileSize: encrypted.ciphertext.byteLength,
          isTestUpload: true,
        }),
      })
    );
    assert.strictEqual(presignRes.status, 200);
    const { blobKey, uploadUrl } = (await presignRes.json()) as any;
    assert.ok(blobKey.startsWith('chat/test/'));

    let objectUploaded = false;
    try {
      // 4. PUT ciphertext to real Upstash Blob
      const putRes = await fetch(uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/octet-stream' },
        body: encrypted.ciphertext,
      });
      assert.strictEqual(putRes.status, 200);
      objectUploaded = true;

      // 5. Complete attachment
      const completeRes = await app.fetch(
        new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${tokenA}`,
          },
          body: JSON.stringify({
            messageId,
            blobKey,
            fileSize: encrypted.ciphertext.byteLength,
            contentType: 'application/octet-stream',
            encryptionAlgorithm: 'AES-GCM-256',
          }),
        })
      );
      assert.strictEqual(completeRes.status, 201);
      const { id: attachmentId } = (await completeRes.json()) as any;

      // 6. Verify HeadObject / info directly in real Upstash Blob
      const meta = await getObjectMetadata(blobKey);
      assert.ok(meta);
      assert.strictEqual(meta.size, encrypted.ciphertext.byteLength);

      // 7. Bob downloads via presigned URL
      const downloadRes = await app.fetch(
        new Request(`http://localhost:4000/api/chat/attachments/${attachmentId}/download`, {
          method: 'GET',
          headers: { Authorization: `Bearer ${tokenB}` },
        })
      );
      assert.strictEqual(downloadRes.status, 200);
      const { downloadUrl } = (await downloadRes.json()) as any;

      // 8. Bob fetches ciphertext from Upstash Blob
      const blobFetchRes = await fetch(downloadUrl);
      assert.strictEqual(blobFetchRes.status, 200);
      const downloadedCt = new Uint8Array(await blobFetchRes.arrayBuffer());

      // 9. Bob decrypts and asserts byte-identical
      const decrypted = await AttachmentEncryption.decrypt(
        downloadedCt,
        encrypted.key,
        encrypted.iv,
        encrypted.sha256
      );
      assert.strictEqual(decrypted.length, originalPlaintext.length);
      assert.deepStrictEqual(Buffer.from(decrypted), Buffer.from(originalPlaintext));

      // 10. Delete attachment message and confirm object gone
      const deleteRes = await app.fetch(
        new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/${messageId}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${tokenA}` },
        })
      );
      assert.strictEqual(deleteRes.status, 200);

      const metaAfter = await getObjectMetadata(blobKey);
      assert.strictEqual(metaAfter, null);
      objectUploaded = false;
    } finally {
      if (objectUploaded) {
        await deleteObject(blobKey).catch(() => {});
      }
    }
  });

  it('2. Live Storage guard: if UPSTASH_BLOB_TOKEN is configured, test 1 must execute live', () => {
    const token = (process.env.UPSTASH_BLOB_TOKEN || '').trim();
    const isConfigured = token.length > 0 && !token.includes('fake') && !token.includes('placeholder') && !token.includes('your');
    if (isConfigured) {
      assert.strictEqual(liveTestExecuted, true, 'Test 1 must execute when UPSTASH_BLOB_TOKEN is configured');
    }
  });

  it('3. FIX 3: presign for 1 MB, upload 2 MB object, /complete rejects with 400 size_mismatch', async () => {
    const { UPSTASH_BLOB_TOKEN } = process.env;
    if (!UPSTASH_BLOB_TOKEN) {
      // Fallback with mock metadata if token absent in offline environment
      const mockKey = `chat/test/${mutualConversationId}/${nodeCrypto.randomUUID()}`;
      __setMockObjectMetadata(mockKey, { size: 2 * 1024 * 1024, contentType: 'application/octet-stream' });
      const failMsgId = nodeCrypto.randomUUID();
      await app.fetch(
        new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/init`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokenA}` },
          body: JSON.stringify({ messageId: failMsgId }),
        })
      );
      const res = await app.fetch(
        new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokenA}` },
          body: JSON.stringify({
            messageId: failMsgId,
            blobKey: mockKey,
            fileSize: 1024 * 1024, // Client declared 1 MB, actual is 2 MB
            contentType: 'application/octet-stream',
          }),
        })
      );
      assert.strictEqual(res.status, 400);
      const body = (await res.json()) as any;
      assert.strictEqual(body.error, 'size_mismatch');
      return;
    }

    // Live execution with real Upstash Blob upload
    const mismatchMsgId = nodeCrypto.randomUUID();
    await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/init`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({ messageId: mismatchMsgId }),
      })
    );

    // Presign for 1 MB (1048576 bytes)
    const presignRes = await app.fetch(
      new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/presign`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokenA}` },
        body: JSON.stringify({
          fileName: 'oversized-test.bin',
          mimeType: 'application/octet-stream',
          fileSize: 1024 * 1024, // 1 MB declared
          isTestUpload: true,
        }),
      })
    );
    assert.strictEqual(presignRes.status, 200);
    const { blobKey, uploadUrl } = (await presignRes.json()) as any;

    let uploaded = false;
    try {
      // Actually PUT a 2 MB payload (2097152 bytes)
      const oversizedPayload = Buffer.alloc(2 * 1024 * 1024, 7);
      const putRes = await fetch(uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/octet-stream' },
        body: oversizedPayload,
      });
      assert.strictEqual(putRes.status, 200);
      uploaded = true;

      // Complete attempt declaring 1 MB: backend inspects authoritative getObjectMetadata (2 MB) -> rejects 400 size_mismatch
      const completeRes = await app.fetch(
        new Request(`http://localhost:4000/api/chat/conversations/${mutualConversationId}/attachments/complete`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokenA}` },
          body: JSON.stringify({
            messageId: mismatchMsgId,
            blobKey,
            fileSize: 1024 * 1024, // declared 1 MB
            contentType: 'application/octet-stream',
          }),
        })
      );
      assert.strictEqual(completeRes.status, 400);
      const completeBody = (await completeRes.json()) as any;
      assert.strictEqual(completeBody.error, 'size_mismatch');
    } finally {
      if (uploaded) {
        await deleteObject(blobKey).catch(() => {});
      }
    }
  });
});
