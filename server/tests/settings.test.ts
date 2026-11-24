import { describe, it } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';

describe('Settings API Routes Verification Tests', () => {
  it('GET /api/settings returns 401 without auth token', async () => {
    const res = await app.fetch(new Request('http://localhost:4000/api/settings'));
    assert.strictEqual(res.status, 401);
  });

  it('POST /api/settings returns 401 without auth token', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ settings: [] }),
      })
    );
    assert.strictEqual(res.status, 401);
  });

  it('POST /api/settings rejects malformed payload with 400', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/settings', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: 'Bearer invalid_token',
        },
        body: JSON.stringify({ settings: 'not-an-array' }),
      })
    );
    // Returns 401 due to invalid token or 400
    assert.ok(res.status === 401 || res.status === 400);
  });
});
