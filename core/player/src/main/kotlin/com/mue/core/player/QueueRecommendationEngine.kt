package com.mue.core.player

import android.util.Log
import com.mue.core.database.SongDao
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import com.mue.core.providers.ContentQualityFilter
import com.mue.core.providers.InnerTubeXProvider
import com.mue.core.providers.ServerYouTubeProvider
import com.mue.core.providers.TrackMatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueRecommendationEngine @Inject constructor(
    private val innerTubeXProvider: InnerTubeXProvider,
    private val serverYouTubeProvider: ServerYouTubeProvider,
    private val trackMatcher: TrackMatcher,
    private val songDao: SongDao
) {
    companion object {
        private const val TAG = "QueueRecoEngine"
        private const val MAX_PER_ARTIST = 2
        private const val MAX_FOR_SEED_ARTIST = 4
        private const val TARGET_CANDIDATES = 10
    }

    suspend fun generateRadioCandidates(
        seedTrack: Track,
        recentTracks: List<Track> = emptyList(),
        sessionTasteVector: SessionTasteVector = SessionTasteVector()
    ): List<Track> {
        return try {
            val recentTrackIds = recentTracks.map { it.id }.toSet()
            val seedArtist = seedTrack.artist.trim()
            val seedArtistKey = seedArtist.lowercase()
            val seedVideoId = seedTrack.youtubeVideoId ?: seedTrack.id

            // 1. Fetch related candidates via RDAMVM<lastTrackId> radio, fallback to artist search
            val radioResults = runCatching {
                innerTubeXProvider.getRadioPlaylist(seedVideoId)
            }.getOrDefault(emptyList())

            val searchResults = if (radioResults.isNotEmpty()) {
                radioResults
            } else {
                val query = if (seedArtist.isNotBlank() && seedArtist != "Unknown Artist") {
                    seedArtist
                } else {
                    seedTrack.title
                }
                try {
                    val itx = innerTubeXProvider.search(query)
                    if (itx.isNotEmpty()) itx else serverYouTubeProvider.search(query)
                } catch (_: Exception) {
                    serverYouTubeProvider.search(query)
                }
            }

            if (searchResults.isEmpty()) return emptyList()

            // Filter out exact seed track, recent tracks, and heavily skipped artists (>=3 skips)
            val filteredCandidates = searchResults.filter { candidate ->
                val artistKey = candidate.artist.lowercase().trim()
                val skipPenalty = sessionTasteVector.penalizedArtists[artistKey] ?: 0
                candidate.id != seedTrack.id &&
                !candidate.title.equals(seedTrack.title, ignoreCase = true) &&
                !recentTrackIds.contains(candidate.id) &&
                skipPenalty < 3
            }

            // 2. Score candidates: 4-tier sorting + ATV preference + session taste penalties
            val seedLanguage = ContentQualityFilter.inferLanguage(seedTrack)
            val scoredCandidates = filteredCandidates.map { candidate ->
                var score = 1.0
                if (candidate.videoType == "ATV") {
                    score += 3.0 // Prefer catalogue Audio Track Video over OMV
                }

                val isExactSeedArtist = candidate.artist.equals(seedArtist, ignoreCase = true)
                val isSimilarArtist = !isExactSeedArtist && (
                    candidate.artist.contains(seedArtist, ignoreCase = true) ||
                        seedArtist.contains(candidate.artist, ignoreCase = true)
                    )
                val candidateLanguage = ContentQualityFilter.inferLanguage(candidate)
                val isSameLanguage = seedLanguage != null &&
                    candidateLanguage != null &&
                    seedLanguage.equals(candidateLanguage, ignoreCase = true)

                when {
                    isExactSeedArtist -> score += 40.0 // Tier 1: Seed Artist
                    isSameLanguage && isSimilarArtist -> score += 25.0 // Tier 2: Same Language + Artist Similarity
                    isSameLanguage -> score += 15.0 // Tier 3: Same Language (different artist)
                    isSimilarArtist -> score += 10.0 // Fallback artist similarity when language is unknown
                }

                // Tier 4: Play History (playCount > 0)
                val playCount = runCatching { songDao.getPlayCount(candidate.id) }.getOrNull() ?: 0
                if (playCount > 0) {
                    score += 5.0
                }

                val artistKey = candidate.artist.lowercase().trim()
                val penalty = sessionTasteVector.penalizedArtists[artistKey] ?: 0
                if (penalty > 0) {
                    score -= 10.0 * penalty
                }
                candidate to score
            }.sortedByDescending { it.second }.map { it.first }

            // 3. Enforce caps: max 2 per artist, max 4 for seed artist
            val artistCounts = mutableMapOf<String, Int>()
            val cappedList = mutableListOf<Track>()

            for (track in scoredCandidates) {
                val artistKey = track.artist.lowercase().trim()
                val count = artistCounts.getOrDefault(artistKey, 0)
                val cap = if (artistKey == seedArtistKey) MAX_FOR_SEED_ARTIST else MAX_PER_ARTIST
                if (count < cap) {
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
