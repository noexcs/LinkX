package com.noexcs.indolent.agent.tools.notification

import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.noexcs.indolent.logging.Lumberjack

/**
 * Service that captures notifications from all apps.
 * The user must grant "Notification Access" permission in system settings.
 * Holds a static reference so notification tools can query and dismiss notifications.
 */
class IndolentNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Lumberjack.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Lumberjack.w(TAG, "Notification listener disconnected — notification access may have been revoked")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Lumberjack.i(TAG, "Notification listener destroyed")
    }

    companion object {
        private const val TAG = "NotifListenerService"

        @Volatile
        private var instance: IndolentNotificationListenerService? = null

        fun isConnected(): Boolean = instance != null

        fun getActiveNotifications(): List<StatusBarNotification>? {
            return instance?.activeNotifications?.toList()
        }

        fun cancelNotificationKey(key: String): Boolean {
            val svc = instance ?: return false
            try {
                svc.cancelNotification(key)
                return true
            } catch (e: SecurityException) {
                Lumberjack.e(TAG, "Failed to cancel notification", e)
                return false
            }
        }

        fun snoozeNotification(key: String, durationMs: Long): Boolean {
            val svc = instance ?: return false
            try {
                svc.snoozeNotification(key, durationMs)
                return true
            } catch (e: SecurityException) {
                Lumberjack.e(TAG, "Failed to snooze notification", e)
                return false
            }
        }

        fun getNotificationAccessSettingsIntent(): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
    }
}
