package com.example.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil.Coil
import java.io.File

/**
 * Process-wide Media3 LRU disk cache for online video streams so backward skips,
 * repeated seeks, and unstable connections reuse already-downloaded chunks locally
 * instead of restarting HTTP requests from byte 0.
 */
@OptIn(UnstableApi::class)
object LumaStreamCache {

    private const val STREAM_CACHE_DIR = "luma_stream_cache"

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var configuredMaxMb: Int = -1

    @Synchronized
    fun getOrCreateCache(context: Context, maxSizeMb: Int): SimpleCache? {
        if (maxSizeMb <= 0) {
            releaseInternal()
            return null
        }
        val existing = simpleCache
        if (existing != null && configuredMaxMb == maxSizeMb) {
            return existing
        }
        releaseInternal()
        return runCatching {
            val appCtx = context.applicationContext
            val dir = File(appCtx.cacheDir, STREAM_CACHE_DIR).apply { mkdirs() }
            val evictor = LeastRecentlyUsedCacheEvictor(maxSizeMb.toLong() * 1024L * 1024L)
            val dbProvider = StandaloneDatabaseProvider(appCtx)
            SimpleCache(dir, evictor, dbProvider).also {
                simpleCache = it
                configuredMaxMb = maxSizeMb
            }
        }.getOrNull()
    }

    @Synchronized
    fun clearStreamCache(context: Context) {
        releaseInternal()
        val dir = File(context.applicationContext.cacheDir, STREAM_CACHE_DIR)
        runCatching { dir.deleteRecursively() }
    }

    fun computeStreamCacheBytes(context: Context): Long {
        val dir = File(context.applicationContext.cacheDir, STREAM_CACHE_DIR)
        return directorySizeBytes(dir)
    }

    fun computeThumbnailCacheBytes(context: Context): Long {
        val coilDir = File(context.applicationContext.cacheDir, "image_cache")
        return directorySizeBytes(coilDir)
    }

    @coil.annotation.ExperimentalCoilApi
    fun clearThumbnailCache(context: Context) {
        runCatching {
            val loader = Coil.imageLoader(context.applicationContext)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        val coilDir = File(context.applicationContext.cacheDir, "image_cache")
        runCatching { coilDir.deleteRecursively() }
    }

    @Synchronized
    fun release() {
        releaseInternal()
    }

    private fun releaseInternal() {
        runCatching { simpleCache?.release() }
        simpleCache = null
        configuredMaxMb = -1
    }

    private fun directorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                total += file.length()
            }
        }
        return total
    }
}
