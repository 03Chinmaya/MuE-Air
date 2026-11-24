package com.mue.core.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager as Media3DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService as Media3DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class DownloadService : Media3DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description
) {
    @Inject
    lateinit var media3DownloadManager: Media3DownloadManager

    private val notificationHelper by lazy {
        DownloadNotificationHelper(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                "MuE Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress and completion notifications"
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun getDownloadManager(): Media3DownloadManager = media3DownloadManager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return notificationHelper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 2001
        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "mue_downloads_channel"
    }
}

