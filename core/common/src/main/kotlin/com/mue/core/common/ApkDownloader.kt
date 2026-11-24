package com.mue.core.common

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object ApkDownloader {
    fun downloadApk(context: Context, downloadUrl: String, fileName: String = "MuE-Air-Update.apk"): Long {
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Downloading MuE-Air Update")
            .setDescription("Downloading latest version of MuE-Air...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        return manager?.enqueue(request) ?: -1L
    }

    fun triggerInstall(context: Context, fileUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(manageIntent)
            }
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    fun downloadAndInstall(context: Context, downloadUrl: String, fileName: String = "MuE-Air-Update.apk"): Long {
        val downloadId = downloadApk(context, downloadUrl, fileName)
        if (downloadId == -1L) return -1L

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    val dm = recvContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    val uri = dm?.getUriForDownloadedFile(downloadId)
                    if (uri != null) {
                        triggerInstall(recvContext, uri)
                    } else {
                        val file = File(recvContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                        if (file.exists()) {
                            val contentUri = FileProvider.getUriForFile(
                                recvContext,
                                "${recvContext.packageName}.fileprovider",
                                file
                            )
                            triggerInstall(recvContext, contentUri)
                        }
                    }
                    try {
                        recvContext.unregisterReceiver(this)
                    } catch (_: Exception) {}
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        return downloadId
    }
}
