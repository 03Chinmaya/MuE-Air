import { describe, it } from 'node:test';
import assert from 'node:assert';
import { randomBytes, createHmac } from 'node:crypto';
import { encryptToken, decryptToken } from '../src/services/tokenCrypto.js';
import { signOAuthState, verifyOAuthState } from '../src/routes/youtubeRoutes.js';
import { YtMusicSidecarClient } from '../src/services/ytMusicClient.js';
import { app } from '../src/app.js';

describe('YouTube Import & OAuth Security Tests', () => {
  // Ensure INTEGRATION_TOKEN_KEY is set in test env
  if (!process.env.INTEGRATION_TOKEN_KEY || process.env.INTEGRATION_TOKEN_KEY.length !== 64) {
    process.env.INTEGRATION_TOKEN_KEY = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';
  }

  describe('1. AES-256-GCM Token Encryption', () => {
    it('encrypts and decrypts roundtrip correctly', () => {
      const plaintext = '1//04random_refresh_token_string_here_987654321';
      const encrypted = encryptToken(plaintext);

      assert.ok(encrypted.startsWith('v1:'), 'Must use v1: format prefix');
      const parts = encrypted.split(':');
      assert.strictEqual(parts.length, 4, 'Must have 4 parts (version:iv:tag:ciphertext)');

      const decrypted = decryptToken(encrypted);
      assert.strictEqual(decrypted, plaintext, 'Decrypted token must match original plaintext');
    });

    it('rejects tampered ciphertext with an error', () => {
      const plaintext = 'secret_token_12345';
      const encrypted = encryptToken(plaintext);
      const parts = encrypted.split(':');

      // Tamper ciphertext
      const tamperedCiphertext = Buffer.from(parts[3], 'base64');
      tamperedCiphertext[0] ^= 1; // flip one bit
      parts[3] = tamperedCiphertext.toString('base64');

      const tamperedString = parts.join(':');
      assert.throws(() => {
        decryptToken(tamperedString);
      }, /Unsupported state or unable to authenticate data|Invalid auth tag/);
    });

    it('rejects tampered auth tag with an error', () => {
      const plaintext = 'secret_token_12345';
      const encrypted = encryptToken(plaintext);
      const parts = encrypted.split(':');

      // Tamper auth tag
      const tamperedTag = Buffer.from(parts[2], 'base64');
      tamperedTag[0] ^= 1;
      parts[2] = tamperedTag.toString('base64');

      const tamperedString = parts.join(':');
      assert.throws(() => {
        decryptToken(tamperedString);
      }, /Unsupported state or unable to authenticate data|Invalid auth tag/);
    });

    it('rejects decryption with a wrong key', () => {
      const originalKey = process.env.INTEGRATION_TOKEN_KEY;
      const plaintext = 'secret_token_test';
      const encrypted = encryptToken(plaintext);

      try {
        // Change to another valid 64-hex key
        process.env.INTEGRATION_TOKEN_KEY = 'fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210';
        assert.throws(() => {
          decryptToken(encrypted);
        }, /Unsupported state or unable to authenticate data/);
      } finally {
        process.env.INTEGRATION_TOKEN_KEY = originalKey;
      }
    });

    it('generates unique IVs across multiple encryptions', () => {
      const ivSet = new Set<string>();
      const plaintext = 'repeat_test_token';
      for (let i = 0; i < 100; i++) {
        const encrypted = encryptToken(plaintext);
        const iv = encrypted.split(':')[1];
        assert.strictEqual(ivSet.has(iv), false, 'IV must never collide');
        ivSet.add(iv);
      }
      assert.strictEqual(ivSet.size, 100);
    });
  });

  describe('2. OAuth State HMAC Signing & Verification', () => {
    it('signs and verifies state for valid userId', () => {
      const userId = '11111111-2222-3333-4444-555555555555';
      const state = signOAuthState(userId);
      const verified = verifyOAuthState(state);
      assert.strictEqual(verified, userId);
    });

    it('rejects tampered signature with null', () => {
      const userId = '11111111-2222-3333-4444-555555555555';
      const state = signOAuthState(userId);
      const parts = state.split(':');
      parts[2] = parts[2].substring(0, parts[2].length - 2) + '00';
      const tampered = parts.join(':');

      assert.strictEqual(verifyOAuthState(tampered), null);
    });

    it('rejects expired state (>10 minutes)', () => {
      const userId = '11111111-2222-3333-4444-555555555555';
      const oldTimestamp = Date.now() - 11 * 60 * 1000;
      const secret = process.env.INTEGRATION_TOKEN_KEY || 'default-token-key';
      const hmac = createHmac('sha256', secret).update(`${userId}:${oldTimestamp}`).digest('hex');
      const expiredState = `${userId}:${oldTimestamp}:${hmac}`;

      assert.strictEqual(verifyOAuthState(expiredState), null);
    });
  });

  describe('3. Sidecar Client Graceful Degradation', () => {
    it('health() returns false gracefully when sidecar is unreachable', async () => {
      // Point to an unused local port
      const client = new YtMusicSidecarClient('http://127.0.0.1:59999');
      const healthy = await client.health();
      assert.strictEqual(healthy, false, 'Unreachable sidecar must report false within timeout');
    });

    it('GET /api/youtube/integrations/youtube/playlists returns 401 when unauthenticated', async () => {
      const res = await app.fetch(new Request('http://localhost:4000/api/youtube/integrations/youtube/playlists'));
      assert.strictEqual(res.status, 401);
    });

    it('POST /api/youtube/integrations/youtube/playlists/PL123/import returns 401 when unauthenticated', async () => {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/youtube/integrations/youtube/playlists/PL123/import', {
          method: 'POST',
        })
      );
      assert.strictEqual(res.status, 401);
    });

    it('POST /api/youtube/integrations/youtube/import-url returns 401 when unauthenticated', async () => {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/youtube/integrations/youtube/import-url', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ url: 'https://music.youtube.com/playlist?list=PL123' }),
        })
      );
      assert.strictEqual(res.status, 401);
    });
  });

  describe('4. Token Leakage Prevention', () => {
    it('POST /api/youtube/integrations/youtube/exchange rejects invalid state without echoing token', async () => {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/youtube/integrations/youtube/exchange', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            code: 'test_code_123',
            state: 'invalid_tampered_state',
          }),
        })
      );
      assert.strictEqual(res.status, 403);
      const text = await res.text();
      assert.strictEqual(text.includes('refresh_token'), false);
      assert.strictEqual(text.includes('client_secret'), false);
    });
  });
});
