import { describe, it } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';
import { checkRateLimit, clearRateLimit } from '../src/routes/authRoutes.js';

describe('Auth Rate Limiting Security Tests', () => {
  it('Allows 5 authentication attempts, 6th attempt returns 429 with Retry-After header', async () => {
    const testIp = `192.168.100.${Math.floor(Math.random() * 200) + 10}`;
    clearRateLimit(testIp);

    // First 5 attempts should pass rate limiter and hit application logic (401 Incorrect credentials)
    for (let i = 1; i <= 5; i++) {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/auth/login', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'x-forwarded-for': testIp,
          },
          body: JSON.stringify({
            usernameOrEmail: `ratelimit_target_${i}`,
            password: 'BadPassword!',
          }),
        })
      );
      assert.strictEqual(
        res.status,
        401,
        `Attempt ${i} should be allowed through rate limiter and return 401 Incorrect credentials`
      );
    }

    // 6th attempt must be blocked with HTTP 429
    const blockedRes = await app.fetch(
      new Request('http://localhost:4000/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-forwarded-for': testIp,
        },
        body: JSON.stringify({
          usernameOrEmail: 'ratelimit_target_6',
          password: 'BadPassword!',
        }),
      })
    );

    assert.strictEqual(blockedRes.status, 429, '6th attempt must return 429 Too Many Requests');
    const retryAfter = blockedRes.headers.get('Retry-After');
    assert.ok(retryAfter, 'Retry-After header must be present on 429 response');
    const retryVal = parseInt(retryAfter, 10);
    assert.ok(retryVal > 0 && retryVal <= 60, `Retry-After value must be within 1-60s, got ${retryVal}`);
  });

  it('In-memory fallback works when Redis is unavailable or forced', async () => {
    const fallbackIp = `10.0.99.${Math.floor(Math.random() * 200) + 10}`;
    clearRateLimit(fallbackIp);

    // Call checkRateLimit with forceInMemory = true
    for (let i = 1; i <= 5; i++) {
      const result = await checkRateLimit(fallbackIp, 5, 60, true);
      assert.strictEqual(result.allowed, true, `In-memory attempt ${i} should be allowed`);
    }

    // 6th in-memory attempt must be disallowed
    const blocked = await checkRateLimit(fallbackIp, 5, 60, true);
    assert.strictEqual(blocked.allowed, false, '6th in-memory attempt must be disallowed');
    assert.ok(blocked.retryAfter > 0, 'In-memory fallback must calculate retryAfter');
  });
});
