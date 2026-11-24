package com.mue.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import java.util.TreeSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom LRU cache evictor that supports runtime maximum byte limit updates
 * without requiring the SimpleCache instance to be recreated or restarted.
 */
@OptIn(UnstableApi::class)
@Singleton
class DynamicLruCacheEvictor @Inject constructor() : CacheEvictor, Comparator<CacheSpan> {

    private var maxBytes: Long = 512L * 1024L * 1024L // 512 MB default
    private val leastRecentlyUsed = TreeSet<CacheSpan>(this)
    private var currentSizeBytes: Long = 0

    fun setInitialMaxBytes(bytes: Long) {
        maxBytes = bytes
    }

    fun updateMaxBytes(newMaxBytes: Long, cache: Cache? = null) {
        maxBytes = newMaxBytes
        if (cache != null) {
            evictCache(cache, 0)
        }
    }

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() {}

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) {
            evictCache(cache, length)
        }
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        currentSizeBytes += span.length
        evictCache(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSizeBytes -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    override fun compare(span1: CacheSpan, span2: CacheSpan): Int {
        if (span1.lastTouchTimestamp - span2.lastTouchTimestamp == 0L) {
            return span1.compareTo(span2)
        }
        return if (span1.lastTouchTimestamp < span2.lastTouchTimestamp) -1 else 1
    }

    private fun evictCache(cache: Cache, requiredSpace: Long) {
        while (currentSizeBytes + requiredSpace > maxBytes && !leastRecentlyUsed.isEmpty()) {
            try {
                cache.removeSpan(leastRecentlyUsed.first())
            } catch (_: Exception) {
                break
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Singleton
class MediaCacheManager @Inject constructor(
    private val simpleCache: SimpleCache,
    private val evictor: DynamicLruCacheEvictor
) {
    fun updateCacheLimit(maxBytes: Long) {
        evictor.updateMaxBytes(maxBytes, simpleCache)
    }
}

