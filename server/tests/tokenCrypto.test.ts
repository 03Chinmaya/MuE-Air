import { describe, it } from 'node:test';
import assert from 'node:assert';
import { encryptToken, decryptToken } from '../src/services/tokenCrypto.js';

describe('Token Crypto Service Edge Cases & Error Handling', () => {
  const originalKey = process.env.INTEGRATION_TOKEN_KEY;
  const validKey = originalKey && originalKey.length === 64
    ? originalKey
    : '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';

  it('1. Missing INTEGRATION_TOKEN_KEY throws error', () => {
    try {
      delete process.env.INTEGRATION_TOKEN_KEY;
      assert.throws(() => {
        encryptToken('test_plaintext');
      }, /INTEGRATION_TOKEN_KEY environment variable is not set/);
    } finally {
      process.env.INTEGRATION_TOKEN_KEY = validKey;
    }
  });

  it('2. Invalid INTEGRATION_TOKEN_KEY length throws error', () => {
    try {
      process.env.INTEGRATION_TOKEN_KEY = '0123456789abcdef0123456789abcdef'; // 32 chars instead of 64
      assert.throws(() => {
        encryptToken('test_plaintext');
      }, /INTEGRATION_TOKEN_KEY must be exactly 64 hex characters/);
    } finally {
      process.env.INTEGRATION_TOKEN_KEY = validKey;
    }
  });

  it('3. Empty plaintext or encrypted string inputs throw error', () => {
    process.env.INTEGRATION_TOKEN_KEY = validKey;

    assert.throws(() => {
      encryptToken('');
    }, /Cannot encrypt empty plaintext/);

    assert.throws(() => {
      decryptToken('');
    }, /Cannot decrypt empty string/);
  });

  it('4. Malformed token format (<4 parts) throws error', () => {
    process.env.INTEGRATION_TOKEN_KEY = validKey;

    assert.throws(() => {
      decryptToken('v1:iv:tag'); // 3 parts instead of 4
    }, /Invalid encrypted token format: expected version:iv:authTag:ciphertext/);

    assert.throws(() => {
      decryptToken('v1:iv:tag:cipher:extra'); // 5 parts
    }, /Invalid encrypted token format: expected version:iv:authTag:ciphertext/);
  });

  it('5. Unsupported version prefix throws error', () => {
    process.env.INTEGRATION_TOKEN_KEY = validKey;

    assert.throws(() => {
      decryptToken('v2:aXY=:dGFn:Y2lwaGVy');
    }, /Unsupported token encryption version: v2/);
  });

  it('6. Invalid IV and Auth Tag byte lengths throw error', () => {
    process.env.INTEGRATION_TOKEN_KEY = validKey;

    // IV with only 8 bytes instead of 12 (base64 of 8 bytes)
    const shortIvB64 = Buffer.alloc(8).toString('base64');
    const validTagB64 = Buffer.alloc(16).toString('base64');
    const dummyCipherB64 = Buffer.from('data').toString('base64');

    assert.throws(() => {
      decryptToken(`v1:${shortIvB64}:${validTagB64}:${dummyCipherB64}`);
    }, /Invalid IV length: expected 12 bytes/);

    // Auth tag with only 8 bytes instead of 16
    const validIvB64 = Buffer.alloc(12).toString('base64');
    const shortTagB64 = Buffer.alloc(8).toString('base64');

    assert.throws(() => {
      decryptToken(`v1:${validIvB64}:${shortTagB64}:${dummyCipherB64}`);
    }, /Invalid auth tag length: expected 16 bytes/);
  });

  it('7. Multi-key rotation: encrypts with primary key (v2) and decrypts both v1 and v2', () => {
    const keyV1 = '1111111111111111111111111111111111111111111111111111111111111111';
    const keyV2 = '2222222222222222222222222222222222222222222222222222222222222222';

    // 1. Encrypt with v1 only
    process.env.INTEGRATION_TOKEN_KEY = `v1:${keyV1}`;
    const tokenV1 = encryptToken('secret_v1_payload');
    assert.ok(tokenV1.startsWith('v1:'));
    assert.strictEqual(decryptToken(tokenV1), 'secret_v1_payload');

    // 2. Rotate to v2 (primary) while keeping v1 for reading old tokens
    process.env.INTEGRATION_TOKEN_KEY = `v2:${keyV2},v1:${keyV1}`;

    // New encryption uses v2
    const tokenV2 = encryptToken('secret_v2_payload');
    assert.ok(tokenV2.startsWith('v2:'));
    assert.strictEqual(decryptToken(tokenV2), 'secret_v2_payload');

    // Old token encrypted with v1 can still be decrypted!
    assert.strictEqual(decryptToken(tokenV1), 'secret_v1_payload');

    // Restore
    process.env.INTEGRATION_TOKEN_KEY = validKey;
  });

  it('8. Legacy unversioned token format decrypts using fallback key', () => {
    process.env.INTEGRATION_TOKEN_KEY = validKey;
    const token = encryptToken('legacy_test_secret');
    // Strip the version prefix: "v1:iv:tag:cipher" -> "iv:tag:cipher"
    const unversioned = token.slice(3);
    assert.strictEqual(unversioned.split(':').length, 3);
    assert.strictEqual(decryptToken(unversioned), 'legacy_test_secret');
  });
});

