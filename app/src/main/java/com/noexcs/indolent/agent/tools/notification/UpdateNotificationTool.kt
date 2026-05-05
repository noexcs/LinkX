package com.noexcs.indolent.agent.tools.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.noexcs.indolent.R
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

class UpdateNotificationTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext
    private val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override val name = "update_notification"
    override val description = """
        Update an existing notification by its ID. The notification is replaced in-place
        with the new content — it keeps the same position in the drawer and does not
        re-alert the user (no new sound/vibration).

        Capabilities:
        - Change title, content, and style text on the fly
        - Show/hide progress bars (determinate 0-100% or indeterminate spinner)
        - Switch between ongoing and dismissable
        - Update badge numbers, group assignments, priority
        - Silence or re-enable sound for the updated notification

        Use cases:
        - Progress indicators: "Downloading... 45%" → "Downloading... 100%" → "Download complete"
        - Status updates: "Running backup..." → "Backup complete (12 files)"
        - Live counters: "3 new messages" → "5 new messages"
        - Transition an ongoing notification to auto-cancel when work finishes

        IMPORTANT: You MUST provide the notification 'id' that was returned by
        create_notification. All other fields are optional — only the fields you specify
        will change; unspecified fields keep their previous values from the last post.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "id",
            type = "integer",
            description = "The notification ID to update (returned by create_notification). Required.",
            required = true
        ),
        ToolParameter(
            name = "title",
            type = "string",
            description = "New notification title (bold heading). Omit to keep unchanged.",
            required = false
        ),
        ToolParameter(
            name = "content",
            type = "string",
            description = "New body text. Omit to keep unchanged.",
            required = false
        ),
        ToolParameter(
            name = "channelId",
            type = "string",
            description = "Move the notification to a different channel. Channel is auto-created if needed.",
            required = false
        ),
        ToolParameter(
            name = "channelName",
            type = "string",
            description = "Human-readable channel name if a new channel needs to be created",
            required = false
        ),
        ToolParameter(
            name = "priority",
            type = "string",
            description = "'min', 'low', 'default', 'high', or 'max'",
            required = false
        ),
        ToolParameter(
            name = "ongoing",
            type = "boolean",
            description = "If true, notification becomes persistent and cannot be swiped away",
            required = false
        ),
        ToolParameter(
            name = "autoCancel",
            type = "boolean",
            description = "If true, tapping dismisses the notification",
            required = false
        ),
        ToolParameter(
            name = "silent",
            type = "boolean",
            description = "If true, no sound or vibration on update",
            required = false
        ),
        ToolParameter(
            name = "category",
            type = "string",
            description = "Notification category: alarm, call, email, event, message, navigation, progress, promo, recommendation, reminder, service, social, status, system, transport, workout",
            required = false
        ),
        ToolParameter(
            name = "subText",
            type = "string",
            description = "Secondary text line between title and body",
            required = false
        ),
        ToolParameter(
            name = "bigText",
            type = "string",
            description = "Expanded body text (BigTextStyle)",
            required = false
        ),
        ToolParameter(
            name = "ticker",
            type = "string",
            description = "Legacy status-bar scroll text",
            required = false
        ),
        ToolParameter(
            name = "number",
            type = "integer",
            description = "Badge number on the icon (-1 to hide, 0 to clear)",
            required = false
        ),
        ToolParameter(
            name = "groupId",
            type = "string",
            description = "Group key for bundled notifications",
            required = false
        ),
        ToolParameter(
            name = "progress",
            type = "integer",
            description = "Progress value 0-100. Shows a determinate progress bar. Set to -1 to remove the progress bar.",
            required = false
        ),
        ToolParameter(
            name = "progressIndeterminate",
            type = "boolean",
            description = "Show an indeterminate (spinning) progress indicator. Overrides 'progress' value. Default: false",
            required = false
        ),
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val id = (args["id"] as? Number)?.toInt()
                ?: return "Error: 'id' (notification ID) is required."

            // Preserve original channel unless explicitly overridden
            val existingState = NotificationStateTracker.get(id)
            val channelId = (args["channelId"] as? String)?.takeIf { it.isNotBlank() }
                ?: existingState?.channelId
                ?: DEFAULT_CHANNEL_ID
            val channelName = (args["channelName"] as? String)?.takeIf { it.isNotBlank() }
                ?: "AI Notifications"
            val priorityStr = (args["priority"] as? String)?.lowercase() ?: "default"

            ensureChannel(channelId, channelName, priorityStr)

            val priority = when (priorityStr) {
                "min" -> NotificationCompat.PRIORITY_MIN
                "low" -> NotificationCompat.PRIORITY_LOW
                "high" -> NotificationCompat.PRIORITY_HIGH
                "max" -> NotificationCompat.PRIORITY_MAX
                else -> NotificationCompat.PRIORITY_DEFAULT
            }

            val builder = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(priority)

            val title = args["title"] as? String ?: ""
            val content = args["content"] as? String ?: ""
            val ongoing = args["ongoing"] as? Boolean
            val autoCancel = args["autoCancel"] as? Boolean
            val silent = args["silent"] as? Boolean
            val subText = (args["subText"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val bigText = (args["bigText"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val ticker = (args["ticker"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val number = (args["number"] as? Number)?.toInt()
            val groupId = (args["groupId"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val category = (args["category"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val progress = (args["progress"] as? Number)?.toInt()
            val progressIndeterminate = args["progressIndeterminate"] as? Boolean ?: false

            if (title.isNotBlank()) builder.setContentTitle(title)
            if (content.isNotBlank()) builder.setContentText(content)
            if (ongoing != null) builder.setOngoing(ongoing)
            if (autoCancel != null) builder.setAutoCancel(autoCancel)
            if (silent != null) builder.setSilent(silent)

            if (subText.isNotEmpty()) builder.setSubText(subText)

            if (bigText.isNotEmpty() || subText.isNotEmpty()) {
                val style = NotificationCompat.BigTextStyle()
                    .bigText(bigText.ifEmpty { content.ifBlank { null } })
                    .setBigContentTitle(title.ifBlank { null })
                    .setSummaryText(subText.ifBlank { null })
                builder.setStyle(style)
            }

            if (ticker.isNotEmpty()) builder.setTicker(ticker)
            if (number != null && number >= 0) builder.setNumber(number)
            if (category.isNotEmpty()) normalizeCategory(category)?.let { builder.setCategory(it) }
            if (groupId.isNotEmpty()) builder.setGroup(groupId)

            when {
                progressIndeterminate -> builder.setProgress(0, 0, true)
                progress != null && progress in 0..100 -> builder.setProgress(100, progress, false)
                progress != null && progress == -1 -> builder.setProgress(0, 0, false)
            }

            val notification = builder.build()
            nm.notify(id, notification)

            // Update tracker with new state
            val effectiveTitle = if (title.isNotBlank()) title else existingState?.title ?: ""
            val effectiveContent = if (content.isNotBlank()) content else existingState?.content ?: ""
            val effectiveGroupId = if (groupId.isNotEmpty()) groupId else existingState?.groupId ?: ""
            val key = existingState?.key ?: NotificationStateTracker.generateKey(id)
            NotificationStateTracker.put(id, key, channelId, effectiveGroupId, effectiveTitle, effectiveContent)

            buildString {
                appendLine("Notification #$id updated.")
                appendLine("key: $key")
                appendLine("channel: $channelId")
                if (title.isNotBlank()) appendLine("title: $title")
                if (content.isNotBlank()) appendLine("content: $content")
                if (ongoing != null) appendLine("ongoing: $ongoing")
                if (silent == true) appendLine("silent: true")
                when {
                    progressIndeterminate -> appendLine("progress: indeterminate")
                    progress != null && progress in 0..100 -> appendLine("progress: $progress%")
                }
            }
        } catch (e: SecurityException) {
            Lumberjack.e("UpdateNotificationTool", "Notification permission not granted", e)
            "Error: Notification permission not granted."
        } catch (e: Exception) {
            Lumberjack.e("UpdateNotificationTool", "Error updating notification", e)
            "Error updating notification: ${e.message}"
        }
    }

    private fun ensureChannel(channelId: String, channelName: String, priorityStr: String) {
        if (nm.getNotificationChannel(channelId) != null) return
        val importance = when (priorityStr) {
            "min" -> NotificationManager.IMPORTANCE_MIN
            "low" -> NotificationManager.IMPORTANCE_LOW
            "high" -> NotificationManager.IMPORTANCE_HIGH
            "max" -> NotificationManager.IMPORTANCE_MAX
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
        val channel = NotificationChannel(channelId, channelName, importance)
        nm.createNotificationChannel(channel)
    }

    private fun normalizeCategory(category: String): String? = when (category.lowercase()) {
        "alarm" -> NotificationCompat.CATEGORY_ALARM
        "call" -> NotificationCompat.CATEGORY_CALL
        "email" -> NotificationCompat.CATEGORY_EMAIL
        "event" -> NotificationCompat.CATEGORY_EVENT
        "message" -> NotificationCompat.CATEGORY_MESSAGE
        "navigation" -> NotificationCompat.CATEGORY_NAVIGATION
        "progress" -> NotificationCompat.CATEGORY_PROGRESS
        "promo" -> NotificationCompat.CATEGORY_PROMO
        "recommendation" -> NotificationCompat.CATEGORY_RECOMMENDATION
        "reminder" -> NotificationCompat.CATEGORY_REMINDER
        "service" -> NotificationCompat.CATEGORY_SERVICE
        "social" -> NotificationCompat.CATEGORY_SOCIAL
        "status" -> NotificationCompat.CATEGORY_STATUS
        "system" -> NotificationCompat.CATEGORY_SYSTEM
        "transport" -> NotificationCompat.CATEGORY_TRANSPORT
        "workout" -> NotificationCompat.CATEGORY_WORKOUT
        else -> null
    }

    companion object {
        private const val DEFAULT_CHANNEL_ID = "ai_tools"
    }
}
