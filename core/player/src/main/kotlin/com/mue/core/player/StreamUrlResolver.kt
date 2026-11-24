package com.mue.core.player

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.mue.core.common.storage.AudioCacheManager
import com.mue.core.datastore.SettingsDataStore
import com.mue.core.model.ProviderId
import com.mue.core.model.Track
import com.mue.core.providerchain.Cap
import com.mue.core.providerchain.ProviderChainExecutor
import com.mue.core.providerchain.ProviderChains
import com.mue.core.providers.InnerTubeXProvider
import com.mue.core.providers.JamendoProvider
import com.mue.core.providers.JioSaavnProvider
import com.mue.core.providers.ServerYouTubeProvider
import com.mue.core.providers.TrackMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamUrlResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val audioCacheManager: AudioCacheManager,
    private val settingsDataStore: SettingsDataStore,
    private val jamendoProvider: JamendoProvider,
    private val trackMatcher: TrackMatcher,
    private val jioSaavnProvider: JioSaavnProvider,
    private val innerTubeXProvider: InnerTubeXProvider,
    private val serverYouTubeProvider: ServerYouTubeProvider
) {
    companion object {
        private const val TAG = "StreamUrlResolver"
        private const val TOTAL_BUDGET_CAP_MS = 12000L
    }

    private val negativeJamendoMatches = ConcurrentHashMap.newKeySet<String>()

    private fun localMediaStoreQuery(title: String, artist: String): String? {
        return try {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA
            )
            val selection = "${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("%$title%", "%$title%")
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (dataIndex >= 0) {
                        val path = cursor.getString(dataIndex)
                        if (!path.isNullOrBlank()) return path
                    }
                    val idIndex = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    if (idIndex >= 0) {
                        val id = cursor.getLong(idIndex)
                        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Local MediaStore query failed: ${e.message}")
            null
        }
    }

    /**
     * Resolves playable audio stream URL according to ProviderChains.playback:
     * 1. downloaded file (offline fast-path, 0 latency)
     * 2. Jamendo FLAC (lossless) - only if Jamendo match exists; caches negative matches
     * 3. JioSaavn 320 kbps (lossy)
     * 4. InnerTubeX on-device stream
     * 5. InnerTune (disabled slot)
     * 6. Server youtubei.js stream
     * 7. Server ytmusic-api stream
     * 8. Local MediaStore track
     */
    suspend fun resolve(track: Track): String? = withTimeoutOrNull(TOTAL_BUDGET_CAP_MS) {
        val quality = runCatching { settingsDataStore.streamingQualityFlow.firstOrNull() }.getOrNull() ?: "Auto"
        val isLosslessPreferred = runCatching { settingsDataStore.losslessAudioFlow.firstOrNull() }.getOrNull() ?: false

        val result = ProviderChainExecutor.firstOf(
            links = ProviderChains.playback,
            capability = Cap.STREAM_LOSSY // Checks all stream and local caps in chain
        ) { link ->
            when (link.id) {
                "downloaded" -> withContext(Dispatchers.IO) {
                    downloadManager.getDownloadedPath(track.id)
                        ?: audioCacheManager.getCachedPath(track.id)
                }

                "jamendo" -> withContext(Dispatchers.IO) {
                    val matchKey = "${track.title}:::${track.artist}".lowercase().trim()
                    if (negativeJamendoMatches.contains(matchKey) && track.provider != ProviderId.JAMENDO && !isLosslessPreferred) {
                        return@withContext null
                    }

                    if (track.provider == ProviderId.JAMENDO) {
                        jamendoProvider.getPlaybackSource(track)?.streamUrl
                    } else {
                        val matches = jamendoProvider.search("${track.title} ${track.artist}")
                        val match = matches.firstOrNull()
                        if (match != null) {
                            jamendoProvider.getPlaybackSource(match)?.streamUrl
                        } else {
                            negativeJamendoMatches.add(matchKey)
                            null
                        }
                    }
                }

                "jiosaavn" -> withContext(Dispatchers.IO) {
                    val targetTrack = if (track.provider == ProviderId.YOUTUBE_MUSIC) {
                        runCatching { trackMatcher.match(track) }.getOrNull() ?: track
                    } else {
                        track
                    }
                    val directSource = jioSaavnProvider.getPlaybackSource(targetTrack, quality)
                    directSource?.streamUrl ?: run {
                        val matches = jioSaavnProvider.queryTracks("${track.title} ${track.artist}")
                        val match = matches.firstOrNull()
                        if (match != null) {
                            jioSaavnProvider.getPlaybackSource(match, quality)?.streamUrl
                        } else null
                    }
                }

                "innertubex" -> withContext(Dispatchers.IO) {
                    val videoId = track.youtubeVideoId ?: track.id
                    innerTubeXProvider.streamUrl(videoId)
                }

                "server-youtubei" -> withContext(Dispatchers.IO) {
                    serverYouTubeProvider.getPlaybackSource(track, quality)?.streamUrl
                }

                "server-ytmusicapi" -> withContext(Dispatchers.IO) {
                    serverYouTubeProvider.getPlaybackSource(track, quality)?.streamUrl
                }

                "local" -> withContext(Dispatchers.IO) {
                    localMediaStoreQuery(track.title, track.artist)
                }

                else -> null
            }
        }

        if (result != null) {
            Log.d(TAG, "Resolved track '${track.title}' (${track.id}) via linkId: '${result.linkId}' in ${result.durationMs}ms")
            result.data
        } else {
            Log.w(TAG, "All playback links in ProviderChains exhausted for track '${track.title}' (${track.id})")
            null
        }
    }
}
