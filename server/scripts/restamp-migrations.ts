/**
 * Re-stamp migrations in drizzle.__drizzle_migrations for production journal synchronization.
 * Conforms to MuE Project Rules (Zero-Print Rule for secrets/credentials).
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import 'dotenv/config';
import { pgClientDirect } from '../src/db/connection.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const migrationsDir = path.resolve(__dirname, '../src/db/migrations');
const journalPath = path.join(migrationsDir, 'meta/_journal.json');

const isDryRun = process.argv.includes('--dry');

interface JournalEntry {
  idx: number;
  version: string;
  when: number;
  tag: string;
  breakpoints: boolean;
}

interface Journal {
  version: string;
  dialect: string;
  entries: JournalEntry[];
}

async function main() {
  if (!fs.existsSync(journalPath)) {
    console.error(`[Error] Journal file not found: ${journalPath}`);
    process.exit(1);
  }

  const journal: Journal = JSON.parse(fs.readFileSync(journalPath, 'utf8'));
  console.log(`[Re-stamp] Found ${journal.entries.length} migration entries in journal.`);

  // 1. Ensure schema and table exist
  await pgClientDirect`CREATE SCHEMA IF NOT EXISTS drizzle;`;
  await pgClientDirect`
    CREATE TABLE IF NOT EXISTS drizzle.__drizzle_migrations (
      id SERIAL PRIMARY KEY,
      hash text NOT NULL,
      created_at bigint
    );
  `;

  // 2. Read existing migrations from database
  const existingRows = await pgClientDirect<Array<{ id: number; hash: string; created_at: string }>>`
    SELECT id, hash, created_at FROM drizzle.__drizzle_migrations ORDER BY created_at ASC;
  `;

  console.log(`[Re-stamp] Existing migrations in database: ${existingRows.length}`);
  const existingHashes = new Set(existingRows.map((r) => r.hash));
  const existingCreatedAts = new Set(existingRows.map((r) => String(r.created_at)));

  const missingEntries: Array<{ tag: string; hash: string; when: number }> = [];

  for (const entry of journal.entries) {
    const sqlPath = path.join(migrationsDir, `${entry.tag}.sql`);
    if (!fs.existsSync(sqlPath)) {
      console.error(`[Error] Missing SQL file: ${sqlPath}`);
      process.exit(1);
    }

    const sqlContent = fs.readFileSync(sqlPath, 'utf8');
    const hash = crypto.createHash('sha256').update(sqlContent).digest('hex');

    if (!existingHashes.has(hash) && !existingCreatedAts.has(String(entry.when))) {
      missingEntries.push({
        tag: entry.tag,
        hash,
        when: entry.when,
      });
    }
  }

  console.log(`[Re-stamp] Missing migration entries to re-stamp: ${missingEntries.length}`);

  if (missingEntries.length === 0) {
    console.log('[Re-stamp] All journal migrations already stamped in database.');
    await pgClientDirect.end({ timeout: 2 });
    process.exit(0);
  }

  for (const entry of missingEntries) {
    if (isDryRun) {
      console.log(`[DRY-RUN] Would stamp: tag="${entry.tag}", when=${entry.when}, hash=${entry.hash}`);
    } else {
      console.log(`[Re-stamp] Inserting migration record: tag="${entry.tag}", when=${entry.when}`);
      await pgClientDirect`
        INSERT INTO drizzle.__drizzle_migrations ("hash", "created_at")
        VALUES (${entry.hash}, ${entry.when});
      `;
    }
  }

  if (isDryRun) {
    console.log('[DRY-RUN] Completed plan preview. No records were written.');
  } else {
    const updatedRows = await pgClientDirect<Array<{ id: number; hash: string; created_at: string }>>`
      SELECT id, hash, created_at FROM drizzle.__drizzle_migrations ORDER BY created_at ASC;
    `;
    console.log(`[Re-stamp] Success. Current total migrations in DB: ${updatedRows.length}`);
  }

  await pgClientDirect.end({ timeout: 2 });
}

main().catch(async (err) => {
  console.error('[Re-stamp] Fatal error:', err?.message || err);
  try {
    await pgClientDirect.end({ timeout: 2 });
  } catch {}
  process.exit(1);
});
