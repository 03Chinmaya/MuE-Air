/**
 * Rate Limiting Middleware / Utility
 * Supports Redis pipeline with in-memory fallback.
 */

interface RateLimitResult {
  allowed: boolean;
  retryAfter: number;
  remaining: number;
}

const memoryStore = new Map<string, { count: number; resetTime: number }>();

export async function checkRateLimit(
  prefix: string,
  identifier: string,
  maxAttempts: number,
  windowSec: number
): Promise<RateLimitResult> {
  // Never remove: tests share the production Upstash keyspace (rl:* keys).
  // Double-guarded so production can never activate this branch.
  if (process.env.NODE_ENV === 'test' && process.env.RATE_LIMIT_BYPASS === '1') {
    return { allowed: true, retryAfter: 0, remaining: Number.MAX_SAFE_INTEGER };
  }

  const key = `rl:${prefix}:${identifier}`;
  const redisUrl = process.env.REDIS_URL;
  const redisToken = process.env.REDIS_TOKEN;

  if (redisUrl && redisToken) {
    try {
      const res = await fetch(`${redisUrl}/pipeline`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${redisToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify([
          ['INCR', key],
          ['EXPIRE', key, windowSec],
        ]),
      });
      if (res.ok) {
        const results = (await res.json()) as any[];
        const count = results[0]?.result;
        if (typeof count === 'number') {
          return {
            allowed: count <= maxAttempts,
            retryAfter: windowSec,
            remaining: Math.max(0, maxAttempts - count),
          };
        }
      }
    } catch {
      // Fallback to in-memory on network or config error
    }
  }

  const now = Date.now();
  const entry = memoryStore.get(key);
  if (!entry || now > entry.resetTime) {
    memoryStore.set(key, { count: 1, resetTime: now + windowSec * 1000 });
    return { allowed: true, retryAfter: windowSec, remaining: maxAttempts - 1 };
  }

  entry.count++;
  if (entry.count > maxAttempts) {
    return {
      allowed: false,
      retryAfter: Math.ceil((entry.resetTime - now) / 1000),
      remaining: 0,
    };
  }

  return {
    allowed: true,
    retryAfter: Math.ceil((entry.resetTime - now) / 1000),
    remaining: maxAttempts - entry.count,
  };
}

export function resetRateLimits(): void {
  memoryStore.clear();
}
