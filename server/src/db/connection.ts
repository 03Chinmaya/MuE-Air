/**
 * PostgreSQL Database Connection (Drizzle ORM)
 * Connects to Supabase PostgreSQL instance via postgres.js client.
 * Strictly adheres to docs/00_STACK_LOCK.md Section 4 and docs/04_DATABASE.md.
 */

import { drizzle } from 'drizzle-orm/postgres-js';
import postgres from 'postgres';
import dotenv from 'dotenv';
import * as schema from './schema.js';

dotenv.config();

const runtimeUrl = process.env.DATABASE_URL || 'postgresql://postgres:postgres@localhost:5432/postgres';
const migrationUrl = process.env.DATABASE_URL_DIRECT || process.env.DATABASE_URL || runtimeUrl;
const authUrl = process.env.DATABASE_URL_AUTH || runtimeUrl;

// Runtime connection pooler (Session/Transaction mode on port 6543)
// Requires prepare: false for PgBouncer compatibility
export const pgClient = postgres(runtimeUrl, {
  prepare: false,
  max: 10,
  idle_timeout: 20,
  connect_timeout: 10,
});

export const db = drizzle(pgClient, { schema });

// Direct connection (port 5432) for migrations & schema operations
export const pgClientDirect = postgres(migrationUrl, {
  prepare: false,
  max: 1,
  idle_timeout: 20,
  connect_timeout: 10,
});

// Auth-service connection: privileged connection for auth operations.
// Only authService.ts and authRoutes.ts may import this.
// Any other use is a security violation.
export const pgClientAuth = postgres(authUrl, {
  prepare: false,
  max: 5,
  idle_timeout: 20,
  connect_timeout: 10,
});

export const authDb = drizzle(pgClientAuth, { schema });

// Verify authDb connection role on startup
(async () => {
  try {
    const result = await pgClientAuth`SELECT current_user, session_user`;
    console.log(`[DB] authDb connected as: ${result[0].current_user}`);
  } catch (e) {
    console.error(`[DB] authDb connection test failed`, e);
  }
})();

export async function closeConnections() {
  await Promise.all([
    pgClient.end({ timeout: 2 }),
    pgClientDirect.end({ timeout: 2 }),
    pgClientAuth.end({ timeout: 2 }),
  ]);
}
