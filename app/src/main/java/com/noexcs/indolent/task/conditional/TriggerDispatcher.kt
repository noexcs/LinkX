package com.noexcs.indolent.task.conditional

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.noexcs.indolent.R
import com.noexcs.indolent.agent.BackgroundSessionRunner
import com.noexcs.indolent.agent.LLMMessage
import com.noexcs.indolent.agent.SessionType
import com.noexcs.indolent.agent.tools.common.AgentClipboardStore
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.task.ExecutionStatus
import com.noexcs.indolent.task.TaskExecutionRecord
import com.noexcs.indolent.task.TaskExecutionRepository
import com.noexcs.indolent.task.conditional.conditionProvider.BatteryConditionProvider
import com.noexcs.indolent.task.conditional.conditionProvider.PowerConditionProvider
import com.noexcs.indolent.task.conditional.conditionProvider.SettingConditionProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class TriggerDispatcher(private val context: Context) {

    private val dispatchMutex = Mutex()

    suspend fun dispatch(trigger: ConditionalTrigger) = dispatchMutex.withLock {
        val now = System.currentTimeMillis()
        val repo = ConditionalTriggerRepository(context)

        // Reload from repository to get latest state under the lock.
        // The parameter 'trigger' may be stale if another coroutine already dispatched it.
        val currentTrigger = repo.load(trigger.id) ?: run {
            Lumberjack.w(TAG, "Trigger '${trigger.title}' (${trigger.id}) not found in repository, skipping")
            return
        }

        // Re-check enabled — trigger may have been disabled since loading
        if (!currentTrigger.enabled) {
            Lumberjack.d(TAG, "Trigger '${currentTrigger.title}' is disabled, skipping")
            return
        }

        // Cooldown check against reloaded state
        if (currentTrigger.lastTriggeredAt > 0 && (now - currentTrigger.lastTriggeredAt) < currentTrigger.cooldownMs) {
            Lumberjack.d(TAG, "Trigger '${currentTrigger.title}' is in cooldown (last=${currentTrigger.lastTriggeredAt}, cooldownMs=${currentTrigger.cooldownMs})")
            return
        }

        // Daily cap check against reloaded state
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val fireCount = if (currentTrigger.fireCountDate == today) currentTrigger.fireCount else 0
        if (fireCount >= currentTrigger.maxFiresPerDay) {
            Lumberjack.d(TAG, "Trigger '${currentTrigger.title}' reached daily cap ($fireCount/${currentTrigger.maxFiresPerDay})")
            return
        }

        // Update state before execution using reloaded data
        val updatedTrigger = currentTrigger.copy(
            lastTriggeredAt = now,
            fireCount = fireCount + 1,
            fireCountDate = today
        )
        repo.save(updatedTrigger)

        Lumberjack.i(TAG, "Dispatching trigger: '${currentTrigger.title}' (${currentTrigger.id})")

        val contextualPrompt = buildContextualPrompt(currentTrigger)

        try {
            val clipboardStore = AgentClipboardStore()
            val session = try {
                BackgroundSessionRunner.create(context, currentTrigger.id, SessionType.CONDITIONAL_TRIGGER,
                    clipboardStore = clipboardStore)
            } catch (e: IllegalStateException) {
                Lumberjack.w(TAG, "${e.message}, skipping trigger: ${currentTrigger.id}")
                return
            }
            session.context = BackgroundSessionRunner.buildContextConfig(
                context,
                buildString {
                    appendLine("You are a helpful Android assistant executing a condition-triggered task.")
                    appendLine("This task was triggered because specific device conditions were met.")
                    appendLine("Your role is to execute the given instructions precisely and efficiently.")
                }.trimEnd(),
                clipboardStore = clipboardStore
            )
            val tools = BackgroundSessionRunner.buildTools(
                context,
                clipboardStore = clipboardStore,
                historyProvider = { session.history }
            )

            // Timeout: 5 minutes to avoid hanging
            val reply = withTimeoutOrNull(300_000) {
                session.execute(contextualPrompt, tools)
            } ?: listOf(LLMMessage(role = "system", content = "Conditional trigger execution timed out after 5 minutes."))

            Lumberjack.i(TAG, "Trigger '${currentTrigger.title}' completed (${reply.size} messages)")
            session.save()

            // Save execution record
            val record = TaskExecutionRecord(
                id = UUID.randomUUID().toString(),
                taskId = currentTrigger.id,
                taskTitle = currentTrigger.title,
                prompt = contextualPrompt,
                status = ExecutionStatus.SUCCESS,
                result = reply,
                executedAt = now,
                durationMs = System.currentTimeMillis() - now
            )
            TaskExecutionRepository(context).save(record)

            // Notify if enabled
            if (currentTrigger.notifyEnabled) {
                val replyText = reply.lastOrNull { it.role == "assistant" }?.content ?: ""
                showNotification(currentTrigger.title, replyText.take(200))
            }

        } catch (e: Exception) {
            Lumberjack.e(TAG, "Trigger '${currentTrigger.title}' failed", e)
            val record = TaskExecutionRecord(
                id = UUID.randomUUID().toString(),
                taskId = currentTrigger.id,
                taskTitle = currentTrigger.title,
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

    companion object {
        private const val TAG = "TriggerDispatcher"
        private const val CHANNEL_ID = "conditional_triggers"
        private const val CONDITION_NOTIFICATION_ID = 9995
    }
}
