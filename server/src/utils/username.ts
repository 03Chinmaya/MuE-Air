/**
 * Shared Username Validator & Rules
 * Enforces strict username standards across all backend routes and client flows:
 * - 3–20 characters total
 * - Lowercase only (rejects uppercase)
 * - Allowed chars: a-z, 0-9, hyphen (-), underscore (_), dot (.)
 * - Maximum 4 digits in entire username
 * - Cannot be all numbers (must have at least one letter, underscore, or dot)
 * - Must start with a-z, 0-9, or _ (NOT - and NOT .)
 */

export const USERNAME_REGEX = /^[a-z0-9_][a-z0-9_.-]{2,19}$/;
export const MAX_USERNAME_DIGITS = 4;

export function countDigits(str: string): number {
  return (str.match(/\d/g) || []).length;
}

export function validateUsername(raw: unknown): { ok: true; value: string } | { ok: false; reason: string } {
  // 1. typeof raw === 'string' and raw.length > 0
  if (typeof raw !== 'string' || raw.length === 0) {
    return { ok: false, reason: 'Username is required.' };
  }

  // 2. Trim whitespace
  const value = raw.trim();
  if (value.length === 0) {
    return { ok: false, reason: 'Username is required.' };
  }

  // 3. Lowercase only (reject uppercase; do NOT silently lowercase on server)
  if (value !== value.toLowerCase()) {
    return { ok: false, reason: 'Username must be lowercase.' };
  }

  // 4. Length: 3-20 characters
  if (value.length < 3 || value.length > 20) {
    return { ok: false, reason: 'Username must be 3–20 characters.' };
  }

  // 5. Allowed chars & starting character
  if (!USERNAME_REGEX.test(value)) {
    return {
      ok: false,
      reason:
        'Username must start with a letter, number, or underscore, and may only contain lowercase letters, numbers, hyphens, underscores, and dots.',
    };
  }

  // 6. Not all numbers
  if (/^\d+$/.test(value)) {
    return { ok: false, reason: 'Username cannot be only numbers.' };
  }

  // 7. Max 4 numbers
  const digitCount = countDigits(value);
  if (digitCount > MAX_USERNAME_DIGITS) {
    return { ok: false, reason: 'Username can contain at most 4 numbers.' };
  }

  // 8. Success
  return { ok: true, value };
}

