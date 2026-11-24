import { TokenPayload } from './services/authService.js';
import { drizzle } from 'drizzle-orm/postgres-js';
import * as schema from './db/schema.js';

export type AppDb = ReturnType<typeof drizzle<typeof schema>>;

export interface AppEnv {
  Variables: {
    userId: string;
    tokenPayload: TokenPayload;
    db: AppDb;
    requestId?: string;
  };
}

