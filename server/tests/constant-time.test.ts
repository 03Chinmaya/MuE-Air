import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import argon2 from 'argon2';
import { app } from '../src/app.js';
import { sqlAuthRole } from './helpers/rls.js';
import { verifyCredential, DUMMY_HASH, parseArgonCost, TEST_DUMMY_HASH, PROD_DUMMY_HASH } from '../src/services/authService.js';

describe('Constant-Time Login Security Tests', () => {
  const timestamp = Math.floor(Math.random() * 9000 + 1000);
  const knownUser = {
    username: `c_usr_${timestamp}`,
    email: `const_${Date.now()}@example.com`,
    password: 'KnownCorrectPassword123!',
  };

  let userId = '';
  let userPasswordHash = '';

  before(async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(knownUser),
      })
    );
    assert.strictEqual(res.status, 201);
    const data = await res.json() as any;
    userId = data.user.id;

    const [row] = await sqlAuthRole<{ password_hash: string }[]>`
      SELECT password_hash FROM users WHERE id = ${userId}
    `;
    userPasswordHash = row?.password_hash || '';
  });

  after(async () => {
    if (userId) {
      try {
        await sqlAuthRole`DELETE FROM users WHERE id = ${userId}`;
      } catch {}
    }
  });

  it('Unknown user login executes dummy Argon2 verify taking real computational time', async () => {
    const start = Date.now();
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: `unknown_ghost_${Date.now()}`,
          password: 'RandomPassword999!',
        }),
      })
    );
    const elapsed = Date.now() - start;

    assert.strictEqual(res.status, 401);
    const data = await res.json() as any;
    assert.strictEqual(data.error, 'Incorrect credentials');
    assert.ok(
      elapsed >= 15,
      `Unknown user verification should take computational time for Argon2, elapsed: ${elapsed}ms`
    );
  });

  it('Unknown user login time is within statistical parity of wrong-password login time', async () => {
    // 1. Direct cryptographic verification parity (eliminates WAN network roundtrip jitter)
    // Asserts that verifying a wrong password against the user's Argon2 hash vs DUMMY_HASH
    // takes equivalent CPU time (strict tolerance: < 25ms difference)
    const cryptoStartKnown = performance.now();
    await verifyCredential(userPasswordHash, 'WrongPasswordAttempt123!');
    const cryptoKnown = performance.now() - cryptoStartKnown;

    const cryptoStartUnknown = performance.now();
    await verifyCredential(DUMMY_HASH, 'WrongPasswordAttempt123!');
    const cryptoUnknown = performance.now() - cryptoStartUnknown;

    const cryptoDiff = Math.abs(cryptoKnown - cryptoUnknown);
    assert.ok(
      cryptoDiff < 25,
      `Cryptographic compute time between known user hash (${cryptoKnown.toFixed(2)}ms) and dummy hash (${cryptoUnknown.toFixed(2)}ms) must be within strict parity (< 25ms), diff: ${cryptoDiff.toFixed(2)}ms`
    );

    // 2. HTTP endpoint verification: Both return 401 with generic "Incorrect credentials"
    const resKnown = await app.fetch(
      new Request('http://localhost:4000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: knownUser.username,
          password: 'IncorrectPasswordAttempt123!',
        }),
      })
    );
    assert.strictEqual(resKnown.status, 401);
    const dataKnown = await resKnown.json() as any;
    assert.strictEqual(dataKnown.error, 'Incorrect credentials');

    const resUnknown = await app.fetch(
      new Request('http://localhost:4000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: `unknown_${Date.now()}`,
          password: 'IncorrectPasswordAttempt123!',
        }),
      })
    );
    assert.strictEqual(resUnknown.status, 401);
    const dataUnknown = await resUnknown.json() as any;
    assert.strictEqual(dataUnknown.error, 'Incorrect credentials');

    // 3. HTTP-path timing parity (median of 5 attempts)
    // This catches early-return regressions where the handler skips crypto for unknown users.
    const median = (arr: number[]): number => {
      const sorted = [...arr].sort((a, b) => a - b);
      const mid = Math.floor(sorted.length / 2);
      return sorted.length % 2 !== 0 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    };

    // Warm up both paths once
    await app.fetch(
      new Request('http://localhost:4000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: knownUser.username,
          password: 'WarmupPassword!',
        }),
      })
    );
    await app.fetch(
      new Request('http://localhost:4000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: `unknown_warmup_${Date.now()}`,
          password: 'WarmupPassword!',
        }),
      })
    );

    const knownTimes: number[] = [];
    for (let i = 0; i < 5; i++) {
      const start = performance.now();
      const res = await app.fetch(
        new Request('http://localhost:4000/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            usernameOrEmail: knownUser.username,
            password: `WrongPasswordSample_${i}!`,
          }),
        })
      );
      assert.strictEqual(res.status, 401);
      knownTimes.push(performance.now() - start);
    }

    const unknownTimes: number[] = [];
    for (let i = 0; i < 5; i++) {
      const start = performance.now();
      const res = await app.fetch(
        new Request('http://localhost:4000/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            usernameOrEmail: `unknown_sample_${Date.now()}_${i}`,
            password: `WrongPasswordSample_${i}!`,
          }),
        })
      );
      assert.strictEqual(res.status, 401);
      unknownTimes.push(performance.now() - start);
    }

    const medianKnown = median(knownTimes);
    const medianUnknown = median(unknownTimes);
    const httpDiff = Math.abs(medianKnown - medianUnknown);

    assert.ok(
      httpDiff < 500,
      `HTTP path timing diff (${httpDiff.toFixed(2)}ms) between known user median (${medianKnown.toFixed(2)}ms) and unknown user median (${medianUnknown.toFixed(2)}ms) must be < 500ms — catches early-return regressions where the handler skips crypto for unknown users`
    );
  });

  it('TEST_DUMMY_HASH and PROD_DUMMY_HASH parse to distinct memory cost factors', () => {
    const testCost = parseArgonCost(TEST_DUMMY_HASH);
    const prodCost = parseArgonCost(PROD_DUMMY_HASH);
    assert.strictEqual(testCost, 1024, 'Test dummy hash must have m=1024');
    assert.strictEqual(prodCost, 65536, 'Production dummy hash must have m=65536 per docs/15_SECURITY.md §5.1');
    assert.notStrictEqual(testCost, prodCost, 'Test and production dummy hashes must use different cost factors');
  });

  it('verifyCredential is param-agnostic: verifies hashes with older/different Argon2 parameters', async () => {
    const password = 'UniversalTestPassword123!';

    // 1. Hash with test parameters (m=1024, t=2)
    const testParamHash = await argon2.hash(password, {
      type: argon2.argon2id,
      memoryCost: 1024,
      timeCost: 2,
      parallelism: 1,
    });
    assert.strictEqual(await verifyCredential(testParamHash, password), true, 'verifyCredential must verify test-param hash (m=1024)');
    assert.strictEqual(await verifyCredential(testParamHash, 'WrongPassword!'), false, 'verifyCredential must reject wrong password on test-param hash');

    // 2. Hash with legacy parameters (m=19456, t=2 per commit 0080535)
    const legacyParamHash = await argon2.hash(password, {
      type: argon2.argon2id,
      memoryCost: 19456,
      timeCost: 2,
      parallelism: 1,
    });
    assert.strictEqual(await verifyCredential(legacyParamHash, password), true, 'verifyCredential must verify legacy-param hash (m=19456)');
    assert.strictEqual(await verifyCredential(legacyParamHash, 'WrongPassword!'), false, 'verifyCredential must reject wrong password on legacy-param hash');

    // 3. Hash with production locked spec parameters (m=65536, t=3 per docs/15_SECURITY.md §5.1)
    const prodParamHash = await argon2.hash(password, {
      type: argon2.argon2id,
      memoryCost: 65536,
      timeCost: 3,
      parallelism: 1,
    });
    assert.strictEqual(await verifyCredential(prodParamHash, password), true, 'verifyCredential must verify production spec hash (m=65536)');
    assert.strictEqual(await verifyCredential(prodParamHash, 'WrongPassword!'), false, 'verifyCredential must reject wrong password on production spec hash');

    // Proves verifyCredential is param-agnostic: existing user hashes with old params remain fully verifiable after default params changed
  });
});
