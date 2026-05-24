package com.noexcs.indolent.task.heartbeat

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.data.SettingsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HeartbeatScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    fun schedule(): Boolean = scheduleWithInterval(null)

    fun scheduleWithInterval(intervalMs: Long? = null): Boolean {
        val settings = SettingsManager(context)
        if (!settings.heartbeatEnabled) {
            Lumberjack.d(TAG, "Heartbeat disabled, skipping schedule")
            return true
        }
        if (!canScheduleExact()) {
            Lumberjack.w(TAG, "Exact alarm permission missing, cannot schedule heartbeat")
            return false
        }

        val effectiveInterval = intervalMs ?: (settings.heartbeatIntervalMinutes * 60_000L)
        val triggerTime = System.currentTimeMillis() + effectiveInterval
        val pendingIntent = buildPendingIntent()

        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
            pendingIntent
        )
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(triggerTime))
        Lumberjack.i(TAG, "Heartbeat scheduled — next at $timeStr (every ${effectiveInterval / 60_000} min)")
        return true
    }

    fun cancel() {
        alarmManager.cancel(buildPendingIntent())
        Lumberjack.i(TAG, "Heartbeat cancelled")
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, HeartbeatAlarmReceiver::class.java).apply {
            action = ACTION_HEARTBEAT_ALARM
            data = Uri.parse("heartbeat://trigger")
        }
        return PendingIntent.getBroadcast(
            context,
            HEARTBEAT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "HeartbeatScheduler"
        const val ACTION_HEARTBEAT_ALARM = "com.noexcs.indolent.ACTION_HEARTBEAT_ALARM"
        private const val HEARTBEAT_REQUEST_CODE = 87342
    }
}
