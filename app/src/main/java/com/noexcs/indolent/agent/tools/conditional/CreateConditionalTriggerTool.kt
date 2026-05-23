package com.noexcs.indolent.agent.tools.conditional

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.task.conditional.ConditionMonitorScheduler
import com.noexcs.indolent.task.conditional.ConditionalTrigger
import com.noexcs.indolent.task.conditional.ConditionalTriggerRepository
import java.util.UUID

class CreateConditionalTriggerTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "create_conditional_trigger"
    override val description = """
        Create a conditional trigger that fires an agent task when specific device conditions are met.
        This is not time-based — the trigger fires when the condition becomes true.

        Condition sources:
        - BATTERY: level, status (charging/discharging/full/not_charging), plugged, health, temperature, battery_saver, is_charging, is_discharging
        - SYSTEM_SETTING: brightness, auto_brightness, screen_timeout, font_scale, animator_scale, transition_scale, window_animation_scale, haptic_feedback, sound_effects, accelerometer_rotation, time_12_24
        - SENSOR: light, proximity, temperature, humidity, pressure, accelerometer_rms, gyroscope_rms, magnetometer_rms, step_counter
        - POWER: is_power_save, is_interactive, screen_on

        Operators: EQUAL, NOT_EQUAL, GREATER_THAN, LESS_THAN, GREATER_OR_EQUAL, LESS_OR_EQUAL, CHANGED, BECOMES_TRUE, BECOMES_FALSE

        You can specify multiple conditions — ALL must be met (AND logic). For OR logic, create separate triggers.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "title",
            type = "string",
            description = "Short title for the trigger"
        ),
        ToolParameter(
            name = "conditions_json",
            type = "string",
            description = "JSON array of conditions, e.g. [{\"source\":\"BATTERY\",\"field\":\"level\",\"operator\":\"LESS_THAN\",\"targetValue\":\"20\"}]"
        ),
        ToolParameter(
            name = "prompt",
            type = "string",
            description = "The full prompt/instructions for the agent task to execute when triggered"
        ),
        ToolParameter(
            name = "cooldown_seconds",
            type = "integer",
            required = false,
            description = "Minimum seconds between triggers (default: 300 = 5 minutes)"
        ),
        ToolParameter(
            name = "max_fires_per_day",
            type = "integer",
            required = false,
            description = "Maximum times this trigger can fire per day (default: 10)"
        ),
        ToolParameter(
            name = "notify_enabled",
            type = "boolean",
            required = false,
            description = "Whether to show a notification when triggered (default: true)"
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val title = (args["title"] as? String)?.trim().orEmpty()
            val conditionsJson = (args["conditions_json"] as? String)?.trim().orEmpty()
            val prompt = (args["prompt"] as? String)?.trim().orEmpty()
            val cooldownSeconds = (args["cooldown_seconds"] as? Number)?.toLong() ?: 300
            val maxFires = (args["max_fires_per_day"] as? Number)?.toInt() ?: 10
            val notifyEnabled = args["notify_enabled"] as? Boolean ?: true

            if (title.isBlank()) return "Error: title is required."
            if (conditionsJson.isBlank()) return "Error: conditions_json is required."
            if (prompt.isBlank()) return "Error: prompt is required."
            if (cooldownSeconds < 30) return "Error: cooldown_seconds must be at least 30."
            if (maxFires < 1) return "Error: max_fires_per_day must be at least 1."

            val conditionEntries = ConditionParser.parseConditions(conditionsJson)
            if (conditionEntries.isEmpty()) return "Error: conditions_json must contain at least one valid condition."

            val trigger = ConditionalTrigger(
                id = UUID.randomUUID().toString(),
                title = title,
                conditions = conditionEntries,
                prompt = prompt,
                cooldownMs = cooldownSeconds * 1000,
                maxFiresPerDay = maxFires,
                notifyEnabled = notifyEnabled,
                enabled = true
            )

            val repo = ConditionalTriggerRepository(appContext)
            repo.save(trigger)

            // Ensure monitor is scheduled
            ConditionMonitorScheduler(appContext).schedule()

            buildString {
                appendLine("Conditional trigger created successfully!")
                appendLine("ID: ${trigger.id}")
                appendLine("Title: $title")
                appendLine("Conditions:")
                conditionEntries.forEach { cond ->
                    appendLine("  - ${cond.source}.${cond.field} ${cond.operator} ${cond.targetValue ?: ""}")
                }
                appendLine("Cooldown: ${cooldownSeconds}s")
                appendLine("Max fires/day: $maxFires")
            }
        } catch (e: Exception) {
            Lumberjack.e("CreateConditionalTriggerTool", "Error creating trigger", e)
            "Error creating conditional trigger: ${e.message}"
        }
    }

}
