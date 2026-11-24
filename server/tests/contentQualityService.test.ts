import { describe, it } from 'node:test';
import assert from 'node:assert';
import { ContentQualityService } from '../src/services/ContentQualityService.js';

describe('ContentQualityService Baseline Suite', () => {
  it('1. AI keywords rejected: suno, udio, ai cover, rvc, ai generated', () => {
    const aiKeywords = ['suno', 'udio', 'ai cover', 'rvc', 'ai generated'];
    for (const kw of aiKeywords) {
      const res = ContentQualityService.classify({
        title: `Kesariya ${kw}`,
        artist: 'Various Artists',
        duration: 200,
        videoType: 'ATV',
      });
      assert.strictEqual(res.contentType, 'ai_generated', `Expected "${kw}" to be classified as ai_generated`);
      assert.strictEqual(res.score, 0);
    }
  });

  it('2. Counterfeit regexes rejected: KING -, Dhurandhar, Ramayana', () => {
    const fakeTracks = [
      { title: 'KING - Maan Meri Jaan Official', artist: 'Fake Channel' },
      { title: 'Dhurandhar Theme Song', artist: 'Unknown' },
      { title: 'Ramayana Official Teaser Track', artist: 'Namit Malhotra' },
    ];
    for (const t of fakeTracks) {
      const res = ContentQualityService.classify({ ...t, duration: 200 });
      assert.strictEqual(res.contentType, 'ai_generated', `Expected "${t.title}" to be rejected as ai_generated`);
      assert.strictEqual(res.score, 0);
    }
  });

  it('3. Compilation keywords rejected: jukebox, mashup, 1 hour, top 50', () => {
    const compilations = [
      'Arijit Singh Best Audio Jukebox',
      'Bollywood Romantic Mashup 2026',
      'Deep Focus Lofi 1 Hour Beats',
      'Billboard Top 50 Hits',
    ];
    for (const comp of compilations) {
      const res = ContentQualityService.classify({
        title: comp,
        artist: 'Various',
        duration: 300,
      });
      assert.strictEqual(res.contentType, 'creator_long_mix', `Expected "${comp}" to be rejected as creator_long_mix`);
      assert.strictEqual(res.score, 0);
    }
  });

  it('4. Duration rejected: > 900s (15 min)', () => {
    const res = ContentQualityService.classify({
      title: 'Valid Song Title',
      artist: 'Arijit Singh',
      duration: 901,
      videoType: 'ATV',
    });
    assert.strictEqual(res.contentType, 'creator_long_mix');
    assert.strictEqual(res.score, 0);
  });

  it('5. Duration rejected: < 30s', () => {
    const res = ContentQualityService.classify({
      title: 'Kesariya Ringtone Clip',
      artist: 'Pritam',
      duration: 25,
      videoType: 'ATV',
    });
    assert.strictEqual(res.contentType, 'rejected');
    assert.strictEqual(res.score, 0);
  });

  it('6. Official ATV accepted (score 100)', () => {
    const res = ContentQualityService.classify({
      title: 'Kesariya',
      artist: 'Arijit Singh',
      duration: 268,
      videoType: 'ATV',
    });
    assert.strictEqual(res.contentType, 'official_art_track');
    assert.strictEqual(res.score, 100);
  });

  it('7. OMV accepted (score 80)', () => {
    const res = ContentQualityService.classify({
      title: 'Kesariya Official Music Video',
      artist: 'Arijit Singh',
      duration: 280,
      videoType: 'OMV',
    });
    assert.strictEqual(res.contentType, 'official_music_video');
    assert.strictEqual(res.score, 80);
  });

  it('8. UGC rejected unless explicit query ("cover", "remix", "live")', () => {
    const ugcTrack = {
      title: 'Kesariya Acoustic Rendition',
      artist: 'Independent Singer',
      duration: 240,
      videoType: 'UGC',
    };
    // Non-explicit query
    const resStandard = ContentQualityService.classify(ugcTrack, false, 'Kesariya');
    assert.strictEqual(resStandard.contentType, 'creator_upload');
    assert.strictEqual(resStandard.score, 10);

    // Explicit query ("cover")
    const resExplicit = ContentQualityService.classify(ugcTrack, false, 'Kesariya cover');
    assert.notStrictEqual(resExplicit.contentType, 'creator_upload');
    assert.strictEqual(resExplicit.score, 100);
  });

  it('9. isOlderContent rejects year < currentYear-1', () => {
    const currentYear = new Date().getFullYear();
    const oldTrack = { title: 'Kal Ho Naa Ho', year: currentYear - 3 };
    const newTrack = { title: 'Recent Release', year: currentYear };
    assert.strictEqual(ContentQualityService.isOlderContent(oldTrack), true);
    assert.strictEqual(ContentQualityService.isOlderContent(newTrack), false);
  });

  it('10. Empty track rejected', () => {
    const resEmptyObj = ContentQualityService.classify({});
    assert.strictEqual(resEmptyObj.contentType, 'rejected');
    assert.strictEqual(resEmptyObj.score, 0);

    const resNull = ContentQualityService.classify(null as any);
    assert.strictEqual(resNull.contentType, 'rejected');
    assert.strictEqual(resNull.score, 0);

    const resEmptyTitle = ContentQualityService.classify({ title: '   ' });
    assert.strictEqual(resEmptyTitle.contentType, 'rejected');
    assert.strictEqual(resEmptyTitle.score, 0);
  });
});
