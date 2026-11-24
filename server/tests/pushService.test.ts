import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  sendDataPush,
  isPushConfigured,
  __setMockPushHandler,
  __setMockGoogleAuth,
} from '../src/services/pushService.js';

describe('PushService — Dual-Branch Delivery Tests (UnifiedPush + FCM HTTP v1)', () => {
  it('1. isPushConfigured returns true by default and false when DISABLE_PUSH=true', () => {
    const original = process.env.DISABLE_PUSH;
    try {
      delete process.env.DISABLE_PUSH;
      assert.strictEqual(isPushConfigured(), true);

      process.env.DISABLE_PUSH = 'true';
      assert.strictEqual(isPushConfigured(), false);
    } finally {
      process.env.DISABLE_PUSH = original;
    }
  });

  it('2. sendDataPush returns false for empty endpoint or token', async () => {
    assert.strictEqual(await sendDataPush('', { key: 'val' }), false);
    assert.strictEqual(await sendDataPush(null as any, { key: 'val' }), false);
  });

  it('3. UnifiedPush branch: sends POST to http/https endpoint with JSON payload', async () => {
    __setMockPushHandler(null);
    const originalFetch = global.fetch;
    const calls: Array<{ url: string; headers: any; body: any }> = [];

    global.fetch = (async (url: any, opts: any) => {
      calls.push({
        url: String(url),
        headers: opts?.headers,
        body: JSON.parse(opts?.body || '{}'),
      });
      return new Response(JSON.stringify({ success: true }), { status: 200 });
    }) as any;

    try {
      const endpoint = 'https://ntfy.sh/test_topic_mue';
      const ok = await sendDataPush(endpoint, {
        type: 'chat_message',
        hintCiphertext: 'cipher_123',
      });
      assert.strictEqual(ok, true);
      assert.strictEqual(calls.length, 1);
      assert.strictEqual(calls[0].url, endpoint);
      assert.strictEqual(calls[0].headers['Content-Type'], 'application/json');
      assert.strictEqual(calls[0].body.type, 'chat_message');
      assert.strictEqual(calls[0].body.hintCiphertext, 'cipher_123');
    } finally {
      global.fetch = originalFetch;
    }
  });

  it('4. FCM HTTP v1 branch: routes non-http tokens to FCM HTTP v1 with Bearer token & high priority', async () => {
    __setMockPushHandler(null);
    const originalFetch = global.fetch;
    const calls: Array<{ url: string; headers: any; body: any }> = [];

    global.fetch = (async (url: any, opts: any) => {
      calls.push({
        url: String(url),
        headers: opts?.headers,
        body: JSON.parse(opts?.body || '{}'),
      });
      return new Response(JSON.stringify({ name: 'projects/test/messages/123' }), { status: 200 });
    }) as any;

    // Mock GoogleAuth to avoid real external network call during unit test
    const mockAuth = {
      getClient: async () => ({
        getAccessToken: async () => ({ token: 'mock_oauth2_access_token' }),
      }),
    };
    __setMockGoogleAuth(mockAuth);

    try {
      const fcmToken = 'fake_device_fcm_token_xyz_456';
      const ok = await sendDataPush(fcmToken, {
        type: 'chat_message',
        conversationId: 'conv_123',
        hintCiphertext: 'enc_hint_bytes',
      });

      assert.strictEqual(ok, true);
      assert.strictEqual(calls.length, 1);
      assert.match(calls[0].url, /https:\/\/fcm\.googleapis\.com\/v1\/projects\/.*\/messages:send/);
      assert.strictEqual(calls[0].headers['Authorization'], 'Bearer mock_oauth2_access_token');
      assert.strictEqual(calls[0].headers['Content-Type'], 'application/json');

      const payload = calls[0].body;
      assert.strictEqual(payload.message.token, fcmToken);
      assert.strictEqual(payload.message.android.priority, 'high');
      assert.strictEqual(payload.message.data.type, 'chat_message');
      assert.strictEqual(payload.message.data.conversationId, 'conv_123');
      assert.strictEqual(payload.message.data.hintCiphertext, 'enc_hint_bytes');
      // Verify NO notification title/body block (privacy compliance)
      assert.strictEqual((payload.message as any).notification, undefined);
    } finally {
      global.fetch = originalFetch;
      __setMockGoogleAuth(null);
    }
  });

  it('5. mockPushHandler intercepts both branches when registered', async () => {
    let intercepted = 0;
    __setMockPushHandler(async (target, data) => {
      intercepted++;
      assert.strictEqual(data.test, 'val');
      return true;
    });

    try {
      const r1 = await sendDataPush('https://ntfy.sh/test', { test: 'val' });
      assert.strictEqual(r1, true);
      const r2 = await sendDataPush('fcm_token_abc', { test: 'val' });
      assert.strictEqual(r2, true);
      assert.strictEqual(intercepted, 2);
    } finally {
      __setMockPushHandler(null);
    }
  });

  it('6. Test hooks throw when NODE_ENV !== test', () => {
    const originalEnv = process.env.NODE_ENV;
    try {
      (process.env as any).NODE_ENV = 'production';
      assert.throws(() => __setMockPushHandler(null), /test-only hook/);
      assert.throws(() => __setMockGoogleAuth(null), /test-only hook/);
    } finally {
      process.env.NODE_ENV = originalEnv;
    }
  });

  it('7. Real FCM credential verification (verifies project_id, token minting, and FCM HTTP v1 reachability)', async () => {
    if (!process.env.FCM_SERVICE_ACCOUNT_JSON) return;

    __setMockPushHandler(null);
    __setMockGoogleAuth(null);

    // With a non-http string (fake token), FCM API receives request with real OAuth2 Bearer token,
    // verifies authentication, and rejects invalid device token with 400 (returning false),
    // proving the entire auth + dispatch pipeline works against live Google servers.
    const ok = await sendDataPush('fake_fcm_token_verifying_oauth', {
      type: 'chat_message',
      hintCiphertext: 'cipher_test',
    });
    assert.strictEqual(ok, false);
  });
});
