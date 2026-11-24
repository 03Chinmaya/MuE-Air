package com.mue.core.player

import android.util.Log
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import com.mue.core.providers.InnerTubeXProvider
import com.mue.core.providers.ServerYouTubeProvider
import com.mue.core.providers.TrackMatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueRecommendationEngine @Inject constructor(
    private val innerTubeXProvider: InnerTubeXProvider,
    private val serverYouTubeProvider: ServerYouTubeProvider,
    private val trackMatcher: TrackMatcher
) {
    companion object {
        private const val TAG = "QueueRecoEngine"
        private const val MAX_PER_ARTIST = 2
        private const val TARGET_CANDIDATES = 10
    }

    suspend fun generateRadioCandidates(
        seedTrack: Track,
        recentTracks: List<Track> = emptyList()
    ): List<Track> {
        return try {
            val recentTrackIds = recentTracks.map { it.id }.toSet()
            val seedArtist = seedTrack.artist.trim()

            // 1. Fetch related candidates using seed artist / title context
            val query = if (seedArtist.isNotBlank() && seedArtist != "Unknown Artist") {
                seedArtist
            } else {
                seedTrack.title
            }
            val searchResults = try {
                val itx = innerTubeXProvider.search(query)
                if (itx.isNotEmpty()) itx else serverYouTubeProvider.search(query)
            } catch (_: Exception) {
                serverYouTubeProvider.search(query)
            }

            if (searchResults.isEmpty()) return emptyList()

            // Filter out exact seed track and already queued/recent tracks
            val filteredCandidates = searchResults.filter { candidate ->
                candidate.id != seedTrack.id &&
                !candidate.title.equals(seedTrack.title, ignoreCase = true) &&
                !recentTrackIds.contains(candidate.id)
            }

            // 2. Score candidates based on artist similarity and novelty
            val scoredCandidates = filteredCandidates.map { candidate ->
                var score = 1.0 // Base novelty score
                if (candidate.artist.equals(seedArtist, ignoreCase = true)) {
                    score += 2.0 // Exact artist match
                } else if (candidate.artist.contains(seedArtist, ignoreCase = true) || seedArtist.contains(candidate.artist, ignoreCase = true)) {
                    score += 1.5 // Partial artist affinity
                }
                candidate to score
            }.sortedByDescending { it.second }.map { it.first }

            // 3. Cap artist repetition to max 2 tracks per artist
            val artistCounts = mutableMapOf<String, Int>()
            val cappedList = mutableListOf<Track>()

            for (track in scoredCandidates) {
                val artistKey = track.artist.lowercase().trim()
                val count = artistCounts.getOrDefault(artistKey, 0)
                if (count < MAX_PER_ARTIST) {
                    artistCounts[artistKey] = count + 1
                    cappedList.add(track)
                }
                if (cappedList.size >= TARGET_CANDIDATES) break
            }

            // 4. Map candidates to JioSaavn CDN audio via TrackMatcher where possible
            cappedList.map { track ->
                if (track.provider == ProviderId.YOUTUBE_MUSIC) {
                    runCatching { trackMatcher.match(track) }.getOrNull() ?: track
                } else {
                    track
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating radio candidates for seed track: ${seedTrack.title}", e)
            emptyList()
        }
    }
}
