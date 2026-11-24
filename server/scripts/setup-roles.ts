import postgres from 'postgres';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function main() {
  const url = process.env.DATABASE_URL_DIRECT;
  if (!url) {
    throw new Error('DATABASE_URL_DIRECT is required');
  }

  const appUserPassword = process.env.APP_USER_PASSWORD || 'app_user_dev_pass_123';
  const authRolePassword = process.env.AUTH_ROLE_PASSWORD || 'auth_role_dev_pass_123';

  const sql = postgres(url, { max: 1 });
  const script = fs
    .readFileSync(path.join(__dirname, 'setup-roles.sql'), 'utf8')
    .replace(/\$\{APP_USER_PASSWORD\}/g, appUserPassword)
    .replace(/\$\{AUTH_ROLE_PASSWORD\}/g, authRolePassword);

  await sql.unsafe(script);
  console.log('Roles (app_user, auth_role) + auth.uid() installed successfully.');
  await sql.end();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
