package com.mue.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager as Media3DownloadManager
import com.mue.core.datastore.SettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object MediaCacheModule {

    const val DEFAULT_CACHE_SIZE_BYTES = 200 * 1024 * 1024L // 200 MB

    @Provides
    @Singleton
    fun provideDatabaseProvider(
        @ApplicationContext context: Context
    ): DatabaseProvider {
        return StandaloneDatabaseProvider(context)
    }

    @Provides
    @Singleton
    fun provideDynamicLruCacheEvictor(
        settingsDataStore: SettingsDataStore
    ): DynamicLruCacheEvictor {
        val limitMb = runCatching {
            runBlocking { settingsDataStore.cacheLimitMbFlow.firstOrNull() }
        }.getOrNull() ?: 512
        return DynamicLruCacheEvictor().apply {
            setInitialMaxBytes(limitMb * 1024L * 1024L)
        }
    }

    @Provides
    @Singleton
    fun provideSimpleCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        evictor: DynamicLruCacheEvictor
    ): SimpleCache {
        val cacheDir = File(context.cacheDir, "media_cache").apply { mkdirs() }
        return SimpleCache(cacheDir, evictor, databaseProvider)
    }

    @Provides
    @Singleton
    fun provideUpstreamDataSourceFactory(
        @ApplicationContext context: Context
    ): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
        return DefaultDataSource.Factory(context, httpDataSourceFactory)
    }

    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        simpleCache: SimpleCache,
        upstreamFactory: DataSource.Factory
    ): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Provides
    @Singleton
    fun provideMedia3DownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        simpleCache: SimpleCache,
        upstreamFactory: DataSource.Factory
    ): Media3DownloadManager {
        val executor = Executors.newFixedThreadPool(2)
        return Media3DownloadManager(
            context,
            databaseProvider,
            simpleCache,
            upstreamFactory,
            executor
        ).apply {
            maxParallelDownloads = 3
        }
    }
}

