package com.noexcs.indolent.agent.tools.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

class GetSensorDataTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "get_sensor_data"
    override val description = """
        Read data from device hardware sensors.

        Sensor modes:
        - "all" — full scan of every available sensor, grouped by category
        - "summary" — compact 1-line-per-sensor overview, lowest token cost
        - A name like "light", "accelerometer", "step_counter" — detailed single sensor
        - A type number like "33171036" — read any sensor by its numeric type
        - A substring like "pickup" or "knuckle" — matches against sensor names on device

        By default: idle sensors and vendor-private sensors are filtered. Duplicate types are deduplicated.
        High-frequency sensors are auto-summarized. Use summary_only to force/disable.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "sensor",
            type = "string",
            required = false,
            defaultValue = "all",
            description = "Sensor to read: \"all\", \"summary\", a known name (\"light\"), a type number (\"27\"), or a substring match (\"pickup\")."
        ),
        ToolParameter(
            name = "duration_ms",
            type = "integer",
            required = false,
            defaultValue = 500,
            description = "How long to sample in ms. Auto-adapted per sensor type. Step counters use 0ms. Range 0–3000ms."
        ),
        ToolParameter(
            name = "summary_only",
            type = "boolean",
            required = false,
            defaultValue = false,
            description = "Force summary mode (average/min/max) even for low-frequency sensors. Default false."
        ),
        ToolParameter(
            name = "filter_idle",
            type = "boolean",
            required = false,
            defaultValue = true,
            description = "Skip sensors that produce no readings. Only applies in \"all\"/\"summary\" mode. Default true."
        ),
        ToolParameter(
            name = "filter_private",
            type = "boolean",
            required = false,
            defaultValue = true,
            description = "Skip vendor-private sensors (non-android.* string types). Only applies in \"all\"/\"summary\" mode. Default true."
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val sensorName = (args["sensor"] as? String)?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: "all"
        val explicitDuration = args["duration_ms"] as? Number
        val durationMs = explicitDuration?.toLong()?.coerceIn(0, 3000)
        val forceSummary = args["summary_only"] as? Boolean ?: false
        val filterIdle = args["filter_idle"] as? Boolean ?: true
        val filterPrivate = args["filter_private"] as? Boolean ?: true

        Lumberjack.i("GetSensorDataTool", "Reading sensor=$sensorName duration=${explicitDuration?.toLong()} summary=$forceSummary filterIdle=$filterIdle filterPrivate=$filterPrivate")

        return try {
            val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

            when (sensorName) {
                "summary" -> readSummary(sensorManager, filterIdle, filterPrivate)
                "all" -> readAllSensors(sensorManager, durationMs, forceSummary, filterIdle, filterPrivate)
                else -> readSpecificSensor(sensorManager, sensorName, durationMs, forceSummary)
            }
        } catch (e: SecurityException) {
            Lumberjack.e("GetSensorDataTool", "Permission denied reading sensor data", e)
            "Error: Permission denied — some sensors may require additional permissions."
        } catch (e: Exception) {
            Lumberjack.e("GetSensorDataTool", "Sensor data read failed", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    // ── "all" mode ──

    private suspend fun readAllSensors(
        sensorManager: SensorManager,
        explicitDuration: Long?,
        forceSummary: Boolean,
        filterIdle: Boolean,
        filterPrivate: Boolean
    ): String {
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        if (allSensors.isEmpty()) return "No sensors available on this device."

        val deduped = allSensors
            .sortedBy { if (it.isWakeUpSensor) 1 else 0 }
            .distinctBy { it.type }

        var skippedPrivate = 0
        var skippedIdle = 0
        var readCount = 0
        val idleNames = mutableListOf<String>()

        val categoryResults = mutableMapOf<SensorUtils.SensorCategory, StringBuilder>()
        SensorUtils.SensorCategory.entries.forEach { categoryResults[it] = StringBuilder() }

        deduped.forEach { sensor ->
            if (filterPrivate && isPrivateSensor(sensor)) {
                skippedPrivate++
                return@forEach
            }

            val dur = explicitDuration ?: SensorUtils.getDefaultDurationMs(sensor.type)
            val readings = SensorUtils.collectSensorReadings(appContext, sensor, dur)

            if (filterIdle && readings.isEmpty()) {
                skippedIdle++
                idleNames.add(SensorUtils.getTypeName(sensor.type).lowercase().replace(" ", "_"))
                return@forEach
            }

            val category = SensorUtils.getCategory(sensor.type)
            categoryResults[category]!!.appendLine(SensorUtils.formatReadings(sensor, readings, forceSummary))
            categoryResults[category]!!.appendLine()
            readCount++
        }

        val sb = StringBuilder()
        sb.appendLine("Device Sensors (${allSensors.size} total, ${deduped.size} unique types)")
        if (filterPrivate || filterIdle) {
            val parts = mutableListOf<String>()
            if (filterPrivate) parts.add("private")
            if (filterIdle) parts.add("idle")
            sb.appendLine("Filters active: skip ${parts.joinToString("/")}")
        }
        sb.appendLine()

        SensorUtils.SensorCategory.entries.forEach { category ->
            val body = categoryResults[category]?.toString()?.trimEnd() ?: ""
            if (body.isNotBlank()) {
                sb.appendLine("═══ ${category.label} ═══")
                sb.appendLine(body)
                sb.appendLine()
            }
        }

        val skippedTotal = skippedPrivate + skippedIdle
        if (skippedTotal > 0) {
            sb.appendLine("─".repeat(50))
            val detail = buildList {
                if (skippedPrivate > 0) add("$skippedPrivate private/vendor")
                if (skippedIdle > 0) add("$skippedIdle idle")
            }.joinToString(", ")
            sb.appendLine("Skipped: $detail ($readCount shown of ${deduped.size} unique types)")
        }

        // #9: List idle sensor names
        if (filterIdle && idleNames.isNotEmpty()) {
            sb.appendLine("IDLE sensors available: ${idleNames.joinToString(", ")} (use filter_idle=false to include)")
        }

        Lumberjack.i("GetSensorDataTool", "Completed — $readCount shown, $skippedPrivate private, $skippedIdle idle")
        return sb.toString().trimEnd()
    }

    // ── "summary" mode ──

    private suspend fun readSummary(
        sensorManager: SensorManager,
        filterIdle: Boolean,
        filterPrivate: Boolean
    ): String {
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        if (allSensors.isEmpty()) return "No sensors available on this device."

        val deduped = allSensors.distinctBy { it.type }

        var skippedPrivate = 0
        var skippedIdle = 0
        var activeCount = 0
        val idleNames = mutableListOf<String>()

        val categoryLines = mutableMapOf<SensorUtils.SensorCategory, MutableList<String>>()
        SensorUtils.SensorCategory.entries.forEach { categoryLines[it] = mutableListOf() }

        deduped.forEach { sensor ->
            if (filterPrivate && isPrivateSensor(sensor)) {
                skippedPrivate++
                return@forEach
            }

            val dur = SensorUtils.getDefaultDurationMs(sensor.type)
            val readings = SensorUtils.collectSensorReadings(appContext, sensor, dur)

            if (filterIdle && readings.isEmpty()) {
                skippedIdle++
                idleNames.add(SensorUtils.getTypeName(sensor.type).lowercase().replace(" ", "_"))
                return@forEach
            }

            val line = SensorUtils.formatSummaryLine(sensor, readings)
            val category = SensorUtils.getCategory(sensor.type)
            categoryLines[category]!!.add(line)
            activeCount++
        }

        val sb = StringBuilder()
        sb.appendLine("Sensor Summary (auto-duration per type)")

        SensorUtils.SensorCategory.entries.forEach { category ->
            val lines = categoryLines[category] ?: emptyList()
            if (lines.isNotEmpty()) {
                sb.appendLine("${category.label}: ${lines.joinToString(", ")}")
            }
        }

        sb.appendLine()
        sb.append("$activeCount active")
        val detail = buildList {
            if (skippedIdle > 0) add("$skippedIdle idle")
            if (skippedPrivate > 0) add("$skippedPrivate private")
        }
        if (detail.isNotEmpty()) sb.append(", ${detail.joinToString(", ")} skipped")
        sb.append(" of ${deduped.size} unique types")

        // #9: List idle sensor names
        if (filterIdle && idleNames.isNotEmpty()) {
            sb.appendLine()
            sb.append("IDLE sensors available: ${idleNames.joinToString(", ")}")
        }

        Lumberjack.i("GetSensorDataTool", "Summary — $activeCount active, $skippedPrivate private, $skippedIdle idle")
        return sb.toString()
    }

    // ── Single sensor read ──

    private suspend fun readSpecificSensor(
        sensorManager: SensorManager,
        name: String,
        explicitDuration: Long?,
        forceSummary: Boolean
    ): String {
        // #4: Try standard alias first, then device sensor search
        val targetType = SensorUtils.resolveSensorType(name)

        val sensor: Sensor? = if (targetType != null) {
            sensorManager.getDefaultSensor(targetType)
        } else {
            // Not a known alias — try searching all device sensors by name/type substring
            SensorUtils.findSensorByName(sensorManager, name)
        }

        if (sensor == null) {
            Lumberjack.w("GetSensorDataTool", "Sensor '$name' not found on this device")
            return buildString {
                appendLine("Sensor '$name' not found on this device.")
                if (targetType != null) {
                    appendLine("Type $targetType is known but no such hardware is present.")
                }
                appendLine()
                appendLine("Available sensors on this device:")
                sensorManager.getSensorList(Sensor.TYPE_ALL)
                    .distinctBy { it.type }
                    .forEach { s ->
                        val typeName = SensorUtils.getTypeName(s.type)
                        if (isPrivateSensor(s)) {
                            appendLine("  - $typeName (type=${s.type}, ${s.stringType}) [private]")
                        } else {
                            appendLine("  - $typeName (type=${s.type})")
                        }
                    }
            }
        }

        val dur = explicitDuration ?: SensorUtils.getDefaultDurationMs(sensor.type)
        // #10: Show duration used
        val readings = SensorUtils.collectSensorReadings(appContext, sensor, dur)
        val result = SensorUtils.formatReadings(sensor, readings, forceSummary)
        return buildString {
            appendLine("duration: ${dur}ms")
            append(result)
        }
    }

    companion object {
        fun isPrivateSensor(sensor: Sensor): Boolean {
            val st = sensor.stringType
            return !st.startsWith("android.")
        }
    }
}
