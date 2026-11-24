/**
 * Master Hono Application
 * Assembles all API sub-routes, CORS, and health endpoints for MuE-Air.
 */

import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { timeout } from 'hono/timeout';
import { secureHeaders } from 'hono/secure-headers';
import { bodyLimit } from 'hono/body-limit';
import { rateLimit, inMemoryRateLimit, getClientIp } from './lib/rateLimit.js';
import { requireAuthOrGuest } from './middleware/honoAuth.js';
import { authRoutes } from './routes/authRoutes.js';
import { profileRoutes } from './routes/profileRoutes.js';
import { syncRoutes } from './routes/syncRoutes.js';
import { listenRoutes } from './routes/listenRoutes.js';
import { recommendationsRoutes } from './routes/recommendationsRoutes.js';
import { statsRoutes } from './routes/statsRoutes.js';
import { youtubeRoutes } from './routes/youtubeRoutes.js';
import { searchRoutes } from './routes/searchRoutes.js';
import { jiosaavnRoutes } from './routes/jiosaavnRoutes.js';
import { artistRoutes } from './routes/artistRoutes.js';
import { playlistRoutes } from './routes/playlistRoutes.js';
import { settingsRoutes } from './routes/settingsRoutes.js';
import { importRoutes } from './routes/importRoutes.js';
import { updatesRoutes } from './routes/updatesRoutes.js';
import { logger } from './lib/logger.js';
import { AppEnv } from './types.js';

export const app = new Hono<AppEnv>();

// Request ID Middleware
app.use('*', async (c, next) => {
  const reqId = c.req.header('x-request-id') || crypto.randomUUID();
  c.set('requestId', reqId);
  c.header('X-Request-Id', reqId);
  await next();
});

// Access Logging Middleware
app.use('*', async (c, next) => {
  const start = performance.now();
  await next();
  const durationMs = Math.round(performance.now() - start);
  const reqId = c.get('requestId') || c.req.header('x-request-id') || '';
  const method = c.req.method;
  const path = c.req.path;
  const status = c.res.status;

  logger.info({
    reqId,
    method,
    path,
    status,
    durationMs,
  });
});

// Global 30-second request timeout to prevent hanging connections
app.use('*', timeout(30000));

// Security Headers Middleware (G1)
app.use(
  '*',
  secureHeaders({
    strictTransportSecurity: 'max-age=31536000; includeSubDomains',
    xContentTypeOptions: 'nosniff',
    xFrameOptions: 'DENY',
    referrerPolicy: 'strict-origin-when-cross-origin',
    contentSecurityPolicy: { defaultSrc: ["'none'"] },
  })
);

// Global 1MB Body Limit Middleware (G3)
app.use(
  '*',
  bodyLimit({
    maxSize: 1024 * 1024,
    onError: (c) => c.json({ error: 'payload_too_large' }, 413),
  })
);

// Parse ALLOWED_ORIGINS allowlist
const configuredAllowedOrigins = (process.env.ALLOWED_ORIGINS ?? '')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);

if (process.env.NODE_ENV === 'production' && configuredAllowedOrigins.length === 0) {
  throw new Error('ALLOWED_ORIGINS environment variable must be configured in production');
}

const defaultDevOrigins = ['https://mue.app', 'https://admin.mue.app', 'http://localhost:3000', 'http://localhost:5173'];
const allowedOriginsSet = new Set(
  configuredAllowedOrigins.length > 0 ? configuredAllowedOrigins : defaultDevOrigins
);

// Global CORS Middleware
app.use(
  '*',
  cors({
    origin: (origin) => {
      if (!origin) return null;
      if (allowedOriginsSet.has(origin)) return origin;
      if (process.env.NODE_ENV !== 'production') {
        if (
          origin.startsWith('http://localhost:') ||
          origin.startsWith('http://127.0.0.1:') ||
          origin.startsWith('mue://')
        ) {
          return origin;
        }
      }
      return null;
    },
    allowMethods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowHeaders: ['Content-Type', 'Authorization', 'X-Requested-With', 'Cache-Control', 'Pragma', 'If-Modified-Since'],
    exposeHeaders: ['Retry-After', 'X-RateLimit-Remaining'],
    maxAge: 600,
    credentials: false,
  })
);

// Health Check: instant response
app.get('/health', (c) => c.json({ ok: true }));

let shuttingDown = false;
export function setShuttingDown(val = true) {
  shuttingDown = val;
}
export function getShuttingDown() {
  return shuttingDown;
}

// Readiness check for orchestrators / zero-downtime deploys
app.get('/readyz', (c) => {
  if (shuttingDown) {
    return c.json({ status: 'shutting_down' }, 503);
  }
  return c.json({ status: 'ready' }, 200);
});

