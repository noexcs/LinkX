package com.noexcs.indolent.agent.tools.notification

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

class OpenNotificationAccessSettingsTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "open_notification_access_settings"
    override val description = """
        Open the system Notification Access settings screen.

        This is where the user can grant the "Notification Access" permission
        required by list_active_notifications and dismiss_notification tools.

        Use cases:
        - The user wants to use cross-app notification tools but permission is missing
        - Guide the user through the one-time setup process
        - After granting access, the tools will work immediately

        No parameters required. This simply opens the system settings page.
    """.trimIndent()

    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val intent = IndolentNotificationListenerService.getNotificationAccessSettingsIntent()
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            "Opened Notification Access settings. The user should enable access for this app, then notification tools (list_active_notifications, dismiss_notification) will work."
        } catch (e: Exception) {
            Lumberjack.e("OpenNotificationAccessSettingsTool", "Error opening settings", e)
            "Error opening notification access settings: ${e.message}"
        }
    }
}
