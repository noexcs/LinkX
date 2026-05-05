package com.noexcs.indolent.agent.tools.notification

import android.app.NotificationManager
import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

class DismissNotificationTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext
    private val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override val name = "dismiss_notification"
    override val description = """
        Dismiss (cancel) or snooze notifications from this app or other apps.

        Unified tool replacing separate cancel and snooze operations. You MUST pick
        exactly one target mode, plus optionally 'snooze' when using 'key' mode.

        TARGET MODES (exactly one required):
        - id:    cancel a single notification created by create_notification (by integer ID)
        - ids:   batch-cancel multiple notifications created by create_notification
        - channelId: cancel all tracked notifications in a channel
        - all:   cancel ALL notifications from this app (indiscriminate)
        - key:   cancel ANY notification from any app by its system key
                 (requires notification access permission)
        - key + snooze: snooze (temporarily hide) a notification from any app
                 snooze accepts "30s", "5m", "1h" etc. Min: 60 seconds.

        Use cases:
        - Dismiss a status notification after work completes (use id)
        - Clear all "ai_status" channel notifications (use channelId)
        - Remove spam notifications from other apps (use key)
        - "Remind me about this message in 30 minutes" (use key + snooze)
        - Clean up everything before posting a fresh batch (use all)

        Note: 'key' and 'snooze' require notification access permission.
        If not granted, you'll get an error instructing the user to enable it.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "id",
            type = "integer",
            description = "Cancel a single notification from this app by its integer ID (returned by create_notification).",
            required = false
        ),
        ToolParameter(
            name = "ids",
            type = "array of integer",
            description = "Batch-cancel multiple notifications from this app by their integer IDs.",
            required = false
        ),
        ToolParameter(
            name = "channelId",
            type = "string",
            description = "Cancel all tracked notifications in this channel (uses internal tracking).",
            required = false
        ),
        ToolParameter(
            name = "all",
            type = "boolean",
            description = "Cancel ALL notifications from this app indiscriminately. Use with caution.",
            required = false
        ),
        ToolParameter(
            name = "key",
            type = "string",
            description = "System notification key (from list_active_notifications). Cancel any app's notification, or combine with 'snooze' to temporarily hide it.",
            required = false
        ),
        ToolParameter(
            name = "snooze",
            type = "string",
            description = "Snooze duration: '30s', '5m', '1h', etc. Min 60s. ONLY valid with 'key' — hides the notification temporarily then restores it. Without 'snooze', 'key' cancels permanently.",
            required = false
        ),
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val id = (args["id"] as? Number)?.toInt()
            val ids = (args["ids"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
            val channelId = (args["channelId"] as? String)?.takeIf { it.isNotBlank() }
            val all = args["all"] as? Boolean ?: false
            val key = (args["key"] as? String)?.takeIf { it.isNotBlank() }
            val snooze = (args["snooze"] as? String)?.takeIf { it.isNotBlank() }

            // Validate: snooze only with key
            if (snooze != null && key == null) {
                return "Error: 'snooze' can only be used together with 'key'."
            }

            when {
                // ── Key-based operations (external app notifications) ──
                key != null -> handleKeyOperation(key, snooze)

                // ── ID-based operations (own notifications) ──
                id != null -> cancelById(id)
                !ids.isNullOrEmpty() -> cancelByIds(ids)
                channelId != null -> cancelByChannel(channelId)
                all -> cancelAll()

                else -> "Error: You must provide one of: 'id', 'ids', 'channelId', 'all', or 'key'."
            }
        } catch (e: SecurityException) {
            Lumberjack.e("DismissNotificationTool", "Notification permission not granted", e)
            "Error: Notification permission not granted."
        } catch (e: Exception) {
            Lumberjack.e("DismissNotificationTool", "Error dismissing notification", e)
            "Error dismissing notification: ${e.message}"
        }
    }

    private fun handleKeyOperation(key: String, snooze: String?): String {
        if (!IndolentNotificationListenerService.isConnected()) {
            return "Error: Notification access not granted. The user must grant notification access in system settings first."
        }

        if (snooze != null) {
            val delayMs = ScheduledTimeParser.parseDelayMs(snooze)
                ?: return "Error: Could not parse snooze duration '$snooze'. Use '5m', '1h', '30s', etc."
            if (delayMs < MIN_SNOOZE_MS) {
                return "Error: Snooze must be at least ${MIN_SNOOZE_MS / 1000} seconds. Received: ${delayMs}ms."
            }
            val success = IndolentNotificationListenerService.snoozeNotification(key, delayMs)
            return if (success) {
                "Notification snoozed for ${delayMs / 60_000} minute(s). key: $key"
            } else {
                "Error: Failed to snooze notification '$key'."
            }
        }

        val success = IndolentNotificationListenerService.cancelNotificationKey(key)
        return if (success) {
            "Notification cancelled. key: $key"
        } else {
            "Error: Failed to cancel notification '$key'."
        }
    }

    private fun cancelById(id: Int): String {
        nm.cancel(id)
        NotificationStateTracker.markCancelled(id)
        return "Notification #$id dismissed."
    }

    private fun cancelByIds(ids: List<Int>): String {
        ids.forEach { nm.cancel(it); NotificationStateTracker.markCancelled(it) }
        return "Dismissed ${ids.size} notification(s): ${ids.joinToString(", ")}."
    }

    private fun cancelByChannel(channelId: String): String {
        val tracked = NotificationStateTracker.getByChannel(channelId)
        tracked.forEach { nm.cancel(it.id) }
        NotificationStateTracker.markCancelledAll(channelId)
        return "Dismissed ${tracked.size} notification(s) in channel '$channelId'."
    }

    private fun cancelAll(): String {
        nm.cancelAll()
        NotificationStateTracker.markCancelledAll()
        return "All notifications dismissed."
    }

    companion object {
        private const val MIN_SNOOZE_MS = 60_000L
    }
}
