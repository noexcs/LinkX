package com.noexcs.indolent.agent.tools.notification

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Parses the scheduledTime string from the LLM into a delay in milliseconds
 * relative to the current time. Supports:
 * - Relative: "30s", "5m", "1h", "2h30m"
 * - Time of day: "14:00", "14:30" (today)
 * - ISO timestamp: "2024-01-01T14:00:00"
 */
object ScheduledTimeParser {

    private val relativePattern = Regex("""^(\d+)\s*(s|sec|m|min|h|hr|hour)s?$""", RegexOption.IGNORE_CASE)
    private val compoundPattern = Regex("""^(\d+)\s*h\s*(\d+)\s*m(in)?$""", RegexOption.IGNORE_CASE)
    private val timePattern = Regex("""^(\d{1,2}):(\d{2})$""")
    private val isoPattern = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?""")

    /**
     * Parse scheduledTime string and return the delay in milliseconds from now.
     * Returns null if parsing fails.
     */
    fun parseDelayMs(input: String): Long? {
        val trimmed = input.trim()

        // Relative: "30s", "5m", "1h"
        relativePattern.matchEntire(trimmed)?.let { match ->
            val value = match.groupValues[1].toLong()
            val unit = match.groupValues[2].lowercase()
            return when {
                unit.startsWith("s") -> value * 1000
                unit.startsWith("m") -> value * 60_000
                unit.startsWith("h") -> value * 3_600_000
                else -> null
            }
        }

        // Compound: "1h30m", "2h 15min"
        compoundPattern.matchEntire(trimmed)?.let { match ->
            val hours = match.groupValues[1].toLong()
            val mins = match.groupValues[2].toLong()
            return hours * 3_600_000 + mins * 60_000
        }

        // Time of day: "14:00", "9:30"
        timePattern.matchEntire(trimmed)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            if (hour !in 0..23 || minute !in 0..59) return null

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // If target time has already passed today, schedule for tomorrow
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_MONTH, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }

        // ISO timestamp: "2024-01-01T14:00:00"
        if (isoPattern.matches(trimmed)) {
            return try {
                val fmt = if (trimmed.length == 19) {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                } else {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
                }
                val date = fmt.parse(trimmed) ?: return null
                val delay = date.time - System.currentTimeMillis()
                if (delay <= 0) null else delay
            } catch (e: Exception) {
                null
            }
        }

        return null
    }
}
