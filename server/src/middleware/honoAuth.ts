/**
 * Hono Auth Middleware
 * Validates Supabase-compatible JWT tokens on incoming requests.
 * Conforms to docs/00_STACK_LOCK.md Section 4 & docs/05_API.md Section 16.
 *
 * Implements Option A connection reservation pattern:
 * Reserves a dedicated pooled connection per request, binds JWT claims to it,
 * attaches per-request Drizzle ORM instance to Hono Context ('db'),
 * resets claims and releases connection back to pool in finally.
 */

import { Context, Next } from 'hono';
import { drizzle } from 'drizzle-orm/postgres-js';
import { verifyAccessToken, TokenPayload } from '../services/authService.js';
import { pgClient } from '../db/connection.js';
import * as schema from '../db/schema.js';
import { AppDb } from '../types.js';

export interface AuthContextVariables {
  userId: string;
  tokenPayload: TokenPayload;
  db: AppDb;
}

export async function authenticateToken(c: Context, allowGuest: boolean, next: Next) {
  const authHeader = c.req.header('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return c.json({ error: 'Missing or malformed Authorization header' }, 401);
  }

  const token = authHeader.substring(7).trim();
  const payload = verifyAccessToken(token);

  if (!payload || !payload.sub) {
    return c.json({ error: 'Invalid or expired authentication token' }, 401);
  }

  if (!allowGuest && payload.is_guest) {
    return c.json(
      { error: 'guest_restricted', message: 'This feature is not available for guest accounts.' },
      403
    );
  }

  c.set('userId', payload.sub);
  c.set('tokenPayload', payload);

  // Execute request within a dedicated transaction to guarantee PgBouncer pinning
  // and transaction-local claims isolation (auto-reset on commit/rollback).
  await pgClient.begin(async (tx) => {
    (tx as any).options = (pgClient as any).options;
    const reqDb = drizzle(tx as any, { schema });
    c.set('db', reqDb);

    await tx`
      SELECT set_config('request.jwt.claim.sub', ${payload.sub}, true),
             set_config('request.jwt.claims', ${JSON.stringify({ sub: payload.sub, role: payload.role })}, true);
    `;
    await next();
  });
}

export const requireAuth = (c: Context, next: Next) => authenticateToken(c, false, next);
export const requireAuthOrGuest = (c: Context, next: Next) => authenticateToken(c, true, next);
export const honoAuthMiddleware = requireAuth;


