package com.mue.core.providers

import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class TrackMatcher @Inject constructor(
    private val jioSaavnProvider: JioSaavnProvider
) {
    suspend fun match(ytTrack: Track): Track? {
        val cleanTitle = normalize(ytTrack.title)
        val cleanArtist = normalize(ytTrack.artist)
        val query = "$cleanTitle $cleanArtist".trim()
        if (query.isBlank()) return null

        val candidates = jioSaavnProvider.queryTracks(query)
        if (candidates.isEmpty()) return null

        var bestMatch: Track? = null
        var bestScore = 0

        for (candidate in candidates) {
            val candTitle = normalize(candidate.title)
            val candArtist = normalize(candidate.artist)

            var score = 0

            // Title matching
            if (candTitle == cleanTitle) {
                score += 50
            } else if (candTitle.contains(cleanTitle) || cleanTitle.contains(candTitle)) {
                score += 35
            } else {
                val words1 = cleanTitle.split(" ").filter { it.length > 2 }
                val words2 = candTitle.split(" ").filter { it.length > 2 }
                val common = words1.intersect(words2.toSet()).size
                if (common > 0) {
                    score += (common * 15).coerceAtMost(30)
                }
            }

            // Artist matching
            if (candArtist.isNotEmpty() && cleanArtist.isNotEmpty()) {
                if (candArtist == cleanArtist || candArtist.contains(cleanArtist) || cleanArtist.contains(candArtist)) {
                    score += 30
                } else {
                    val artists1 = cleanArtist.split(" ", ",").filter { it.length > 2 }
                    val artists2 = candArtist.split(" ", ",").filter { it.length > 2 }
                    val commonArtists = artists1.intersect(artists2.toSet()).size
                    if (commonArtists > 0) {
                        score += 20
                    }
                }
            }

            // Duration tolerance (within 5000 ms = +20 pts, within 10000 ms = +10 pts)
            if (ytTrack.durationMs > 0 && candidate.durationMs > 0) {
                val diff = abs(ytTrack.durationMs - candidate.durationMs)
                if (diff <= 5000) {
                    score += 20
                } else if (diff <= 10000) {
                    score += 10
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestMatch = candidate
            }
        }

        val matched = if (bestScore >= 40) bestMatch else candidates.firstOrNull()
        return matched?.copy(youtubeVideoId = ytTrack.youtubeVideoId ?: ytTrack.id)
    }

    private fun normalize(str: String): String {
        return str.lowercase()
            .replace(Regex("\\(official (video|audio|music video|lyric video)\\)"), "")
            .replace(Regex("\\[(official (video|audio|music video|lyric video))\\]"), "")
            .replace(Regex("\\(lyrics\\)"), "")
            .replace(Regex("\\[lyrics\\]"), "")
            .replace(Regex("\\(feat\\..*?\\)"), "")
            .replace(Regex("\\[feat\\..*?\\]"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
