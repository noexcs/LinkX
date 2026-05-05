package com.noexcs.indolent.task.conditional

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.logging.Lumberjack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConditionMonitorScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    fun schedule(): Boolean {
        return scheduleWithInterval(null)
    }

    fun scheduleWithInterval(intervalMs: Long?): Boolean {
        val settings = SettingsManager(context)
        if (!settings.conditionMonitorEnabled) {
            Lumberjack.d(TAG, "Condition monitor disabled, skipping schedule")
            return true
        }
        if (!canScheduleExact()) {
            Lumberjack.w(TAG, "Exact alarm permission missing, cannot schedule condition monitor")
            return false
        }

        val effectiveInterval = intervalMs ?: (settings.conditionMonitorIntervalMinutes * 60_000L)
        val triggerTime = System.currentTimeMillis() + effectiveInterval
        val pendingIntent = buildPendingIntent()

        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
            pendingIntent
        )

        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(triggerTime))
        val intervalSec = effectiveInterval / 1000
        Lumberjack.i(TAG, "Condition monitor scheduled — next at $timeStr (interval=${intervalSec}s)")
        return true
    }

    fun cancel() {
        alarmManager.cancel(buildPendingIntent())
        Lumberjack.i(TAG, "Condition monitor cancelled")
    }

    fun rescheduleAll() {
        val settings = SettingsManager(context)
        if (settings.conditionMonitorEnabled) {
            schedule()
        }
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, ConditionMonitorReceiver::class.java).apply {
            action = ACTION_CONDITION_MONITOR
            data = Uri.parse("condition_monitor://trigger")
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "ConditionMonitorScheduler"
        const val ACTION_CONDITION_MONITOR = "com.noexcs.indolent.ACTION_CONDITION_MONITOR"
        private const val REQUEST_CODE = 87343
    }
}
