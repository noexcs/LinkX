package com.noexcs.indolent.task.heartbeat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.noexcs.indolent.logging.Lumberjack

class HeartbeatAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Lumberjack.i(TAG, "Heartbeat alarm fired")
        val workRequest = OneTimeWorkRequestBuilder<HeartbeatWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            HEARTBEAT_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    companion object {
        private const val TAG = "HeartbeatAlarmReceiver"
        private const val HEARTBEAT_WORK_NAME = "heartbeat_worker"
    }
}
