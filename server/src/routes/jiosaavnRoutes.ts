import { safeError } from '../lib/errors.js';
/**
 * JioSaavn Proxy Routes (Hono)
 * Proxies JioSaavn API queries with proper User-Agent and Referer headers
 * to prevent Akamai 403 Access Denied blocks on Web and mobile.
 */

import { Hono } from 'hono';

export const jiosaavnRoutes = new Hono();

const jioSaavnCache = new Map<string, { body: string; status: number; expiresAt: number }>();

jiosaavnRoutes.get('/*', async (c) => {
  const url = new URL(c.req.url);
  const cacheKey = url.search;
  const cached = jioSaavnCache.get(cacheKey);
  if (cached && cached.expiresAt > Date.now()) {
    return c.newResponse(cached.body, cached.status as any, {
      'Content-Type': 'application/json',
      'X-Cache': 'HIT',
    });
  }

  const targetUrl = `https://www.jiosaavn.com/api.php${url.search}`;

  const ctrl = new AbortController();
  const timeout = setTimeout(() => ctrl.abort(), 6000);

  try {
    const res = await fetch(targetUrl, {
      signal: ctrl.signal,
      headers: {
        'User-Agent':
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        Accept: 'application/json, text/plain, */*',
        Referer: 'https://www.jiosaavn.com/',
      },
    });
    clearTimeout(timeout);

    const text = await res.text();
    const cleanJson = text.trim().replace(/^[^{[]*/, '').replace(/[^}\]]*$/, '');

    if (res.ok && cleanJson.length > 2) {
      jioSaavnCache.set(cacheKey, {
        body: cleanJson,
        status: res.status,
        expiresAt: Date.now() + 5 * 60 * 1000,
      });
    }

    return c.newResponse(cleanJson, res.status as any, {
      'Content-Type': 'application/json',
    });
  } catch (err: any) {
    clearTimeout(timeout);
    return c.json(safeError(err, c, 500), 502);
  }
});
