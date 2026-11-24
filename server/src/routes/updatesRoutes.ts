import { Hono } from 'hono';
import { AppEnv } from '../types.js';

export const updatesRoutes = new Hono<AppEnv>();

function parseVersion(v: string): number[] {
  return v.replace(/^v/, '').split('.').map((p) => parseInt(p, 10) || 0);
}

function isNewer(latest: string, current: string): boolean {
  const l = parseVersion(latest);
  const c = parseVersion(current);
  const len = Math.max(l.length, c.length);
  for (let i = 0; i < len; i++) {
    const lPart = l[i] ?? 0;
    const cPart = c[i] ?? 0;
    if (lPart > cPart) return true;
    if (lPart < cPart) return false;
  }
  return false;
}

// GET /api/updates/check?version=1.0
updatesRoutes.get('/check', async (c) => {
  const currentVersion = c.req.query('version') || '1.0';
  const repo = process.env.GITHUB_REPO || '03Chinmaya/MuE-Air';
  const token = process.env.GITHUB_TOKEN || process.env.GITHUB_PAT;

  try {
    const headers: Record<string, string> = {
      'User-Agent': 'MuE-Air-Server',
      Accept: 'application/vnd.github.v3+json',
    };
    if (token) {
      headers['Authorization'] = `token ${token}`;
    }

    const res = await fetch(`https://api.github.com/repos/${repo}/releases/latest`, { headers });
    if (!res.ok) {
      return c.json({ updateAvailable: false, reason: 'No releases found' });
    }

    const release = (await res.json()) as any;
    if (!release || !release.tag_name) {
      return c.json({ updateAvailable: false, reason: 'No releases found' });
    }

    const latestVersion = release.tag_name.replace(/^v/, '');
    const updateAvailable = isNewer(latestVersion, currentVersion);

    if (updateAvailable) {
      const apkAsset = Array.isArray(release.assets)
        ? release.assets.find((a: any) => typeof a.name === 'string' && a.name.endsWith('.apk')) || release.assets[0]
        : null;

      return c.json({
        updateAvailable: true,
        latestVersion,
        currentVersion,
        releaseNotes: release.body || '',
        apkUrl: apkAsset?.browser_download_url || '',
        apkSize: apkAsset?.size || 0,
      });
    }

    return c.json({
      updateAvailable: false,
      latestVersion,
    });
  } catch (err: any) {
    return c.json({ updateAvailable: false, error: err?.message || 'Check failed' }, 500);
  }
});
