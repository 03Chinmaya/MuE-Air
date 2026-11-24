/**
 * Guest Merge Service
 * Merges guest user data into an authenticated user account inside a single transaction.
 * Implements Phase 6 Multi-Device Guest->Auth merge specification.
 */

import { eq } from 'drizzle-orm';
import { pgClientAuth, authDb } from '../db/connection.js';
import { users, guestMergeLog } from '../db/schema.js';

export interface MergeResult {
  merged: boolean;
  alreadyMerged?: boolean;
  authId: string;
}

export async function mergeGuestIntoAuth(
  guestId: string,
  authId: string,
  deviceId?: string,
  clientBatchId?: string
): Promise<MergeResult> {
  if (!guestId || !authId) {
    throw new Error('Both guestId and authId are required');
  }

  if (guestId === authId) {
    throw new Error('Cannot merge user into themselves');
  }

  // 0. Check clientBatchId idempotency
  if (clientBatchId) {
    const [existingBatch] = await authDb
      .select()
      .from(guestMergeLog)
      .where(eq(guestMergeLog.clientBatchId, clientBatchId))
      .limit(1);
    if (existingBatch) {
      return { merged: true, alreadyMerged: true, authId: existingBatch.authId };
    }
  }

  // 1. Check guest user state
  const [guest] = await authDb
    .select()
    .from(users)
    .where(eq(users.id, guestId))
    .limit(1);

  if (!guest) {
    throw new Error('Guest user not found');
  }

  if (guest.mergedInto) {
    return { merged: true, alreadyMerged: true, authId: guest.mergedInto };
  }

  if (!guest.isGuest) {
    throw new Error('Target source user is not a guest account');
  }

  // 2. Check auth user state
  const [authUser] = await authDb
    .select()
    .from(users)
    .where(eq(users.id, authId))
    .limit(1);

  if (!authUser) {
    throw new Error('Authenticated user not found');
  }

  if (authUser.isGuest) {
    throw new Error('Destination account cannot be a guest account');
  }

  const validDeviceId: string | null = (deviceId && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(deviceId)) ? deviceId : null;

  // 3. Perform transactional per-table merge
  try {
    await pgClientAuth.begin(async (tx) => {
      // 1. user_play_history
      await tx`UPDATE user_play_history SET user_id = ${authId} WHERE user_id = ${guestId};`;

      // 2. user_likes (dedupe on provider + provider_track_id)
      await tx`
        UPDATE user_likes SET user_id = ${authId}
        WHERE user_id = ${guestId}
          AND NOT EXISTS (
            SELECT 1 FROM user_likes u2
            WHERE u2.user_id = ${authId}
              AND u2.provider = user_likes.provider
              AND u2.provider_track_id = user_likes.provider_track_id
          );
      `;
      await tx`DELETE FROM user_likes WHERE user_id = ${guestId};`;

      // 3. song_play_stats (sum on conflict)
      await tx`
        INSERT INTO song_play_stats (user_id, provider, provider_track_id, play_count, completion_count, skip_count, updated_at)
        SELECT ${authId}, provider, provider_track_id, play_count, completion_count, skip_count, updated_at
        FROM song_play_stats WHERE user_id = ${guestId}
        ON CONFLICT (user_id, provider, provider_track_id)
        DO UPDATE SET
          play_count = song_play_stats.play_count + EXCLUDED.play_count,
          completion_count = song_play_stats.completion_count + EXCLUDED.completion_count,
          skip_count = song_play_stats.skip_count + EXCLUDED.skip_count,
          updated_at = NOW();
      `;
      await tx`DELETE FROM song_play_stats WHERE user_id = ${guestId};`;

      // 4. artist_play_stats and genre_play_stats (sum on conflict)
      await tx`
        INSERT INTO artist_play_stats (user_id, artist_name, play_count, updated_at)
        SELECT ${authId}, artist_name, play_count, updated_at
        FROM artist_play_stats WHERE user_id = ${guestId}
        ON CONFLICT (user_id, artist_name)
        DO UPDATE SET
          play_count = artist_play_stats.play_count + EXCLUDED.play_count,
          updated_at = NOW();
      `;
      await tx`DELETE FROM artist_play_stats WHERE user_id = ${guestId};`;

      await tx`
        INSERT INTO genre_play_stats (user_id, genre, play_count, updated_at)
        SELECT ${authId}, genre, play_count, updated_at
        FROM genre_play_stats WHERE user_id = ${guestId}
        ON CONFLICT (user_id, genre)
        DO UPDATE SET
          play_count = genre_play_stats.play_count + EXCLUDED.play_count,
          updated_at = NOW();
      `;
      await tx`DELETE FROM genre_play_stats WHERE user_id = ${guestId};`;

      // 5. user_playlists + user_playlist_tracks (rename on name collision)
      await tx`
        UPDATE user_playlists
        SET name = name || ' (imported)'
        WHERE user_id = ${guestId}
          AND EXISTS (
            SELECT 1 FROM user_playlists u2
            WHERE u2.user_id = ${authId} AND u2.name = user_playlists.name
          );
      `;
      await tx`UPDATE user_playlists SET user_id = ${authId} WHERE user_id = ${guestId};`;

      // 6. imported_playlists (dedupe on provider + external_id)
      await tx`
        DELETE FROM imported_playlists
        WHERE user_id = ${guestId}
          AND EXISTS (
            SELECT 1 FROM imported_playlists ip2
            WHERE ip2.user_id = ${authId}
              AND ip2.provider = imported_playlists.provider
              AND ip2.external_id = imported_playlists.external_id
          );
      `;
      await tx`UPDATE imported_playlists SET user_id = ${authId} WHERE user_id = ${guestId};`;

      // 7. user_music_integrations (auth user wins)
      await tx`
        DELETE FROM user_music_integrations
        WHERE user_id = ${guestId}
          AND EXISTS (
            SELECT 1 FROM user_music_integrations umi2
            WHERE umi2.user_id = ${authId}
              AND umi2.provider = user_music_integrations.provider
          );
      `;
      await tx`UPDATE user_music_integrations SET user_id = ${authId} WHERE user_id = ${guestId};`;

      // 8. user_devices (drop duplicate push tokens and null tokens)
      await tx`
        DELETE FROM user_devices
        WHERE user_id = ${guestId}
          AND (
            push_token IS NULL
            OR EXISTS (
              SELECT 1 FROM user_devices ud2
              WHERE ud2.user_id = ${authId} AND ud2.push_token = user_devices.push_token
            )
          );
      `;
      await tx`UPDATE user_devices SET user_id = ${authId} WHERE user_id = ${guestId};`;

      // 9. user_sessions
      await tx`DELETE FROM user_sessions WHERE user_id = ${guestId};`;

      // 10. listen_participants (dedupe on sessionId + userId)
      await tx`
        DELETE FROM listen_participants
        WHERE user_id = ${guestId}
          AND EXISTS (
            SELECT 1 FROM listen_participants lp2
            WHERE lp2.session_id = listen_participants.session_id
              AND lp2.user_id = ${authId}
          );
      `;
      await tx`UPDATE listen_participants SET user_id = ${authId} WHERE user_id = ${guestId};`;

      // 11. chat_participants (dedupe on conversationId + userId)
      await tx`
        DELETE FROM chat_participants
        WHERE user_id = ${guestId}
          AND EXISTS (
            SELECT 1 FROM chat_participants cp2
            WHERE cp2.conversation_id = chat_participants.conversation_id
              AND cp2.user_id = ${authId}
          );
      `;
      await tx`UPDATE chat_participants SET user_id = ${authId} WHERE user_id = ${guestId};`;

      // 12. user_preferences (auth user preferences win)
      await tx`DELETE FROM user_preferences WHERE user_id = ${guestId};`;

      // 13. Mark guest as merged into auth user
      await tx`
        UPDATE users
        SET is_guest = false, merged_into = ${authId}, merged_at = NOW()
        WHERE id = ${guestId};
      `;

      // 14. Insert success record in guest_merge_log
      await tx`
        INSERT INTO guest_merge_log (guest_id, auth_id, device_id, client_batch_id, status, merged_at)
        VALUES (${guestId}, ${authId}, ${validDeviceId}, ${clientBatchId ?? null}, 'complete', NOW());
      `;
    });

    return { merged: true, alreadyMerged: false, authId };
  } catch (err: unknown) {
    const errorMsg: string = err instanceof Error ? err.message : String(err ?? 'unknown error');
    // Record rollback in separate non-transactional connection
    try {
      await pgClientAuth`
        INSERT INTO guest_merge_log (guest_id, auth_id, device_id, client_batch_id, status, error, merged_at)
        VALUES (${guestId}, ${authId}, ${validDeviceId}, ${clientBatchId ?? null}, 'rolled_back', ${errorMsg}, NOW());
      `;
    } catch {
      // Ignore logging failure to rethrow original error
    }
    throw err;
  }
}
