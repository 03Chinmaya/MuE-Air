import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const srcDir = path.resolve(__dirname, '../src');

const ALLOWED_FILES = new Set([
  'db/connection.ts', // Definition file
  'routes/authRoutes.ts',
  'services/authService.ts',
  'services/guestMergeService.ts'
]);

function getFiles(dir: string): string[] {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  const files: string[] = [];
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...getFiles(fullPath));
    } else if (entry.isFile() && (entry.name.endsWith('.ts') || entry.name.endsWith('.js'))) {
      files.push(fullPath);
    }
  }
  return files;
}

const allFiles = getFiles(srcDir);
const violations: { file: string; line: number; content: string }[] = [];

for (const file of allFiles) {
  const relativePath = path.relative(srcDir, file).replace(/\\/g, '/');
  if (ALLOWED_FILES.has(relativePath)) {
    continue;
  }

  const content = fs.readFileSync(file, 'utf-8');
  const lines = content.split('\n');

  lines.forEach((lineText, index) => {
    // Check if authDb is imported or referenced
    if (/\bauthDb\b/.test(lineText)) {
      violations.push({
        file: relativePath,
        line: index + 1,
        content: lineText.trim()
      });
    }
  });
}

if (violations.length > 0) {
  console.error('[FAIL] authDb isolation violation detected! authDb must only be used by authRoutes.ts and authService.ts:');
  for (const v of violations) {
    console.error(`  - ${v.file}:${v.line} -> ${v.content}`);
  }
  process.exit(1);
} else {
  console.log('[OK] authDb isolation check passed. Only authRoutes.ts and authService.ts access authDb.');
  process.exit(0);
}
