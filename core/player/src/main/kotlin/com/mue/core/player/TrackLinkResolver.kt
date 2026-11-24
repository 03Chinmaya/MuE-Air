package com.mue.core.player

import android.util.Log
import com.mue.core.database.SongDao
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import com.mue.core.providers.InnerTubeXProvider
import com.mue.core.providers.ServerYouTubeProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackLinkResolver @Inject constructor(
    private val songDao: SongDao,
    private val innerTubeXProvider: InnerTubeXProvider,
    private val serverYouTubeProvider: ServerYouTubeProvider
) {
    companion object {
        private const val TAG = "TrackLinkResolver"
    }

    suspend fun resolveYtmVideoId(track: Track): String? {
        if (!track.youtubeVideoId.isNullOrBlank()) {
            return track.youtubeVideoId
        }

        if (track.provider == ProviderId.YOUTUBE_MUSIC && track.id.isNotBlank()) {
            return track.id
        }

        try {
            val cachedSong = songDao.getSongById(track.id)
            if (!cachedSong?.youtubeVideoId.isNullOrBlank()) {
                return cachedSong!!.youtubeVideoId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check cached song from database for track ${track.id}", e)
        }

        return try {
            val query = "${track.title} ${track.artist}".trim()
            if (query.isBlank()) return null

            val results = try {
                val itx = innerTubeXProvider.search(query)
                if (itx.isNotEmpty()) itx else serverYouTubeProvider.search(query)
            } catch (_: Exception) {
                serverYouTubeProvider.search(query)
            }
            val matchedTrack = results.firstOrNull()
            val videoId = matchedTrack?.youtubeVideoId?.takeIf { it.isNotBlank() }
                ?: matchedTrack?.id?.takeIf { it.isNotBlank() }

            if (!videoId.isNullOrBlank()) {
                try {
                    songDao.updateYoutubeVideoId(track.id, videoId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cache resolved youtubeVideoId for track ${track.id}", e)
                }
                videoId
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving YTM video ID for track: ${track.id} (${track.title})", e)
            null
        }
    }
}
