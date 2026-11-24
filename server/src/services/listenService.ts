import { randomInt } from 'node:crypto';
import { eq } from 'drizzle-orm';
import { listenSessions } from '../db/schema.js';

const ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ'; // no 0 O 1 I
const CODE_LENGTH = 6;
const MAX_ATTEMPTS = 10;

export class CodeGenerationError extends Error {
  constructor(message = 'CODE_GENERATION_FAILED') {
    super(message);
    this.name = 'CodeGenerationError';
  }
}

// Takes a scoped Drizzle handle (c.get('db')). Never import privileged database connections here.
export async function generateUniqueJoinCode(dbHandle: any): Promise<string> {
  for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
    let code = '';
    for (let i = 0; i < CODE_LENGTH; i++) {
      code += ALPHABET[randomInt(ALPHABET.length)];
    }

    const existing = await dbHandle
      .select({ id: listenSessions.id })
      .from(listenSessions)
      .where(eq(listenSessions.joinCode, code))
      .limit(1);

    if (existing.length === 0) {
      return code;
    }
  }

  throw new CodeGenerationError('CODE_GENERATION_FAILED: Exceeded max collision attempts');
}
