/**
 * OTP Service
 * Cryptographically secure 6-digit numeric OTP generation, SHA-256 hashing,
 * and constant-time verification.
 */

import crypto from 'node:crypto';

export const OTP_EXPIRY_MS = 10 * 60 * 1000; // 10 minutes
export const MAX_OTP_ATTEMPTS = 5;

/**
 * Generates a cryptographically secure 6-digit numeric OTP (e.g. "482910").
 */
export function generateOtp(): string {
  const num = crypto.randomInt(0, 1000000);
  return num.toString().padStart(6, '0');
}

/**
 * Computes a SHA-256 hash of the trimmed OTP string.
 */
export function hashOtp(otp: string): string {
  return crypto.createHash('sha256').update(otp.trim()).digest('hex');
}

/**
 * Constant-time verification between provided OTP and stored hash.
 */
export function verifyOtp(providedOtp: string, expectedHash: string): boolean {
  if (!providedOtp || typeof providedOtp !== 'string' || !expectedHash || typeof expectedHash !== 'string') {
    return false;
  }
  const providedHash = hashOtp(providedOtp);
  const bufA = Buffer.from(providedHash, 'hex');
  const bufB = Buffer.from(expectedHash, 'hex');
  if (bufA.length !== bufB.length) {
    return false;
  }
  return crypto.timingSafeEqual(bufA, bufB);
}

/**
 * Checks if the OTP expiration timestamp has passed.
 */
export function isOtpExpired(expiresAt: Date | string): boolean {
  return new Date() > new Date(expiresAt);
}
