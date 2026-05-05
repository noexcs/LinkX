package com.noexcs.indolent.agent.tools.notification

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListActiveNotificationsTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext
    private val pm = appContext.packageManager

    override val name = "list_active_notifications"
    override val description = """
        List all currently active notifications from all apps on this device.

        Capabilities:
        - View notifications from any app (not just this one)
        - Filter by package name to focus on a specific app
        - Get notification metadata: title, text, post time, app name, ongoing status, etc.
        - Each notification includes a unique 'key' that can be used with dismiss_notification (key parameter)

        Requirements:
        - The user must grant "Notification Access" permission in system settings.
          If not granted, the tool will return an error with instructions.

        Use cases:
        - Check what notifications the user has pending from messaging apps
        - Monitor notifications from a specific app
        - Find and dismiss spam or unwanted notifications
        - Get context about what the user is seeing on their device

        Note: This only returns currently active notifications (visible in the drawer).
        It cannot retrieve notification history (notifications that have been dismissed).
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "packageName",
            type = "string",
            description = "Filter results to only this package (e.g., 'com.whatsapp'). Omit to list all.",
            required = false
        ),
        ToolParameter(
            name = "limit",
            type = "integer",
            description = "Maximum number of notifications to return. Default: 50. Use to avoid overwhelming the context.",
            required = false
        ),
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            if (!IndolentNotificationListenerService.isConnected()) {
                return "Error: Notification access not granted. The user must enable it in " +
                    "Settings > Special Access > Notification Access, or you can guide them to " +
                    "open the notification access settings."
            }

            val packageFilter = (args["packageName"] as? String)?.takeIf { it.isNotBlank() }
            val limit = (args["limit"] as? Number)?.toInt() ?: 50

            val allNotifications = IndolentNotificationListenerService.getActiveNotifications()
            if (allNotifications == null) {
                return "Error: Could not retrieve active notifications. Notification listener may have disconnected."
            }

            val filtered = if (packageFilter != null) {
                allNotifications.filter { it.packageName == packageFilter }
            } else {
                allNotifications
            }

            if (filtered.isEmpty()) {
                return if (packageFilter != null) {
                    "No active notifications from package '$packageFilter'."
                } else {
                    "No active notifications from any app."
                }
            }

            val result = filtered.take(limit)

            buildString {
                appendLine("${result.size} active notification(s):")
                result.forEachIndexed { index, sbn ->
                    appendLine()
                    appendLine("---")
                    append(formatNotification(sbn, index))
                }

                val remaining = filtered.size - result.size
                if (remaining > 0) {
                    appendLine()
                    appendLine("($remaining more notification(s) not shown. Use 'limit' to adjust, or filter by 'packageName'.)")
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("ListActiveNotificationsTool", "Error listing notifications", e)
            "Error listing notifications: ${e.message}"
        }
    }

    private fun formatNotification(sbn: StatusBarNotification, index: Int): String {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        val appName = resolveAppName(sbn.packageName)
        val timeStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(sbn.postTime))

        return buildString {
            appendLine("key: ${sbn.key}")
            appendLine("app: $appName")
            appendLine("package: ${sbn.packageName}")
            appendLine("time: $timeStr")
            if (title.isNotEmpty()) appendLine("title: $title")
            if (text.isNotEmpty()) appendLine("text: $text")
            if (subText.isNotEmpty()) appendLine("subText: $subText")
            if (summaryText.isNotEmpty()) appendLine("summary: $summaryText")
            appendLine("ongoing: ${sbn.isOngoing}")
            appendLine("clearable: ${sbn.isClearable}")
            if (sbn.notification.channelId != null) {
                appendLine("channelId: ${sbn.notification.channelId}")
            }
            if (sbn.groupKey != null) {
                appendLine("groupKey: ${sbn.groupKey}")
            }
        }
    }

    private fun resolveAppName(packageName: String): String {
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
