package com.mue.core.common.storage

import android.content.Context
import com.mue.core.database.AudioCacheDao
import com.mue.core.database.AudioCacheEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioCacheDao: AudioCacheDao
) {
    private var maxCacheSizeBytes = 1_073_741_824L // 1 GB default

    fun setMaxSize(maxBytes: Long) {
        maxCacheSizeBytes = maxBytes
    }

    suspend fun clearAll() {
        audioCacheDao.deleteAll()
        runCatching {
            val dir = StorageUtils.getCacheDir(context)
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    suspend fun getSize(): Long {
        return runCatching {
            val dir = StorageUtils.getCacheDir(context)
            dir.listFiles()?.sumOf { it.length() } ?: 0L
        }.getOrDefault(0L)
    }

    suspend fun getCachedPath(songId: String): String? {
        val entry = audioCacheDao.getBySongId(songId) ?: return null
        val file = File(entry.filePath)
        if (file.exists()) return file.absolutePath
        audioCacheDao.deleteBySongId(songId)
        return null
    }

    suspend fun cacheFile(songId: String, inputStream: InputStream) {
        val file = File(StorageUtils.getCacheDir(context), "$songId.tmp")
        file.outputStream().use { inputStream.copyTo(it) }
        audioCacheDao.insert(AudioCacheEntity(songId, file.absolutePath, file.length()))
        enforceLruLimit()
    }

    private suspend fun enforceLruLimit() {
        var currentSize = audioCacheDao.getAllSortedByLru().sumOf { it.fileSize }
        val entries = audioCacheDao.getAllSortedByLru()
        for (entry in entries) {
            if (currentSize <= maxCacheSizeBytes) break
            File(entry.filePath).delete()
            audioCacheDao.deleteBySongId(entry.songId)
            currentSize -= entry.fileSize
        }
    }
}

