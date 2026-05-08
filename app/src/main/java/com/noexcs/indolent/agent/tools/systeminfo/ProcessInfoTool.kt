package com.noexcs.indolent.agent.tools.systeminfo

import android.app.ActivityManager
import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

class ProcessInfoTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "get_process_info"
    override val description = """
        Get information about running processes on this Android device.

        Returns for each process:
        - Process name (usually package name)
        - PID and UID
        - Importance: foreground, visible, service, cached, empty
        - Importance reason (what keeps it at that level)
        - Memory usage (PSS in MB)
        - Last activity time (when available)

        Use this to see what's running, find memory-hungry apps, or check if a
        specific app is active.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "package_name",
            type = "string",
            description = "Filter processes by package name (partial match). Leave empty to list all.",
            required = false
        ),
        ToolParameter(
            name = "limit",
            type = "integer",
            description = "Maximum number of processes to return. Default 15.",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val packageFilter = (args["package_name"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            val limit = (args["limit"] as? Number)?.toInt() ?: 15

            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processes = try {
                am.runningAppProcesses
            } catch (e: Exception) {
                return "Error: unable to query running processes: ${e.message}"
            }

            if (processes.isNullOrEmpty()) {
                return "No running processes found."
            }

            val filtered = if (packageFilter != null) {
                processes.filter { it.processName.contains(packageFilter, ignoreCase = true) }
            } else {
                processes.toList()
            }

            if (filtered.isEmpty()) {
                return "No processes matching \"$packageFilter\"."
            }

            // Get memory for filtered processes
            val pids = filtered.map { it.pid }.toIntArray()
            val memInfos = am.getProcessMemoryInfo(pids)
            val memMap = filtered.zip(memInfos).associate { (proc, mem) -> proc.pid to mem }

            // Sort: foreground first, then by memory
            val sorted = filtered.sortedWith(
                compareByDescending<ActivityManager.RunningAppProcessInfo> { importanceScore(it.importance) }
                    .thenByDescending { memMap[it.pid]?.totalPss ?: 0 }
            )

            val resultLines = sorted.take(limit).map { proc ->
                val mem = memMap[proc.pid]
                val memMB = if (mem != null) mem.totalPss / 1024 else 0
                val impStr = importanceString(proc.importance)
                val reason = proc.importanceReasonComponent?.let { cmp ->
                    " (reason: ${cmp.flattenToShortString()})"
                } ?: ""

                buildProcessLine(proc, memMB, impStr, reason)
            }

            buildString {
                if (packageFilter != null) {
                    appendLine("Processes matching \"$packageFilter\" (${sorted.size} found, showing ${resultLines.size}):")
                } else {
                    appendLine("Running processes (${sorted.size} total, showing ${resultLines.size}):")
                }
                appendLine("=".repeat(64))
                appendLine(String.format("%-8s %-8s %-12s %-40s", "PID", "Mem(MB)", "Importance", "Process"))
                appendLine("-".repeat(64))
                resultLines.forEach { appendLine(it) }

                if (sorted.size > limit) {
                    appendLine("... and ${sorted.size - limit} more processes")
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("ProcessInfoTool", "Error reading process info", e)
            "Error reading process info: ${e.message}"
        }
    }

    private fun buildProcessLine(
        proc: ActivityManager.RunningAppProcessInfo,
        memMB: Int,
        importance: String,
        reason: String
    ): String {
        val shortName = proc.processName
            .replace(Regex("^com\\.|^org\\.|^net\\."), "")
            .let { if (it.length > 38) "..." + it.takeLast(35) else it }

        return String.format(
            "%-8d %-8d %-12s %s%s",
            proc.pid, memMB, importance, shortName, reason
        )
    }

    private fun importanceScore(importance: Int): Int = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> 600
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> 500
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> 400
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> 300
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> 100
        @Suppress("DEPRECATION")
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY -> 0
        else -> 200
    }

    private fun importanceString(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "perceptible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "cached"
        @Suppress("DEPRECATION")
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY -> "empty"
        else -> "unk($importance)"
    }
}
