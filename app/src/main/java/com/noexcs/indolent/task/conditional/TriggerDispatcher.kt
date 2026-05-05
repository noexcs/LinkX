package com.noexcs.indolent.task.conditional

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.noexcs.indolent.R
import com.noexcs.indolent.agent.Agent
import com.noexcs.indolent.agent.termux.TermuxExecutor
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.common.CalendarTool
import com.noexcs.indolent.agent.tools.common.ClipboardTool
import com.noexcs.indolent.agent.tools.common.GetCurrentTimeTool
import com.noexcs.indolent.agent.tools.common.IntentTool
import com.noexcs.indolent.agent.tools.ToolProvider
import com.noexcs.indolent.agent.tools.common.SubagentTool
import com.noexcs.indolent.agent.tools.common.UpdateMemoryTool
import com.noexcs.indolent.agent.tools.filesystem.DeleteFileTool
import com.noexcs.indolent.agent.tools.filesystem.GetStorageInfoTool
import com.noexcs.indolent.agent.tools.filesystem.ListFilesTool
import com.noexcs.indolent.agent.tools.filesystem.ReadFileTool
import com.noexcs.indolent.agent.tools.filesystem.WriteFileTool
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
import com.noexcs.indolent.agent.tools.interact.AskUserTool
import com.noexcs.indolent.agent.tools.notification.CreateNotificationTool
import com.noexcs.indolent.agent.tools.notification.DismissNotificationTool
import com.noexcs.indolent.agent.tools.notification.ListActiveNotificationsTool
import com.noexcs.indolent.agent.tools.notification.ManageNotificationChannelTool
import com.noexcs.indolent.agent.tools.notification.OpenNotificationAccessSettingsTool
import com.noexcs.indolent.agent.tools.notification.QueryNotificationTool
import com.noexcs.indolent.agent.tools.notification.UpdateNotificationTool
import com.noexcs.indolent.agent.tools.conditional.CreateConditionalTriggerTool
import com.noexcs.indolent.agent.tools.conditional.DeleteConditionalTriggerTool
import com.noexcs.indolent.agent.tools.conditional.EditConditionalTriggerTool
import com.noexcs.indolent.agent.tools.conditional.ListConditionalTriggerHistoryTool
import com.noexcs.indolent.agent.tools.conditional.ListConditionalTriggersTool
import com.noexcs.indolent.agent.tools.scheduledTask.CreateScheduledTaskTool
import com.noexcs.indolent.agent.tools.scheduledTask.DeleteScheduledTaskTool
import com.noexcs.indolent.agent.tools.scheduledTask.EditScheduledTaskTool
import com.noexcs.indolent.agent.tools.scheduledTask.ListScheduledTasksTool
import com.noexcs.indolent.agent.tools.self.LogQueryTool
import com.noexcs.indolent.agent.tools.sensor.GetSensorDataTool
import com.noexcs.indolent.agent.tools.setting.AudioControlTool
import com.noexcs.indolent.agent.tools.setting.SystemSettingTool
import com.noexcs.indolent.agent.tools.systeminfo.BatteryInfoTool
import com.noexcs.indolent.agent.tools.systeminfo.CurrentScreenInfoTool
import com.noexcs.indolent.agent.tools.systeminfo.GetAppInfoTool
import com.noexcs.indolent.agent.tools.systeminfo.NetworkStatusTool
import com.noexcs.indolent.agent.tools.termux.TermuxDialogTool
import com.noexcs.indolent.agent.tools.termux.TermuxExecuteCommandTool
import com.noexcs.indolent.agent.tools.termux.TermuxReadFileTool
import com.noexcs.indolent.agent.tools.termux.TermuxWriteFileTool
import com.noexcs.indolent.data.MemoryManager
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.task.ExecutionStatus
import com.noexcs.indolent.task.TaskExecutionRecord
import com.noexcs.indolent.task.TaskExecutionRepository
import com.noexcs.indolent.task.conditional.conditionProvider.BatteryConditionProvider
import com.noexcs.indolent.task.conditional.conditionProvider.PowerConditionProvider
import com.noexcs.indolent.task.conditional.conditionProvider.SettingConditionProvider
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class TriggerDispatcher(private val context: Context) {

    suspend fun dispatch(trigger: ConditionalTrigger) {
        val now = System.currentTimeMillis()
        val repo = ConditionalTriggerRepository(context)

        // Cooldown check
        if (trigger.lastTriggeredAt > 0 && (now - trigger.lastTriggeredAt) < trigger.cooldownMs) {
            Lumberjack.d(TAG, "Trigger '${trigger.title}' is in cooldown (last=${trigger.lastTriggeredAt}, cooldownMs=${trigger.cooldownMs})")
            return
        }

        // Daily cap check
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val fireCount = if (trigger.fireCountDate == today) trigger.fireCount else 0
        if (fireCount >= trigger.maxFiresPerDay) {
            Lumberjack.d(TAG, "Trigger '${trigger.title}' reached daily cap ($fireCount/${trigger.maxFiresPerDay})")
            return
        }

        // Update state before execution to prevent race conditions
        val updatedTrigger = trigger.copy(
            lastTriggeredAt = now,
            fireCount = fireCount + 1,
            fireCountDate = today
        )
        repo.save(updatedTrigger)

        Lumberjack.i(TAG, "Dispatching trigger: '${trigger.title}' (${trigger.id})")

        val contextualPrompt = buildContextualPrompt(trigger)

        try {
            val settings = SettingsManager(context)
            val baseUrl = settings.baseUrl?.takeIf { it.isNotBlank() } ?: run {
                Lumberjack.w(TAG, "Base URL not configured, skipping trigger: ${trigger.id}")
                return
            }
            val apiKey = settings.apiKey?.takeIf { it.isNotBlank() } ?: run {
                Lumberjack.w(TAG, "API key not configured, skipping trigger: ${trigger.id}")
                return
            }
            val model = settings.model?.ifBlank { "deepseek-chat" } ?: "deepseek-chat"

            val agent = Agent(baseUrl, apiKey, model, settings.thinkingEnabled, settings.reasoningEffort)
            val systemPrompt = buildSystemPrompt()
            val tools = buildTools()

            // Timeout: 5 minutes to avoid hanging
            val reply = withTimeoutOrNull(300_000) {
                agent.execute(contextualPrompt, systemPrompt, tools, 100, true)
            } ?: "Conditional trigger execution timed out after 5 minutes."

            Lumberjack.i(TAG, "Trigger '${trigger.title}' completed (${reply.length} chars)")

            // Save execution record
            val record = TaskExecutionRecord(
                id = UUID.randomUUID().toString(),
                taskId = trigger.id,
                taskTitle = trigger.title,
                prompt = contextualPrompt,
                status = ExecutionStatus.SUCCESS,
                result = reply,
                executedAt = now,
                durationMs = System.currentTimeMillis() - now
            )
            TaskExecutionRepository(context).save(record)

            // Notify if enabled
            if (trigger.notifyEnabled) {
                showNotification(trigger.title, reply.take(200))
            }

        } catch (e: Exception) {
            Lumberjack.e(TAG, "Trigger '${trigger.title}' failed", e)
            val record = TaskExecutionRecord(
                id = UUID.randomUUID().toString(),
                taskId = trigger.id,
                taskTitle = trigger.title,
                prompt = contextualPrompt,
                status = ExecutionStatus.FAILURE,
                errorMessage = e.message ?: "Unknown error",
                executedAt = now,
                durationMs = System.currentTimeMillis() - now
            )
            TaskExecutionRepository(context).save(record)
        }
    }

    private suspend fun buildContextualPrompt(trigger: ConditionalTrigger): String {
        val batteryState = BatteryConditionProvider(context).getState()
        val settingState = SettingConditionProvider(context).getState()
        val powerState = PowerConditionProvider(context).getState()

        val conditionDetails = trigger.conditions.joinToString("\n") { condition ->
            val currentValue = when (condition.source) {
                ConditionSource.BATTERY -> batteryState[condition.field.lowercase()]
                ConditionSource.SYSTEM_SETTING -> settingState[condition.field.lowercase()]
                ConditionSource.POWER -> powerState[condition.field.lowercase()]
                ConditionSource.SENSOR -> null  // sensors are sampled by the evaluator
            }
            val valueStr = if (currentValue != null) " (current: $currentValue)" else ""
            "  - ${condition.source}.${condition.field} ${condition.operator} ${condition.targetValue ?: ""}$valueStr"
        }

        return buildString {
            appendLine("# Condition Trigger Context")
            appendLine()
            appendLine("This task was automatically triggered because the following conditions were met:")
            appendLine()
            appendLine(conditionDetails)
            appendLine()
            appendLine("## Task Instructions")
            appendLine()
            appendLine(trigger.prompt)
        }
    }

    private fun showNotification(title: String, summary: String) {
        ensureChannel()
        val display = if (summary.length >= 200) summary + "…" else summary
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(display)
            .setStyle(NotificationCompat.BigTextStyle().bigText(display))
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(CONDITION_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            "Conditional Triggers",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications from conditional trigger tasks"
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildSystemPrompt(): String {
        val settings = SettingsManager(context)
        val memory = MemoryManager(context).read()
        return buildString {
            appendLine("You are a helpful Android assistant executing a condition-triggered task.")
            appendLine("This task was triggered because specific device conditions were met.")
            appendLine("Your role is to execute the given instructions precisely and efficiently.")
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

    private fun buildTools(): List<AgentTool> {
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
        private const val TAG = "TriggerDispatcher"
        private const val CHANNEL_ID = "conditional_triggers"
        private const val CONDITION_NOTIFICATION_ID = 9995
    }
}
