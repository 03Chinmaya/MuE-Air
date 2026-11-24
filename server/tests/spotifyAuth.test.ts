import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { createHmac } from 'node:crypto';
import { app } from '../src/app.js';
import { generateAccessToken } from '../src/services/authService.js';
import { createTestUser, cleanupTestUsers } from './helpers/rls.js';
import {
  SpotifyAuthService,
  signSpotifyOAuthState,
  verifySpotifyOAuthState,
} from '../src/services/spotifyAuthService.js';
import { SpotifyImportService } from '../src/services/spotifyImportService.js';

describe('Spotify OAuth & Private Playlist Tests (Phase 6.5.7)', () => {
  let testUser: any;
  let authToken: string;

  before(async () => {
    // Set dummy Spotify credentials for tests
    process.env.SPOTIFY_CLIENT_ID = 'test-spotify-client-id';
    process.env.SPOTIFY_CLIENT_SECRET = 'test-spotify-client-secret';
    process.env.SPOTIFY_REDIRECT_URI = 'http://localhost:4000/api/integrations/spotify/callback';
    if (!process.env.INTEGRATION_TOKEN_KEY || process.env.INTEGRATION_TOKEN_KEY.length !== 64) {
      process.env.INTEGRATION_TOKEN_KEY = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';
    }

    const ts = Date.now();
    testUser = await createTestUser(`sp_oauth_${ts}`, `sp_oauth_${ts}@test.com`, false);
    authToken = generateAccessToken(testUser.id, testUser.username);
  });

  after(async () => {
    if (testUser?.id) {
      try {
        await SpotifyAuthService.disconnect(testUser.id);
        await cleanupTestUsers([testUser.id]);
      } catch {}
    }
  });

  describe('1. PKCE State Signing and Verification', () => {
    it('signs and verifies state with embedded code verifier', () => {
      const codeVerifier = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';
      const state = signSpotifyOAuthState(testUser.id, codeVerifier);

      assert.ok(state);
      const verified = verifySpotifyOAuthState(state);
      assert.ok(verified);
      assert.strictEqual(verified.userId, testUser.id);
      assert.strictEqual(verified.codeVerifier, codeVerifier);
    });

    it('rejects tampered state string', () => {
      const state = signSpotifyOAuthState(testUser.id, 'some-verifier');
      const tampered = state.slice(0, -4) + 'abcd';
      const verified = verifySpotifyOAuthState(tampered);
      assert.strictEqual(verified, null);
    });

    it('rejects expired state', () => {
      const timestamp = (Date.now() - 15 * 60 * 1000).toString(); // 15 mins ago
      const secret = process.env.INTEGRATION_TOKEN_KEY || 'default-token-key-for-tests-only-0000000000000000';
      const payload = `${testUser.id}:${timestamp}:old-verifier`;
      const hmac = createHmac('sha256', secret).update(payload).digest('hex');
      const expiredState = `${payload}:${hmac}`;

      const verified = verifySpotifyOAuthState(expiredState);
      assert.strictEqual(verified, null);
    });
  });

  describe('2. Spotify OAuth Endpoints', () => {
    it('GET /api/integrations/spotify/login returns 401 without auth', async () => {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/integrations/spotify/login')
      );
      assert.strictEqual(res.status, 401);
    });

    it('GET /api/integrations/spotify/login returns authorization URL with PKCE params', async () => {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/integrations/spotify/login', {
          headers: { Authorization: `Bearer ${authToken}` },
        })
      );
      assert.strictEqual(res.status, 200);
      const data = await res.json();
      assert.ok(data.url);
      assert.ok(data.url.includes('accounts.spotify.com/authorize'));
      assert.ok(data.url.includes('code_challenge_method=S256'));
      assert.ok(data.url.includes('client_id=test-spotify-client-id'));
    });

    it('GET /api/integrations/spotify/status returns connected: false initially', async () => {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/integrations/spotify/status', {
          headers: { Authorization: `Bearer ${authToken}` },
        })
      );
      assert.strictEqual(res.status, 200);
      const data = await res.json();
      assert.strictEqual(data.connected, false);
      assert.strictEqual(data.spotifyUserId, null);
    });

    it('GET /api/integrations/spotify/callback handles code exchange', async () => {
      const state = signSpotifyOAuthState(testUser.id, 'mock-code-verifier');

      // Mock fetch for token exchange and me profile
      const origFetch = globalThis.fetch;
      globalThis.fetch = async (url: any, init?: any) => {
        const urlStr = String(url);
        if (urlStr.includes('accounts.spotify.com/api/token')) {
          return new Response(
            JSON.stringify({
              access_token: 'mock-access-token',
              token_type: 'Bearer',
              scope: 'playlist-read-private',
              expires_in: 3600,
              refresh_token: 'mock-refresh-token',
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } }
          );
        }
        if (urlStr.includes('api.spotify.com/v1/me')) {
          return new Response(
            JSON.stringify({
              id: 'spotify-user-12345',
              email: 'spotify_user@example.com',
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } }
          );
        }
        return origFetch(url, init);
      };

      const res = await app.fetch(
        new Request(`http://localhost:4000/api/integrations/spotify/callback?code=mock-code&state=${encodeURIComponent(state)}`, {
          headers: { Accept: 'application/json' },
        })
      );

      globalThis.fetch = origFetch;

      assert.strictEqual(res.status, 200);
      const data = await res.json();
      assert.strictEqual(data.status, 'connected');
      assert.strictEqual(data.spotifyUserId, 'spotify-user-12345');

      // Verify status now reports connected: true
      const statusRes = await app.fetch(
        new Request('http://localhost:4000/api/integrations/spotify/status', {
          headers: { Authorization: `Bearer ${authToken}` },
        })
      );
      const statusData = await statusRes.json();
      assert.strictEqual(statusData.connected, true);
      if (statusData.spotifyUserId !== null) {
        assert.strictEqual(statusData.spotifyUserId, 'spotify-user-12345');
      }
    });

    it('private playlist import falls back to OAuth when connected', async () => {
      // Mock scrapePublicPlaylist throwing 403
      const origScrape = SpotifyImportService.scrapePublicPlaylist;
      SpotifyImportService.scrapePublicPlaylist = async () => {
        const err = new Error('Private playlist');
        (err as any).code = 'spotify_auth_required';
        (err as any).status = 403;
        throw err;
      };

      // Mock fetchPrivatePlaylist
      const origFetchPrivate = SpotifyAuthService.fetchPrivatePlaylist;
      let privateFetched = false;
      SpotifyAuthService.fetchPrivatePlaylist = async (userId: string, playlistId: string) => {
        privateFetched = true;
        return {
          title: 'Private OAuth Playlist',
          trackCount: 1,
          tracks: [
            {
              title: 'Private Song',
              artists: ['Private Artist'],
              durationMs: 240000,
              externalId: 'spotify-priv-1',
            },
          ],
        };
      };

      const imported = await SpotifyImportService.importPlaylist(
        'https://open.spotify.com/playlist/private_pl_123',
        testUser.id
      );

      SpotifyImportService.scrapePublicPlaylist = origScrape;
      SpotifyAuthService.fetchPrivatePlaylist = origFetchPrivate;

      assert.strictEqual(privateFetched, true);
      assert.strictEqual(imported.title, 'Private OAuth Playlist');
      assert.strictEqual(imported.tracks.length, 1);
    });

    it('DELETE /api/integrations/spotify/disconnect removes tokens', async () => {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/integrations/spotify/disconnect', {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${authToken}` },
        })
      );
      assert.strictEqual(res.status, 200);
      const data = await res.json();
      assert.strictEqual(data.status, 'disconnected');

      const statusRes = await app.fetch(
        new Request('http://localhost:4000/api/integrations/spotify/status', {
          headers: { Authorization: `Bearer ${authToken}` },
        })
      );
      const statusData = await statusRes.json();
      assert.strictEqual(statusData.connected, false);
      assert.strictEqual(statusData.spotifyUserId, null);
    });
  });
});
