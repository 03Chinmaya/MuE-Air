import { describe, it, afterEach } from 'node:test';
import assert from 'node:assert';
import { TrackMatcher, cleanTrackTitle } from '../src/services/trackMatcher.js';
import { decryptJioSaavnMediaUrl } from '../src/services/jiosaavnAudio.js';

describe('TrackMatcher & JioSaavn Audio Tests', () => {
  const originalFetch = global.fetch;

  afterEach(() => {
    global.fetch = originalFetch;
    TrackMatcher.setSearchSongsProvider(null);
  });

  it('1. cleanTrackTitle cleans video, quality, and remastered suffixes', () => {
    assert.strictEqual(
      cleanTrackTitle('Song Title (Official Video) [HD]'),
      'Song Title'
    );
    assert.strictEqual(
      cleanTrackTitle('Song Title - Remastered 2011'),
      'Song Title'
    );
    assert.strictEqual(
      cleanTrackTitle('Track Name (Lyric Video) [4K]'),
      'Track Name'
    );
  });

  it('2. decryptJioSaavnMediaUrl decrypts DES-ECB encrypted media URL and upgrades to 320kbps', () => {
    // DES key '38346591' encrypted "http://example.com/song_96.mp4"
    const sampleCiphertext = 'GcRAsZwDausnRPgB/r0X2Sk9KahQwFMd8VkqH2HzcP0=';
    const decrypted = decryptJioSaavnMediaUrl(sampleCiphertext);

    assert.ok(decrypted, 'Must return decrypted URL');
    assert.strictEqual(decrypted, 'http://example.com/song_320.mp4');

    // Safe fallbacks for empty / invalid input
    assert.strictEqual(decryptJioSaavnMediaUrl(''), null);
    assert.strictEqual(decryptJioSaavnMediaUrl(null), null);
    assert.strictEqual(decryptJioSaavnMediaUrl('invalid-non-base64'), null);
  });

  it('3. TrackMatcher.matchTrack resolves YouTube track to JioSaavn stream URL', async () => {
    const sampleCiphertext = 'GcRAsZwDausnRPgB/r0X2Sk9KahQwFMd8VkqH2HzcP0=';

    // Mock fetch for JioSaavn search API
    global.fetch = (async (input: any) => {
      const url = typeof input === 'string' ? input : input.url;
      if (url.includes('api.php') && url.includes('search.getResults')) {
        return {
          ok: true,
          text: async () =>
            JSON.stringify({
              results: [
                {
                  id: 'jio_match_456',
                  title: 'Chaleya',
                  primary_artists: 'Arijit Singh, Anirudh Ravichander',
                  album: 'Jawan',
                  image: 'https://c.saavncdn.com/123/Chaleya-150x150.jpg',
                  more_info: {
                    duration: '200',
                    encrypted_media_url: sampleCiphertext,
                  },
                },
              ],
            }),
        } as any;
      }
      return { ok: false, text: async () => '' } as any;
    }) as any;

    const ytmTrack = {
      id: 'ytm_chaleya_1',
      title: 'Chaleya (Official Music Video)',
      artists: ['Arijit Singh'],
      provider: 'youtube' as const,
      providerId: 'ytm_chaleya_1',
    };

    const result = await TrackMatcher.matchTrack(ytmTrack, 'jiosaavn');

    assert.ok(result, 'Match result must not be null');
    assert.strictEqual(result.matchedProviderId, 'jio_match_456');
    assert.strictEqual(result.track.id, 'jio_match_456');
    assert.strictEqual(result.track.provider, 'jiosaavn');
    assert.strictEqual(result.track.title, 'Chaleya');
    assert.strictEqual(result.track.uri, 'http://example.com/song_320.mp4');
    assert.strictEqual(result.track.artworkUrl, 'https://c.saavncdn.com/123/Chaleya-500x500.jpg');
    assert.strictEqual(result.track.durationMs, 200000);
  });

  it('4. TrackMatcher.matchTrack returns immediate match when track already on target provider', async () => {
    const jioTrack = {
      id: 'jio_direct_789',
      title: 'Kesariya',
      artists: ['Arijit Singh'],
      provider: 'jiosaavn' as const,
      providerId: 'jio_direct_789',
    };

    const result = await TrackMatcher.matchTrack(jioTrack, 'jiosaavn');
    assert.ok(result);
    assert.strictEqual(result.matchedProviderId, 'jio_direct_789');
    assert.strictEqual(result.track, jioTrack);
  });
});

