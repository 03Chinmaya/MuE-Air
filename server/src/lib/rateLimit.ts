/**
 * Rate Limiting Middleware & Helpers (G5).
 * Connects to Redis via Upstash REST pipeline with in-memory fallback.
 * Strictly adheres to docs/00_STACK_LOCK.md and Phase 5 hardening.
 */
import { Context, Next } from 'hono';
import { checkRateLimit } from '../middleware/rateLimiter.js';
import { verifyAccessToken } from '../services/authService.js';

export { checkRateLimit, resetRateLimits } from '../middleware/rateLimiter.js';

export interface RateLimitOptions {
  prefix: string;
  limit: number;
  windowSec: number;
  keyGenerator?: (c: Context) => string;
}

export function getClientIp(c: Context): string {
  const forwarded = c.req.header('x-forwarded-for');
  if (forwarded) {
    return forwarded.split(',')[0].trim();
  }
  return c.req.header('cf-connecting-ip') || 'anonymous-ip';
}

export function rateLimit(options: RateLimitOptions) {
  const { prefix, limit, windowSec } = options;

  return async (c: Context, next: Next) => {
    let identifier: string;
    if (options.keyGenerator) {
      identifier = options.keyGenerator(c);
    } else {
      // Default: User ID from Context or Bearer token if present, fallback to client IP
      let userId: string | null = (c.get as any)('userId') || null;
      if (!userId) {
        const auth = c.req.header('Authorization');
        if (auth && auth.startsWith('Bearer ')) {
          const payload = verifyAccessToken(auth.slice(7).trim());
          if (payload?.sub) {
            userId = payload.sub;
          }
        }
      }
      identifier = userId || getClientIp(c);
    }

    const result = await checkRateLimit(prefix, identifier, limit, windowSec);

    c.header('X-RateLimit-Remaining', String(result.remaining));
    c.header('Retry-After', String(result.retryAfter));

    if (!result.allowed) {
      return c.json(
        {
          error: 'rate_limit_exceeded',
          message: 'Too many requests. Please try again later.',
          retryAfter: result.retryAfter,
        },
        429
      );
    }

    await next();
  };
}

// In-Memory Sliding Window Rate Limiter for saving external Upstash Redis commands on high-volume endpoints (e.g. image proxy)
const inMemoryHits = new Map<string, number[]>();

export function resetInMemoryRateLimits(): void {
  inMemoryHits.clear();
}

export interface InMemoryRateLimitOptions {
  limit: number;
  windowMs: number;
  keyGenerator?: (c: Context) => string;
}

export function inMemoryRateLimit(options: InMemoryRateLimitOptions) {
  const { limit, windowMs } = options;
  const cleanupInterval = 5 * 60 * 1000;
  let lastCleanup = Date.now();

  return async (c: Context, next: Next) => {
    const now = Date.now();
    if (now - lastCleanup > cleanupInterval) {
      lastCleanup = now;
      for (const [key, timestamps] of inMemoryHits.entries()) {
        const valid = timestamps.filter((t) => now - t < windowMs);
        if (valid.length === 0) {
          inMemoryHits.delete(key);
        } else {
          inMemoryHits.set(key, valid);
        }
      }
    }

    const key = options.keyGenerator ? options.keyGenerator(c) : getClientIp(c);
    const timestamps = (inMemoryHits.get(key) || []).filter((t) => now - t < windowMs);

    if (timestamps.length >= limit) {
      const oldest = timestamps[0];
      const retryAfter = Math.max(1, Math.ceil((oldest + windowMs - now) / 1000));
      c.header('X-RateLimit-Remaining', '0');
      c.header('Retry-After', String(retryAfter));
      return c.json(
        {
          error: 'rate_limit_exceeded',
          message: 'Too many requests. Please try again later.',
          retryAfter,
        },
        429
      );
    }

    timestamps.push(now);
    inMemoryHits.set(key, timestamps);

    c.header('X-RateLimit-Remaining', String(limit - timestamps.length));
    await next();
  };
}
