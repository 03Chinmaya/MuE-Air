package com.mue.core.providers

import android.util.Log
import com.mue.core.database.SongDao
import com.mue.core.database.toDomain
import com.mue.core.database.toEntity
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import com.mue.core.providerchain.Cap
import com.mue.core.providerchain.ProviderChains
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchCoordinator @Inject constructor(
    private val songDao: SongDao,
    private val innerTubeXProvider: InnerTubeXProvider,
    private val serverYouTubeProvider: ServerYouTubeProvider,
    private val jioSaavnProvider: JioSaavnProvider,
    private val jamendoProvider: JamendoProvider
) {
    companion object {
        private const val TAG = "SearchCoordinator"
        private const val HEDGE_DELAY_MS = 1200L
        private const val MERGE_WINDOW_MS = 400L
    }

    private val compilationKeywords = listOf(
        "mix", "mashup", "compilation", "jukebox", "nonstop", "top 100", "top hits",
        "best songs", "all songs", "audio jukebox"
    )

    private suspend fun executeMetadataLink(linkId: String, query: String): List<Track> {
        return try {
            when (linkId) {
                "innertubex" -> innerTubeXProvider.search(query)
                "server-youtubei" -> serverYouTubeProvider.search(query, null, "youtubei")
                "server-ytmusicapi" -> serverYouTubeProvider.search(query, null, "ytmusicapi")
                "jiosaavn" -> jioSaavnProvider.queryTracks(query)
                "jamendo" -> jamendoProvider.search(query)
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search link '$linkId' failed: ${e.message}")
            emptyList()
        }
    }

    private fun rankAndFilterTracks(
        tracks: List<Track>,
        query: String,
        chainRanks: Map<String, Int> = emptyMap()
    ): List<Track> {
        val qLower = query.lowercase().trim()
        val qTokens = qLower.split(Regex("\\s+")).filter { it.isNotBlank() }
        val isExplicitQuery = Regex("\\b(cover|remix|live|acoustic|flip)\\b", RegexOption.IGNORE_CASE).containsMatchIn(query)

        // Drop AI, Counterfeit, UGC (unless explicit), and blacklisted terms (unless in query)
        val filtered = tracks.filter { !ContentQualityFilter.shouldReject(it, query) }

        // Score and rank
        val scoredTracks = filtered.map { track ->
            val titleLower = track.title.lowercase().trim()
            val artistLower = track.artist.lowercase().trim()
            val albumLower = (track.album ?: "").lowercase().trim()

            var score = 0

            // Chain rank boost: InnerTubeX outranks JioSaavn/Jamendo
            val chainRank = chainRanks[track.id] ?: when (track.provider) {
                ProviderId.YOUTUBE_MUSIC -> 0
                ProviderId.JIOSAAVN -> 2
                ProviderId.JAMENDO -> 3
                else -> 4
            }
            score += (10 - minOf(chainRank, 10)) * 50

            // 1. Exact/Normalized title match
            val normTitle = titleLower.replace(Regex("[^a-z0-9]"), "")
            val normQuery = qLower.replace(Regex("[^a-z0-9]"), "")
            if (normTitle == normQuery) {
                score += 100
            } else if (titleLower.startsWith(qLower)) {
                score += 60
            } else if (titleLower.contains(qLower)) {
                score += 40
            }

            // Word-level token match
            val matchedTokens = qTokens.count { token -> titleLower.contains(token) || artistLower.contains(token) }
            score += matchedTokens * 10

            // 2. Genuine Release Boost
            val isGenuine = !track.album.isNullOrBlank() &&
                !albumLower.contains("unknown") &&
                (track.provider == ProviderId.JIOSAAVN || track.durationMs > 0L)
            if (isGenuine) {
                score += 150
            }

            // 3. Provenance scoring with ATV boost and UGC penalty
            when (track.videoType) {
                "ATV" -> score += 200       // Official Art Track
                "OMV" -> score += 100       // Official Music Video
                "UGC" -> {
                    if (isExplicitQuery) score += 50 else score -= 300
                }
                else -> {}
            }

            // 4. Official artist presence
            if (artistLower.isNotBlank() && artistLower != "unknown artist" && artistLower != "various artists") {
                score += 20
            }

            // 5. Creator / Bootleg / Cover Penalty (-200) unless explicitly in search query
            val hasCreatorKeywords = ContentQualityFilter.BLACKLIST_KEYWORDS.any { kw -> titleLower.contains(kw) && !qLower.contains(kw) }
            if (hasCreatorKeywords) {
                score -= 200
            }

            // 6. Demote long mixes / compilations to the very bottom
            val isLongMix = (track.durationMs > 7 * 60 * 1000L) || compilationKeywords.any { titleLower.contains(it) }
            if (isLongMix && !qLower.contains("mix") && !qLower.contains("jukebox")) {
                score -= 250
            }

            track to (chainRank to score)
        }

        return scoredTracks
            .filter { (track, rankAndScore) ->
                val (_, score) = rankAndScore
                if (track.videoType == "UGC" && !isExplicitQuery && score < 0) false else true
            }
            .sortedWith(
                compareBy<Pair<Track, Pair<Int, Int>>> { it.second.first }
                    .thenByDescending { it.second.second }
            )
            .map { it.first }
    }

    fun search(query: String): Flow<List<Track>> = channelFlow {
        if (query.isBlank()) {
            send(emptyList())
            return@channelFlow
        }

        // 1. Instant local Room search
        val localResults = try {
            songDao.searchSongs("$query*").first().map { it.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
        if (localResults.isNotEmpty()) {
            send(rankAndFilterTracks(localResults, query))
        }

        // 2. Hedged provider chain execution
        try {
            coroutineScope {
                val metadataLinks = ProviderChains.metadata.filter { it.enabled && Cap.METADATA in it.caps }
                val collectedTracks = mutableListOf<Track>()
                val chainRanks = ConcurrentHashMap<String, Int>()

                val primaryLink = metadataLinks.firstOrNull()
                val fallbackLinks = metadataLinks.drop(1)

                val primaryDeferred = primaryLink?.let { link ->
                    async {
                        val res = withTimeoutOrNull(link.budgetMs) { executeMetadataLink(link.id, query) } ?: emptyList()
                        res.forEach { chainRanks[it.id] = 0 }
                        res
                    }
                }

                // Launch hedge timer: after 1200ms, start fallbacks in parallel if primary hasn't returned
                val fallbackDeferreds = async {
                    if (primaryDeferred != null && !primaryDeferred.isCompleted) {
                        delay(HEDGE_DELAY_MS)
                    }
                    if (primaryDeferred != null && primaryDeferred.isCompleted && primaryDeferred.await().isNotEmpty()) {
                        // Primary already yielded results; skip heavy fallbacks unless needed for merging
                        emptyList<Track>()
                    } else {
                        // Start fallbacks in parallel
                        val defs = fallbackLinks.mapIndexed { idx, link ->
                            async {
                                val res = withTimeoutOrNull(link.budgetMs) { executeMetadataLink(link.id, query) } ?: emptyList()
                                res.forEach { chainRanks[it.id] = idx + 1 }
                                res
                            }
                        }
                        defs.flatMap { it.await() }
                    }
                }

                val primaryRes = primaryDeferred?.await() ?: emptyList()
                if (primaryRes.isNotEmpty()) {
                    collectedTracks.addAll(primaryRes)
                    val currentDeduplicated = TrackDeduplicator.deduplicate(localResults + collectedTracks)
                    send(rankAndFilterTracks(currentDeduplicated, query, chainRanks))
                    // Allow late arrivals to merge for 400ms
                    withTimeoutOrNull(MERGE_WINDOW_MS) {
                        val lateRes = fallbackDeferreds.await()
                        collectedTracks.addAll(lateRes)
                    }
                } else {
                    val fallbackRes = fallbackDeferreds.await()
                    collectedTracks.addAll(fallbackRes)
                }

                val allCombined = TrackDeduplicator.deduplicate(localResults + collectedTracks)
                val rankedResults = rankAndFilterTracks(allCombined, query, chainRanks)
                send(rankedResults)

                // Cache remote results in Room database
                val remoteToCache = collectedTracks.map { it.toEntity() }
                if (remoteToCache.isNotEmpty()) {
                    try {
                        songDao.insertAll(remoteToCache)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remote search error for $query", e)
            if (localResults.isEmpty()) {
                send(emptyList())
            }
        }
    }
}
