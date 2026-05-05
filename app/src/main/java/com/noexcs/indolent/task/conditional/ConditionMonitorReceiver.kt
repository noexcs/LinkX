package com.noexcs.indolent.task.conditional

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.noexcs.indolent.logging.Lumberjack

class ConditionMonitorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ConditionMonitorScheduler.ACTION_CONDITION_MONITOR) return

        Lumberjack.d(TAG, "Alarm received, starting condition monitor worker")

        val workRequest = OneTimeWorkRequestBuilder<ConditionMonitorWorker>()
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
    }

    companion object {
        const val TAG = "ConditionMonitorReceiver"
        const val WORK_NAME = "condition_monitor_work"
    }
}
