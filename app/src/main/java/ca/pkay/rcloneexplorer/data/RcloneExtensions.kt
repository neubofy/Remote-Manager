package ca.pkay.rcloneexplorer.data

import ca.pkay.rcloneexplorer.Items.FileItem
import ca.pkay.rcloneexplorer.Items.RemoteItem
import ca.pkay.rcloneexplorer.Rclone
import ca.pkay.rcloneexplorer.util.FLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class DuplicateGroup(
    val hashOrKey: String,
    val size: Long,
    val items: List<FileItem>
)

data class BatchRenameRule(
    val prefix: String = "",
    val suffix: String = "",
    val findText: String = "",
    val replaceText: String = "",
    val useSequentialNumbering: Boolean = false,
    val startNumber: Int = 1,
    val numberPadding: Int = 2
)

enum class FileTypeFilter(val displayName: String) {
    ALL("All"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    DOCUMENTS("Documents"),
    AUDIO("Audio"),
    ARCHIVES("Archives")
}

object RcloneExtensions {
    private const val TAG = "RcloneExtensions"

    private suspend fun Process.awaitSafeExit(): Int = withContext(Dispatchers.IO) {
        val stderrThread = Thread {
            try {
                errorStream.bufferedReader().use { it.readText() }
            } catch (ignored: Exception) {}
        }
        val stdoutThread = Thread {
            try {
                inputStream.bufferedReader().use { it.readText() }
            } catch (ignored: Exception) {}
        }
        stderrThread.isDaemon = true
        stdoutThread.isDaemon = true
        stderrThread.start()
        stdoutThread.start()
        val exit = waitFor()
        try {
            stderrThread.join(500)
            stdoutThread.join(500)
        } catch (ignored: Exception) {}
        exit
    }

    /**
     * Copy a single file or folder to a destination path or remote.
     */
    suspend fun copyItem(
        rclone: Rclone,
        sourceRemote: RemoteItem,
        sourceItem: FileItem,
        destRemote: RemoteItem,
        destPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanSource = Rclone.cleanPathString(sourceRemote, sourceItem.path)
            val sourcePath = rclone.buildRemoteLocation(sourceRemote, cleanSource)
            
            val cleanDest = Rclone.cleanPathString(destRemote, destPath)
            val fullDestRelative = if (cleanDest.isEmpty()) sourceItem.name else "$cleanDest/${sourceItem.name}"
            val fullDest = rclone.buildRemoteLocation(destRemote, fullDestRelative)

            val command = if (sourceItem.isDir) {
                arrayOf("copy", sourcePath, fullDest, "--transfers", "2", "--stats=1s")
            } else {
                arrayOf("copyto", sourcePath, fullDest)
            }

            val process = rclone.executeCommandWithOptions(*command)
            val exitCode = process?.awaitSafeExit() ?: -1
            exitCode == 0
        } catch (e: Exception) {
            FLog.e(TAG, "copyItem error", e)
            false
        }
    }

