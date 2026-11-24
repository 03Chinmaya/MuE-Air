/**
 * Acceptance Gate Script for Phase P5.5 Commit 2
 * Measures real genre coverage on 140 live JioSaavn catalog tracks
 * using the REAL production mapper (mapJioSaavnSongToTrack) and
 * REAL genre resolution engine (resolveGenreSet).
 *
 * Requirements:
 * 1. Aggregate coverage must be >= 60%.
 * 2. Per-language breakdown printed for Hindi, Tamil, Punjabi, English, and Korean.
 */

const fs = require('fs');
const Module = require('module');
const babel = require('@babel/core');

// Mock react-native for Node environment execution
const origRequire = Module.prototype.require;
Module.prototype.require = function (path) {
  if (path === 'react-native') {
    return { Platform: { OS: 'android' } };
  }
  return origRequire.apply(this, arguments);
};

// Register TypeScript on-the-fly compiler using babel-preset-expo
require.extensions['.ts'] = function (module, filename) {
  const content = fs.readFileSync(filename, 'utf8');
  const compiled = babel.transformSync(content, {
    filename,
    presets: ['babel-preset-expo'],
  });
  module._compile(compiled.code, filename);
};

const { resolveGenreSet } = require('../src/providers/jiosaavn/artistGenreIndex');
const { mapJioSaavnSongToTrack } = require('../src/providers/jiosaavn/mappers/JioSaavnMappers');

async function run() {
  const queries = [
    { q: 'bollywood', lang: 'hindi' },
    { q: 'hindi hits', lang: 'hindi' },
    { q: 'arijit singh', lang: 'hindi' },
    { q: 'tamil hits', lang: 'tamil' },
    { q: 'punjabi hits', lang: 'punjabi' },
    { q: 'english pop', lang: 'english' },
    { q: 'korean pop', lang: 'korean' },
  ];

  console.log('Fetching 140 live catalog tracks across 7 queries...');
  const resultsByQuery = await Promise.all(
    queries.map(({ q, lang }) =>
      fetch(
        'https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=20&p=1&q=' +
          encodeURIComponent(q),
        { headers: { 'User-Agent': 'Mozilla/5.0', Referer: 'https://www.jiosaavn.com/' } }
      )
        .then((r) => r.json())
        .then((d) => ({ q, lang, songs: d.results || [] }))
    )
  );

  let totalTracks = 0;
  let totalWithGenre = 0;
  let totalStrictPrimary = 0;

  const perLang = {};

  for (const { q, lang, songs } of resultsByQuery) {
    if (!perLang[lang]) perLang[lang] = { total: 0, withGenre: 0, strictPrimary: 0 };

    for (const song of songs) {
      totalTracks++;
      perLang[lang].total++;

      // 1. Run real production mapper
      const track = mapJioSaavnSongToTrack(song);

      // 2. Run real production genre resolution
      const genres = resolveGenreSet(track);

      // 3. Check if primary artist alone would have resolved
      const primaryArtistTrack = { genre: track.genre, artists: track.artists.slice(0, 1) };
      const primaryGenres = resolveGenreSet(primaryArtistTrack);

      if (primaryGenres.length > 0) {
        totalStrictPrimary++;
        perLang[lang].strictPrimary++;
      }

      if (genres.length > 0) {
        totalWithGenre++;
        perLang[lang].withGenre++;
      }
    }
  }

  const coveragePct = (totalWithGenre / totalTracks) * 100;
  const strictPct = (totalStrictPrimary / totalTracks) * 100;

  console.log('\n======================================================');
  console.log('  Commit P5.5.2 Real Production Coverage Report');
  console.log('======================================================');
  console.log(`Total sample tracks evaluated: ${totalTracks}`);
  console.log(
    `Strict artists[0] coverage:     ${totalStrictPrimary}/${totalTracks} (${strictPct.toFixed(1)}%)`
  );
  console.log(
    `Full resolveGenreSet coverage:  ${totalWithGenre}/${totalTracks} (${coveragePct.toFixed(1)}%)`
  );
  console.log('------------------------------------------------------');
  console.log('Per-Language Coverage Breakdown:');
  for (const [lang, stats] of Object.entries(perLang)) {
    const langPct = (stats.withGenre / stats.total) * 100;
    const langStrictPct = (stats.strictPrimary / stats.total) * 100;
    console.log(
      `  - ${lang.padEnd(8)}: ${stats.withGenre}/${stats.total} (${langPct.toFixed(1)}%) [Strict: ${langStrictPct.toFixed(1)}%]`
    );
  }
  console.log('======================================================');

  if (coveragePct >= 60.0) {
    console.log(`\n>>> PASS: Aggregate coverage (${coveragePct.toFixed(1)}%) clears >= 60% acceptance gate!`);
    process.exit(0);
  } else {
    console.error(`\n>>> FAIL: Aggregate coverage (${coveragePct.toFixed(1)}%) is below 60% acceptance gate!`);
    process.exit(1);
  }
}

run().catch((err) => {
  console.error('Measurement execution failed:', err);
  process.exit(1);
});
