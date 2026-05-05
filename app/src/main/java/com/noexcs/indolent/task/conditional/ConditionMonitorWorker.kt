package com.noexcs.indolent.task.conditional

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.noexcs.indolent.R
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.logging.Lumberjack

class ConditionMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.condition_monitor_running))
            .setSilent(true)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override suspend fun doWork(): Result {
        val settings = SettingsManager(applicationContext)
        if (!settings.conditionMonitorEnabled) {
            Lumberjack.d(TAG, "Condition monitor disabled, skipping")
            return Result.success()
        }

        Lumberjack.d(TAG, "Condition monitor evaluating...")
        setForeground(getForegroundInfo())

        // Start passive monitors for event-driven evaluation while process is alive
        val passiveMonitor = PassiveConditionMonitor(applicationContext)
        passiveMonitor.start()

        return try {
            val evaluator = ConditionEvaluator(applicationContext)
            val triggers = evaluator.evaluateAll()

            if (triggers.isNotEmpty()) {
                Lumberjack.i(TAG, "${triggers.size} trigger(s) met, dispatching...")
                val dispatcher = TriggerDispatcher(applicationContext)
                for (trigger in triggers) {
                    dispatcher.dispatch(trigger)
                }
            } else {
                Lumberjack.d(TAG, "No conditions met")
            }

            // Keep passive monitor alive for a brief window after evaluation
            // to catch rapid changes caused by the agent's own actions
            kotlinx.coroutines.delay(10_000)

            // Calculate adaptive interval and reschedule
            val adaptiveIntervalMs = evaluator.getRecommendedIntervalMs(settings.conditionMonitorIntervalMinutes)
            ConditionMonitorScheduler(applicationContext).scheduleWithInterval(adaptiveIntervalMs)

            Result.success()
        } catch (e: Exception) {
            Lumberjack.e(TAG, "Condition monitor failed", e)
            ConditionMonitorScheduler(applicationContext).scheduleWithInterval(null)
            Result.failure()
        } finally {
            passiveMonitor.stop()
        }
    }

    companion object {
        private const val TAG = "ConditionMonitorWorker"
        private const val CHANNEL_ID = "condition_monitor"
        private const val FOREGROUND_NOTIFICATION_ID = 9994
        const val KEY_TRIGGER_ID = "trigger_id"

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Condition Monitor",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background condition monitoring"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