    /**
     * Duplicate a file or folder in its current directory with auto-incremented name.
     */
    suspend fun duplicateItem(
        rclone: Rclone,
        remote: RemoteItem,
        item: FileItem,
        existingNames: Set<String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val oldName = item.name
            val newName = generateDuplicateName(oldName, item.isDir, existingNames)
            val cleanItemPath = Rclone.cleanPathString(remote, item.path)
            val parentPath = cleanItemPath.substringBeforeLast('/', "")
            val sourceRemotePath = rclone.buildRemoteLocation(remote, cleanItemPath)
            val newRelativePath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
            val destRemotePath = rclone.buildRemoteLocation(remote, newRelativePath)

            val command = if (item.isDir) {
                arrayOf("copy", sourceRemotePath, destRemotePath)
            } else {
                arrayOf("copyto", sourceRemotePath, destRemotePath)
            }

            val process = rclone.executeCommandWithOptions(*command)
            val exitCode = process?.awaitSafeExit() ?: -1
            exitCode == 0
        } catch (e: Exception) {
            FLog.e(TAG, "duplicateItem error", e)
            false
        }
    }

    /**
     * Rename a batch of files according to a BatchRenameRule.
     */
    suspend fun batchRename(
        rclone: Rclone,
        remote: RemoteItem,
        items: List<FileItem>,
        rule: BatchRenameRule
    ): List<Pair<FileItem, Boolean>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<FileItem, Boolean>>()
        for ((index, item) in items.withIndex()) {
            val newName = applyRenameRule(item.name, item.isDir, rule, index)
            if (newName == item.name) {
                results.add(item to true)
                continue
            }
            val cleanItemPath = Rclone.cleanPathString(remote, item.path)
            val parentPath = cleanItemPath.substringBeforeLast('/', "")
            val oldRemotePath = rclone.buildRemoteLocation(remote, cleanItemPath)
            val newRelativePath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
            val newRemotePath = rclone.buildRemoteLocation(remote, newRelativePath)

            val process = rclone.executeCommandWithOptions("moveto", oldRemotePath, newRemotePath)
            val exitCode = process?.awaitSafeExit() ?: -1
            results.add(item to (exitCode == 0))
        }
        results
    }

    /**
     * Scan current directory or subtree for duplicates by comparing file content hashes (MD5/SHA1) or size.
     */
    suspend fun scanDuplicates(
        rclone: Rclone,
        remote: RemoteItem,
        path: String
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        try {
            val cleanPath = Rclone.cleanPathString(remote, path)
            val remotePath = rclone.buildRemoteLocation(remote, cleanPath)
            val process = rclone.executeCommandWithOptions("lsjson", "-R", "--hash", "--max-depth", "4", remotePath) ?: return@withContext emptyList()

            val stdoutBuffer = StringBuilder()
            val stdoutThread = Thread {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stdoutBuffer.append(line)
                        }
                    }
                } catch (ignored: Exception) {}
            }
            val stderrThread = Thread {
                try {
                    process.errorStream.bufferedReader().use { it.readText() }
                } catch (ignored: Exception) {}
            }
            stdoutThread.isDaemon = true
            stderrThread.isDaemon = true
            stdoutThread.start()
            stderrThread.start()

            val exitCode = process.waitFor()
            try {
                stdoutThread.join(1000)
                stderrThread.join(500)
            } catch (ignored: Exception) {}

            val jsonText = stdoutBuffer.toString()
            if (exitCode != 0 || jsonText.isBlank()) return@withContext emptyList()

            val jsonArray = JSONArray(org.json.JSONTokener(jsonText))
            val fileItemsWithHashes = mutableListOf<Pair<FileItem, String>>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val isDir = obj.optBoolean("IsDir", false)
                if (isDir) continue // Duplicates scanned on files

                val itemPath = obj.optString("Path")
                val name = obj.optString("Name")
                val size = obj.optLong("Size", 0)
                val modTime = obj.optString("ModTime", "")
                val mimeType = obj.optString("MimeType", "")

                val hashesObj = obj.optJSONObject("Hashes")
                val md5 = hashesObj?.optString("MD5", "") ?: ""
                val sha1 = hashesObj?.optString("SHA-1", "") ?: ""
                val effectiveHash = when {
                    md5.isNotBlank() -> md5
                    sha1.isNotBlank() -> sha1
                    else -> "size_${size}"
                }

                val fullPath = if (cleanPath.isEmpty()) itemPath else "$cleanPath/$itemPath"
                val fileItem = FileItem(remote, fullPath, name, size, modTime, mimeType, false, false)
                fileItemsWithHashes.add(fileItem to effectiveHash)
            }

            // Group by content hash (finds identical files even if renamed)
            val groups = fileItemsWithHashes.groupBy { it.second }
                .filter { it.value.size > 1 && it.value.first().first.size > 0 }
                .map { (key, list) ->
                    DuplicateGroup(
                        hashOrKey = key,
                        size = list.first().first.size,
                        items = list.map { it.first }
                    )
                }
            groups
        } catch (e: Exception) {
            FLog.e(TAG, "scanDuplicates error", e)
            emptyList()
        }
    }

    fun generateDuplicateName(originalName: String, isDir: Boolean, existingNames: Set<String>): String {
        val nameWithoutExt = if (isDir || !originalName.contains('.')) originalName else originalName.substringBeforeLast('.')
        val ext = if (isDir || !originalName.contains('.')) "" else "." + originalName.substringAfterLast('.')

        var count = 1
        var candidate = "$nameWithoutExt ($count)$ext"
        while (existingNames.contains(candidate)) {
            count++
            candidate = "$nameWithoutExt ($count)$ext"
        }
        return candidate
    }

    fun applyRenameRule(originalName: String, isDir: Boolean, rule: BatchRenameRule, index: Int): String {
        val nameWithoutExt = if (isDir || !originalName.contains('.')) originalName else originalName.substringBeforeLast('.')
        val ext = if (isDir || !originalName.contains('.')) "" else "." + originalName.substringAfterLast('.')

        var modifiedName = nameWithoutExt
        if (rule.findText.isNotEmpty()) {
            modifiedName = modifiedName.replace(rule.findText, rule.replaceText)
        }

        if (rule.useSequentialNumbering) {
            val num = rule.startNumber + index
            val numStr = String.format("%0${rule.numberPadding}d", num)
            modifiedName = "$modifiedName-$numStr"
        }

        return "${rule.prefix}$modifiedName${rule.suffix}$ext"
    }
}
