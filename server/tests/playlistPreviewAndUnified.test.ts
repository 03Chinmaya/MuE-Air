import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';
import { generateAccessToken } from '../src/services/authService.js';
import { createTestUser, cleanupTestUsers } from './helpers/rls.js';
import { SpotifyImportService } from '../src/services/spotifyImportService.js';
import * as ytMusicClientModule from '../src/services/ytMusicClient.js';
import { userPlaylists, userPlaylistTracks, importedPlaylists } from '../src/db/schema.js';
import { pgClient } from '../src/db/connection.js';
import { eq } from 'drizzle-orm';
import { drizzle } from 'drizzle-orm/postgres-js';
import * as schema from '../src/db/schema.js';

describe('Phase 6.6.1: Playlist Preview & Unified Library Tests', () => {
  let testUser: any;
  let authToken: string;

  before(async () => {
    const ts = Date.now();
    testUser = await createTestUser(`prev_usr_${ts}`, `prev_usr_${ts}@test.com`, false);
    authToken = generateAccessToken(testUser.id, testUser.username);
  });

  after(async () => {
    if (testUser?.id) {
      try {
        await cleanupTestUsers([testUser.id]);
      } catch {}
    }
  });

  describe('1. POST /api/import/preview', () => {
    it('returns 401 when unauthenticated', async () => {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/import/preview', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ url: 'https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M' }),
        })
      );
      assert.strictEqual(res.status, 401);
    });

    it('returns preview metadata and does NOT write to database', async () => {
      // Mock SpotifyImportService
      const origScrape = SpotifyImportService.importPlaylist;
      (SpotifyImportService as any).importPlaylist = async () => ({
        title: 'Mocked Preview Playlist',
        description: 'Preview test description',
        thumbnailUrl: 'https://preview.image/art.jpg',
        trackCount: 2,
        tracks: [
          {
            title: 'Preview Song 1',
            artists: ['Artist A'],
            album: 'Album A',
            durationMs: 180000,
            thumbnailUrl: 'https://preview.image/t1.jpg',
            externalId: 'sp-track-1',
          },
          {
            title: 'Preview Song 2',
            artists: ['Artist B', 'Artist C'],
            album: 'Album B',
            durationMs: 210000,
            thumbnailUrl: 'https://preview.image/t2.jpg',
            externalId: 'sp-track-2',
          },
        ],
      });

      try {
        const previewUrl = 'https://open.spotify.com/playlist/preview12345';
        const res = await app.fetch(
          new Request('http://localhost:4000/api/import/preview', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${authToken}`,
            },
            body: JSON.stringify({ url: previewUrl }),
          })
        );

        assert.strictEqual(res.status, 200);
        const data = (await res.json()) as any;
        assert.strictEqual(data.title, 'Mocked Preview Playlist');
        assert.strictEqual(data.artworkUrl, 'https://preview.image/art.jpg');
        assert.strictEqual(data.trackCount, 2);
        assert.strictEqual(data.source, 'spotify');
        assert.strictEqual(data.tracks.length, 2);
        assert.strictEqual(data.tracks[0].title, 'Preview Song 1');
        assert.strictEqual(data.tracks[0].artist, 'Artist A');
        assert.strictEqual(data.tracks[0].providerTrackId, 'sp-track-1');

        // CRITICAL CHECK: Verify NO rows in imported_playlists for this user/externalId
        let foundRows: any[] = [];
        await pgClient.begin(async (tx) => {
          (tx as any).options = (pgClient as any).options;
          const txDb = drizzle(tx as any, { schema });
          await tx`SELECT set_config('request.jwt.claim.sub', ${testUser.id}, true)`;
          foundRows = await txDb
            .select()
            .from(importedPlaylists)
            .where(eq(importedPlaylists.userId, testUser.id));
        });
        assert.strictEqual(foundRows.length, 0, 'Database MUST NOT contain any imported playlist rows from preview');
      } finally {
        (SpotifyImportService as any).importPlaylist = origScrape;
      }
    });
  });

  describe('2. GET /api/playlists (Unified Endpoint)', () => {
    it('returns 401 when unauthenticated', async () => {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/playlists', {
          method: 'GET',
        })
      );
      assert.strictEqual(res.status, 401);
    });

    it('returns unified list with both user-created and imported playlists', async () => {
      // Seed 1 user playlist with 2 tracks and 1 imported playlist
      let userPlId = '';
      let importedPlId = '';

      await pgClient.begin(async (tx) => {
        (tx as any).options = (pgClient as any).options;
        const txDb = drizzle(tx as any, { schema });
        await tx`SELECT set_config('request.jwt.claim.sub', ${testUser.id}, true)`;

        const [uPl] = await txDb
          .insert(userPlaylists)
          .values({
            userId: testUser.id,
            name: 'My Personal Jam',
            artworkUrl: 'https://art.jam/cover.png',
            createdAt: new Date(Date.now() - 5000),
            updatedAt: new Date(Date.now() - 5000),
          })
          .returning();
        userPlId = uPl.id;

        await txDb.insert(userPlaylistTracks).values([
          {
            playlistId: uPl.id,
            position: 0,
            provider: 'jiosaavn',
            providerTrackId: 'track-1',
            title: 'Track One',
            artistName: 'Singer One',
          },
          {
            playlistId: uPl.id,
            position: 1,
            provider: 'jiosaavn',
            providerTrackId: 'track-2',
            title: 'Track Two',
            artistName: 'Singer Two',
          },
        ]);

        const [iPl] = await txDb
          .insert(importedPlaylists)
          .values({
            userId: testUser.id,
            provider: 'spotify',
            externalId: 'ext-spotify-999',
            title: 'Imported Hits',
            thumbnailUrl: 'https://thumb.spotify/hits.jpg',
            trackCount: 15,
            matchedCount: 10,
            createdAt: new Date(),
            lastSyncedAt: new Date(),
          })
          .returning();
        importedPlId = iPl.id;
      });

      const res = await app.fetch(
        new Request('http://localhost:4000/api/playlists', {
          method: 'GET',
          headers: {
            Authorization: `Bearer ${authToken}`,
          },
        })
      );

      assert.strictEqual(res.status, 200);
      const body = (await res.json()) as any;
      assert.ok(Array.isArray(body.playlists));
      assert.strictEqual(body.playlists.length, 2);

      const userPl = body.playlists.find((p: any) => p.id === userPlId);
      assert.ok(userPl, 'User playlist must be in the list');
      assert.strictEqual(userPl.name, 'My Personal Jam');
      assert.strictEqual(userPl.trackCount, 2);
      assert.strictEqual(userPl.isImported, false);
      assert.strictEqual(userPl.source, 'local');

      const impPl = body.playlists.find((p: any) => p.id === importedPlId);
      assert.ok(impPl, 'Imported playlist must be in the list');
      assert.strictEqual(impPl.name, 'Imported Hits');
      assert.strictEqual(impPl.trackCount, 15);
      assert.strictEqual(impPl.isImported, true);
      assert.strictEqual(impPl.source, 'spotify');

      // Verify sorting: importedPl has later timestamp, so it should be first
      assert.strictEqual(body.playlists[0].id, importedPlId);
      assert.strictEqual(body.playlists[1].id, userPlId);
    });
  });
});
