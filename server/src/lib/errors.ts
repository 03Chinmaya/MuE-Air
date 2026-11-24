import { Context } from 'hono';
import { logger } from './logger.js';

/**
 * Utility for safe error responses in production environments (G4).
 * Masks internal 5xx error messages to prevent leaking schema or system details.
 * Logs unhandled 5xx errors server-side with stack trace and requestId before masking.
 */
export function safeError(err: unknown, c?: Context<any>, status = 500): { error: string } {
  const isProd = process.env.NODE_ENV === 'production';
  const msg = err instanceof Error ? err.message : typeof err === 'string' ? err : 'error';

  if (status >= 500) {
    const requestId = c?.get ? c.get('requestId') : undefined;
    logger.error({ requestId, err }, 'Unhandled Server Error');
  }

  return { error: isProd && status >= 500 ? 'internal_error' : msg };
}
