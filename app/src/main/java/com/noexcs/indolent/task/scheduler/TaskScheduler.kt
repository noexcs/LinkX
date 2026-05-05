package com.noexcs.indolent.task.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.task.ScheduledTask
import com.noexcs.indolent.task.ScheduledTaskRepository
import com.noexcs.indolent.task.TaskFrequency
import java.util.Calendar

class TaskScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Returns true if exact alarms are permitted. */
    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    /**
     * Schedule the task. Returns false if the exact-alarm permission is missing.
     */
    fun schedule(task: ScheduledTask): Boolean {
        if (!task.enabled) {
            Lumberjack.d(TAG, "Skipping disabled task: ${task.id}")
            return true
        }
        if (!canScheduleExact()) {
            Lumberjack.w(TAG, "Exact alarm permission missing, cannot schedule task: ${task.id}")
            return false
        }
        val triggerTime = nextTriggerTime(task)
        val pendingIntent = buildPendingIntent(task.id)
        // setAlarmClock — highest priority alarm type. Penetrates doze and
        // battery optimization, same level as system alarm clock apps.
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
            pendingIntent
        )
        val timeStr = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(triggerTime))
        Lumberjack.i(TAG, "Task '${task.title}' (${task.id}) scheduled at $timeStr, freq=${task.frequency.name}")
        return true
    }

    fun cancel(taskId: String) {
        alarmManager.cancel(buildPendingIntent(taskId))
        Lumberjack.i(TAG, "Task cancelled: $taskId")
    }

    fun rescheduleAll() {
        val repo = ScheduledTaskRepository(context)
        val all = repo.listAll()
        val enabled = all.filter { it.enabled }
        Lumberjack.i(TAG, "Rescheduling all tasks: ${all.size} total, ${enabled.size} enabled")
        enabled.forEach { schedule(it) }
    }

    private fun buildPendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION_TASK_ALARM
            data = Uri.parse("task://$taskId")
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerTime(task: ScheduledTask): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, task.hour)
            set(Calendar.MINUTE, task.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the target time already passed today, move to next valid day.
        // Except for ONCE: fire almost immediately rather than a full day late.
        if (!target.after(now)) {
            if (task.frequency == TaskFrequency.ONCE) {
                target.timeInMillis = System.currentTimeMillis() + 60_000
                return target.timeInMillis
            }
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        // For WEEKDAYS, skip weekends
        if (task.frequency == TaskFrequency.WEEKDAYS) {
            while (target.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                target.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            ) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // For WEEKLY, find the next occurrence of the same weekday as creation
        if (task.frequency == TaskFrequency.WEEKLY) {
            val createdDay = Calendar.getInstance().apply {
                timeInMillis = task.createdAt
            }.get(Calendar.DAY_OF_WEEK)
            while (target.get(Calendar.DAY_OF_WEEK) != createdDay) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return target.timeInMillis
    }

    companion object {
        private const val TAG = "TaskScheduler"
        const val ACTION_TASK_ALARM = "com.noexcs.indolent.ACTION_TASK_ALARM"
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
