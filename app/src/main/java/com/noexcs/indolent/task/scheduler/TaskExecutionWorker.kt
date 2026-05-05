package com.noexcs.indolent.task.scheduler

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.noexcs.indolent.R
import com.noexcs.indolent.agent.Agent
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolProvider
import com.noexcs.indolent.agent.tools.systeminfo.GetAppInfoTool
import com.noexcs.indolent.agent.tools.systeminfo.BatteryInfoTool
import com.noexcs.indolent.agent.tools.common.CalendarTool
import com.noexcs.indolent.agent.tools.common.ClipboardTool
import com.noexcs.indolent.agent.tools.notification.CreateNotificationTool
import com.noexcs.indolent.agent.tools.notification.DismissNotificationTool
import com.noexcs.indolent.agent.tools.notification.ListActiveNotificationsTool
import com.noexcs.indolent.agent.tools.notification.ManageNotificationChannelTool
import com.noexcs.indolent.agent.tools.notification.OpenNotificationAccessSettingsTool
import com.noexcs.indolent.agent.tools.notification.QueryNotificationTool
import com.noexcs.indolent.agent.tools.notification.UpdateNotificationTool
import com.noexcs.indolent.agent.tools.systeminfo.CurrentScreenInfoTool
import com.noexcs.indolent.agent.tools.filesystem.ReadFileTool
import com.noexcs.indolent.agent.tools.filesystem.WriteFileTool
import com.noexcs.indolent.agent.tools.filesystem.ListFilesTool
import com.noexcs.indolent.agent.tools.filesystem.DeleteFileTool
import com.noexcs.indolent.agent.tools.filesystem.GetStorageInfoTool
import com.noexcs.indolent.agent.tools.sensor.GetSensorDataTool
import com.noexcs.indolent.agent.tools.setting.SystemSettingTool
import com.noexcs.indolent.agent.tools.setting.AudioControlTool
import com.noexcs.indolent.agent.tools.common.IntentTool
import com.noexcs.indolent.agent.tools.self.LogQueryTool
import com.noexcs.indolent.agent.tools.common.GetCurrentTimeTool
import com.noexcs.indolent.agent.tools.interact.AskUserTool
import com.noexcs.indolent.agent.tools.systeminfo.NetworkStatusTool
import com.noexcs.indolent.agent.tools.scheduledTask.CreateScheduledTaskTool
import com.noexcs.indolent.agent.tools.scheduledTask.ListScheduledTasksTool
import com.noexcs.indolent.agent.tools.scheduledTask.EditScheduledTaskTool
import com.noexcs.indolent.agent.tools.scheduledTask.DeleteScheduledTaskTool
import com.noexcs.indolent.agent.tools.termux.TermuxDialogTool
import com.noexcs.indolent.agent.tools.termux.TermuxExecuteCommandTool
import com.noexcs.indolent.agent.tools.termux.TermuxReadFileTool
import com.noexcs.indolent.agent.tools.termux.TermuxWriteFileTool
import com.noexcs.indolent.agent.tools.common.UpdateMemoryTool
import com.noexcs.indolent.agent.termux.TermuxExecutor
import com.noexcs.indolent.agent.tools.common.SubagentTool
import com.noexcs.indolent.agent.tools.finance.FundETFFundInfoEmTool
import com.noexcs.indolent.agent.tools.finance.FundIndividualAchievementXqTool
import com.noexcs.indolent.agent.tools.finance.FundIndividualAnalysisXqTool
import com.noexcs.indolent.agent.tools.finance.FundIndividualBasicInfoXqTool
import com.noexcs.indolent.agent.tools.finance.FundIndividualDetailHoldXqTool
import com.noexcs.indolent.agent.tools.finance.FundIndividualDetailInfoXqTool
import com.noexcs.indolent.agent.tools.finance.FundIndividualProfitProbabilityXqTool
import com.noexcs.indolent.agent.tools.finance.FundInfoIndexEmTool
import com.noexcs.indolent.agent.tools.finance.FundManagerEmTool
import com.noexcs.indolent.agent.tools.finance.FundOpenFundInfoEmTool
import com.noexcs.indolent.agent.tools.finance.FundOpenFundRankEmTool
import com.noexcs.indolent.agent.tools.finance.FundOverviewEmTool
import com.noexcs.indolent.agent.tools.finance.FundPerformanceTool
import com.noexcs.indolent.agent.tools.finance.FundPortfolioBondHoldEmTool
import com.noexcs.indolent.agent.tools.finance.FundPortfolioChangeEmTool
import com.noexcs.indolent.agent.tools.finance.FundPortfolioHoldEmTool
import com.noexcs.indolent.agent.tools.finance.FundPortfolioIndustryAllocationEmTool
import com.noexcs.indolent.agent.tools.finance.FundValueEstimationEmRankTool
import com.noexcs.indolent.agent.tools.finance.FundValueEstimationEmTool
import com.noexcs.indolent.agent.tools.finance.FundVsBenchmarkTool
import com.noexcs.indolent.agent.tools.finance.PortfolioListTool
import com.noexcs.indolent.agent.tools.finance.PortfolioSummaryTool
import com.noexcs.indolent.agent.tools.finance.StockBoardConceptConsEmTool
import com.noexcs.indolent.agent.tools.finance.StockBoardConceptHistEmTool
import com.noexcs.indolent.agent.tools.finance.StockBoardConceptSpotEmTool
import com.noexcs.indolent.agent.tools.finance.StockBoardIndustrySpotEmTool
import com.noexcs.indolent.agent.tools.finance.StockIndividualFundFlowTool
import com.noexcs.indolent.agent.tools.finance.StockIndividualInfoEmTool
import com.noexcs.indolent.agent.tools.finance.StockIndividualSpotXqTool
import com.noexcs.indolent.agent.tools.finance.StockIntradayEmTool
import com.noexcs.indolent.agent.tools.finance.StockMarketFundFlowTool
import com.noexcs.indolent.agent.tools.finance.StockSectorFundFlowHistTool
import com.noexcs.indolent.agent.tools.finance.StockSectorFundFlowRankTool
import com.noexcs.indolent.agent.tools.finance.StockSectorFundFlowSummaryTool
import com.noexcs.indolent.agent.tools.finance.StockSectorSpotTool
import com.noexcs.indolent.agent.tools.finance.StockZhAHistTool
import com.noexcs.indolent.agent.tools.finance.DirectionalIndicatorTool
import com.noexcs.indolent.agent.tools.finance.EnergyIndicatorTool
import com.noexcs.indolent.agent.tools.finance.MomentumIndicatorTool
import com.noexcs.indolent.agent.tools.finance.OscillatorIndicatorTool
import com.noexcs.indolent.agent.tools.finance.TrendIndicatorTool
import com.noexcs.indolent.agent.tools.finance.VolumeIndicatorTool
import com.noexcs.indolent.data.MemoryManager
import com.noexcs.indolent.data.SettingsManager
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
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.task_running))
            .setSilent(true)
            .build()
        return ForegroundInfo(
            FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
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
            val settings = SettingsManager(applicationContext)
            val baseUrl = settings.baseUrl?.takeIf { it.isNotBlank() }
            if (baseUrl == null) {
                Lumberjack.w("TaskExecutionWorker", "Base URL not configured, cannot run task: $taskId")
                return Result.failure()
            }
            val apiKey = settings.apiKey?.takeIf { it.isNotBlank() }
            if (apiKey == null) {
                Lumberjack.w("TaskExecutionWorker", "API key not configured, cannot run task: $taskId")
                return Result.failure()
            }
            val model = settings.model?.ifBlank { "deepseek-chat" } ?: "deepseek-chat"

            Lumberjack.i("TaskExecutionWorker", "Agent starting — model=$model, promptLen=${task.prompt.length}")
            val agent = Agent(baseUrl, apiKey, model, settings.thinkingEnabled, settings.reasoningEffort)
            val systemPrompt = buildSystemPrompt(applicationContext)
            val tools = buildTools(applicationContext)

            val reply = agent.execute(task.prompt, systemPrompt, tools, 100, true)

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

    private fun buildSystemPrompt(context: Context): String {
        val settings = SettingsManager(context)
        val memory = MemoryManager(context).read()
        return buildString {
            appendLine("You are a helpful Android assistant specialized in executing scheduled tasks.")
            appendLine("This is an automated task execution context - there will be no user conversation.")
            appendLine("Your role is to execute the given task instructions precisely and efficiently.")
            if (settings.userSystemPrompt.isNotBlank()) {
                appendLine()
                appendLine("# User Custom Instruct")
                appendLine(settings.userSystemPrompt)
            }
            if (memory.isNotBlank()) {
                appendLine()
                appendLine("# Memory")
                appendLine("<memory>")
                appendLine(memory)
                appendLine("</memory>")
            }
        }
    }

    private fun buildTools(context: Context): List<AgentTool> {
        val appContext = context.applicationContext
        val settings = SettingsManager(appContext)
        val memoryManager = MemoryManager(appContext)
        val hasTermux = ContextCompat.checkSelfPermission(
            appContext, "com.termux.permission.RUN_COMMAND"
        ) == PackageManager.PERMISSION_GRANTED
        val executor = if (hasTermux && settings.termuxToolsEnabled) TermuxExecutor(appContext) else null
        return ToolProvider.build(appContext, settings, memoryManager, executor)
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val CHANNEL_ID = "scheduled_tasks"
        private const val FOREGROUND_NOTIFICATION_ID = 9999
    }
}
