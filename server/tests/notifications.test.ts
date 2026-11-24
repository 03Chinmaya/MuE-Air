import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';
import { createTestUser, cleanupTestUsers, sqlAuthRole } from './helpers/rls.js';
import { generateAccessToken } from '../src/services/authService.js';

describe('Notifications & UnifiedPush Endpoint Registration Tests', () => {
  let user: { id: string; username: string; email: string };
  let token: string;

  before(async () => {
    const ts = Date.now();
    user = await createTestUser(`notif_${ts}`, `notif_${ts}@test.com`, false);
    token = generateAccessToken(user.id, user.username);
  });

  after(async () => {
    try {
      await cleanupTestUsers([user.id]);
    } catch {}
  });

  it('POST /api/notifications/register returns 401 without auth token', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/notifications/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pushEndpoint: 'https://ntfy.sh/test-endpoint-123' }),
      })
    );
    assert.strictEqual(res.status, 401);
  });

  it('POST /api/notifications/register rejects empty token with 400', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/notifications/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({}),
      })
    );
    assert.strictEqual(res.status, 400);
  });

  it('POST /api/notifications/register successfully registers push endpoint', async () => {
    const testPushToken = 'https://ntfy.sh/sample_endpoint_abc_123';

    const res = await app.fetch(
      new Request('http://localhost:4000/api/notifications/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          pushToken: testPushToken,
          platform: 'android',
        }),
      })
    );

    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.strictEqual(body.success, true);
    assert.strictEqual(body.message, 'Push endpoint registered');

    // Verify token exists in database for this user
    const rows = await sqlAuthRole`
      SELECT push_token, platform FROM user_devices WHERE user_id = ${user.id}
    `;
    assert.ok(rows.length > 0, 'user_devices record must be created');
    assert.strictEqual(rows[0].push_token, testPushToken);
    assert.strictEqual(rows[0].platform, 'android');
  });

  it('DELETE /api/notifications/register removes push endpoint', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/notifications/register', {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })
    );

    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.strictEqual(body.success, true);
    assert.strictEqual(body.message, 'Push endpoint removed');

    // Verify token was cleared
    const rows = await sqlAuthRole`
      SELECT push_token FROM user_devices WHERE user_id = ${user.id}
    `;
    assert.ok(rows.length > 0);
    assert.strictEqual(rows[0].push_token, null, 'push_token should be cleared to null');
  });

  it('GET /api/notifications retrieves notification list for authenticated user', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/notifications', {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })
    );
    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.ok(Array.isArray(body.items));
  });
});

