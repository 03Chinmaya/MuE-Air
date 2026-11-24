package com.mue.core.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager as Media3DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService as Media3DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.mue.core.database.DownloadDao
import com.mue.core.database.DownloadEntity
import com.mue.core.datastore.SettingsDataStore
import com.mue.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val media3DownloadManager: Media3DownloadManager,
    private val simpleCache: SimpleCache,
    private val downloadDao: DownloadDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            settingsDataStore.downloadWifiOnlyFlow.collect { wifiOnly ->
                val requirements = if (wifiOnly) {
                    Requirements(Requirements.NETWORK_UNMETERED)
                } else {
                    Requirements(Requirements.NETWORK)
                }
                try {
                    media3DownloadManager.requirements = requirements
                    Media3DownloadService.sendSetRequirements(
                        context,
                        DownloadService::class.java,
                        requirements,
                        false
                    )
                } catch (_: Exception) {}
            }
        }

        media3DownloadManager.addListener(object : Media3DownloadManager.Listener {
            override fun onDownloadChanged(
                manager: Media3DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                val songId = download.request.id
                scope.launch {
                    when (download.state) {
                        Download.STATE_QUEUED -> {
                            downloadDao.updateStatus(songId, "QUEUED")
                        }
                        Download.STATE_DOWNLOADING -> {
                            downloadDao.updateProgress(songId, "DOWNLOADING", download.bytesDownloaded)
                        }
                        Download.STATE_COMPLETED -> {
                            downloadDao.updateProgress(songId, "COMPLETED", download.bytesDownloaded)
                        }
                        Download.STATE_FAILED -> {
                            downloadDao.updateStatus(songId, "FAILED")
                        }
                        Download.STATE_STOPPED -> {
                            downloadDao.updateStatus(songId, "PAUSED")
                        }
                        Download.STATE_REMOVING -> {
                            downloadDao.deleteBySongId(songId)
                        }
                        Download.STATE_RESTARTING -> {
                            downloadDao.updateStatus(songId, "QUEUED")
                        }
                    }
                }
            }
        })
    }

    suspend fun enqueueDownload(track: Track, streamUrl: String) {
        val wifiOnly = settingsDataStore.downloadWifiOnlyFlow.firstOrNull() ?: true
        val requirements = if (wifiOnly) {
            Requirements(Requirements.NETWORK_UNMETERED)
        } else {
            Requirements(Requirements.NETWORK)
        }
        try {
            media3DownloadManager.requirements = requirements
            Media3DownloadService.sendSetRequirements(
                context,
                DownloadService::class.java,
                requirements,
                false
            )
        } catch (_: Exception) {}

        val entity = DownloadEntity(
            songId = track.id,
            filePath = streamUrl,
            status = "QUEUED",
            title = track.title,
            artist = track.artist,
            artworkUrl = track.artworkUrl,
            durationMs = track.durationMs
        )
        downloadDao.insert(entity)

        val request = DownloadRequest.Builder(track.id, Uri.parse(streamUrl))
            .setCustomCacheKey(track.id)
            .build()

        try {
            Media3DownloadService.sendAddDownload(
                context,
                DownloadService::class.java,
                request,
                false
            )
        } catch (_: Exception) {
            media3DownloadManager.addDownload(request)
        }
    }

    fun pauseDownload() {
        try {
            Media3DownloadService.sendPauseDownloads(
                context,
                DownloadService::class.java,
                false
            )
        } catch (_: Exception) {
            media3DownloadManager.pauseDownloads()
        }
    }

    fun resumeDownload() {
        try {
            Media3DownloadService.sendResumeDownloads(
                context,
                DownloadService::class.java,
                false
            )
        } catch (_: Exception) {
            media3DownloadManager.resumeDownloads()
        }
    }

    fun cancelDownload(songId: String) {
        try {
            Media3DownloadService.sendRemoveDownload(
                context,
                DownloadService::class.java,
                songId,
                false
            )
        } catch (_: Exception) {
            media3DownloadManager.removeDownload(songId)
        }
        scope.launch {
            downloadDao.deleteBySongId(songId)
            runCatching { simpleCache.removeResource(songId) }
        }
    }

    fun clearAll() {
        try {
            Media3DownloadService.sendRemoveAllDownloads(
                context,
                DownloadService::class.java,
                false
            )
        } catch (_: Exception) {
            // fallback
        }
        scope.launch {
            downloadDao.deleteAll()
            for (key in simpleCache.keys.toList()) {
                runCatching { simpleCache.removeResource(key) }
            }
        }
    }

    suspend fun getDownloadedPath(songId: String): String? {
        val entry = downloadDao.getBySongId(songId) ?: return null
        if (entry.status == "COMPLETED") {
            return entry.filePath
        }
        return null
    }

    fun getCacheSpace(): Long {
        return runCatching { simpleCache.cacheSpace }.getOrDefault(0L)
    }

    fun getAllDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
}

