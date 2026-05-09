package com.noexcs.indolent.data

import android.content.Context
import com.noexcs.indolent.task.ExecutionStatus
import com.noexcs.indolent.task.TaskExecutionRepository
import com.noexcs.indolent.task.conditional.ConditionalTriggerRepository
import com.noexcs.indolent.task.heartbeat.HeartbeatRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class TimePeriod { TODAY, THIS_WEEK, THIS_MONTH, TOTAL }

data class TaskTypeStats(
    val count: Int,
    val successCount: Int,
    val failureCount: Int
) {
    val successRate: Float get() = if (count > 0) successCount.toFloat() / count else 0f
}

data class UsageStats(
    val timePeriod: TimePeriod,
    val conversationCount: Int,
    val scheduledTaskStats: TaskTypeStats,
    val conditionalTriggerStats: TaskTypeStats,
    val heartbeatStats: TaskTypeStats,
    val promptTokens: Long,
    val completionTokens: Long
) {
    val totalTokens: Long get() = promptTokens + completionTokens
}

class UsageStatisticsAggregator(context: Context) {
    private val appContext = context.applicationContext
    private val chatHistoryProvider by lazy { FileChatHistoryProvider(appContext) }
    private val executionRepository by lazy { TaskExecutionRepository(appContext) }
    private val conditionalTriggerRepository by lazy { ConditionalTriggerRepository(appContext) }
    private val heartbeatRepository by lazy { HeartbeatRecordRepository(appContext) }
    private val settingsManager by lazy { SettingsManager(appContext) }

    suspend fun computeStats(period: TimePeriod): UsageStats = withContext(Dispatchers.IO) {
        val rangeStart = getTimeRangeStart(period)

        val sessions = chatHistoryProvider.listSessions()
        val conversationCount = if (period == TimePeriod.TOTAL) {
            sessions.size
        } else {
            sessions.count { it.updatedAt >= rangeStart }
        }

        val allExecutions = executionRepository.listAll()
        val executions = if (period == TimePeriod.TOTAL) {
            allExecutions
        } else {
            allExecutions.filter { it.executedAt >= rangeStart }
        }

        val conditionalTaskIds = conditionalTriggerRepository.listAll().map { it.id }.toSet()

        val scheduledExecs = executions.filter { it.taskId !in conditionalTaskIds }
        val conditionalExecs = executions.filter { it.taskId in conditionalTaskIds }

        val allHeartbeats = heartbeatRepository.listAll()
        val heartbeats = if (period == TimePeriod.TOTAL) {
            allHeartbeats
        } else {
            allHeartbeats.filter { it.executedAt >= rangeStart }
        }

        UsageStats(
            timePeriod = period,
            conversationCount = conversationCount,
            scheduledTaskStats = buildTaskTypeStats(scheduledExecs.map { it.status }),
            conditionalTriggerStats = buildTaskTypeStats(conditionalExecs.map { it.status }),
            heartbeatStats = buildTaskTypeStats(heartbeats.map { it.status }),
            promptTokens = settingsManager.cumulativePromptTokens,
            completionTokens = settingsManager.cumulativeCompletionTokens
        )
    }

    private fun buildTaskTypeStats(statuses: List<ExecutionStatus>): TaskTypeStats {
        val success = statuses.count { it == ExecutionStatus.SUCCESS }
        val failure = statuses.count { it == ExecutionStatus.FAILURE }
        return TaskTypeStats(
            count = success + failure,
            successCount = success,
            failureCount = failure
        )
    }

    private fun getTimeRangeStart(period: TimePeriod): Long {
        val cal = Calendar.getInstance()
        when (period) {
            TimePeriod.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            TimePeriod.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            TimePeriod.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            TimePeriod.TOTAL -> return 0
        }
        return cal.timeInMillis
    }
}
