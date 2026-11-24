package com.mue.core.lyrics

import com.mue.core.lyrics.model.TrackLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface LyricsRepository {
    fun getLyrics(trackId: String, title: String, artist: String): Flow<TrackLyrics?>
    suspend fun prefetchLyrics(trackId: String, title: String, artist: String)
}

@Singleton
class DefaultLyricsRepository @Inject constructor(
    private val apiClient: LrcLibApiClient
) : LyricsRepository {

    private val cache = ConcurrentHashMap<String, TrackLyrics>()

    override fun getLyrics(trackId: String, title: String, artist: String): Flow<TrackLyrics?> = flow {
        val cached = cache[trackId]
        if (cached != null) {
            emit(cached)
            return@flow
        }

        try {
            val raw = apiClient.fetchLyrics(title = title, artist = artist)
            if (!raw.isNullOrBlank()) {
                val parsed = withContext(Dispatchers.Default) {
                    EnhancedLrcParser.parse(trackId, raw)
                }
                cache[trackId] = parsed
                emit(parsed)
            } else {
                emit(null)
            }
        } catch (_: Exception) {
            emit(null)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun prefetchLyrics(trackId: String, title: String, artist: String) {
        if (cache.containsKey(trackId)) return
        try {
            val raw = apiClient.fetchLyrics(title = title, artist = artist)
            if (!raw.isNullOrBlank()) {
                val parsed = withContext(Dispatchers.Default) {
                    EnhancedLrcParser.parse(trackId, raw)
                }
                cache[trackId] = parsed
            }
        } catch (_: Exception) {
            // Silently ignore prefetch errors
        }
    }
}
