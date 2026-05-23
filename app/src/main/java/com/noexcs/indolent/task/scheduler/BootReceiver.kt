package com.noexcs.indolent.task.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.task.conditional.ConditionMonitorScheduler
import com.noexcs.indolent.task.heartbeat.HeartbeatScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Lumberjack.i(TAG, "Boot completed, rescheduling tasks and heartbeat")
            goAsync()
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                TaskScheduler(appContext).rescheduleAll()
                val settings = SettingsManager(appContext)
                if (settings.heartbeatEnabled) {
                    HeartbeatScheduler(appContext).schedule()
                }
                if (settings.conditionMonitorEnabled) {
                    ConditionMonitorScheduler(appContext).schedule()
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
