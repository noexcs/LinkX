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
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Lumberjack.i(TAG, "${intent.action}, rescheduling tasks and heartbeat")
            val pendingResult = goAsync()
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    TaskScheduler(appContext).rescheduleAll()
                    val settings = SettingsManager(appContext)
                    if (settings.heartbeatEnabled) {
                        HeartbeatScheduler(appContext).schedule()
                    }
                    if (settings.conditionMonitorEnabled) {
                        ConditionMonitorScheduler(appContext).schedule()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
