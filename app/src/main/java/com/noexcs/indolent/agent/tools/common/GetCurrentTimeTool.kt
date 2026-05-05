package com.noexcs.indolent.agent.tools.common

import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class GetCurrentTimeTool : AgentTool {

    override val name = "get_current_time"
    override val description = """
        Get the current date, time, and timezone information.

        Returns ISO 8601 timestamp, epoch millis, day of week, week number,
        and whether DST is active. Timezone defaults to the device's system timezone.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "timezone",
            type = "string",
            required = false,
            defaultValue = "",
            description = "IANA timezone ID (e.g. \"Asia/Shanghai\", \"America/New_York\", \"UTC\"). Defaults to device timezone."
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val tzArg = (args["timezone"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        val zone: ZoneId = try {
            if (tzArg != null) ZoneId.of(tzArg) else ZoneId.systemDefault()
        } catch (_: Exception) {
            return "Error: Unknown timezone '$tzArg'. Use IANA IDs like \"Asia/Shanghai\", \"America/New_York\", \"UTC\"."
        }

        Lumberjack.i("GetCurrentTimeTool", "Querying time zone=$zone")

        return try {
            val now = ZonedDateTime.now(zone)
            val instant = Instant.now()
            val systemZone = ZoneId.systemDefault()
            val dayOfWeek = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

            buildString {
                appendLine("Current Time")
                appendLine("─".repeat(40))
                appendLine("  ISO 8601:     ${now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")
                appendLine("  Date:         ${now.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
                appendLine("  Time:         ${now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}")
                appendLine("  Day of week:  $dayOfWeek")
                appendLine("  Week:         ${now.format(DateTimeFormatter.ofPattern("w"))}")
                appendLine("  Epoch millis: ${instant.toEpochMilli()}")
                appendLine("  Epoch sec:    ${instant.epochSecond}")
                appendLine("  Timezone:     $zone")
                if (zone != systemZone) appendLine("  System TZ:    $systemZone")
                appendLine("  DST active:   ${zone.rules.isDaylightSavings(instant)}")
                appendLine("  Offset:       ${now.offset}")
            }
        } catch (e: Exception) {
            Lumberjack.e("GetCurrentTimeTool", "Time query failed", e)
            "Error: ${e.message}"
        }
    }
}