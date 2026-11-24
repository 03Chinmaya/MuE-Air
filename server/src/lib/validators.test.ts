import { describe, it } from 'node:test';
import assert from 'node:assert';
import { Hono } from 'hono';
import { zValidator } from '@hono/zod-validator';
import {
  validationHook,
  guestSetupSchema,
  SendMessageSchema,
  presignAttachmentSchema,
  signupSchema,
  loginSchema,
  refreshSchema,
  uploadLocalSchema,
} from './validators.js';

describe('Centralized Validators & zValidator Integration', () => {
  const app = new Hono();

  // Test routes
  app.post(
    '/api/test/guest-setup',
    zValidator('json', guestSetupSchema, validationHook('invalid_request')),
    (c) => c.json({ ok: true, data: c.req.valid('json') })
  );

  app.post(
    '/api/test/messages',
    zValidator('json', SendMessageSchema, validationHook('invalid_payload')),
    (c) => c.json({ ok: true, data: c.req.valid('json') })
  );

  app.post(
    '/api/test/presign',
    zValidator('json', presignAttachmentSchema, validationHook('invalid_request')),
    (c) => c.json({ ok: true, data: c.req.valid('json') })
  );

  it('1. valid payload passes', async () => {
    const res = await app.request('/api/test/guest-setup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        deviceId: 'device-123',
        deviceName: 'Pixel 8',
        platform: 'android',
      }),
    });
    assert.strictEqual(res.status, 200);
    const body = await res.json();
    assert.strictEqual(body.ok, true);
    assert.strictEqual(body.data.deviceId, 'device-123');
  });

  it('2. missing required fields reject 400', async () => {
    const res = await app.request('/api/test/guest-setup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        deviceName: 'Pixel 8',
      }),
    });
    assert.strictEqual(res.status, 400);
    const body = await res.json();
    assert.ok(body.error);
    assert.ok(body.details);
  });

  it('3. oversize payload rejects 400', async () => {
    const oversizeCiphertext = 'a'.repeat(12_289);
    const res = await app.request('/api/test/messages', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contentCiphertext: oversizeCiphertext,
        contentNonce: 'nonce-123',
      }),
    });
    assert.strictEqual(res.status, 400);
    const body = await res.json();
    assert.strictEqual(body.error, 'Payload exceeds 12KB limit');

    // Also test attachment oversize > 25MB
    const resPresign = await app.request('/api/test/presign', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        mimeType: 'image/png',
        fileSize: 30 * 1024 * 1024,
      }),
    });
    assert.strictEqual(resPresign.status, 400);
  });

  it('4. bad mimeType rejects 400', async () => {
    const res = await app.request('/api/test/presign', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        mimeType: '',
        fileSize: 1024,
      }),
    });
    assert.strictEqual(res.status, 400);
    const body = await res.json();
    assert.ok(body.error.includes('mimeType'));
  });

  it('5. malformed JSON returns 400, not 500', async () => {
    const res = await app.request('/api/test/guest-setup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{"deviceId": "device-123", invalid_json',
    });
    assert.strictEqual(res.status, 400);
  });
});
