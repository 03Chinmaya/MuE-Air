import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';
import { generateAccessToken } from '../src/services/authService.js';
import { createTestUser, cleanupTestUsers } from './helpers/rls.js';
import { detectPlaylistSource } from '../src/services/playlistSourceDetector.js';
import { SpotifyImportService } from '../src/services/spotifyImportService.js';
import * as ytMusicClientModule from '../src/services/ytMusicClient.js';

describe('Playlist Import Routing Tests (Phase 6.5.6)', () => {
  let testUser: any;
  let authToken: string;

  before(async () => {
    const ts = Date.now();
    testUser = await createTestUser(`imp_usr_${ts}`, `imp_usr_${ts}@test.com`, false);
    authToken = generateAccessToken(testUser.id, testUser.username);
  });

  after(async () => {
    if (testUser?.id) {
      try {
        await cleanupTestUsers([testUser.id]);
      } catch {}
    }
  });

  describe('1. detectPlaylistSource Unit Logic', () => {
    it('detects Spotify open playlist URL', () => {
      const res = detectPlaylistSource('https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=12345');
      assert.strictEqual(res.source, 'spotify');
      assert.strictEqual(res.id, '37i9dQZF1DXcBWIGoYBM5M');
    });

    it('detects Spotify short link', () => {
      const res = detectPlaylistSource('https://spotify.link/AbCdEf123');
      assert.strictEqual(res.source, 'spotify');
      assert.strictEqual(res.id, 'AbCdEf123');
    });

    it('detects Spotify URI', () => {
      const res = detectPlaylistSource('spotify:playlist:37i9dQZF1DXcBWIGoYBM5M');
      assert.strictEqual(res.source, 'spotify');
      assert.strictEqual(res.id, '37i9dQZF1DXcBWIGoYBM5M');
    });

    it('detects YouTube Music playlist URL', () => {
      const res = detectPlaylistSource('https://music.youtube.com/playlist?list=PLrAlrXb222k3_rY_6P0V5');
      assert.strictEqual(res.source, 'ytm');
      assert.strictEqual(res.id, 'PLrAlrXb222k3_rY_6P0V5');
    });

    it('detects standard YouTube playlist URL', () => {
      const res = detectPlaylistSource('https://www.youtube.com/playlist?list=RDCLAK5uy_kmPRjHDECIcuVwnKsx2');
      assert.strictEqual(res.source, 'ytm');
      assert.strictEqual(res.id, 'RDCLAK5uy_kmPRjHDECIcuVwnKsx2');
    });

    it('returns unsupported for non-matching URLs', () => {
      const res1 = detectPlaylistSource('https://soundcloud.com/artist/sets/album');
      assert.strictEqual(res1.source, 'unsupported');

      const res2 = detectPlaylistSource('https://example.com/song/123');
      assert.strictEqual(res2.source, 'unsupported');

      const res3 = detectPlaylistSource('');
      assert.strictEqual(res3.source, 'unsupported');
    });
  });

  describe('2. POST /api/import/playlist Endpoint Routing', () => {
    it('returns 401 when unauthenticated', async () => {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/import/playlist', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ url: 'https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M' }),
        })
      );
      assert.strictEqual(res.status, 401);
    });

    it('returns 400 when url is missing or empty', async () => {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/import/playlist', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${authToken}`,
          },
          body: JSON.stringify({ url: '' }),
        })
      );
      assert.strictEqual(res.status, 400);
    });

    it('returns 400 unsupported_url and calls neither Spotify nor YTM sidecar', async () => {
      let sidecarCalled = false;
      let spotifyScrapeCalled = false;

      const origScrape = SpotifyImportService.scrapePublicPlaylist;
      SpotifyImportService.scrapePublicPlaylist = async () => {
        spotifyScrapeCalled = true;
        throw new Error('Should not be called');
      };

      const res = await app.fetch(
        new Request('http://localhost:4000/api/import/playlist', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${authToken}`,
          },
          body: JSON.stringify({ url: 'https://soundcloud.com/artist/sets/unsupported' }),
        })
      );

      SpotifyImportService.scrapePublicPlaylist = origScrape;

      assert.strictEqual(res.status, 400);
      const body = await res.json();
      assert.strictEqual(body.error, 'unsupported_url');
      assert.strictEqual(sidecarCalled, false);
      assert.strictEqual(spotifyScrapeCalled, false);
    });

    it('routes Spotify URL exclusively to SpotifyImportService (never sidecar)', async () => {
      let spotifyScrapeCalled = false;
      let sidecarCalled = false;

      const origScrape = SpotifyImportService.scrapePublicPlaylist;
      SpotifyImportService.scrapePublicPlaylist = async (url: string) => {
        spotifyScrapeCalled = true;
        return {
          title: 'Mock Spotify Playlist',
          description: 'A mock playlist for unit tests',
          thumbnailUrl: 'https://example.com/art.jpg',
          trackCount: 1,
          tracks: [
            {
              title: 'Mock Song',
              artists: ['Mock Artist'],
              durationMs: 180000,
              thumbnailUrl: 'https://example.com/art.jpg',
              externalId: 'spotify-track-1',
            },
          ],
        };
      };

      const res = await app.fetch(
        new Request('http://localhost:4000/api/import/playlist', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${authToken}`,
          },
          body: JSON.stringify({ url: 'https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M' }),
        })
      );

      SpotifyImportService.scrapePublicPlaylist = origScrape;

      assert.strictEqual(res.status, 202);
      const body = await res.json();
      assert.strictEqual(body.status, 'processing');
      assert.strictEqual(body.provider, 'spotify');
      assert.strictEqual(spotifyScrapeCalled, true);
      assert.strictEqual(sidecarCalled, false);
    });

    it('routes YTM URL exclusively to YTM sidecar (never Spotify)', async () => {
      let spotifyScrapeCalled = false;
      let sidecarCalled = false;

      const origScrape = SpotifyImportService.scrapePublicPlaylist;
      SpotifyImportService.scrapePublicPlaylist = async () => {
        spotifyScrapeCalled = true;
        throw new Error('Spotify scraper must NOT be called for YTM url');
      };

      const origHealth = ytMusicClientModule.YtMusicSidecarClient.prototype.health;
      const origGetPublicPlaylist = ytMusicClientModule.YtMusicSidecarClient.prototype.getPublicPlaylist;

      ytMusicClientModule.YtMusicSidecarClient.prototype.health = async () => true;
      ytMusicClientModule.YtMusicSidecarClient.prototype.getPublicPlaylist = async (url: string) => {
        sidecarCalled = true;
        return {
          title: 'Mock YTM Playlist',
          tracks: [
            {
              videoId: 'vid-123',
              title: 'Mock YTM Song',
              artists: ['Mock YTM Artist'],
              durationMs: 200000,
            },
          ],
          thumbnail: 'https://example.com/ytm.jpg',
        };
      };

      const res = await app.fetch(
        new Request('http://localhost:4000/api/import/playlist', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${authToken}`,
          },
          body: JSON.stringify({ url: 'https://music.youtube.com/playlist?list=PLmock12345' }),
        })
      );

      SpotifyImportService.scrapePublicPlaylist = origScrape;
      ytMusicClientModule.YtMusicSidecarClient.prototype.health = origHealth;
      ytMusicClientModule.YtMusicSidecarClient.prototype.getPublicPlaylist = origGetPublicPlaylist;

      assert.strictEqual(res.status, 202);
      const body = await res.json();
      assert.strictEqual(body.status, 'processing');
      assert.strictEqual(body.provider, 'youtube');
      assert.strictEqual(sidecarCalled, true);
      assert.strictEqual(spotifyScrapeCalled, false);
    });

    it('returns 403 spotify_auth_required when private and no sp_dc header', async () => {
      const origImport = SpotifyImportService.importPlaylist;
      SpotifyImportService.importPlaylist = async () => {
        const err: any = new Error('Auth required');
        err.code = 'spotify_auth_required';
        err.status = 403;
        throw err;
      };

      const res = await app.fetch(
        new Request('http://localhost:4000/api/import/playlist', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${authToken}`,
          },
          body: JSON.stringify({ url: 'https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M' }),
        })
      );

      SpotifyImportService.importPlaylist = origImport;

      assert.strictEqual(res.status, 403);
      const body = await res.json();
      assert.strictEqual(body.error, 'spotify_auth_required');
    });

    it('routes to sidecar when X-Spotify-Sp-Dc header provided and handles invalid cookie', async () => {
      const origImport = SpotifyImportService.importPlaylist;
      SpotifyImportService.importPlaylist = async () => {
        const err: any = new Error('Auth required');
        err.code = 'spotify_auth_required';
        err.status = 403;
        throw err;
      };

      const origFetch = globalThis.fetch;
      globalThis.fetch = async (input: any) => {
        return new Response(JSON.stringify({ detail: 'invalid_spotify_cookie' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        });
      };

      const res = await app.fetch(
        new Request('http://localhost:4000/api/import/playlist', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${authToken}`,
            'X-Spotify-Sp-Dc': 'mock_cookie_123',
          },
          body: JSON.stringify({ url: 'https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M' }),
        })
      );

      SpotifyImportService.importPlaylist = origImport;
      globalThis.fetch = origFetch;

      assert.strictEqual(res.status, 400);
      const body = await res.json();
      assert.strictEqual(body.error, 'invalid_spotify_cookie');
    });

    it('routes to sidecar when X-Spotify-Sp-Dc header provided and handles success', async () => {
      const origImport = SpotifyImportService.importPlaylist;
      SpotifyImportService.importPlaylist = async () => {
        const err: any = new Error('Auth required');
        err.code = 'spotify_auth_required';
        err.status = 403;
        throw err;
      };

      const origFetch = globalThis.fetch;
      globalThis.fetch = async (input: any) => {
        return new Response(
          JSON.stringify({
            title: 'Sidecar Spotify Playlist',
            description: 'Imported via sp_dc',
            thumbnailUrl: 'https://example.com/sidecar.jpg',
            tracks: [
              {
                externalId: 'sp-sidecar-1',
                title: 'Sidecar Song',
                artists: ['Sidecar Artist'],
                durationMs: 190000,
                thumbnailUrl: 'https://example.com/sidecar.jpg',
              },
            ],
          }),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }
        );
      };

      const res = await app.fetch(
        new Request('http://localhost:4000/api/import/playlist', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${authToken}`,
            'X-Spotify-Sp-Dc': 'valid_cookie_123',
          },
          body: JSON.stringify({ url: 'https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M' }),
        })
      );

      SpotifyImportService.importPlaylist = origImport;
      globalThis.fetch = origFetch;

      assert.strictEqual(res.status, 202);
      const body = await res.json();
      assert.strictEqual(body.status, 'processing');
      assert.strictEqual(body.provider, 'spotify');
      assert.strictEqual(body.title, 'Sidecar Spotify Playlist');
    });
  });
});
