package com.mue.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.mue.core.common.CrashHandler
import com.mue.core.network.ReliableDns
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import timber.log.Timber

@HiltAndroidApp
class MuEApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .dns(ReliableDns)
            .build()
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
