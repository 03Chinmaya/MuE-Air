import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { pgClientDirect, pgClientAuth } from '../src/db/connection.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function applyRls() {
  const policiesPath = path.resolve(__dirname, '../src/db/rls/policies.sql');
  const sql = fs.readFileSync(policiesPath, 'utf8');

  console.log('Applying RLS policies to database...');
  try {
    await pgClientDirect.unsafe(sql);
  } catch (err: any) {
    if (err?.code === '42P01') {
      console.error(`[DB] Schema Error: Table or relation missing: ${err.message}. Run migrations before applying RLS.`);
      throw err;
    }

    const isConnectionError =
      err?.code === 'ECONNREFUSED' ||
      err?.code === 'ETIMEDOUT' ||
      err?.code === 'ENOTFOUND' ||
      err?.message?.includes('Connection refused') ||
      err?.message?.includes('connect ETIMEDOUT');

    if (isConnectionError) {
      console.warn(`Direct connection failed (${err?.code || err?.message}), retrying with privileged auth connection...`);
      await pgClientAuth.unsafe(sql);
    } else {
      console.error(`[DB] SQL Error applying RLS policies (${err?.code || err?.message}): ${err.message}`);
      throw err;
    }
  }
  console.log('RLS policies applied successfully.');

  await Promise.all([
    pgClientDirect.end({ timeout: 2 }).catch(() => {}),
    pgClientAuth.end({ timeout: 2 }).catch(() => {}),
  ]);
}

applyRls().catch((err) => {
  console.error('Failed to apply RLS policies:', err?.message || err);
  process.exit(1);
});
