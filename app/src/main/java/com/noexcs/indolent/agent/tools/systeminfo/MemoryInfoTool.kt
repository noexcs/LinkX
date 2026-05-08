package com.noexcs.indolent.agent.tools.systeminfo

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import java.io.File

class MemoryInfoTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "get_memory_info"
    override val description = """
        Get system memory and app heap information for this Android device.

        Returns:
        - System RAM: total, used, available, low memory flag
        - Detailed memory breakdown from /proc/meminfo:
          MemTotal, MemFree, MemAvailable, Cached, Buffers
          SwapTotal, SwapFree, SwapCached
          Active, Inactive, Dirty, Writeback, Mapped
        - App heap: Java heap used/max, native heap, allocated memory
        - Optionally: per-process memory breakdown (when process_detail=true)

        Use this to check available RAM, memory pressure, or diagnose memory issues.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "process_detail",
            type = "boolean",
            description = "Include per-process memory breakdown. Default false.",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val processDetail = args["process_detail"] as? Boolean ?: false

            buildString {
                appendLine("=== System RAM ===")
                appendSystemMemory()
                appendLine()

                appendLine("=== /proc/meminfo ===")
                appendMeminfo()
                appendLine()

                appendLine("=== App Heap (${appContext.packageName}) ===")
                appendAppHeap()

                if (processDetail) {
                    appendLine()
                    appendLine("=== Top Process Memory ===")
                    appendProcessMemory()
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("MemoryInfoTool", "Error reading memory info", e)
            "Error reading memory info: ${e.message}"
        }
    }

    private fun StringBuilder.appendSystemMemory() {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalMB = memInfo.totalMem / (1024 * 1024)
        val availMB = memInfo.availMem / (1024 * 1024)
        val usedMB = totalMB - availMB
        val usedPct = if (totalMB > 0) (usedMB * 100 / totalMB) else 0

        appendLine("Total: ${totalMB}MB")
        appendLine("Available: ${availMB}MB")
        appendLine("Used: ${usedMB}MB ($usedPct%)")
        appendLine("Low memory: ${memInfo.lowMemory}")
        if (memInfo.threshold > 0) {
            appendLine("Low memory threshold: ${memInfo.threshold / (1024 * 1024)}MB")
        }
    }

    private fun StringBuilder.appendMeminfo() {
        val meminfo = readProcMeminfo()
        if (meminfo.isEmpty()) {
            appendLine("(unavailable)")
            return
        }

        val importantKeys = listOf(
            "MemTotal", "MemFree", "MemAvailable",
            "Buffers", "Cached", "SwapCached",
            "Active", "Inactive",
            "Dirty", "Writeback", "Mapped",
            "SwapTotal", "SwapFree",
            "KernelStack", "PageTables",
            "Slab", "SUnreclaim", "SReclaimable",
            "VmallocTotal", "VmallocUsed",
            "AnonPages", "Shmem"
        )

        importantKeys.forEach { key ->
            meminfo[key]?.let { value ->
                appendLine("$key: $value")
            }
        }
    }

    private fun StringBuilder.appendAppHeap() {
        val runtime = Runtime.getRuntime()
        val maxMB = runtime.maxMemory() / (1024 * 1024)
        val totalMB = runtime.totalMemory() / (1024 * 1024)
        val freeMB = runtime.freeMemory() / (1024 * 1024)
        val usedMB = totalMB - freeMB

        appendLine("Java heap:")
        appendLine("  Max: ${maxMB}MB")
        appendLine("  Total: ${totalMB}MB")
        appendLine("  Free: ${freeMB}MB")
        appendLine("  Used: ${usedMB}MB")

        val debugMem = Debug.MemoryInfo()
        Debug.getMemoryInfo(debugMem)
        val nativeMB = debugMem.nativePss / 1024
        val dalvikMB = debugMem.dalvikPss / 1024
        val totalPssMB = debugMem.totalPss / 1024

        if (totalPssMB > 0) {
            appendLine("PSS (proportional set size):")
            appendLine("  Total: ${totalPssMB}MB")
            appendLine("  Dalvik: ${dalvikMB}MB")
            appendLine("  Native: ${nativeMB}MB")
        }
    }

    private fun StringBuilder.appendProcessMemory() {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = try {
            am.runningAppProcesses
        } catch (e: Exception) {
            appendLine("(unable to query process memory)")
            return
        }

        if (processes.isNullOrEmpty()) {
            appendLine("(no process information)")
            return
        }

        val pids = processes.map { it.pid }.toIntArray()
        val memInfos = am.getProcessMemoryInfo(pids)

        val combined = processes.zip(memInfos).sortedByDescending { it.second.totalPss }

        combined.take(15).forEach { (proc, mem) ->
            val memMB = mem.totalPss / 1024
            val impStr = importanceString(proc.importance)
            appendLine("  ${proc.processName} (pid=${proc.pid}, ${memMB}MB, $impStr)")
        }
    }

    private fun importanceString(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "perceptible"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "cached"
        @Suppress("DEPRECATION")
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY -> "empty"
        else -> "unknown($importance)"
    }

    private fun readProcMeminfo(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            File("/proc/meminfo").bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        result[parts[0].trim()] = parts[1].trim()
                    }
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("MemoryInfoTool", "Failed to read /proc/meminfo", e)
        }
        return result
    }
}
