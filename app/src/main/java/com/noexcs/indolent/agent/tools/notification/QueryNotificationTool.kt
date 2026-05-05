package com.noexcs.indolent.agent.tools.notification

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QueryNotificationTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "query_notification"
    override val description = """
        Query notification state and history. Works in two modes:

        1. SINGLE QUERY: pass 'id' to get full details of one notification
        2. LIST QUERY: omit 'id' to get a filtered list (optionally filtered by
           status, channelId, and time range)

        Returns notifications created through create_notification (internal tracking).
        Cannot query notifications from other apps — use list_active_notifications for that.

        Use cases:
        - Check a notification's current state before updating it
        - Review recently sent notifications to avoid duplicates
        - Find all active notifications in a specific channel
        - See what was sent in the last hour

        Note: History is in-memory only and is lost when the process restarts.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "id",
            type = "integer",
            description = "Notification ID (returned by create_notification). When provided, returns full details for that single notification and ignores all filter parameters.",
            required = false
        ),
        ToolParameter(
            name = "status",
            type = "string",
            description = "Filter by status: 'active', 'cancelled', or 'all' (default: 'all'). Only used in list mode.",
            required = false
        ),
        ToolParameter(
            name = "channelId",
            type = "string",
            description = "Filter by channel ID (e.g. 'ai_tools', 'heartbeat'). Only used in list mode.",
            required = false
        ),
        ToolParameter(
            name = "limit",
            type = "integer",
            description = "Max entries to return in list mode. Default: 20, max: 100.",
            required = false
        ),
        ToolParameter(
            name = "since",
            type = "string",
            description = "Earliest time to include. Relative ('1h', '30m') or ISO timestamp. Only used in list mode.",
            required = false
        ),
        ToolParameter(
            name = "before",
            type = "string",
            description = "Latest time to include. Relative or ISO timestamp. Only used in list mode.",
            required = false
        ),
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val id = (args["id"] as? Number)?.toInt()

            if (id != null) {
                return querySingle(id)
            }

            queryList(args)
        } catch (e: Exception) {
            Lumberjack.e("QueryNotificationTool", "Error querying notifications", e)
            "Error querying notifications: ${e.message}"
        }
    }

    private fun querySingle(id: Int): String {
        val state = NotificationStateTracker.get(id)
        if (state == null) {
            return "Notification #$id not found in tracking. It may have been created before tracking was added, or from outside the notification tools."
        }

        val timeStr = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(state.postedAt))

        return buildString {
            appendLine("Notification #$id:")
            appendLine("key: ${state.key}")
            appendLine("status: ${state.status}")
            appendLine("channel: ${state.channelId}")
            appendLine("title: ${state.title}")
            appendLine("content: ${state.content}")
            appendLine("groupId: ${state.groupId.ifEmpty { "(none)" }}")
            appendLine("postedAt: $timeStr")
        }
    }

    private fun queryList(args: Map<String, Any?>): String {
        val statusFilter = (args["status"] as? String)?.lowercase() ?: "all"
        val channelFilter = (args["channelId"] as? String)?.takeIf { it.isNotBlank() }
        val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20
        val sinceStr = (args["since"] as? String)?.takeIf { it.isNotBlank() }
        val beforeStr = (args["before"] as? String)?.takeIf { it.isNotBlank() }

        val sinceMs = sinceStr?.let { parseTimeToMs(it) }
        val beforeMs = beforeStr?.let { parseTimeToMs(it) }

        val all = NotificationStateTracker.getHistory(1000)
        val filtered = all.filter { state ->
            val matchStatus = when (statusFilter) {
                "active" -> state.status == "active"
                "cancelled" -> state.status == "cancelled"
                else -> true
            }
            val matchChannel = channelFilter == null || state.channelId == channelFilter
            val matchSince = sinceMs == null || state.postedAt >= sinceMs
            val matchBefore = beforeMs == null || state.postedAt <= beforeMs
            matchStatus && matchChannel && matchSince && matchBefore
        }.take(limit)

        if (filtered.isEmpty()) {
            return buildString {
                append("No notifications found")
                val filters = mutableListOf<String>()
                if (statusFilter != "all") filters.add("status=$statusFilter")
                if (channelFilter != null) filters.add("channel=$channelFilter")
                if (sinceStr != null) filters.add("since=$sinceStr")
                if (beforeStr != null) filters.add("before=$beforeStr")
                if (filters.isNotEmpty()) append(" matching: ${filters.joinToString(", ")}")
                append(".")
            }
        }

        val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        return buildString {
            appendLine("${filtered.size} notification(s):")
            filtered.forEach { state ->
                appendLine()
                appendLine("---")
                appendLine("id: ${state.id}")
                appendLine("status: ${state.status}")
                appendLine("channel: ${state.channelId}")
                appendLine("title: ${state.title}")
                appendLine("content: ${state.content.take(100)}")
                appendLine("postedAt: ${timeFmt.format(Date(state.postedAt))}")
                if (state.groupId.isNotEmpty()) appendLine("group: ${state.groupId}")
            }
        }
    }

    /**
     * Parse a time string into an absolute epoch millis value.
     * Relative times ("1h", "30m") are converted to (now - duration).
     * ISO timestamps are parsed directly.
     */
    private fun parseTimeToMs(input: String): Long? {
        // Try relative duration: now - delay
        val delay = ScheduledTimeParser.parseDelayMs(input)
        if (delay != null) return System.currentTimeMillis() - delay

        // Try absolute ISO timestamp
        return try {
            val fmt = if (input.length == 19) {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            } else {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            }
            fmt.parse(input)?.time
        } catch (e: Exception) {
            null
        }
    }
}
