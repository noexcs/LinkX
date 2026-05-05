package com.noexcs.indolent.task.heartbeat

import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.noexcs.indolent.agent.tools.common.IntentTool
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
import com.noexcs.indolent.agent.tools.ToolProvider
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
import com.noexcs.indolent.agent.tools.finance.FundPortfolioBondHoldEmTool
import com.noexcs.indolent.agent.tools.finance.FundPortfolioChangeEmTool
import com.noexcs.indolent.agent.tools.finance.FundPortfolioHoldEmTool
import com.noexcs.indolent.agent.tools.finance.FundPortfolioIndustryAllocationEmTool
import com.noexcs.indolent.agent.tools.finance.FundValueEstimationEmRankTool
import com.noexcs.indolent.agent.tools.finance.FundValueEstimationEmTool
import com.noexcs.indolent.data.MemoryManager
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.task.ExecutionStatus
import java.util.UUID

class HeartbeatWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.heartbeat_running))
            .build()
        return ForegroundInfo(
            FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override suspend fun doWork(): Result {
        val settings = SettingsManager(applicationContext)
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!settings.heartbeatEnabled) {
            Lumberjack.w("HeartbeatWorker", "Heartbeat disabled in settings, skipping")
            return Result.success()
        }

        Lumberjack.i("HeartbeatWorker", "Heartbeat starting")
        // Post a visible notification so the user knows heartbeat fired
        nm.notify(START_NOTIFICATION_ID, buildStartNotification())
        setForeground(getForegroundInfo())
        val startTime = System.currentTimeMillis()

        return try {
            val baseUrl = settings.baseUrl?.takeIf { it.isNotBlank() }
            if (baseUrl == null) {
                Lumberjack.w("HeartbeatWorker", "Base URL not configured")
                return finishWithError(nm, "Base URL not configured", startTime)
            }
            val apiKey = settings.apiKey?.takeIf { it.isNotBlank() }
            if (apiKey == null) {
                Lumberjack.w("HeartbeatWorker", "API key not configured")
                return finishWithError(nm, "API key not configured", startTime)
            }
            val model = settings.model?.ifBlank { "deepseek-chat" } ?: "deepseek-chat"

            Lumberjack.i("HeartbeatWorker", "Agent starting — model=$model, interval=${settings.heartbeatIntervalMinutes}min")
            val agent = Agent(baseUrl, apiKey, model, settings.thinkingEnabled, settings.reasoningEffort)
            val systemPrompt = buildSystemPrompt(applicationContext)
            val tools = buildTools(applicationContext)
            val heartbeatPrompt = buildHeartbeatPrompt(applicationContext)

            val reply = agent.execute(heartbeatPrompt, systemPrompt, tools, 100, true)

            val durationMs = System.currentTimeMillis() - startTime
            Lumberjack.i("HeartbeatWorker", "Heartbeat completed (${durationMs}ms, ${reply.length} chars)")
            val recordRepo = HeartbeatRecordRepository(applicationContext)
            recordRepo.save(
                HeartbeatRecord(
                    id = UUID.randomUUID().toString(),
                    status = ExecutionStatus.SUCCESS,
                    result = reply,
                    executedAt = startTime,
                    durationMs = durationMs
                )
            )
            recordRepo.pruneOldRecords()

            // Notify completion
            val summary = if (reply.length > 200) reply.take(200) + "…" else reply
            nm.notify(COMPLETE_NOTIFICATION_ID, buildCompleteNotification(summary, null, durationMs))
            // Cancel the start notification
            nm.cancel(START_NOTIFICATION_ID)

            // Re-schedule for next interval
            Lumberjack.i("HeartbeatWorker", "Re-scheduling for next interval")
            HeartbeatScheduler(applicationContext).schedule()

            Result.success()
        } catch (e: Exception) {
            Lumberjack.e("HeartbeatWorker", "Heartbeat execution failed", e)
            val durationMs = System.currentTimeMillis() - startTime
            val errorMsg = e.message ?: "Unknown error"
            val recordRepo = HeartbeatRecordRepository(applicationContext)
            recordRepo.save(
                HeartbeatRecord(
                    id = UUID.randomUUID().toString(),
                    status = ExecutionStatus.FAILURE,
                    errorMessage = errorMsg,
                    executedAt = startTime,
                    durationMs = durationMs
                )
            )

            nm.notify(COMPLETE_NOTIFICATION_ID, buildCompleteNotification(null, errorMsg, durationMs))
            nm.cancel(START_NOTIFICATION_ID)

            // Still re-schedule on failure so heartbeat doesn't die permanently
            HeartbeatScheduler(applicationContext).schedule()

            Result.failure()
        }
    }

    private suspend fun finishWithError(nm: NotificationManager, error: String, startTime: Long): Result {
        Lumberjack.w("HeartbeatWorker", "Heartbeat aborted: $error")
        nm.notify(COMPLETE_NOTIFICATION_ID, buildCompleteNotification(null, error, System.currentTimeMillis() - startTime))
        nm.cancel(START_NOTIFICATION_ID)
        return Result.failure()
    }

    private fun buildStartNotification() = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(applicationContext.getString(R.string.heartbeat_starting))
        .setContentText(applicationContext.getString(R.string.heartbeat_starting_subtitle))
        .setOngoing(true)
        .setAutoCancel(false)
        .build()

    private fun buildCompleteNotification(result: String?, error: String?, durationMs: Long) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                if (error != null) applicationContext.getString(R.string.heartbeat_failed)
                else applicationContext.getString(R.string.heartbeat_complete)
            )
            .setContentText(
                error ?: (result ?: "")
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    error ?: (result ?: "")
                )
            )
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

    private fun buildHeartbeatPrompt(context: Context): String {
        val settings = SettingsManager(context)
        val memory = MemoryManager(context).read()
        val lastRecord = HeartbeatRecordRepository(context).lastRecord()

        return buildString {
            appendLine("This is a heartbeat check — you are proactively looking for something useful to do.")
            appendLine()
            appendLine("## Context")
            appendLine("The user has configured you to wake up periodically and take initiative.")
            appendLine("You have access to various tools: system info, calendar, clipboard, notifications, Termux commands, finance data, etc.")
            if (memory.isNotBlank()) {
                appendLine()
                appendLine("### User Memory")
                appendLine(memory)
            }
            if (lastRecord != null) {
                appendLine()
                appendLine("### Previous Heartbeat")
                appendLine("Status: ${lastRecord.status.name}")
                if (lastRecord.result.isNotBlank()) {
                    appendLine("Result: ${lastRecord.result.take(500)}")
                }
                if (lastRecord.errorMessage.isNotBlank()) {
                    appendLine("Error: ${lastRecord.errorMessage}")
                }
            }
            if (settings.heartbeatCustomPrompt.isNotBlank()) {
                appendLine()
                appendLine("### User's Focus Area")
                appendLine(settings.heartbeatCustomPrompt)
            }
            appendLine()
            appendLine("## Instructions")
            appendLine("1. Briefly review memory and previous heartbeat results to avoid redundancy")
            appendLine("2. Use available tools to find something useful — check calendar events, system status, finance data, etc.")
            appendLine("3. If you find something worth acting on, do it (create a notification, update memory, etc.)")
            appendLine("4. IMPORTANT: If you discover anything the user should know about, use the CreateNotificationTool to send a push notification. Include a clear title and a concise summary.")
            appendLine("5. If there's nothing significant to do, respond with a brief status summary — do NOT create a notification for trivial status updates")
            appendLine("6. Update memory with any important discoveries or state changes")
            appendLine()
            appendLine("Be concise. Don't repeat what was already done in the previous heartbeat.")
        }
    }

    private fun buildSystemPrompt(context: Context): String {
        val settings = SettingsManager(context)
        val memory = MemoryManager(context).read()
        return buildString {
            appendLine("You are a proactive AI assistant running on an Android device.")
            appendLine("This is an automated heartbeat check — there is no direct user conversation.")
            appendLine("Your role is to take initiative: check on things, discover useful information, and act on it autonomously.")
            appendLine("Be practical and helpful. Don't fabricate urgency or make up tasks.")
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
        private const val CHANNEL_ID = "heartbeat"
        private const val FOREGROUND_NOTIFICATION_ID = 9998
        private const val START_NOTIFICATION_ID = 9997
        private const val COMPLETE_NOTIFICATION_ID = 9996

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_heartbeat),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_heartbeat_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
