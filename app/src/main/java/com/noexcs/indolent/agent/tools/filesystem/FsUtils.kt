package com.noexcs.indolent.agent.tools.filesystem

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.documentfile.provider.DocumentFile
import com.noexcs.indolent.data.SettingsManager
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object FsUtils {

    private val ALLOWED_ROOTS by lazy {
        // Lazily populated per-context; see resolveFile()
        emptyList<File>() // placeholder, not used directly
    }

    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // API < 30: WRITE_EXTERNAL_STORAGE covers it
        }
    }

    fun getAllowedRoots(context: Context): List<File> {
        val roots = mutableListOf<File>()
        // Internal app storage
        roots.add(context.filesDir)
        roots.add(context.cacheDir)
        // External app storage
        context.getExternalFilesDir(null)?.let { roots.add(it) }
        context.externalCacheDir?.let { roots.add(it) }
        // Also allow the data/data directory (for Termux home etc)
        context.filesDir.parentFile?.let { roots.add(it) }

        if (hasAllFilesAccess(context)) {
            // Full external storage
            Environment.getExternalStorageDirectory()?.let { roots.add(it) }
            File("/storage/").takeIf { it.exists() }?.let { roots.add(it) }
            // Root filesystem for /data/local/tmp, /sdcard, etc.
            File("/").let { roots.add(it) }
        }
        return roots
    }

    fun isPathTraversal(path: String): Boolean {
        val normalized = path.replace("\\", "/")
        return normalized.contains("../") || normalized == ".." || normalized.endsWith("/..")
    }

    fun isUnderAllowedRoot(path: File, context: Context): Boolean {
        val canonicalPath = try { path.canonicalPath } catch (_: Exception) { path.absolutePath }
        return getAllowedRoots(context).any { root ->
            val canonicalRoot = try { root.canonicalPath } catch (_: Exception) { root.absolutePath }
            canonicalPath.startsWith(canonicalRoot)
        }
    }

    fun resolveFile(path: String, context: Context): File? {
        val file = if (path.startsWith("/")) {
            File(path)
        } else if (path.startsWith("~/")) {
            File(context.filesDir, path.removePrefix("~/"))
        } else {
            File(context.filesDir, path)
        }
        // Only allow paths under allowed roots
        if (!isUnderAllowedRoot(file, context)) return null
        return file
    }

    fun resolveDocumentFile(uri: String, context: Context): DocumentFile? {
        val contentUri = try {
            android.net.Uri.parse(uri)
        } catch (e: Exception) {
            return null
        }
        return try {
            DocumentFile.fromSingleUri(context, contentUri)
                ?: DocumentFile.fromTreeUri(context, contentUri)
        } catch (e: Exception) {
            null
        }
    }

    fun isBinaryContent(bytes: ByteArray): Boolean {
        val sample = bytes.take(512)
        return sample.any { it == 0.toByte() }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }

    fun formatPermissions(file: File): String {
        return buildString {
            append(if (file.canRead()) "r" else "-")
            append(if (file.canWrite()) "w" else "-")
            append(if (file.canExecute()) "x" else "-")
        }
    }

    fun formatTimestamp(epochMs: Long): String {
        val instant = Instant.ofEpochMilli(epochMs)
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }

    fun getStorageVolumes(context: Context): List<StorageVolume> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            return sm?.storageVolumes?.toList() ?: emptyList()
        }
        return emptyList()
    }

    fun getStatFs(path: String): StatFs? {
        return try {
            StatFs(path)
        } catch (e: Exception) {
            null
        }
    }
}
