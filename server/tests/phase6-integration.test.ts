import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import crypto from 'node:crypto';
import dotenv from 'dotenv';
dotenv.config();

import { app } from '../src/app.js';
import { pgClientAuth } from '../src/db/connection.js';

describe('Phase 6 Integration Tests (Guest Identity, Gating, Upload-Local)', () => {
  let guestAccessToken: string;
  let guestRefreshToken: string;
  let guestId: string;

  let authAccessToken: string;
  let authUserId: string;

  const testDeviceId = crypto.randomUUID();

  before(async () => {
    // 1. Create an auth user via signup endpoint
    const randomSuffix = Math.floor(100 + Math.random() * 899);
    const signupRes = await app.request('/api/auth/signup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: `p6_user_${randomSuffix}`,
        email: `p6_test_${Date.now()}_${randomSuffix}@example.com`,
        password: 'Password123!',
        deviceId: crypto.randomUUID(),
        deviceName: 'Phase6 Test Runner',
      }),
    });
    assert.strictEqual(signupRes.status, 201);
    const signupBody = await signupRes.json();
    authAccessToken = signupBody.accessToken;
    authUserId = signupBody.user.id;
  });

  after(async () => {
    try {
      if (guestId) await pgClientAuth`DELETE FROM users WHERE id = ${guestId};`;
      if (authUserId) await pgClientAuth`DELETE FROM users WHERE id = ${authUserId};`;
    } catch {}
  });

  it('POST /api/auth/guest-setup creates guest identity with guest JWT role', async () => {
    const res = await app.request('/api/auth/guest-setup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        deviceId: testDeviceId,
        deviceName: 'Android Pixel',
        platform: 'android',
        deviceFingerprint: 'fp_p6_test_123',
      }),
    });

    assert.strictEqual(res.status, 200);
    const body = await res.json();
    assert.ok(body.accessToken);
    assert.ok(body.refreshToken);
    assert.ok(body.expiresIn);
    assert.strictEqual(body.isGuest, true);
    assert.ok(body.userId);

    guestAccessToken = body.accessToken;
    guestRefreshToken = body.refreshToken;
    guestId = body.userId;
  });

  it('POST /api/auth/refresh rotates guest refresh token successfully', async () => {
    const res = await app.request('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        refreshToken: guestRefreshToken,
      }),
    });

    assert.strictEqual(res.status, 200);
    const body = await res.json();
    assert.ok(body.accessToken);
    assert.ok(body.refreshToken);
    assert.notStrictEqual(body.refreshToken, guestRefreshToken); // Rotated!

    // Update tokens for subsequent tests
    guestAccessToken = body.accessToken;
    guestRefreshToken = body.refreshToken;
  });

  it('GET /api/auth/guest-status returns needsMerge=true for active guest', async () => {
    const res = await app.request('/api/auth/guest-status', {
      headers: {
        Authorization: `Bearer ${guestAccessToken}`,
      },
    });

    assert.strictEqual(res.status, 200);
    const body = await res.json();
    assert.strictEqual(body.isGuest, true);
    assert.strictEqual(body.needsMerge, true);
    assert.strictEqual(body.mergedInto, null);
  });

  it('rejects guest token on requireAuth endpoints with 403 guest_restricted', async () => {
    // 1. /api/chat/conversations
    const chatRes = await app.request('/api/chat/conversations', {
      headers: {
        Authorization: `Bearer ${guestAccessToken}`,
      },
    });
    assert.strictEqual(chatRes.status, 403);
    const chatBody = await chatRes.json();
    assert.strictEqual(chatBody.error, 'guest_restricted');

    // 2. /api/auth/merge-guest
    const mergeRes = await app.request('/api/auth/merge-guest', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${guestAccessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        guestId: crypto.randomUUID(),
      }),
    });
    assert.strictEqual(mergeRes.status, 403);
    const mergeBody = await mergeRes.json();
    assert.strictEqual(mergeBody.error, 'guest_restricted');

    // 3. /api/sync/upload-local
    const syncRes = await app.request('/api/sync/upload-local', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${guestAccessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        clientBatchId: crypto.randomUUID(),
        history: [],
      }),
    });
    assert.strictEqual(syncRes.status, 403);
    const syncBody = await syncRes.json();
    assert.strictEqual(syncBody.error, 'guest_restricted');
  });

  it('POST /api/sync/upload-local processes batch and is idempotent on repeat', async () => {
    const clientBatchId = crypto.randomUUID();
    const ops = [
      {
        type: 'create' as const,
        entityType: 'history' as const,
        entityId: crypto.randomUUID(),
        payload: {
          provider: 'jiosaavn',
          providerTrackId: 'upload_test_1',
          title: 'Uploaded Song',
        },
      },
    ];

    // First upload
    const res1 = await app.request('/api/sync/upload-local', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${authAccessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        clientBatchId,
        operations: ops,
      }),
    });

    assert.strictEqual(res1.status, 200);
    const body1 = await res1.json();
    assert.strictEqual(body1.status, 'ok');
    assert.strictEqual(body1.processed, 1);

    // Duplicate upload with same clientBatchId
    const res2 = await app.request('/api/sync/upload-local', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${authAccessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        clientBatchId,
        operations: ops,
      }),
    });

    assert.strictEqual(res2.status, 200);
    const body2 = await res2.json();
    assert.strictEqual(body2.status, 'duplicate');
    assert.strictEqual(body2.processed, 0);
  });
});

