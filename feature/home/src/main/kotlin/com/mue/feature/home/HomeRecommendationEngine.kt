package com.mue.feature.home

import android.util.Log
import com.mue.core.database.LibraryDao
import com.mue.core.database.SongDao
import com.mue.core.database.toDomain
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import com.mue.core.providers.ContentQualityFilter
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class HomeRecommendationEngine @Inject constructor(
    private val libraryDao: LibraryDao,
    private val songDao: SongDao
) {
    companion object {
        private const val TAG = "HomeRecoEngine"
        private const val EPSILON = 0.15f // 15% exploration probability
    }

    private fun getTimePartition(timestampMs: Long): Int {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> 0 // Morning (05:00 - 11:59)
            in 12..17 -> 1 // Afternoon (12:00 - 17:59)
            else -> 2 // Night (18:00 - 04:59)
        }
    }

    private fun isSameTimePartition(t1: Long, t2: Long): Boolean {
        return getTimePartition(t1) == getTimePartition(t2)
    }

    suspend fun generateQuickPicks(fallbackTracks: List<Track> = emptyList()): List<Track> {
        return try {
            val history = libraryDao.getRecentHistory(200)
            if (history.isEmpty()) return fallbackTracks.take(21)

            val now = System.currentTimeMillis()
            val groupedByTrack = history.groupBy { "${it.provider}:::${it.providerTrackId}" }

            val scoredTracks = groupedByTrack.mapNotNull { (_, plays) ->
                var score = 0.0
                for (play in plays) {
                    val hoursAgo = (now - play.playedAt).coerceAtLeast(0L) / (1000.0 * 3600.0)
                    // Recency decay: 7-day half life (168 hours)
                    score += 1.0 * kotlin.math.exp(-hoursAgo / 168.0)

                    // Fast skip penalty
                    if (play.skippedFast) {
                        score -= 3.0
                    }

                    // Completion boost
                    if (play.completionPercentage >= 80 || play.completed) {
                        score += 2.0
                    }

                    // Time-of-day boost
                    if (isSameTimePartition(play.playedAt, now)) {
                        score += 1.0
                    }
                }

                if (score <= 0.0) {
                    null
                } else {
                    val latest = plays.maxByOrNull { it.playedAt } ?: plays.first()
                    val domainSong = songDao.getSongById(latest.providerTrackId)?.toDomain()
                    val track = domainSong ?: Track(
                        id = latest.providerTrackId,
                        title = latest.title,
                        artist = latest.artist,
                        album = latest.album ?: "",
                        artworkUrl = latest.artworkUrl,
                        durationMs = latest.durationMs,
                        provider = try { ProviderId.valueOf(latest.provider) } catch (_: Exception) { ProviderId.JIOSAAVN },
                        youtubeVideoId = if (latest.provider == ProviderId.YOUTUBE_MUSIC.name) latest.providerTrackId else null,
                        language = latest.language
                    )

                    if (ContentQualityFilter.isAI(track) ||
                        ContentQualityFilter.isCounterfeit(track) ||
                        track.videoType == "UGC"
                    ) {
                        null
                    } else {
                        track to score
                    }
                }
            }

            val scored = scoredTracks
                .sortedByDescending { it.second }
                .map { it.first }
                .take(21)

            if (scored.size < 21 && fallbackTracks.isNotEmpty()) {
                val scoredIds = scored.map { it.id }.toSet()
                val backfill = fallbackTracks
                    .filter { it.id !in scoredIds && !ContentQualityFilter.shouldReject(it) }
                    .take(21 - scored.size)
                scored + backfill
            } else {
                scored
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error generating quick picks", e)
            fallbackTracks.take(21)
        }
    }

    suspend fun rankCandidates(ytmCandidates: List<Track>): List<Track> {
        if (ytmCandidates.isEmpty()) return emptyList()

        return try {
            // Pre-filter: drop AI, counterfeit, UGC tracks, and tracks with no album and no videoType
            val filteredCandidates = ytmCandidates.filter { track ->
                val isAiOrCounterfeit = ContentQualityFilter.isAI(track) || ContentQualityFilter.isCounterfeit(track)
                val isUgc = track.videoType == "UGC"
                val isUnverified = track.videoType == null && track.albumId == null && track.album.isNullOrBlank()
                !isAiOrCounterfeit && !isUgc && !isUnverified
            }

            val history = libraryDao.getRecentHistory().firstOrNull() ?: emptyList()

            // Top 5 recent artists from playback history
            val topArtists = history
                .map { it.artist.lowercase().trim() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key }
                .toSet()

            val historyByTrackId = history.groupBy { it.providerTrackId }
            val historyByTitle = history.groupBy { it.title.lowercase().trim() }

            val scored = filteredCandidates.map { candidate ->
                var score = 0.0
                val playCount = songDao.getPlayCount(candidate.id) ?: 0

                // 1. Play count signal
                if (playCount >= 2) {
                    score += 2.0
                } else if (playCount == 0) {
                    // Novelty boost if never played
                    score += 1.0
                }

                // 2. Completion / Skip signals from history
                val trackHistory = historyByTrackId[candidate.id]
                    ?: historyByTitle[candidate.title.lowercase().trim()]
                    ?: emptyList()

                if (trackHistory.isNotEmpty()) {
                    val hasHighCompletion = trackHistory.any { it.completionPercentage >= 80 || it.completed }
                    val wasSkippedFast = trackHistory.any { it.skippedFast }

                    if (hasHighCompletion) {
                        score += 1.5
                    }
                    if (wasSkippedFast) {
                        score -= 3.0
                    }
                }

                // 3. Artist affinity signal
                val candidateArtist = candidate.artist.lowercase().trim()
                if (topArtists.any { candidateArtist.contains(it) || it.contains(candidateArtist) }) {
                    score += 1.0
                }

                // 4. Provenance boost (ATV +3.0, OMV +2.0)
                when (candidate.videoType) {
                    "ATV" -> score += 3.0
                    "OMV" -> score += 2.0
                }

                candidate to score
            }

            val ranked = scored.sortedByDescending { it.second }.map { it.first }

            // 5. Epsilon-Greedy exploration (15% chance to explore)
            if (Random.nextFloat() < EPSILON && ranked.size > 2) {
                val exploreCount = minOf(5, ranked.size)
                val topSlice = ranked.take(exploreCount).shuffled()
                val remainder = ranked.drop(exploreCount)
                topSlice + remainder
            } else {
                ranked
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error ranking candidates in HomeRecommendationEngine, falling back to original order", e)
            ytmCandidates
        }
    }
}
