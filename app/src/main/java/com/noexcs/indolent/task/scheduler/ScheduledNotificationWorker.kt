package com.noexcs.indolent.task.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noexcs.indolent.R
import com.noexcs.indolent.agent.tools.notification.NotificationStateTracker
import com.noexcs.indolent.logging.Lumberjack

/**
 * Minimal WorkManager worker that posts a scheduled notification.
 * Used by CreateNotificationTool when scheduledTime is provided.
 */
class ScheduledNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val title = inputData.getString(KEY_TITLE) ?: ""
            val content = inputData.getString(KEY_CONTENT) ?: ""
            val channelId = inputData.getString(KEY_CHANNEL_ID) ?: DEFAULT_CHANNEL_ID
            val channelName = inputData.getString(KEY_CHANNEL_NAME) ?: "AI Notifications"
            val priority = inputData.getInt(KEY_PRIORITY, NotificationCompat.PRIORITY_DEFAULT)
            val ongoing = inputData.getBoolean(KEY_ONGOING, false)
            val autoCancel = inputData.getBoolean(KEY_AUTO_CANCEL, true)
            val silent = inputData.getBoolean(KEY_SILENT, false)
            val subText = inputData.getString(KEY_SUB_TEXT) ?: ""
            val bigText = inputData.getString(KEY_BIG_TEXT) ?: ""
            val groupId = inputData.getString(KEY_GROUP_ID) ?: ""
            val category = inputData.getString(KEY_CATEGORY) ?: ""
            val ticker = inputData.getString(KEY_TICKER) ?: ""
            val number = inputData.getInt(KEY_NUMBER, -1)

            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ensureChannel(nm, channelId, channelName, priority)

            val builder = NotificationCompat.Builder(applicationContext, channelId)
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

            val notificationId = ("$title$content").hashCode()
            val key = NotificationStateTracker.generateKey(notificationId)
            nm.notify(notificationId, builder.build())
            NotificationStateTracker.put(notificationId, key, channelId, groupId, title, content)

            Lumberjack.i(TAG, "Scheduled notification posted: #$notificationId")
            Result.success()
        } catch (e: Exception) {
            Lumberjack.e(TAG, "Failed to post scheduled notification", e)
            Result.failure()
        }
    }

    private fun ensureChannel(nm: NotificationManager, channelId: String, channelName: String, priority: Int) {
        if (nm.getNotificationChannel(channelId) != null) return
        val importance = when (priority) {
            NotificationCompat.PRIORITY_MIN -> NotificationManager.IMPORTANCE_MIN
            NotificationCompat.PRIORITY_LOW -> NotificationManager.IMPORTANCE_LOW
            NotificationCompat.PRIORITY_HIGH -> NotificationManager.IMPORTANCE_HIGH
            NotificationCompat.PRIORITY_MAX -> NotificationManager.IMPORTANCE_MAX
            else -> NotificationManager.IMPORTANCE_DEFAULT
        }
        nm.createNotificationChannel(NotificationChannel(channelId, channelName, importance))
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
        private const val TAG = "ScheduledNotifWorker"
        private const val DEFAULT_CHANNEL_ID = "ai_tools"

        const val KEY_TITLE = "title"
        const val KEY_CONTENT = "content"
        const val KEY_CHANNEL_ID = "channel_id"
        const val KEY_CHANNEL_NAME = "channel_name"
        const val KEY_PRIORITY = "priority"
        const val KEY_ONGOING = "ongoing"
        const val KEY_AUTO_CANCEL = "auto_cancel"
        const val KEY_SILENT = "silent"
        const val KEY_SUB_TEXT = "sub_text"
        const val KEY_BIG_TEXT = "big_text"
        const val KEY_GROUP_ID = "group_id"
        const val KEY_CATEGORY = "category"
        const val KEY_TICKER = "ticker"
        const val KEY_NUMBER = "number"
    }
}
