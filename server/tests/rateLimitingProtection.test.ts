import { test, describe, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { app } from '../src/app.js';
import { resetInMemoryRateLimits } from '../src/lib/rateLimit.js';

describe('Phase 8.1: Rate Limiting & Proxy Protection Tests', () => {
  beforeEach(() => {
    resetInMemoryRateLimits();
  });

  test('31 rapid calls to /api/proxy/image triggers 429 rate_limit_exceeded on the 31st request', async () => {
    const clientIp = '198.51.100.42';

    // First 30 requests should be processed (or return 400 for missing url, not 429)
    for (let i = 1; i <= 30; i++) {
      const res = await app.request('/api/proxy/image', {
        headers: {
          'x-forwarded-for': clientIp,
        },
      });
      assert.notEqual(res.status, 429, `Request ${i} should not be rate limited`);
      assert.equal(res.headers.get('x-ratelimit-remaining'), String(30 - i));
    }

    // 31st request must trigger 429
    const limitedRes = await app.request('/api/proxy/image', {
      headers: {
        'x-forwarded-for': clientIp,
      },
    });

    assert.equal(limitedRes.status, 429, '31st request must return 429');
    const body = await limitedRes.json() as any;
    assert.equal(body.error, 'rate_limit_exceeded');
    assert.equal(limitedRes.headers.get('x-ratelimit-remaining'), '0');
    assert.ok(Number(limitedRes.headers.get('retry-after')) > 0);
  });

  test('different client IPs have independent in-memory rate limit windows', async () => {
    const ipA = '198.51.100.50';
    const ipB = '198.51.100.51';

    // Exhaust IP A
    for (let i = 1; i <= 30; i++) {
      await app.request('/api/proxy/image', {
        headers: { 'x-forwarded-for': ipA },
      });
    }

    const resA = await app.request('/api/proxy/image', {
      headers: { 'x-forwarded-for': ipA },
    });
    assert.equal(resA.status, 429, 'IP A should be limited');

    // IP B should still be allowed
    const resB = await app.request('/api/proxy/image', {
      headers: { 'x-forwarded-for': ipB },
    });
    assert.notEqual(resB.status, 429, 'IP B should not be limited');
  });

  test('unauthenticated call to /api/chat/* returns 401', async () => {
    const res = await app.request('/api/chat/conversations');
    assert.equal(res.status, 401);
  });
});
