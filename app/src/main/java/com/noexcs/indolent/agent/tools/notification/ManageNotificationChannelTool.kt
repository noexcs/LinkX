package com.noexcs.indolent.agent.tools.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

class ManageNotificationChannelTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext
    private val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override val name = "manage_notification_channel"
    override val description = """
        Manage Android notification channels: create, delete, or list them.

        Notification channels are how Android categorizes notifications. Each channel
        appears in the system Settings app where the user can customize its behavior
        (importance, sound, vibration, etc.). You typically create a channel once and
        then reference it via channelId in create_notification / update_notification.

        Capabilities:
        - Create a new channel with fine-grained importance, vibration, lockscreen visibility, and DND bypass
        - Delete an existing channel (also dismisses all notifications in that channel)
        - List all channels currently registered by this app

        Use cases:
        - Set up a high-importance channel for urgent alerts before sending the first alert
        - Create separate channels for different types of AI-generated notifications
          (e.g., "ai_alerts" with MAX importance, "ai_status" with LOW importance)
        - Clean up unused channels
        - Audit which channels exist and their current configuration

        IMPORTANT: Channel settings (importance, vibration, etc.) can only be set at
        creation time. To change a channel's behavior, delete it and recreate it.
        However, the system may rate-limit or ignore rapid delete+recreate cycles.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "action",
            type = "string",
            description = "One of: 'create', 'delete', 'list'",
            required = true
        ),
        ToolParameter(
            name = "channelId",
            type = "string",
            description = "Channel ID. Required for 'create' and 'delete' actions.",
            required = false
        ),
        ToolParameter(
            name = "channelName",
            type = "string",
            description = "Human-readable channel name shown in system settings. Required for 'create'.",
            required = false
        ),
        ToolParameter(
            name = "importance",
            type = "string",
            description = "'min', 'low', 'default', 'high', or 'max'. Controls sound, heads-up, and notification drawer placement. Default: 'default'",
            required = false
        ),
        ToolParameter(
            name = "description",
            type = "string",
            description = "Channel description shown in system settings. Helps users understand what this channel is for.",
            required = false
        ),
        ToolParameter(
            name = "vibration",
            type = "boolean",
            description = "Enable vibration for this channel. Default: true (except for 'min' importance)",
            required = false
        ),
        ToolParameter(
            name = "lockscreenVisibility",
            type = "string",
            description = "What to show on the lock screen: 'public' (full content), 'private' (hide sensitive content), 'secret' (hide entirely). Default: 'public'",
            required = false
        ),
        ToolParameter(
            name = "bypassDnd",
            type = "boolean",
            description = "Allow notifications from this channel to interrupt Do Not Disturb mode. Default: false",
            required = false
        ),
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val action = (args["action"] as? String)?.lowercase()?.trim()
                ?: return "Error: 'action' is required (one of: create, delete, list)."

            when (action) {
                "create" -> handleCreate(args)
                "delete" -> handleDelete(args)
                "list" -> handleList()
                else -> "Error: Unknown action '$action'. Must be one of: create, delete, list."
            }
        } catch (e: SecurityException) {
            Lumberjack.e("ManageNotificationChannelTool", "Notification permission not granted", e)
            "Error: Notification permission not granted."
        } catch (e: Exception) {
            Lumberjack.e("ManageNotificationChannelTool", "Error managing notification channel", e)
            "Error managing notification channel: ${e.message}"
        }
    }

    private fun handleCreate(args: Map<String, Any?>): String {
        val channelId = (args["channelId"] as? String)?.takeIf { it.isNotBlank() }
            ?: return "Error: 'channelId' is required for 'create' action."
        val channelName = (args["channelName"] as? String)?.takeIf { it.isNotBlank() }
            ?: return "Error: 'channelName' is required for 'create' action."

        val importanceStr = (args["importance"] as? String)?.lowercase() ?: "default"
        val importance = when (importanceStr) {
            "min" -> NotificationManager.IMPORTANCE_MIN
            "low" -> NotificationManager.IMPORTANCE_LOW
            "high" -> NotificationManager.IMPORTANCE_HIGH
            "max" -> NotificationManager.IMPORTANCE_MAX
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }

        val channel = NotificationChannel(channelId, channelName, importance)

        val description = (args["description"] as? String)?.takeIf { it.isNotBlank() }
        if (description != null) {
            channel.description = description
        }

        val vibration = args["vibration"] as? Boolean ?: (importance > NotificationManager.IMPORTANCE_MIN)
        if (!vibration) {
            channel.vibrationPattern = longArrayOf(0)
        }

        val lockscreenStr = (args["lockscreenVisibility"] as? String)?.lowercase()
        channel.lockscreenVisibility = when (lockscreenStr) {
            "private" -> NotificationCompat.VISIBILITY_PRIVATE
            "secret" -> NotificationCompat.VISIBILITY_SECRET
            else -> NotificationCompat.VISIBILITY_PUBLIC
        }

        val bypassDnd = args["bypassDnd"] as? Boolean ?: false
        channel.setBypassDnd(bypassDnd)

        nm.createNotificationChannel(channel)

        return buildString {
            appendLine("Notification channel created.")
            appendLine("id: $channelId")
            appendLine("name: $channelName")
            appendLine("importance: $importanceStr")
            if (description != null) appendLine("description: $description")
            appendLine("vibration: $vibration")
            appendLine("lockscreen: ${lockscreenStr ?: "public"}")
            appendLine("bypassDnd: $bypassDnd")
        }
    }

    private fun handleDelete(args: Map<String, Any?>): String {
        val channelId = (args["channelId"] as? String)?.takeIf { it.isNotBlank() }
            ?: return "Error: 'channelId' is required for 'delete' action."

        nm.deleteNotificationChannel(channelId)
        return "Notification channel '$channelId' deleted. All notifications in this channel have been dismissed."
    }

    private fun handleList(): String {
        val channels = nm.notificationChannels
        if (channels.isEmpty()) {
            return "No notification channels registered by this app."
        }

        return buildString {
            appendLine("Notification channels (${channels.size}):")
            channels.forEach { channel ->
                appendLine()
                appendLine("---")
                appendLine("id: ${channel.id}")
                appendLine("name: ${channel.name}")
                appendLine("importance: ${importanceName(channel.importance)}")
                if (!channel.description.isNullOrBlank()) {
                    appendLine("description: ${channel.description}")
                }
                appendLine("vibration: ${channel.shouldVibrate()}")
                appendLine("bypassDnd: ${channel.canBypassDnd()}")
                appendLine("lockscreen: ${lockscreenName(channel.lockscreenVisibility)}")
            }
        }
    }

    private fun importanceName(importance: Int): String = when (importance) {
        NotificationManager.IMPORTANCE_MIN -> "min"
        NotificationManager.IMPORTANCE_LOW -> "low"
        NotificationManager.IMPORTANCE_DEFAULT -> "default"
        NotificationManager.IMPORTANCE_HIGH -> "high"
        NotificationManager.IMPORTANCE_MAX -> "max"
        else -> "none"
    }

    private fun lockscreenName(visibility: Int): String = when (visibility) {
        NotificationCompat.VISIBILITY_PUBLIC -> "public"
        NotificationCompat.VISIBILITY_PRIVATE -> "private"
        NotificationCompat.VISIBILITY_SECRET -> "secret"
        else -> "unknown"
    }
}
