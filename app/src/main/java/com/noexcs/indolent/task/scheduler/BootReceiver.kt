package com.noexcs.indolent.task.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.task.conditional.ConditionMonitorScheduler
import com.noexcs.indolent.task.heartbeat.HeartbeatScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Lumberjack.i(TAG, "Boot completed, rescheduling tasks and heartbeat")
            TaskScheduler(context.applicationContext).rescheduleAll()
            val settings = SettingsManager(context.applicationContext)
            if (settings.heartbeatEnabled) {
                HeartbeatScheduler(context.applicationContext).schedule()
            }
            if (settings.conditionMonitorEnabled) {
                ConditionMonitorScheduler(context.applicationContext).schedule()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
