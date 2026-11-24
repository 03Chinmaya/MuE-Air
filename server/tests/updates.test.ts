import { describe, it, afterEach } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';

// Compatible spy helper supporting vi.spyOn semantics under node:test
const vi = {
  spyOn: (target: any, method: string) => {
    const original = target[method];
    const spy = {
      mockResolvedValueOnce: (val: any) => {
        target[method] = async () => val;
        return spy;
      },
      mockImplementation: (fn: any) => {
        target[method] = fn;
        return spy;
      },
      mockRestore: () => {
        target[method] = original;
      },
    };
    return spy;
  },
};

describe('Updates Routes Tests', () => {
  let fetchSpy: ReturnType<typeof vi.spyOn> | null = null;

  afterEach(() => {
    if (fetchSpy) {
      fetchSpy.mockRestore();
      fetchSpy = null;
    }
  });

  it('GET /api/updates/check returns updateAvailable: true on newer release', async () => {
    const mockRelease = {
      tag_name: 'v2.1.0',
      body: 'Release notes v2.1.0',
      published_at: '2026-11-01T00:00:00Z',
      assets: [
        {
          name: 'mue-v2.1.0.apk',
          browser_download_url: 'https://github.com/03Chinmaya/MuE/releases/download/v2.1.0/app.apk',
          size: 15728640,
        },
      ],
    };

    fetchSpy = vi.spyOn(global, 'fetch').mockImplementation(async () => {
      return new Response(JSON.stringify(mockRelease), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    const res = await app.fetch(new Request('http://localhost:4000/api/updates/check?version=1.0'));
    assert.strictEqual(res.status, 200);

    const body = (await res.json()) as any;
    assert.strictEqual(body.updateAvailable, true);
    assert.strictEqual(body.latestVersion, '2.1.0');
    assert.strictEqual(body.currentVersion, '1.0');
    assert.strictEqual(body.releaseNotes, 'Release notes v2.1.0');
    assert.strictEqual(body.apkUrl, 'https://github.com/03Chinmaya/MuE/releases/download/v2.1.0/app.apk');
    assert.strictEqual(body.apkSize, 15728640);
  });

  it('GET /api/updates/check returns updateAvailable: false when no newer version exists', async () => {
    const mockRelease = {
      tag_name: 'v1.0',
      body: 'Initial release',
      published_at: '2026-10-01T00:00:00Z',
      assets: [],
    };

    fetchSpy = vi.spyOn(global, 'fetch').mockImplementation(async () => {
      return new Response(JSON.stringify(mockRelease), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    const res = await app.fetch(new Request('http://localhost:4000/api/updates/check?version=1.0'));
    assert.strictEqual(res.status, 200);

    const body = (await res.json()) as any;
    assert.strictEqual(body.updateAvailable, false);
    assert.strictEqual(body.latestVersion, '1.0');
  });

  it('GET /api/updates/check returns updateAvailable: false on empty or 404 response', async () => {
    fetchSpy = vi.spyOn(global, 'fetch').mockImplementation(async () => {
      return new Response(JSON.stringify({ message: 'Not Found' }), {
        status: 404,
        headers: { 'Content-Type': 'application/json' },
      });
    });

    const res = await app.fetch(new Request('http://localhost:4000/api/updates/check?version=1.0'));
    assert.strictEqual(res.status, 200);

    const body = (await res.json()) as any;
    assert.strictEqual(body.updateAvailable, false);
    assert.strictEqual(body.reason, 'No releases found');
  });
});

