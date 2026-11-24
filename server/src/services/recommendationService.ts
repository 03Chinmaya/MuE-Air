/**
 * Cloud Recommendation Service (PostgreSQL + Drizzle ORM)
 * Generates recommendation candidate pools based on cloud aggregate play stats.
 * Conforms to docs/05_API.md Section 13 and docs/00_STACK_LOCK.md Section 5.
 */

import { eq, desc } from 'drizzle-orm';
import { db } from '../db/connection.js';
import { songPlayStats, artistPlayStats } from '../db/schema.js';

export interface ScoredTrack {
  provider: string;
  providerTrackId: string;
  score: number;
}

export async function getUserTopTracks(userId: string, limit = 20): Promise<ScoredTrack[]> {
  try {
    const rows = await db
      .select()
      .from(songPlayStats)
      .where(eq(songPlayStats.userId, userId))
      .orderBy(desc(songPlayStats.playCount))
      .limit(limit);

    return rows.map((r) => ({
      provider: r.provider,
      providerTrackId: r.providerTrackId,
      score: (r.playCount || 0) * 2 + (r.completionCount || 0) * 3 - (r.skipCount || 0),
    }));
  } catch {
    return [];
  }
}

export async function getUserTopArtists(userId: string, limit = 10): Promise<string[]> {
  try {
    const rows = await db
      .select({ artistName: artistPlayStats.artistName })
      .from(artistPlayStats)
      .where(eq(artistPlayStats.userId, userId))
      .orderBy(desc(artistPlayStats.playCount))
      .limit(limit);

    return rows.map((r) => r.artistName);
  } catch {
    return [];
  }
}