// App Feature Configuration (Public Gating)
app.get('/api/config', (c) => {
  return c.json({
    e2ePublicEnabled: false,
  });
});

// Rate Limiters for High-Risk Routes (G5)
app.use('/api/search/*', rateLimit({ prefix: 'search', limit: 30, windowSec: 60 }));
app.use('/api/search', rateLimit({ prefix: 'search', limit: 30, windowSec: 60 }));

app.use('/api/youtube/*', rateLimit({ prefix: 'youtube', limit: 60, windowSec: 60 }));
app.use('/api/youtube', rateLimit({ prefix: 'youtube', limit: 60, windowSec: 60 }));

app.use('/api/sync/*', rateLimit({ prefix: 'sync', limit: 30, windowSec: 60 }));
app.use('/api/sync', rateLimit({ prefix: 'sync', limit: 30, windowSec: 60 }));

app.use(
  '/api/auth/guest-setup',
  rateLimit({
    prefix: 'guest-setup',
    limit: 5,
    windowSec: 60,
    keyGenerator: (c) => getClientIp(c),
  })
);

// Rate limiting for Artists & JioSaavn music endpoints (60/min/user, guests supported)
app.use('/api/artists/*', requireAuthOrGuest);
app.use('/api/artists/*', rateLimit({ prefix: 'artists', limit: 60, windowSec: 60 }));
app.use('/api/artists', requireAuthOrGuest);
app.use('/api/artists', rateLimit({ prefix: 'artists', limit: 60, windowSec: 60 }));

app.use('/api/jiosaavn/*', requireAuthOrGuest);
app.use('/api/jiosaavn/*', rateLimit({ prefix: 'jiosaavn', limit: 60, windowSec: 60 }));
app.use('/api/jiosaavn', requireAuthOrGuest);
app.use('/api/jiosaavn', rateLimit({ prefix: 'jiosaavn', limit: 60, windowSec: 60 }));

// In-Memory sliding window rate limiter for Image Proxy (30/min/IP)
app.use('/api/proxy/image', inMemoryRateLimit({ limit: 30, windowMs: 60 * 1000 }));

// Mount Routes
app.route('/api/auth', authRoutes);
app.route('/api/profile', profileRoutes);
app.route('/api/sync', syncRoutes);
app.route('/api/listen', listenRoutes);
app.route('/api/recommendations', recommendationsRoutes);
app.route('/api/stats', statsRoutes);
app.route('/api/youtube', youtubeRoutes);
app.route('/api/search', searchRoutes);
app.route('/api/jiosaavn', jiosaavnRoutes);
app.route('/api/artists', artistRoutes);
app.route('/api/playlists', playlistRoutes);
app.route('/api/settings', settingsRoutes);
app.route('/api/import', importRoutes);
app.route('/api/updates', updatesRoutes);

// Image proxy for mobile environments with DNS restrictions
app.get('/api/proxy/image', async (c) => {
  const url = c.req.query('url');
  if (!url || !url.startsWith('http')) {
    return c.json({ error: 'Valid url query param required' }, 400);
  }

  let parsedUrl: URL;
  try {
    parsedUrl = new URL(url);
  } catch {
    return c.json({ error: 'Invalid URL format' }, 400);
  }

  const hostname = parsedUrl.hostname.toLowerCase();

  // Block private IPs / loopback / cloud metadata
  const isPrivateIp =
    hostname === 'localhost' ||
    hostname === '127.0.0.1' ||
    hostname === '::1' ||
    hostname.startsWith('10.') ||
    hostname.startsWith('192.168.') ||
    hostname.startsWith('169.254.') ||
    /^172\.(1[6-9]|2[0-9]|3[0-1])\./.test(hostname);

  if (isPrivateIp) {
    return c.json({ error: 'Access to private addresses is forbidden' }, 403);
  }

  const allowedDomains = ['saavncdn.com', 'jiosaavn.com', 'ytimg.com', 'googleusercontent.com', 'dzcdn.net'];
  const isAllowed = allowedDomains.some((domain) => hostname === domain || hostname.endsWith(`.${domain}`));

  if (!isAllowed) {
    return c.json({ error: 'Domain not allowed' }, 403);
  }

  try {
    const res = await fetch(parsedUrl.toString(), {
      headers: {
        'User-Agent':
          'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        Accept: 'image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8',
      },
    });
    if (!res.ok) {
      return c.json({ error: 'Failed to fetch image' }, res.status as any);
    }
    const contentType = res.headers.get('content-type') || 'image/jpeg';
    const buffer = await res.arrayBuffer();
    return new Response(buffer, {
      status: 200,
      headers: {
        'Content-Type': contentType,
        'Cache-Control': 'public, max-age=86400',
      },
    });
  } catch (err: any) {
    return c.json({ error: 'Proxy error', details: err?.message }, 502);
  }
});

export default app;
