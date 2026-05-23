package com.noexcs.indolent.agent.tools.common

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CalendarTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext
    private val cr = appContext.contentResolver

    override val name = "calendar"
    override val description = """
        Query and manage calendar events on this Android device.

        Operations:
        - "list": List upcoming events across all calendars. Default: next 7 days.
        - "create": Create a new event on the default calendar.
        - "delete": Delete an event by its ID.

        List parameters (all optional):
        - days: Number of days to look ahead (default 7, max 90)
        - calendarId: Filter to a specific calendar. If omitted, searches all calendars.
        - query: Free-text search in event title and description
        - maxResults: Max events to return (default 20, max 50)

        Create parameters:
        - title (required): Event title
        - startTime (required): Start time in ISO format (e.g. "2026-05-10T14:00:00") or natural ("in 2 hours", "tomorrow 3pm")
        - endTime: End time in ISO format. If omitted, defaults to startTime + 1 hour.
        - description: Event description / notes
        - location: Event location
        - reminders: Comma-separated minutes before event (e.g. "10,30" for 10min and 30min before)

        Delete parameters:
        - eventId (required): The numeric event ID (from "list" output)

        Permissions: Requires READ_CALENDAR and WRITE_CALENDAR. The tool reports
        if these are not granted.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "action",
            type = "string",
            description = "Operation: 'list', 'create', or 'delete'",
            required = true
        ),
        // List parameters
        ToolParameter(
            name = "days",
            type = "integer",
            description = "Days to look ahead for 'list'. Default 7, max 90.",
            required = false
        ),
        ToolParameter(
            name = "calendarId",
            type = "integer",
            description = "Filter events by calendar ID for 'list'",
            required = false
        ),
        ToolParameter(
            name = "query",
            type = "string",
            description = "Search term for 'list' (matches title and description)",
            required = false
        ),
        ToolParameter(
            name = "maxResults",
            type = "integer",
            description = "Max events to return for 'list'. Default 20, max 50.",
            required = false
        ),
        // Create parameters
        ToolParameter(
            name = "title",
            type = "string",
            description = "Event title (required for 'create')",
            required = false
        ),
        ToolParameter(
            name = "startTime",
            type = "string",
            description = "Start time for 'create'. ISO format: '2026-05-10T14:00:00' or natural: 'tomorrow 3pm', 'in 2 hours'",
            required = false
        ),
        ToolParameter(
            name = "endTime",
            type = "string",
            description = "End time for 'create'. ISO format. Defaults to start + 1 hour.",
            required = false
        ),
        ToolParameter(
            name = "description",
            type = "string",
            description = "Event description / notes for 'create'",
            required = false
        ),
        ToolParameter(
            name = "location",
            type = "string",
            description = "Event location for 'create'",
            required = false
        ),
        ToolParameter(
            name = "reminders",
            type = "string",
            description = "Comma-separated minutes before event for reminders (e.g. '10,30')",
            required = false
        ),
        // Delete parameters
        ToolParameter(
            name = "eventId",
            type = "integer",
            description = "Event ID to delete (from 'list' output)",
            required = false
        ),
    )

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val action = (args["action"] as? String)?.lowercase() ?: "list"

            when (action) {
                "list" -> executeList(args)
                "create" -> executeCreate(args)
                "delete" -> executeDelete(args)
                else -> "Error: Unknown action '$action'. Use 'list', 'create', or 'delete'."
            }
        } catch (e: SecurityException) {
            Lumberjack.e("CalendarTool", "SecurityException in calendar execute", e)
            errorPermission()
        } catch (e: Exception) {
            Lumberjack.e("CalendarTool", "Error in calendar execute", e)
            "Error: ${e.message}"
        }
    }

    private fun executeList(args: Map<String, Any?>): String {
        val days = ((args["days"] as? Number)?.toInt() ?: 7).coerceIn(1, 90)
        val maxResults = ((args["maxResults"] as? Number)?.toInt() ?: 20).coerceIn(1, 50)
        val calendarId = (args["calendarId"] as? Number)?.toLong()
        val query = (args["query"] as? String)?.takeIf { it.isNotBlank() }

        val now = System.currentTimeMillis()
        val end = now + days * 86_400_000L

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.STATUS,
        )

        val selection = buildString {
            append("${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?")
            if (calendarId != null) append(" AND ${CalendarContract.Events.CALENDAR_ID} = ?")
            if (query != null) {
                append(" AND (${CalendarContract.Events.TITLE} LIKE ? OR ${CalendarContract.Events.DESCRIPTION} LIKE ?)")
            }
        }

        val selArgs = buildList {
            add(now.toString())
            add(end.toString())
            if (calendarId != null) add(calendarId.toString())
            if (query != null) {
                val escaped = query.replace("%", "\\%").replace("_", "\\_")
                add("%$escaped%")
                add("%$escaped%")
            }
        }

        val cursor = cr.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selArgs.toTypedArray(),
            "${CalendarContract.Events.DTSTART} ASC LIMIT $maxResults"
        ) ?: return "Error: Unable to query calendar (cursor is null)."

        return cursor.use {
            if (!it.moveToFirst()) {
                return@use buildString {
                    appendLine("No upcoming events found")
                    if (query != null) appendLine("(search: \"$query\")")
                    appendLine("(searched next $days days)")
                }
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val timeZone = TimeZone.getDefault()

            buildString {
                appendLine("Upcoming events (next $days days):")
                do {
                    val id = it.getLong(0)
                    val title = it.getString(1) ?: "(no title)"
                    val desc = it.getString(2) ?: ""
                    val dtStart = it.getLong(3)
                    val dtEnd = it.getLong(4)
                    val location = it.getString(5) ?: ""
                    val isAllDay = it.getInt(7) == 1
                    val tzId = it.getString(8)
                    val status = when (it.getInt(9)) {
                        CalendarContract.Events.STATUS_CONFIRMED -> "confirmed"
                        CalendarContract.Events.STATUS_TENTATIVE -> "tentative"
                        CalendarContract.Events.STATUS_CANCELED -> "canceled"
                        else -> "unknown"
                    }

                    sdf.timeZone = if (tzId != null) TimeZone.getTimeZone(tzId) else timeZone

                    appendLine()
                    appendLine("  [$id] $title")
                    if (isAllDay) {
                        appendLine("    ${sdf.format(Date(dtStart))} (all day)")
                    } else {
                        appendLine("    ${sdf.format(Date(dtStart))} → ${sdf.format(Date(dtEnd))}")
                    }
                    if (location.isNotBlank()) appendLine("    location: $location")
                    if (desc.isNotBlank()) appendLine("    note: ${desc.take(120)}")
                    appendLine("    status: $status")
                } while (it.moveToNext() && it.position < maxResults)

                // Also list available calendars
                appendLine()
                appendLine("Available calendars:")
                listCalendars()?.let { append(it) }
            }
        }
    }

    private fun executeCreate(args: Map<String, Any?>): String {
        val title = args["title"] as? String ?: return "Error: 'title' is required for create."
        val startStr = args["startTime"] as? String ?: return "Error: 'startTime' is required for create."
        val endStr = args["endTime"] as? String ?: ""
        val description = (args["description"] as? String)?.takeIf { it.isNotBlank() } ?: ""
        val location = (args["location"] as? String)?.takeIf { it.isNotBlank() } ?: ""
        val remindersStr = (args["reminders"] as? String)?.takeIf { it.isNotBlank() } ?: ""

        val startMs = parseTime(startStr) ?: return "Error: Cannot parse startTime '$startStr'. Use ISO format like '2026-05-10T14:00:00'."
        val endMs = if (endStr.isNotBlank()) {
            parseTime(endStr) ?: return "Error: Cannot parse endTime '$endStr'."
        } else {
            startMs + 3_600_000 // default 1 hour
        }

        if (endMs <= startMs) {
            return "Error: endTime must be after startTime."
        }

        // Get default calendar ID
        val calendarId = getDefaultCalendarId()
            ?: return "Error: No writable calendar found. Ensure calendar sync is enabled."

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, endMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (description.isNotEmpty()) put(CalendarContract.Events.DESCRIPTION, description)
            if (location.isNotEmpty()) put(CalendarContract.Events.EVENT_LOCATION, location)
        }

        val uri = cr.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return "Error: Failed to create event."

        val eventId = ContentUris.parseId(uri)

        // Add reminders
        if (remindersStr.isNotEmpty()) {
            val minutesList = remindersStr.split(",").mapNotNull { it.trim().toIntOrNull() }
            for (mins in minutesList) {
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, mins)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                cr.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }
        }

        return buildString {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            appendLine("Event created.")
            appendLine("id: $eventId")
            appendLine("title: $title")
            appendLine("time: ${sdf.format(Date(startMs))} → ${sdf.format(Date(endMs))}")
            if (location.isNotEmpty()) appendLine("location: $location")
            if (description.isNotEmpty()) appendLine("description: $description")
            if (remindersStr.isNotEmpty()) appendLine("reminders: ${remindersStr}min before")
        }
    }

    private fun executeDelete(args: Map<String, Any?>): String {
        val eventId = (args["eventId"] as? Number)?.toLong()
            ?: return "Error: 'eventId' (numeric) is required for delete."

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val deleted = cr.delete(uri, null, null)
        return if (deleted > 0) {
            "Event $eventId deleted."
        } else {
            "Error: Event $eventId not found or could not be deleted."
        }
    }

    private fun getDefaultCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val cursor = cr.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null, null, null
        ) ?: return null

        cursor.use {
            // prefer primary, then any writable
            var fallback: Long? = null
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val access = it.getInt(1)
                val isPrimary = it.getInt(2) == 1
                val isWritable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                if (isPrimary && isWritable) return id
                if (fallback == null && isWritable) fallback = id
            }
            return fallback
        }
    }

    private fun listCalendars(): String? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val cursor = cr.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, null, null, null
        ) ?: return null

        return buildString {
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val name = it.getString(1) ?: "?"
                    val account = it.getString(2) ?: ""
                    val primary = if (it.getInt(3) == 1) " (primary)" else ""
                    appendLine("  $id: $name [$account]$primary")
                }
            }
        }
    }

    private fun parseTime(input: String): Long? {
        val trimmed = input.trim()

        // Try ISO format: 2026-05-10T14:00:00 or 2026-05-10 14:00:00
        val isoFormats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
        )
        for (fmt in isoFormats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.getDefault())
                sdf.timeZone = TimeZone.getDefault()
                val parsed = sdf.parse(trimmed)
                if (parsed != null) {
                    // If only date, default to 9:00 AM
                    if (!trimmed.contains(":") && fmt == "yyyy-MM-dd") {
                        return parsed.time + 9 * 3_600_000
                    }
                    return parsed.time
                }
            } catch (e: Exception) {
                Lumberjack.e("CalendarTool", "Error parsing time format", e)
            }
        }

        // Try epoch millis
        trimmed.toLongOrNull()?.let {
            return if (it < 1_000_000_000_000L) it * 1000L else it
        }

        // Try natural language: "tomorrow 3pm", "in 2 hours", "in 30 minutes"
        val now = System.currentTimeMillis()
        val reTomorrow = Regex("""tomorrow\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""", RegexOption.IGNORE_CASE)
        val reInHours = Regex("""in\s+(\d+)\s*hours?""", RegexOption.IGNORE_CASE)
        val reInMins = Regex("""in\s+(\d+)\s*min(?:ute)?s?""", RegexOption.IGNORE_CASE)

        reTomorrow.find(trimmed)?.let { match ->
            var hour = match.groupValues[1].toInt()
            val min = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            val ampm = match.groupValues[3].lowercase()
            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        reInHours.find(trimmed)?.let { match ->
            return now + match.groupValues[1].toLong() * 3_600_000
        }

        reInMins.find(trimmed)?.let { match ->
            return now + match.groupValues[1].toLong() * 60_000
        }

        return null
    }

    private fun errorPermission(): String {
        // 直接打开应用权限设置页面
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", appContext.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (e: Exception) {
            Lumberjack.e("CalendarTool", "Error opening permissions settings", e)
        }

        return buildString {
            appendLine("📅 日历权限未授予，已为你打开应用设置页面。")
            appendLine()
            appendLine("请在设置中点击「权限」，然后开启：")
            appendLine("  • 日历 (Calendar) — 读写权限")
            appendLine()
            appendLine("授权后即可查询、创建和删除日历事件。")
        }
    }
}