import { pgClientAuth } from '../db/connection.js';
import { logger } from '../lib/logger.js';

export async function runGuestCleanup(): Promise<number> {
  try {
    const deleted = await pgClientAuth<{ id: string }[]>`
      DELETE FROM users
      WHERE is_guest = true
        AND merged_into IS NULL
        AND last_seen_at < now() - interval '90 days'
      RETURNING id;
    `;
    const count = deleted.length;
    logger.info({ deletedGuestCount: count }, '[GuestCleanup] Removed stale unmerged guests');
    return count;
  } catch (err) {
    logger.error({ err }, '[GuestCleanup] Failed to clean up stale guests');
    return 0;
  }
}
