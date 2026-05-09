package com.noexcs.indolent.task.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.noexcs.indolent.R
import com.noexcs.indolent.agent.BackgroundSessionRunner
import com.noexcs.indolent.task.ForegroundInfoFactory
import com.noexcs.indolent.agent.SessionType
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.task.ExecutionStatus
import com.noexcs.indolent.task.ScheduledTaskRepository
import com.noexcs.indolent.task.TaskExecutionRecord
import com.noexcs.indolent.task.TaskExecutionRepository
import com.noexcs.indolent.task.TaskFrequency
import java.util.UUID

class TaskExecutionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        TaskNotificationHelper.ensureChannel(applicationContext)
        return ForegroundInfoFactory.create(
            applicationContext, CHANNEL_ID, R.string.task_running,
            FOREGROUND_NOTIFICATION_ID, silent = true
        )
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID)
        if (taskId == null) {
            Lumberjack.w("TaskExecutionWorker", "Missing task_id in inputData")
            return Result.failure()
        }
        val taskRepo = ScheduledTaskRepository(applicationContext)
        val task = taskRepo.load(taskId)
        if (task == null) {
            Lumberjack.w("TaskExecutionWorker", "Task not found: $taskId")
            return Result.failure()
        }
        Lumberjack.i("TaskExecutionWorker", "Starting task: '${task.title}' ($taskId)")
        val execRepo = TaskExecutionRepository(applicationContext)

        if (!task.enabled) {
            Lumberjack.w("TaskExecutionWorker", "Task disabled mid-execution: '${task.title}' ($taskId)")
            return Result.success()
        }

        // Notify user when task starts (before foreground service)
        val notificationHelper = TaskNotificationHelper(applicationContext)
        if (task.notifyEnabled) {
            notificationHelper.notifyStart(task.id, task.title)
        }

        setForeground(getForegroundInfo())
        val startTime = System.currentTimeMillis()

        return try {
            val session = try {
                BackgroundSessionRunner.create(applicationContext, taskId, SessionType.SCHEDULED_TASK)
            } catch (e: IllegalStateException) {
                Lumberjack.w("TaskExecutionWorker", "${e.message}, cannot run task: $taskId")
                return Result.failure()
            }

            Lumberjack.i("TaskExecutionWorker", "Agent starting — promptLen=${task.prompt.length}")
            val systemPrompt = BackgroundSessionRunner.buildSystemPrompt(
                applicationContext,
                buildString {
                    appendLine("You are a helpful Android assistant specialized in executing scheduled tasks.")
                    appendLine("This is an automated task execution context - there will be no user conversation.")
                    appendLine("Your role is to execute the given task instructions precisely and efficiently.")
                }.trimEnd()
            )
            val tools = BackgroundSessionRunner.buildTools(applicationContext)

            val reply = session.execute(task.prompt, systemPrompt, tools, 100, true)

            val durationMs = System.currentTimeMillis() - startTime
            Lumberjack.i("TaskExecutionWorker", "Task completed: '${task.title}' (${durationMs}ms, ${reply.length} chars)")

            val record = TaskExecutionRecord(
                id = UUID.randomUUID().toString(),
                taskId = task.id,
                taskTitle = task.title,
                prompt = task.prompt,
                status = ExecutionStatus.SUCCESS,
                result = reply,
                executedAt = startTime,
                durationMs = durationMs
            )
            execRepo.save(record)
            execRepo.pruneOldRecords(task.id)

            if (task.notifyEnabled) {
                notificationHelper.notify(task.id, task.title, reply)
            }

            if (task.frequency == TaskFrequency.ONCE) {
                taskRepo.save(task.copy(enabled = false))
                Lumberjack.i("TaskExecutionWorker", "Once task auto-disabled: $taskId")
            } else {
                TaskScheduler(applicationContext).schedule(task)
            }

            Result.success()
        } catch (e: Exception) {
            Lumberjack.e("TaskExecutionWorker", "Task execution failed: ${task.title}", e)
            val record = TaskExecutionRecord(
                id = UUID.randomUUID().toString(),
                taskId = task.id,
                taskTitle = task.title,
                prompt = task.prompt,
                status = ExecutionStatus.FAILURE,
                errorMessage = e.message ?: "Unknown error",
                executedAt = startTime,
                durationMs = System.currentTimeMillis() - startTime
            )
            execRepo.save(record)
            execRepo.pruneOldRecords(task.id)

            if (task.notifyEnabled) {
                notificationHelper
                    .notify(task.id, task.title, "Error: ${e.message}")
            }
            Result.failure()
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val CHANNEL_ID = "scheduled_tasks"
        private const val FOREGROUND_NOTIFICATION_ID = 9999
    }
}
