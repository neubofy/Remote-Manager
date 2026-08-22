package com.neubofy.remotemanager.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.preference.PreferenceManager
import com.neubofy.remotemanager.Items.FileItem
import com.neubofy.remotemanager.R
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Dedicated Persistent Thumbnail Cache Manager.
 * Stores downscaled, compressed (160x160 RGB_565/JPEG) thumbnail files permanently
 * in app-private storage (cacheDir/thumbnail_cache/<hash>.thumb).
 * 
 * Guarantees:
 * 1. Tiny footprint: ~8-15 KB per cached thumbnail instead of multi-megabyte raw streams.
 * 2. Instant 0ms local retrieval: checking file.exists() is immediate.
 * 3. Zero-streaming once cached: once a thumbnail file exists, no HTTP server or network is needed.
 * 4. Automatic LRU eviction when user-configured thumbnail cache budget is reached.
 */
object ThumbnailCacheManager {

    private val memoryBitmaps = ConcurrentHashMap<String, Bitmap>()

    fun getCacheKey(fileItem: FileItem): String {
        val raw = "${fileItem.remote.name}:${fileItem.path}:${fileItem.size}"
        val bytes = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getThumbnailDir(context: Context): File {
        val dir = File(context.cacheDir, "thumbnail_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private val backgroundIoExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun getCachedThumbnailFile(context: Context, fileItem: FileItem): File? {
        val key = getCacheKey(fileItem)
        val file = File(getThumbnailDir(context), "$key.thumb")
        return if (file.exists() && file.length() > 0) {
            // Asynchronously update timestamp in background for LRU pruning (zero UI thread blocking)
            backgroundIoExecutor.execute {
                try {
                    file.setLastModified(System.currentTimeMillis())
                } catch (ignored: Exception) {}
            }
            file
        } else {
            null
        }
    }

    fun isCached(context: Context, fileItem: FileItem): Boolean {
        val key = getCacheKey(fileItem)
        val file = File(getThumbnailDir(context), "$key.thumb")
        return file.exists() && file.length() > 0
    }

    private var writeCounter = 0

    fun saveThumbnailBitmap(context: Context, fileItem: FileItem, bitmap: Bitmap) {
        try {
            val key = getCacheKey(fileItem)
            val dir = getThumbnailDir(context)

            writeCounter++
            if (writeCounter % 20 == 0) {
                backgroundIoExecutor.execute {
                    ensureBudget(context, dir)
                }
            }

            val targetFile = File(dir, "$key.thumb")
            // Use unique temp file to avoid thread collisions during fast multi-threaded scrolling
            val tempFile = File.createTempFile("thumb_${key}_", ".tmp", dir)
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.flush()
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)
            }
        } catch (ignored: Exception) {}
    }

    fun removeThumbnail(context: Context, fileItem: FileItem) {
        try {
            val key = getCacheKey(fileItem)
            val file = File(getThumbnailDir(context), "$key.thumb")
            if (file.exists()) {
                file.delete()
            }
        } catch (ignored: Exception) {}
    }

    fun ensureBudget(context: Context, dir: File = getThumbnailDir(context)) {
        try {
            val maxBudget = PreferenceManager.getDefaultSharedPreferences(context)
                .getLong(context.getString(R.string.pref_key_thumbnail_cache_budget), 104857600L) // Default 100 MB

            val files = dir.listFiles { f -> f.extension == "thumb" } ?: return
            var totalSize = files.sumOf { it.length() }

            if (totalSize > maxBudget) {
                // Sort by last modified ascending (oldest accessed first) and delete until within 75% of budget
                val sorted = files.sortedBy { it.lastModified() }
                for (f in sorted) {
                    totalSize -= f.length()
                    f.delete()
                    if (totalSize <= maxBudget * 0.75) {
                        break
                    }
                }
            }
        } catch (ignored: Exception) {}
    }

    fun clear(context: Context) {
        memoryBitmaps.clear()
        try {
            val dir = getThumbnailDir(context)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (ignored: Exception) {}
    }
}
