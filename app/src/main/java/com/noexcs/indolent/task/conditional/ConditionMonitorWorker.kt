package com.noexcs.indolent.task.conditional

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.noexcs.indolent.R
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.task.ForegroundInfoFactory
import com.noexcs.indolent.logging.Lumberjack

class ConditionMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel(applicationContext)
        return ForegroundInfoFactory.create(
            applicationContext, CHANNEL_ID, R.string.condition_monitor_running,
            FOREGROUND_NOTIFICATION_ID, silent = true, ongoing = true
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
                context.getString(R.string.condition_monitor_channel_name),
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.condition_monitor_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
