import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';
import { generateAccessToken } from '../src/services/authService.js';
import { createTestUser, cleanupTestUsers, asUser } from './helpers/rls.js';

describe('Spotify Import Route Tests (P5.4.11)', () => {
  let testUser: any;
  let authToken: string;

  before(async () => {
    const ts = Date.now();
    testUser = await createTestUser(`sp_usr_${ts}`, `sp_usr_${ts}@test.com`, false);
    authToken = generateAccessToken(testUser.id, testUser.username);
  });

  after(async () => {
    if (testUser?.id) {
      try {
        await cleanupTestUsers([testUser.id]);
      } catch {}
    }
  });

  it('POST /api/integrations/spotify/import returns 401 when unauthenticated', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/integrations/spotify/import', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          title: 'Test Playlist',
          tracks: [{ title: 'Track 1', artists: ['Artist 1'] }],
        }),
      })
    );
    assert.strictEqual(res.status, 401);
  });

  it('POST /api/integrations/spotify/import returns 400 when tracks array is empty', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/integrations/spotify/import', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${authToken}`,
        },
        body: JSON.stringify({
          title: 'Empty Playlist',
          tracks: [],
        }),
      })
    );
    assert.strictEqual(res.status, 400);
    const body = await res.json();
    assert.strictEqual(body.error, 'empty_playlist');
  });

  it('POST /api/integrations/spotify/import persists tracks and returns 202 processing', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/integrations/spotify/import', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${authToken}`,
        },
        body: JSON.stringify({
          externalId: 'sp-test-pl-123',
          title: 'My Favorite Spotify Tracks',
          description: 'A collection of classics',
          thumbnailUrl: 'https://example.com/cover.jpg',
          tracks: [
            {
              externalId: 'sp-track-1',
              title: 'Starboy',
              artists: ['The Weeknd', 'Daft Punk'],
              album: 'Starboy',
              durationMs: 230000,
            },
            {
              externalId: 'sp-track-2',
              title: 'Blinding Lights',
              artists: ['The Weeknd'],
              album: 'After Hours',
              durationMs: 200000,
            },
          ],
        }),
      })
    );

    assert.strictEqual(res.status, 202);
    const body = await res.json();
    assert.strictEqual(body.status, 'processing');
    assert.strictEqual(body.trackCount, 2);
    assert.strictEqual(body.title, 'My Favorite Spotify Tracks');
    assert.ok(body.importedPlaylistId);
  });

  it('1. Re-import conflict: updates playlist and replaces existing tracks', async () => {
    const externalId = 'sp-reimport-test-1';

    // First import
    const res1 = await app.fetch(
      new Request('http://localhost:4000/api/integrations/spotify/import', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${authToken}`,
        },
        body: JSON.stringify({
          externalId,
          title: 'Initial Title',
          description: 'Initial Description',
          tracks: [
            { externalId: 'tr-1', title: 'Track 1', artists: ['Artist 1'] },
            { externalId: 'tr-2', title: 'Track 2', artists: ['Artist 2'] },
          ],
        }),
      })
    );
    assert.strictEqual(res1.status, 202);
    const body1 = await res1.json();
    const playlistId = body1.importedPlaylistId;

    // Second import with same externalId, different title, and 1 new track
    const res2 = await app.fetch(
      new Request('http://localhost:4000/api/integrations/spotify/import', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${authToken}`,
        },
        body: JSON.stringify({
          externalId,
          title: 'Updated Title',
          description: 'Updated Description',
          tracks: [
            { externalId: 'tr-3', title: 'Track 3', artists: ['Artist 3'] },
          ],
        }),
      })
    );
    assert.strictEqual(res2.status, 202);
    const body2 = await res2.json();
    assert.strictEqual(body2.importedPlaylistId, playlistId);
    assert.strictEqual(body2.title, 'Updated Title');
    assert.strictEqual(body2.trackCount, 1);

    // Verify DB tracks were deleted and replaced
    const tracks = await asUser(testUser.id, async (sql) => {
      return sql<{ id: string; title: string }[]>`
        SELECT id, title FROM imported_playlist_tracks WHERE imported_playlist_id = ${playlistId}
      `;
    });
    assert.strictEqual(tracks.length, 1);
    assert.strictEqual(tracks[0].title, 'Track 3');
  });

  it('2. Missing metadata fallbacks: applies default title, externalId, and null descriptions', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/integrations/spotify/import', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${authToken}`,
        },
        body: JSON.stringify({
          tracks: [
            { title: 'Fallback Track' },
          ],
        }),
      })
    );
    assert.strictEqual(res.status, 202);
    const body = await res.json();
    assert.strictEqual(body.title, 'Imported Spotify Playlist');
    assert.ok(body.importedPlaylistId);

    const [saved] = await asUser(testUser.id, async (sql) => {
      return sql<{ external_id: string; description: string | null; thumbnail_url: string | null }[]>`
        SELECT external_id, description, thumbnail_url FROM imported_playlists WHERE id = ${body.importedPlaylistId}
      `;
    });
    assert.ok(saved.external_id.startsWith('spotify-'));
    assert.strictEqual(saved.description, null);
    assert.strictEqual(saved.thumbnail_url, null);
  });

  it('3. Artist variations & track metadata fallbacks: handles string artist, empty artist array, artworkUrl', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/integrations/spotify/import', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${authToken}`,
        },
        body: JSON.stringify({
          title: 'Artist Variations Playlist',
          tracks: [
            {
              // String artist case
              title: 'Solo Song',
              artists: 'Single String Artist',
              artworkUrl: 'https://example.com/art.jpg',
            },
            {
              // Empty array artist case (should fallback to Unknown Artist)
              title: '',
              artists: [],
              durationMs: 'not-a-number' as any,
            },
          ],
        }),
      })
    );
    assert.strictEqual(res.status, 202);
    const body = await res.json();
    const tracks = await asUser(testUser.id, async (sql) => {
      return sql<{ position: number; title: string; artists: string[]; duration_ms: number | null; thumbnail_url: string | null }[]>`
        SELECT position, title, artists, duration_ms, thumbnail_url FROM imported_playlist_tracks
        WHERE imported_playlist_id = ${body.importedPlaylistId}
        ORDER BY position ASC
      `;
    });

    assert.strictEqual(tracks.length, 2);
    // Track 1
    assert.deepStrictEqual(tracks[0].artists, ['Single String Artist']);
    assert.strictEqual(tracks[0].thumbnail_url, 'https://example.com/art.jpg');
    assert.strictEqual(tracks[0].title, 'Solo Song');
    // Track 2
    assert.deepStrictEqual(tracks[1].artists, ['Unknown Artist']);
    assert.strictEqual(tracks[1].title, 'Untitled Track');
    assert.strictEqual(tracks[1].duration_ms, null);
  });

  it('4. Malformed JSON body returns 400 empty_playlist safely', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/integrations/spotify/import', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${authToken}`,
        },
        body: 'invalid-non-json-body-{{{',
      })
    );
    assert.strictEqual(res.status, 400);
    const body = await res.json();
    assert.strictEqual(body.error, 'empty_playlist');
  });
});
