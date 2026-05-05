package com.noexcs.indolent.agent.tools.conditional

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.task.conditional.ConditionalTrigger
import com.noexcs.indolent.task.conditional.ConditionalTriggerRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListConditionalTriggersTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "list_conditional_triggers"
    override val description = "List all conditional triggers or get details of a specific one. The id can be a partial prefix."

    override val parameters = listOf(
        ToolParameter(
            name = "id",
            type = "string",
            description = "Trigger ID or prefix to get details of a specific trigger (omit to list all)",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val repo = ConditionalTriggerRepository(appContext)
            val id = (args["id"] as? String)?.trim().orEmpty()

            if (id.isNotBlank()) {
                val trigger = repo.resolveByPrefix(id)
                    ?: return "Error: No conditional trigger found matching '$id'."
                formatTrigger(trigger)
            } else {
                val triggers = repo.listAll()
                if (triggers.isEmpty()) return "No conditional triggers found."

                buildString {
                    appendLine("Conditional Triggers (${triggers.size}):")
                    appendLine("=".repeat(50))
                    triggers.forEach { trigger ->
                        val status = when {
                            !trigger.enabled -> "disabled"
                            else -> "active"
                        }
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        val todayFires = if (trigger.fireCountDate == today) trigger.fireCount else 0
                        appendLine("[${trigger.id.take(8)}] $status | ${trigger.title}")
                        appendLine("  Conditions: ${trigger.conditions.size}, Fires today: $todayFires/${trigger.maxFiresPerDay}")
                        appendLine()
                    }
                }.trim()
            }
        } catch (e: IllegalArgumentException) {
            Lumberjack.e("ListConditionalTriggersTool", "IllegalArgument in list triggers", e)
            "Error: ${e.message}"
        } catch (e: Exception) {
            Lumberjack.e("ListConditionalTriggersTool", "Error listing triggers", e)
            "Error listing triggers: ${e.message}"
        }
    }

    private fun formatTrigger(trigger: ConditionalTrigger): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val lastFired = if (trigger.lastTriggeredAt > 0) {
            dateFormat.format(Date(trigger.lastTriggeredAt))
        } else "never"

        return buildString {
            appendLine("Conditional Trigger Details:")
            appendLine("=".repeat(50))
            appendLine("ID: ${trigger.id}")
            appendLine("Title: ${trigger.title}")
            appendLine("Enabled: ${trigger.enabled}")
            appendLine("Notify: ${trigger.notifyEnabled}")
            appendLine("Cooldown: ${trigger.cooldownMs / 1000}s")
            appendLine("Max fires/day: ${trigger.maxFiresPerDay}")
            appendLine("Created: ${dateFormat.format(Date(trigger.createdAt))}")
            appendLine("Last triggered: $lastFired")
            appendLine()
            appendLine("Conditions (ALL must be met):")
            trigger.conditions.forEach { cond ->
                appendLine("  ${cond.source}.${cond.field} ${cond.operator} ${cond.targetValue ?: "(none)"}")
            }
            appendLine()
            appendLine("Prompt:")
            appendLine(trigger.prompt)
        }
    }
}
