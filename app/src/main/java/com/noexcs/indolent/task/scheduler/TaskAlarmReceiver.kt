package com.noexcs.indolent.task.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.noexcs.indolent.logging.Lumberjack

class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(TaskScheduler.EXTRA_TASK_ID)
        if (taskId == null) {
            Lumberjack.w(TAG, "Alarm received without task_id")
            return
        }
        Lumberjack.i(TAG, "Alarm fired for task: $taskId")

        val workData = Data.Builder()
            .putString(TaskExecutionWorker.KEY_TASK_ID, taskId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<TaskExecutionWorker>()
            .setInputData(workData)
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "task_execution_$taskId",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    companion object {
        private const val TAG = "TaskAlarmReceiver"
    }
}
