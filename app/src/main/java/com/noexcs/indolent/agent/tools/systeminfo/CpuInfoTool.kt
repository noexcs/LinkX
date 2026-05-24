package com.noexcs.indolent.agent.tools.systeminfo

import android.content.Context
import android.os.Build
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class CpuInfoTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "get_cpu_info"
    override val description = """
        Get CPU and hardware thermal information for this Android device.

        Returns:
        - CPU architecture (ABI list) and processor name from /proc/cpuinfo
        - Total logical cores and online core count
        - Per-core current frequency (MHz) and scaling governor
        - CPU load: overall usage percentage computed from /proc/stat
        - CPU, GPU, and skin temperatures (Celsius) when available
        - SoC model (from HardwarePropertiesManager or /proc/cpuinfo)

        Use this to check device thermal throttling, CPU load, or hardware specs.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "include_frequencies",
            type = "boolean",
            description = "Include per-core frequency and governor info. Default true.",
            required = false
        ),
        ToolParameter(
            name = "include_temperature",
            type = "boolean",
            description = "Include thermal sensor readings. Default true.",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            val includeFreq = args["include_frequencies"] as? Boolean ?: true
            val includeTemp = args["include_temperature"] as? Boolean ?: true

            buildString {
                appendLine("=== CPU Architecture ===")
                appendArchitecture()
                appendLine()

                appendLine("=== Core Configuration ===")
                appendCoreInfo(includeFreq)
                appendLine()

                appendLine("=== CPU Load ===")
                appendLoadInfo()
                appendLine()

                if (includeTemp) {
                    appendLine("=== Thermal ===")
                    appendThermalInfo()
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("CpuInfoTool", "Error reading CPU info", e)
            "Error reading CPU info: ${e.message}"
        }
    }

    private fun StringBuilder.appendArchitecture() {
        appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")

        val cpuinfo = readProcCpuinfo()
        cpuinfo["processor"]?.let { cores -> appendLine("CPU cores (from cpuinfo): $cores") }
        cpuinfo["Hardware"]?.let { hw -> appendLine("Hardware: $hw") }
        cpuinfo["model name"]?.let { model -> appendLine("Model: $model") }
        cpuinfo["Features"]?.let { feat -> appendLine("Features: $feat") }
        cpuinfo["CPU implementer"]?.let { imp -> appendLine("Implementer: $imp") }
        cpuinfo["CPU architecture"]?.let { arch -> appendLine("Architecture: $arch") }
        cpuinfo["CPU variant"]?.let { v -> appendLine("Variant: $v") }
        cpuinfo["CPU revision"]?.let { r -> appendLine("Revision: $r") }

        val socModel = getSocModel()
        if (socModel != null) appendLine("SoC: $socModel")
    }

    private fun StringBuilder.appendCoreInfo(includeFreq: Boolean) {
        val online = countOnlineCpus()
        val total = Runtime.getRuntime().availableProcessors()
        appendLine("Logical cores: $total (online: $online)")

        if (includeFreq) {
            appendLine("Per-core frequencies:")
            var freqAvailable = false
            for (i in 0 until Math.min(total, 16)) {
                val freqPath = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq"
                val govPath = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor"
                val freq = readSysFsInt(freqPath)
                val gov = readSysFsString(govPath)
                if (freq > 0) {
                    freqAvailable = true
                    val mhz = (freq / 1000)
                    val govStr = if (gov != null) " ($gov)" else ""
                    appendLine("  cpu$i: ${mhz}MHz$govStr")
                }
            }
            if (!freqAvailable) {
                appendLine("  (no frequency data available)")
            }
        }
    }

    private fun StringBuilder.appendLoadInfo() {
        val load = computeCpuLoad()
        if (load != null) {
            val loadPct = (load * 100).toInt()
            appendLine("Usage: $loadPct% (${loadPct / 10}/10 load)")
        } else {
            appendLine("Usage: unavailable")
        }
    }

    private fun StringBuilder.appendThermalInfo() {
        val thermalZones = readThermalZones()
        if (thermalZones.isEmpty()) {
            appendLine("(no thermal zone data available)")
            return
        }

        // Group by type if available, otherwise list all
        val interesting = thermalZones.filter { (type, _) ->
            type.lowercase().let { it.contains("cpu") || it.contains("gpu") || it.contains("skin") || it.contains("battery") || it.contains("soc") || it.contains("mem") }
        }
        val others = thermalZones.filter { (type, _) ->
            !type.let { it.lowercase().contains("cpu") || it.lowercase().contains("gpu") || it.lowercase().contains("skin") || it.lowercase().contains("battery") || it.lowercase().contains("soc") || it.lowercase().contains("mem") }
        }

        if (interesting.isNotEmpty()) {
            interesting.take(12).forEach { (type, temp) ->
                appendLine("  $type: ${"%.1f".format(temp)}°C")
            }
        }
        if (others.isNotEmpty() && interesting.size < 6) {
            others.take(6).forEach { (type, temp) ->
                appendLine("  $type: ${"%.1f".format(temp)}°C")
            }
        }
    }

    private fun readThermalZones(): List<Pair<String, Float>> {
        val zones = mutableListOf<Pair<String, Float>>()
        try {
            val thermalDir = File("/sys/class/thermal")
            if (!thermalDir.isDirectory) return zones

            thermalDir.listFiles()?.forEach { zoneDir ->
                try {
                    val type = zoneDir.resolve("type").takeIf { it.isFile }?.readText()?.trim() ?: zoneDir.name
                    val tempPath = zoneDir.resolve("temp")
                    if (tempPath.isFile) {
                        val tempRaw = tempPath.readText().trim().toLongOrNull() ?: return@forEach
                        // temp is in millidegrees C
                        val tempC = tempRaw / 1000f
                        zones.add(type to tempC)
                    }
                } catch (_: Exception) {
                    // Skip this zone
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("CpuInfoTool", "Failed to read thermal zones", e)
        }
        return zones.sortedByDescending { it.second }
    }

    private fun readProcCpuinfo(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var processorCount = 0
        try {
            File("/proc/cpuinfo").bufferedReader().use { reader ->
                var lastKey: String? = null
                reader.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isBlank()) return@forEach
                    val parts = trimmed.split(":", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim()
                        if (key == "processor") {
                            processorCount++
                        }
                        // Keep first occurrence of each key
                        if (key !in result) {
                            result[key] = value
                        }
                        lastKey = key
                    } else if (lastKey != null) {
                        // Continuation line
                        result[lastKey] = result[lastKey] + " " + trimmed
                    }
                }
            }
            result["processor"] = processorCount.toString()
        } catch (e: Exception) {
            Lumberjack.e("CpuInfoTool", "Failed to read /proc/cpuinfo", e)
        }
        return result
    }

    private fun countOnlineCpus(): Int {
        return try {
            val onlineFile = File("/sys/devices/system/cpu/online")
            if (onlineFile.isFile) {
                val content = onlineFile.readText().trim()
                // Format: comma-separated ranges like "0-3,5,7"
                content.split(",").sumOf { part ->
                    val range = part.split("-")
                    if (range.size == 2) {
                        val (start, end) = range[0].trim().toInt() to range[1].trim().toInt()
                        (end - start + 1).coerceAtLeast(0)
                    } else {
                        1
                    }
                }
            } else {
                File("/sys/devices/system/cpu/")
                    .listFiles()
                    ?.count { it.isDirectory && it.name.matches(Regex("cpu[0-9]+")) }
                    ?: 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun readSysFsInt(path: String): Int {
        return try {
            File(path).readText().trim().toInt()
        } catch (e: Exception) {
            -1
        }
    }

    private fun readSysFsString(path: String): String? {
        return try {
            File(path).readText().trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun getSocModel(): String? {
        return try {
            val cpuinfo = readProcCpuinfo()
            cpuinfo["Hardware"]?.let { return it }
            // Try reading from sysfs
            File("/sys/devices/soc0/soc_id").takeIf { it.exists() }?.readText()?.trim()?.let {
                return "soc0:$it"
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun computeCpuLoad(): Float? {
        return try {
            val stat1 = readProcStat()
            if (stat1 == null) return null

            Thread.sleep(300)

            val stat2 = readProcStat()
            if (stat2 == null) return null

            val total1 = stat1.total
            val total2 = stat2.total
            val idle1 = stat1.idle + stat1.iowait
            val idle2 = stat2.idle + stat2.iowait

            val totalDelta = total2 - total1
            val idleDelta = idle2 - idle1

            if (totalDelta <= 0) return null
            1f - (idleDelta.toFloat() / totalDelta.toFloat())
        } catch (e: Exception) {
            null
        }
    }

    private data class CpuTimes(val total: Long, val idle: Long, val iowait: Long)

    private fun readProcStat(): CpuTimes? {
        return try {
            File("/proc/stat").bufferedReader().use { reader ->
                val line = reader.readLine()
                val parts = line.removePrefix("cpu ").trim().split("\\s+".toRegex())
                val values = parts.mapNotNull { it.toLongOrNull() }
                if (values.size >= 5) {
                    val idle = values[3]
                    val iowait = values[4]
                    CpuTimes(values.sum(), idle, iowait)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
