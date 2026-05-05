package com.noexcs.indolent.agent.tools.filesystem

import android.content.Context
import android.os.Build
import android.os.Environment
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

class GetStorageInfoTool(private val context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "fs_storage_info"
    override val description = """
        Get information about storage volumes on the device.

        Reports total space, free space, available space, filesystem type,
        and whether the volume is removable or emulated.

        If no path is provided, reports on all available storage volumes.
        If a path is provided, reports only on the volume containing that path.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "path",
            type = "string",
            required = false,
            defaultValue = "",
            description = "Optional path to query a specific volume. If empty, all volumes are reported."
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val path = args["path"] as? String ?: ""

        Lumberjack.i("GetStorageInfoTool", "Querying storage info path=${path.ifBlank { "all" }}")

        return try {
            buildString {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val volumes = FsUtils.getStorageVolumes(appContext)
                    if (volumes.isEmpty()) {
                        append(reportClassicPaths(path))
                    } else {
                        volumes.forEach { volume ->
                            appendVolumeInfo(volume, path)
                        }
                    }
                } else {
                    append(reportClassicPaths(path))
                }

                appendLine()
                appendLine("Internal app storage:")
                appendLine("  filesDir: ${appContext.filesDir.absolutePath} (${formatStat(appContext.filesDir.absolutePath)})")
                appendLine("  cacheDir: ${appContext.cacheDir.absolutePath} (${formatStat(appContext.cacheDir.absolutePath)})")
                appContext.getExternalFilesDir(null)?.let {
                    appendLine("  externalFilesDir: ${it.absolutePath} (${formatStat(it.absolutePath)})")
                }
                appContext.externalCacheDir?.let {
                    appendLine("  externalCacheDir: ${it.absolutePath} (${formatStat(it.absolutePath)})")
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("GetStorageInfoTool", "Storage info query failed", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    private fun StringBuilder.appendVolumeInfo(volume: android.os.storage.StorageVolume, filterPath: String) {
        val volPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory?.absolutePath
        } else {
            null
        } ?: return

        if (filterPath.isNotBlank() && !volPath.startsWith("/")) return
        if (filterPath.isNotBlank() && !filterPath.startsWith(volPath) && !volPath.startsWith(filterPath)) return

        appendLine("Volume: ${volume.getDescription(appContext) ?: volPath}")
        appendLine("  Path: $volPath")
        appendLine("  State: ${volume.state ?: "unknown"}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appendLine("  Removable: ${volume.isRemovable}")
            appendLine("  Emulated: ${volume.isEmulated}")
        }
        appendLine("  ${formatStat(volPath)}")
        appendLine()
    }

    private fun reportClassicPaths(filterPath: String): String {
        val sb = StringBuilder()
        val dataPath = Environment.getDataDirectory().absolutePath
        val storagePath = Environment.getExternalStorageDirectory().absolutePath

        listOf(dataPath, storagePath).forEach { p ->
            if (filterPath.isBlank() || p.startsWith(filterPath) || filterPath.startsWith(p)) {
                sb.appendLine("Path: $p")
                sb.appendLine("  State: ${Environment.getExternalStorageState()}")
                sb.appendLine("  ${formatStat(p)}")
                sb.appendLine()
            }
        }
        return sb.toString()
    }

    private fun formatStat(path: String): String {
        val stat = FsUtils.getStatFs(path) ?: return "Unavailable"
        val total = stat.totalBytes
        val free = stat.freeBytes
        val available = stat.availableBytes
        val used = total - free
        val usedPercent = if (total > 0) (used * 100 / total) else 0
        return "Total: ${FsUtils.formatFileSize(total)}, Used: ${FsUtils.formatFileSize(used)} ($usedPercent%), Free: ${FsUtils.formatFileSize(free)}, Available: ${FsUtils.formatFileSize(available)}"
    }
}
