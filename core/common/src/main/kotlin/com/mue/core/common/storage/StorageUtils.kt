package com.mue.core.common.storage

import android.content.Context
import java.io.File

object StorageUtils {
    fun getCacheDir(context: Context): File = File(context.cacheDir, "audio").apply { mkdirs() }
    fun getDownloadDir(context: Context): File = File(context.filesDir, "downloads").apply { mkdirs() }
}

