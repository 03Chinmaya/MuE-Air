import postgres from 'postgres';
import dotenv from 'dotenv';

dotenv.config();
process.env.NODE_ENV = 'test';
process.env.SOMU_TEST_MODE = '1';

const appUserUrl = process.env.DATABASE_URL!;
const authRoleUrl = process.env.DATABASE_URL_AUTH!;

export const sqlAppUser = postgres(appUserUrl, { prepare: false });
export const sqlAuthRole = postgres(authRoleUrl, { prepare: false });

export async function createTestUser(username: string, email: string, isPrivate = false) {
  const [user] = await sqlAuthRole<{ id: string; username: string; email: string; isPrivate: boolean }[]>`
    INSERT INTO users (username, display_name, email, password_hash, is_private)
    VALUES (${username}, ${username}, ${email}, 'dummy_hash_for_test', ${isPrivate})
    RETURNING id, username, email, is_private as "isPrivate"
  `;
  return user;
}

export async function cleanupTestUsers(userIds: string[]) {
  if (userIds.length === 0) return;
  await sqlAuthRole`
    DELETE FROM users WHERE id IN ${sqlAuthRole(userIds)}
  `;
}

export async function asUser<T>(userId: string, callback: (tx: postgres.Sql) => Promise<T>): Promise<T> {
  return sqlAppUser.begin(async (tx) => {
    await tx`SELECT set_config('request.jwt.claim.sub', ${userId}, true)`;
    return callback(tx as any);
  });
}
