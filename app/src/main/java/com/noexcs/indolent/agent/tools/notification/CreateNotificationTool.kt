package com.noexcs.indolent.agent.tools.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.noexcs.indolent.R
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.task.scheduler.ScheduledNotificationWorker
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import java.util.concurrent.TimeUnit

class CreateNotificationTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext
    private val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override val name = "create_notification"
    override val description = """
        Create and post a system notification on this Android device with full customization.

        Capabilities:
        - Create custom notification channels with any importance level
        - Set priority from MIN to MAX (affects heads-up display and sorting)
        - Create ongoing (non-dismissable) or auto-cancel notifications
        - Full category support for proper Android notification grouping
        - Expandable BigText style for showing longer content
        - Silent mode, badge numbers, ticker text, and more
        - Group notifications together with groupId

        Use cases:
        - Alert when background work completes or fails
        - Remind about important events or deadlines
        - Show structured results visible from the notification shade
        - Display persistent status indicators the user can monitor

        Default channel is "ai_tools" (importance DEFAULT). Create a custom channel
        via channelId + channelName if you need different behavior like MAX importance
        for heads-up alerts or MIN importance for silent log-style notifications.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "title",
            type = "string",
            description = "Notification title (bold, primary heading)",
            required = true
        ),
        ToolParameter(
            name = "content",
            type = "string",
            description = "Notification body text shown in collapsed form",
            required = true
        ),
        ToolParameter(
            name = "channelId",
            type = "string",
            description = "Custom channel ID. Channel is auto-created if it does not exist. Default: 'ai_tools'",
            required = false
        ),
        ToolParameter(
            name = "channelName",
            type = "string",
            description = "Human-readable channel name shown in system settings. Only applies when creating a new channel. Default: 'AI Notifications'",
            required = false
        ),
        ToolParameter(
            name = "priority",
            type = "string",
            description = "'min', 'low', 'default', 'high', or 'max'. High/max may trigger heads-up popup. Default: 'default'",
            required = false
        ),
        ToolParameter(
            name = "ongoing",
            type = "boolean",
            description = "If true, notification is persistent and cannot be swiped away. Default: false",
            required = false
        ),
        ToolParameter(
            name = "autoCancel",
            type = "boolean",
            description = "If true (default), tapping the notification dismisses it",
            required = false,
            defaultValue = true
        ),
        ToolParameter(
            name = "silent",
            type = "boolean",
            description = "If true, no sound or vibration. Default: false",
            required = false
        ),
        ToolParameter(
            name = "category",
            type = "string",
            description = "System notification category: alarm, call, email, event, message, navigation, progress, promo, recommendation, reminder, service, social, status, system, transport, workout",
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
            description = "Expanded body text visible when user pulls down the notification (BigTextStyle)",
            required = false
        ),
        ToolParameter(
            name = "ticker",
            type = "string",
            description = "Legacy status-bar scroll text shown briefly when notification arrives",
            required = false
        ),
        ToolParameter(
            name = "number",
            type = "integer",
            description = "Badge number shown on the notification icon (set to -1 to hide, 0 to clear)",
            required = false
        ),
        ToolParameter(
            name = "groupId",
            type = "string",
            description = "Group key — notifications with the same groupId are bundled together by the system",
            required = false
        ),
        ToolParameter(
            name = "notifications",
            type = "array of object",
            description = "Batch create: an array of notification objects, each with 'title' and 'content' (required) plus optional per-item overrides: channelId, channelName, priority, ongoing, autoCancel, silent, category, subText, bigText, ticker, number, groupId. Top-level params serve as defaults for all items.",
            required = false
        ),
        ToolParameter(
            name = "scheduledTime",
            type = "string",
            description = "Delay posting until this time. Supports: relative ('5m', '1h', '30s'), time of day ('14:00'), or ISO timestamp ('2024-01-01T14:00:00'). When omitted, notification posts immediately.",
            required = false
        ),
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val notificationsArg = args["notifications"] as? List<*>
            if (!notificationsArg.isNullOrEmpty()) {
                return handleBatchCreate(notificationsArg, args)
            }
            handleSingleCreate(args)
        } catch (e: SecurityException) {
            Lumberjack.e("CreateNotificationTool", "Notification permission not granted", e)
            "Error: Notification permission not granted. Grant POST_NOTIFICATIONS permission to this app."
        } catch (e: Exception) {
            Lumberjack.e("CreateNotificationTool", "Error creating notification", e)
            "Error creating notification: ${e.message}"
        }
    }

    private fun handleBatchCreate(notificationsArg: List<*>, defaults: Map<String, Any?>): String {
        val results = mutableListOf<String>()
        val defaultChannelId = (defaults["channelId"] as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_CHANNEL_ID
        val defaultChannelName = (defaults["channelName"] as? String)?.takeIf { it.isNotBlank() } ?: "AI Notifications"
        val defaultPriorityStr = (defaults["priority"] as? String)?.lowercase() ?: "default"
        val defaultGroupId = (defaults["groupId"] as? String)?.takeIf { it.isNotBlank() } ?: ""

        ensureChannel(defaultChannelId, defaultChannelName, defaultPriorityStr)

        notificationsArg.forEach { item ->
            val itemMap = item as? Map<*, *> ?: return@forEach
            val itemTitle = (itemMap["title"] as? String) ?: ""
            val itemContent = (itemMap["content"] as? String) ?: ""
            if (itemTitle.isBlank() && itemContent.isBlank()) {
                results.add("Skipped: missing title and content")
                return@forEach
            }

            val channelId = (itemMap["channelId"] as? String)?.takeIf { it.isNotBlank() } ?: defaultChannelId
            val channelName = (itemMap["channelName"] as? String)?.takeIf { it.isNotBlank() } ?: defaultChannelName
            val priorityStr = (itemMap["priority"] as? String)?.lowercase() ?: defaultPriorityStr

            if (channelId != defaultChannelId) {
                ensureChannel(channelId, channelName, priorityStr)
            }

            val ongoing = (itemMap["ongoing"] as? Boolean) ?: (defaults["ongoing"] as? Boolean) ?: false
            val autoCancel = (itemMap["autoCancel"] as? Boolean) ?: (defaults["autoCancel"] as? Boolean) ?: true
            val silent = (itemMap["silent"] as? Boolean) ?: (defaults["silent"] as? Boolean) ?: false
            val category = (itemMap["category"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val subText = (itemMap["subText"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val bigText = (itemMap["bigText"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val ticker = (itemMap["ticker"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val number = (itemMap["number"] as? Number)?.toInt() ?: -1
            val groupId = (itemMap["groupId"] as? String)?.takeIf { it.isNotBlank() } ?: defaultGroupId

            val priority = when (priorityStr) {
                "min" -> NotificationCompat.PRIORITY_MIN
                "low" -> NotificationCompat.PRIORITY_LOW
                "high" -> NotificationCompat.PRIORITY_HIGH
                "max" -> NotificationCompat.PRIORITY_MAX
                else -> NotificationCompat.PRIORITY_DEFAULT
            }

            val builder = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(itemTitle)
                .setContentText(itemContent)
                .setPriority(priority)
                .setOngoing(ongoing)
                .setAutoCancel(autoCancel)
                .setSilent(silent)

            if (subText.isNotEmpty()) builder.setSubText(subText)
            if (bigText.isNotEmpty()) {
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(bigText)
                        .setBigContentTitle(itemTitle.ifBlank { null })
                        .setSummaryText(subText.ifBlank { null })
                )
            }
            if (ticker.isNotEmpty()) builder.setTicker(ticker)
            if (number >= 0) builder.setNumber(number)
            if (category.isNotEmpty()) normalizeCategory(category)?.let { builder.setCategory(it) }
            if (groupId.isNotEmpty()) builder.setGroup(groupId)

            val notification = builder.build()
            val notificationId = generateId(itemTitle, itemContent)
            val key = NotificationStateTracker.generateKey(notificationId)
            nm.notify(notificationId, notification)
            NotificationStateTracker.put(notificationId, key, channelId, groupId, itemTitle, itemContent)
            results.add("#$notificationId: \"$itemTitle\"")
        }

        return buildString {
            appendLine("Batch created ${results.size} notification(s):")
            results.forEach { appendLine(it) }
        }
    }

    private fun handleSingleCreate(args: Map<String, Any?>): String {
        val title = args["title"] as? String ?: ""
        val content = args["content"] as? String ?: ""
        if (title.isBlank() && content.isBlank()) {
            return "Error: At least one of title or content must be provided."
        }

        val scheduledTime = (args["scheduledTime"] as? String)?.takeIf { it.isNotBlank() }
        if (scheduledTime != null) {
            return scheduleNotification(args, scheduledTime, title, content)
        }

        val channelId = (args["channelId"] as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_CHANNEL_ID
        val channelName = (args["channelName"] as? String)?.takeIf { it.isNotBlank() } ?: "AI Notifications"
        val priorityStr = (args["priority"] as? String)?.lowercase() ?: "default"
        val ongoing = args["ongoing"] as? Boolean ?: false
        val autoCancel = args["autoCancel"] as? Boolean ?: true
        val silent = args["silent"] as? Boolean ?: false
        val category = (args["category"] as? String)?.takeIf { it.isNotBlank() } ?: ""
        val subText = (args["subText"] as? String)?.takeIf { it.isNotBlank() } ?: ""
        val bigText = (args["bigText"] as? String)?.takeIf { it.isNotBlank() } ?: ""
        val ticker = (args["ticker"] as? String)?.takeIf { it.isNotBlank() } ?: ""
        val number = (args["number"] as? Number)?.toInt() ?: -1
        val groupId = (args["groupId"] as? String)?.takeIf { it.isNotBlank() } ?: ""

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
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(priority)
            .setOngoing(ongoing)
            .setAutoCancel(autoCancel)
            .setSilent(silent)

        if (subText.isNotEmpty()) builder.setSubText(subText)
        if (bigText.isNotEmpty()) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
                    .setBigContentTitle(title.ifBlank { null })
                    .setSummaryText(subText.ifBlank { null })
            )
        }
        if (ticker.isNotEmpty()) builder.setTicker(ticker)
        if (number >= 0) builder.setNumber(number)
        if (category.isNotEmpty()) normalizeCategory(category)?.let { builder.setCategory(it) }
        if (groupId.isNotEmpty()) builder.setGroup(groupId)

        val notification = builder.build()
        val notificationId = generateId(title, content)
        val key = NotificationStateTracker.generateKey(notificationId)
        nm.notify(notificationId, notification)
        NotificationStateTracker.put(notificationId, key, channelId, groupId, title, content)

        return buildString {
            appendLine("Notification posted.")
            appendLine("id: $notificationId")
            appendLine("key: $key")
            appendLine("channel: $channelId")
            if (channelId != DEFAULT_CHANNEL_ID) appendLine("channelName: $channelName")
            appendLine("priority: $priorityStr")
            if (ongoing) appendLine("ongoing: true")
            if (silent) appendLine("silent: true")
            if (category.isNotEmpty()) appendLine("category: $category")
            if (bigText.isNotEmpty()) appendLine("style: BigText")
            if (groupId.isNotEmpty()) appendLine("group: $groupId")
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

    private fun scheduleNotification(args: Map<String, Any?>, scheduledTime: String, title: String, content: String): String {
        val delayMs = ScheduledTimeParser.parseDelayMs(scheduledTime)
            ?: return "Error: Could not parse scheduledTime '$scheduledTime'. Supported formats: '5m', '1h', '30s', '14:00', '2024-01-01T14:00:00'."

        val channelId = (args["channelId"] as? String)?.takeIf { it.isNotBlank() } ?: DEFAULT_CHANNEL_ID
        val channelName = (args["channelName"] as? String)?.takeIf { it.isNotBlank() } ?: "AI Notifications"
        val priorityStr = (args["priority"] as? String)?.lowercase() ?: "default"
        val priority = when (priorityStr) {
            "min" -> NotificationCompat.PRIORITY_MIN
            "low" -> NotificationCompat.PRIORITY_LOW
            "high" -> NotificationCompat.PRIORITY_HIGH
            "max" -> NotificationCompat.PRIORITY_MAX
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
        val ongoing = args["ongoing"] as? Boolean ?: false
        val autoCancel = args["autoCancel"] as? Boolean ?: true
        val silent = args["silent"] as? Boolean ?: false
        val subText = (args["subText"] as? String)?.takeIf { it.isNotBlank() } ?: ""
        val bigText = (args["bigText"] as? String)?.takeIf { it.isNotBlank() } ?: ""
        val groupId = (args["groupId"] as? String)?.takeIf { it.isNotBlank() } ?: ""

        val data = workDataOf(
            ScheduledNotificationWorker.KEY_TITLE to title,
            ScheduledNotificationWorker.KEY_CONTENT to content,
            ScheduledNotificationWorker.KEY_CHANNEL_ID to channelId,
            ScheduledNotificationWorker.KEY_CHANNEL_NAME to channelName,
            ScheduledNotificationWorker.KEY_PRIORITY to priority,
            ScheduledNotificationWorker.KEY_ONGOING to ongoing,
            ScheduledNotificationWorker.KEY_AUTO_CANCEL to autoCancel,
            ScheduledNotificationWorker.KEY_SILENT to silent,
            ScheduledNotificationWorker.KEY_SUB_TEXT to subText,
            ScheduledNotificationWorker.KEY_BIG_TEXT to bigText,
            ScheduledNotificationWorker.KEY_GROUP_ID to groupId,
        )

        val request = OneTimeWorkRequestBuilder<ScheduledNotificationWorker>()
            .setInputData(data)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag("scheduled_notification")
            .build()

        WorkManager.getInstance(appContext).enqueue(request)

        val minutes = delayMs / 60_000
        val notificationId = generateId(title, content)
        return buildString {
            appendLine("Notification scheduled.")
            appendLine("id: $notificationId")
            appendLine("delay: ${minutes}min (${delayMs}ms)")
            appendLine("channel: $channelId")
            appendLine("title: $title")
        }
    }

    private var nextId = 1000

    private fun generateId(title: String, content: String): Int {
        val base = ("$title$content").hashCode()
        // Ensure uniqueness across the process lifetime by combining hash with a counter
        val id = base xor nextId
        nextId++
        return if (id < 0) -id else id
    }

    companion object {
        private const val DEFAULT_CHANNEL_ID = "ai_tools"
    }
}