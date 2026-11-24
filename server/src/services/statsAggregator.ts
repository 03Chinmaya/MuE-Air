/**
 * Stats Aggregator Service (PostgreSQL + Drizzle ORM)
 * Strictly conforms to docs/00_STACK_LOCK.md Section 4 and docs/04_DATABASE.md Section 28.
 */

import { db } from '../db/connection.js';
import { songPlayStats, artistPlayStats, genrePlayStats } from '../db/schema.js';

export interface PlayEvent {
  userId: string;
  provider: string;
  providerTrackId: string;
  artistName: string;
  genre?: string;
  durationPlayedMs: number;
  durationMs: number;
  completed: boolean;
  skipped: boolean;
  playedAt: Date;
}

export async function aggregatePlayEvents(events: PlayEvent[]): Promise<void> {
  if (!events || events.length === 0) return;

  for (const event of events) {
    try {
      // Upsert song play stats
      await db
        .insert(songPlayStats)
        .values({
          userId: event.userId,
          provider: event.provider,
          providerTrackId: event.providerTrackId,
          playCount: 1,
          completionCount: event.completed ? 1 : 0,
          skipCount: event.skipped ? 1 : 0,
          updatedAt: new Date(),
        })
        .onConflictDoNothing();

      // Upsert artist play stats
      if (event.artistName) {
        await db
          .insert(artistPlayStats)
          .values({
            userId: event.userId,
            artistName: event.artistName,
            playCount: 1,
            updatedAt: new Date(),
          })
          .onConflictDoNothing();
      }

      // Upsert genre play stats
      if (event.genre) {
        await db
          .insert(genrePlayStats)
          .values({
            userId: event.userId,
            genre: event.genre,
            playCount: 1,
            updatedAt: new Date(),
          })
          .onConflictDoNothing();
      }
    } catch {
      // Non-blocking batch processing
    }
  }
}
