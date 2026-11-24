import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';
import { sqlAuthRole } from './helpers/rls.js';
import { closeConnections } from '../src/db/connection.js';

describe('E2E Key Directory & Signed Prekey Rotation Tests', () => {
  const timestamp = Math.floor(Math.random() * 900 + 100);
  const testUser = {
    username: `ee_u_${timestamp}`,
    email: `e2e_${timestamp}@example.com`,
    password: 'Password123!',
  };

  let userId = '';
  let accessToken = '';
  let deviceId = '';

  before(async () => {
    // 1. Signup user
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(testUser),
      })
    );
    assert.strictEqual(res.status, 201);
    const data = await res.json() as any;
    userId = data.user.id;
    accessToken = data.accessToken;

    // 2. Register device
    const devRes = await app.fetch(
      new Request('http://localhost:4000/api/auth/devices/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({
          deviceName: 'E2E Phone',
          platform: 'android',
        }),
      })
    );
    assert.strictEqual(devRes.status, 201);
    const devData = await devRes.json() as any;
    deviceId = devData.id;
  });

  after(async () => {
    if (userId) {
      try {
        await sqlAuthRole`DELETE FROM users WHERE id = ${userId}`;
      } catch {}
    }
  });

  it('Registers initial identity key, signed prekey, and 50 one-time prekeys', async () => {
    const otks = [];
    for (let i = 1; i <= 50; i++) {
      otks.push({
        keyId: i,
        publicKey: `otk_pub_base64_sample_${i}`,
      });
    }

    const res = await app.fetch(
      new Request('http://localhost:4000/api/e2e/register-keys', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({
          deviceId,
          registrationId: 1337,
          identityKey: 'identity_pub_key_base64_sample',
          signedPrekey: {
            keyId: 1,
            publicKey: 'signed_prekey_pub_base64_v1',
            signature: 'signed_prekey_sig_base64_v1',
          },
          oneTimePrekeys: otks,
        }),
      })
    );

    assert.strictEqual(res.status, 201);
    const data = await res.json() as any;
    assert.strictEqual(data.success, true);
    assert.strictEqual(data.prekeysCount, 50);

    // Verify prekey count
    const countRes = await app.fetch(
      new Request(`http://localhost:4000/api/e2e/prekeys/count/${deviceId}`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      })
    );
    assert.strictEqual(countRes.status, 200);
    const countData = await countRes.json() as any;
    assert.strictEqual(countData.remainingCount, 50);
  });

  it('Rejects rotate-signed-prekey if payload fails Zod validation', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/e2e/rotate-signed-prekey', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({
          deviceId: 'not-a-uuid',
          signedPrekey: { keyId: -1, publicKey: 'short', signature: '' },
        }),
      })
    );

    assert.strictEqual(res.status, 400);
    const data = await res.json() as any;
    assert.ok(data.details, 'Must return Zod validation details');
  });

  it('Rotates signed prekey successfully and serves latest in bundle', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/e2e/rotate-signed-prekey', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({
          deviceId,
          signedPrekey: {
            keyId: 2,
            publicKey: 'signed_prekey_pub_base64_v2_rotated',
            signature: 'signed_prekey_sig_base64_v2_rotated',
          },
        }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = await res.json() as any;
    assert.strictEqual(data.success, true);
    assert.strictEqual(data.keyId, 2);

    // Verify bundle fetch returns the rotated key (keyId 2)
    const bundleRes = await app.fetch(
      new Request(`http://localhost:4000/api/e2e/prekeys/${userId}/${deviceId}`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      })
    );
    assert.strictEqual(bundleRes.status, 200);
    const bundle = await bundleRes.json() as any;
    assert.strictEqual(bundle.signedPrekey.keyId, 2);
    assert.strictEqual(bundle.signedPrekey.publicKey, 'signed_prekey_pub_base64_v2_rotated');
  });

  it('Enforces 1 rotation per 24 hours rate limit (returns 429 with Retry-After)', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/e2e/rotate-signed-prekey', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({
          deviceId,
          signedPrekey: {
            keyId: 3,
            publicKey: 'signed_prekey_pub_base64_v3_rapid',
            signature: 'signed_prekey_sig_base64_v3_rapid',
          },
        }),
      })
    );

    assert.strictEqual(res.status, 429);
    assert.ok(res.headers.get('Retry-After'), 'Must include Retry-After header');
    const data = await res.json() as any;
    assert.strictEqual(data.error, 'Signed prekey can only be rotated once every 24 hours');
  });

  // --- Sender Key Distribution Relay (Phase 11C.2) ---

  it('Relays and retrieves Sender Key distributions between group participants', async () => {
    // 1. Create User 2
    const ts2 = Math.floor(Math.random() * 900 + 100);
    const user2Res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: `ee_ub_${ts2}`,
          email: `e2e_2_${ts2}@example.com`,
          password: 'Password123!',
        }),
      })
    );
    assert.strictEqual(user2Res.status, 201);
    const user2Data = await user2Res.json() as any;
    const userId2 = user2Data.user.id;
    const token2 = user2Data.accessToken;

    const dev2Res = await app.fetch(
      new Request('http://localhost:4000/api/auth/devices/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token2}`,
        },
        body: JSON.stringify({ deviceName: 'User2 Phone', platform: 'android' }),
      })
    );
    const dev2Data = await dev2Res.json() as any;
    const deviceId2 = dev2Data.id;

    // 2. Mutual follow so group trigger passes
    await sqlAuthRole`
      INSERT INTO follows (follower_id, followee_id)
      VALUES (${userId}, ${userId2}), (${userId2}, ${userId})
    `;

    // 3. Create group conversation with User 1 and User 2 as participants
    const convId = crypto.randomUUID();
    await sqlAuthRole`
      INSERT INTO chat_conversations (id, type, created_by, title)
      VALUES (${convId}, 'group', ${userId}, 'Crypto Club')
    `;
    await sqlAuthRole`
      INSERT INTO chat_participants (conversation_id, user_id, role)
      VALUES (${convId}, ${userId}, 'admin'), (${convId}, ${userId2}, 'member')
    `;

    // 4. User 1 sends sender key distribution for User 2
    const distCiphertext = 'dGhpcyBpcyBhbiBlbmNyeXB0ZWQgc2VuZGVyIGtleSBkaXN0cmlidXRpb24gbWVzc2FnZQ==';
    const distNonce = 'nonce_12byte_random_base64';

    const sendDistRes = await app.fetch(
      new Request('http://localhost:4000/api/e2e/sender-key-distributions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({
          conversationId: convId,
          senderDeviceId: deviceId,
          recipientUserId: userId2,
          recipientDeviceId: deviceId2,
          distributionCiphertext: distCiphertext,
          distributionNonce: distNonce,
        }),
      })
    );
    assert.strictEqual(sendDistRes.status, 201);

    // 5. User 2 fetches distributions for the conversation
    const getDistRes = await app.fetch(
      new Request(`http://localhost:4000/api/e2e/sender-key-distributions/${convId}`, {
        headers: { Authorization: `Bearer ${token2}` },
      })
    );
    assert.strictEqual(getDistRes.status, 200);
    const getDistData = await getDistRes.json() as any;
    assert.ok(Array.isArray(getDistData.distributions), 'Must return distributions array');
    assert.strictEqual(getDistData.distributions.length, 1);
    assert.strictEqual(getDistData.distributions[0].distributionCiphertext, distCiphertext);
    assert.strictEqual(getDistData.distributions[0].distributionNonce, distNonce);
    assert.strictEqual(getDistData.distributions[0].senderUserId, userId);

    // 6. Non-participant receives 403 on GET
    const strangerTokenRes = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: `ee_str_${ts2}`,
          email: `e2e_stranger_${ts2}@example.com`,
          password: 'Password123!',
        }),
      })
    );
    const strangerData = await strangerTokenRes.json() as any;
    const strangerRes = await app.fetch(
      new Request(`http://localhost:4000/api/e2e/sender-key-distributions/${convId}`, {
        headers: { Authorization: `Bearer ${strangerData.accessToken}` },
      })
    );
    assert.strictEqual(strangerRes.status, 403);

    // Cleanup
    await sqlAuthRole`DELETE FROM e2e_sender_key_distributions WHERE conversation_id = ${convId}`;
    await sqlAuthRole`DELETE FROM chat_participants WHERE conversation_id = ${convId}`;
    await sqlAuthRole`DELETE FROM chat_conversations WHERE id = ${convId}`;
    await sqlAuthRole`DELETE FROM follows WHERE follower_id IN (${userId}, ${userId2}) OR followee_id IN (${userId}, ${userId2})`;
    await sqlAuthRole`DELETE FROM users WHERE id IN (${userId2}, ${strangerData.user.id})`;
  });

  it('6. Multi-device group distribution passes FK with real UUID and fails 500 with non-UUID dev_primary', async () => {
    const ts = Math.floor(Math.random() * 900 + 100);
    // 1. Create User A and User B
    const userARes = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: `fk_u_a_${ts}`,
          email: `fk_a_${ts}@example.com`,
          password: 'Password123!',
        }),
      })
    );
    assert.strictEqual(userARes.status, 201);
    const userA = (await userARes.json()) as any;

    const userBRes = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: `fk_u_b_${ts}`,
          email: `fk_b_${ts}@example.com`,
          password: 'Password123!',
        }),
      })
    );
    assert.strictEqual(userBRes.status, 201);
    const userB = (await userBRes.json()) as any;

    // 2. Register 2 devices each for User A and User B
    const devA1Res = await app.fetch(
      new Request('http://localhost:4000/api/auth/devices/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userA.accessToken}` },
        body: JSON.stringify({ deviceName: 'Phone A1', platform: 'android' }),
      })
    );
    const devA1 = (await devA1Res.json()) as any;

    const devA2Res = await app.fetch(
      new Request('http://localhost:4000/api/auth/devices/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userA.accessToken}` },
        body: JSON.stringify({ deviceName: 'Tablet A2', platform: 'android' }),
      })
    );
    const devA2 = (await devA2Res.json()) as any;

    const devB1Res = await app.fetch(
      new Request('http://localhost:4000/api/auth/devices/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userB.accessToken}` },
        body: JSON.stringify({ deviceName: 'Phone B1', platform: 'android' }),
      })
    );
    const devB1 = (await devB1Res.json()) as any;

    const devB2Res = await app.fetch(
      new Request('http://localhost:4000/api/auth/devices/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userB.accessToken}` },
        body: JSON.stringify({ deviceName: 'Desktop B2', platform: 'android' }),
      })
    );
    const devB2 = (await devB2Res.json()) as any;

    // 3. Mutual follow and group creation
    await sqlAuthRole`INSERT INTO follows (follower_id, followee_id) VALUES (${userA.user.id}, ${userB.user.id}), (${userB.user.id}, ${userA.user.id})`;

    const groupRes = await app.fetch(
      new Request('http://localhost:4000/api/chat/conversations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userA.accessToken}` },
        body: JSON.stringify({
          type: 'group',
          title: 'FK Verification Group',
          participantIds: [userB.user.id],
        }),
      })
    );
    if (groupRes.status !== 201) {
      console.error('GROUP RES FAIL:', groupRes.status, await groupRes.text());
    }
    assert.strictEqual(groupRes.status, 201);
    const groupData = (await groupRes.json()) as any;
    const convId = groupData.conversation.id;

    // 4. POST sender-key-distribution with real UUID recipient_device_id -> 201
    const postRealRes = await app.fetch(
      new Request('http://localhost:4000/api/e2e/sender-key-distributions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userA.accessToken}` },
        body: JSON.stringify({
          conversationId: convId,
          senderDeviceId: devA1.id,
          recipientUserId: userB.user.id,
          recipientDeviceId: devB1.id,
          distributionCiphertext: 'cipher_real_uuid',
          distributionNonce: 'nonce_real_uuid',
        }),
      })
    );
    assert.strictEqual(postRealRes.status, 201, 'Distribution to real recipient device UUID must succeed with 201');

    // 5. POST sender-key-distribution with non-UUID 'dev_primary' -> rejected with 400 (Zod schema UUID validation)
    const postBadRes = await app.fetch(
      new Request('http://localhost:4000/api/e2e/sender-key-distributions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userA.accessToken}` },
        body: JSON.stringify({
          conversationId: convId,
          senderDeviceId: devA1.id,
          recipientUserId: userB.user.id,
          recipientDeviceId: 'dev_primary',
          distributionCiphertext: 'cipher_bad',
          distributionNonce: 'nonce_bad',
        }),
      })
    );
    assert.strictEqual(postBadRes.status, 400, 'Distribution to dev_primary must fail with 400 due to Zod UUID validation');

    // 6. POST sender-key-distribution with non-existent device UUID -> 400 (sanitized FK violation)
    const postFakeFkRes = await app.fetch(
      new Request('http://localhost:4000/api/e2e/sender-key-distributions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${userA.accessToken}` },
        body: JSON.stringify({
          conversationId: convId,
          senderDeviceId: devA1.id,
          recipientUserId: userB.user.id,
          recipientDeviceId: '00000000-0000-4000-8000-000000000099',
          distributionCiphertext: 'cipher_bad_fk',
          distributionNonce: 'nonce_bad_fk',
        }),
      })
    );
    assert.strictEqual(postFakeFkRes.status, 400, 'Distribution to non-existent device UUID must fail with 400');
    const fakeFkBody = (await postFakeFkRes.json()) as any;
    assert.strictEqual(fakeFkBody.error, 'invalid_recipient_device');
    assert.strictEqual(
      JSON.stringify(fakeFkBody).includes('violates foreign key constraint'),
      false,
      'Must not leak Postgres FK constraint error in body'
    );

    // Cleanup
    await sqlAuthRole`DELETE FROM e2e_sender_key_distributions WHERE conversation_id = ${convId}`;
    await sqlAuthRole`DELETE FROM chat_participants WHERE conversation_id = ${convId}`;
    await sqlAuthRole`DELETE FROM chat_conversations WHERE id = ${convId}`;
    await sqlAuthRole`DELETE FROM user_devices WHERE user_id IN (${userA.user.id}, ${userB.user.id})`;
    await sqlAuthRole`DELETE FROM follows WHERE follower_id IN (${userA.user.id}, ${userB.user.id}) OR followee_id IN (${userA.user.id}, ${userB.user.id})`;
    await sqlAuthRole`DELETE FROM users WHERE id IN (${userA.user.id}, ${userB.user.id})`;
  });

  it('GET /api/e2e/prekeys/:userId returns 401 without auth token', async () => {
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/e2e/prekeys/${userId}`)
    );
    assert.strictEqual(res.status, 401);
  });

  it('POST /api/e2e/keys with malformed device UUID returns 400', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/e2e/keys', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({
          deviceId: 'invalid-non-uuid-device',
          registrationId: 1234,
          identityKey: 'sample_identity_key_base64==',
          signedPrekey: {
            keyId: 1,
            publicKey: 'sample_spk_key_base64==',
            signature: 'sample_spk_sig_base64==',
          },
        }),
      })
    );
    assert.strictEqual(res.status, 400);
    const body = (await res.json()) as any;
    assert.match(body.error, /Invalid device UUID/);
  });
});
