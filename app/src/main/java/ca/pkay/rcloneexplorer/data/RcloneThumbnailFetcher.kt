package ca.pkay.rcloneexplorer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.Rclone
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.InputStream

/**
 * Direct Coil Fetcher for Rclone items.
 * 
 * Features:
 * 1. Checks local persistent thumbnail cache (.thumb) first for instant 0ms load.
 * 2. Pre-checks: respects user thumbnail preference and image size limit.
 * 3. Offline protection: if remote cloud item and device is offline, skips network attempt immediately.
 * 4. Streams raw image bytes directly from rclone cat, downscales to 160x160 RGB_565, and caches locally.
 * 5. Requires zero background HTTP services, open ports, or tokens.
 */
class RcloneThumbnailFetcher(
    private val context: Context,
    private val data: FileItem,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        // 1. Check persistent disk cache first (0ms instant return)
        val cachedFile = ThumbnailCacheManager.getCachedThumbnailFile(context, data)
        if (cachedFile != null && cachedFile.length() > 0) {
            return@withContext SourceResult(
                source = ImageSource(file = cachedFile.toOkioPath(), diskCacheKey = cachedFile.name),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        }

        // 2. Pre-check: Thumbnails enabled in settings
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val thumbnailsEnabled = prefs.getBoolean(context.getString(R.string.pref_key_show_thumbnails), true)
        if (!thumbnailsEnabled) {
            return@withContext null
        }

        // 3. Pre-check: Max image size limit
        val maxSizeBytes = prefs.getLong(context.getString(R.string.pref_key_thumbnail_size_limit), 26214400L) // 25 MB default
        if (data.size > maxSizeBytes && data.size > 0) {
            return@withContext null
        }

        // 4. Pre-check: If remote cloud file and device has no internet, skip network attempt immediately
        val isRemoteCloud = !data.remote.isRemoteType(RemoteItem.LOCAL, RemoteItem.SAFW)
        if (isRemoteCloud && !isNetworkAvailable(context)) {
            return@withContext null
        }

        // 5. Fetch stream directly via rclone cat / local file stream
        try {
            val rclone = Rclone(context)
            val stream: InputStream? = if (data.remote.isRemoteType(RemoteItem.LOCAL)) {
                File(data.path).takeIf { it.exists() }?.inputStream()
            } else {
                rclone.getFileStream(data.remote, data.path)
            }

            if (stream != null) {
                stream.use { inStream ->
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                        inSampleSize = calculateInSampleSize(data.size)
                    }
                    val bitmap = BitmapFactory.decodeStream(inStream, null, opts)
                    if (bitmap != null) {
                        ThumbnailCacheManager.saveThumbnailBitmap(context, data, bitmap)

                        val savedFile = ThumbnailCacheManager.getCachedThumbnailFile(context, data)
                        if (savedFile != null && savedFile.exists()) {
                            return@withContext SourceResult(
                                source = ImageSource(file = savedFile.toOkioPath(), diskCacheKey = savedFile.name),
                                mimeType = "image/jpeg",
                                dataSource = DataSource.NETWORK
                            )
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}

        null
    }

    private fun calculateInSampleSize(fileSize: Long): Int {
        return when {
            fileSize > 15_000_000 -> 8
            fileSize > 5_000_000 -> 4
            fileSize > 1_000_000 -> 2
            else -> 1
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<FileItem> {
        override fun create(data: FileItem, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.isDir) return null
            val isImage = data.mimeType?.startsWith("image/") == true ||
                    data.name.endsWith(".jpg", true) ||
                    data.name.endsWith(".jpeg", true) ||
                    data.name.endsWith(".png", true) ||
                    data.name.endsWith(".webp", true) ||
                    data.name.endsWith(".gif", true) ||
                    data.name.endsWith(".bmp", true)
            return if (isImage) RcloneThumbnailFetcher(context, data, options) else null
        }
    }
}
