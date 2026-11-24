import { describe, it } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';

describe('Federated Search Routes — Specification Tests', () => {
  // Test 14 — POST /api/search with valid query -> returns empty grouped results when DB is empty
  it('14. POST /api/search with valid query returns grouped results', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/search/search', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ q: 'Tum Hi Ho', limit: 10 }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.strictEqual(data.query, 'Tum Hi Ho');
    assert.ok(data.groups);
    assert.ok(Array.isArray(data.groups.tracks));
    assert.ok(Array.isArray(data.groups.artists));
    assert.ok(Array.isArray(data.groups.albums));
    assert.ok(Array.isArray(data.groups.playlists));
    assert.strictEqual(data.groups.tracks.length, 0);
  });

  // Test 15 — POST /api/search with <2 char query -> empty
  it('15. POST /api/search with <2 char query returns empty groups and zero count', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/search/search', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ q: 'a' }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.strictEqual(data.counts.total, 0);
    assert.strictEqual(data.groups.tracks.length, 0);
    assert.strictEqual(data.groups.artists.length, 0);
  });

  // Test 16 — POST /api/search with unindexed track returns empty results without fake fallbacks
  it('16. POST /api/search with unindexed track returns empty results', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/search/search', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ q: 'Underground Jam' }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.ok(Array.isArray(data.groups.tracks));
    assert.strictEqual(data.groups.tracks.length, 0);
  });

  // Test 17 — POST /api/search logs to search_queries
  it('17. POST /api/search/log accepts user interaction logging cleanly', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/search/log', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          query: 'Tum Hi Ho',
          entityType: 'track',
          entityId: '00000000-0000-0000-0000-000000000001',
          position: 0,
          provider: 'jiosaavn',
          action: 'play_from_result',
        }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.strictEqual(data.ok, true);
  });

  // Test 18 — Typo "tum hi h" -> returns empty without mock docs
  it('18. Typo query on empty DB returns empty results cleanly', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/search/search', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ q: 'tum hi h' }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.ok(Array.isArray(data.groups.tracks));
    assert.strictEqual(data.groups.tracks.length, 0);
  });
});
