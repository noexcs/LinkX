package com.noexcs.indolent.agent.tools.conditional

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.task.conditional.ConditionMonitorScheduler
import com.noexcs.indolent.task.conditional.ConditionalTriggerRepository

class EditConditionalTriggerTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "edit_conditional_trigger"
    override val description = """
        Modify an existing conditional trigger. The id can be a prefix — it will be matched automatically.
        Only the provided fields will be updated — all other fields keep their current values.
        To disable a trigger, set enabled to false. To enable it, set enabled to true.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "id",
            type = "string",
            description = "Trigger ID or prefix (e.g. first 4+ characters) of the trigger to edit"
        ),
        ToolParameter(
            name = "title",
            type = "string",
            description = "New title for the trigger",
            required = false
        ),
        ToolParameter(
            name = "conditions_json",
            type = "string",
            description = "New conditions JSON array",
            required = false
        ),
        ToolParameter(
            name = "prompt",
            type = "string",
            description = "New prompt/instructions",
            required = false
        ),
        ToolParameter(
            name = "cooldown_seconds",
            type = "integer",
            description = "New cooldown in seconds (minimum 30)",
            required = false
        ),
        ToolParameter(
            name = "max_fires_per_day",
            type = "integer",
            description = "New max fires per day",
            required = false
        ),
        ToolParameter(
            name = "enabled",
            type = "boolean",
            description = "Enable or disable the trigger",
            required = false
        ),
        ToolParameter(
            name = "notify_enabled",
            type = "boolean",
            description = "Whether to show a notification when triggered",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val prefix = (args["id"] as? String)?.trim().orEmpty()
            if (prefix.isBlank()) return "Error: id is required."

            val repo = ConditionalTriggerRepository(appContext)
            val existing = repo.resolveByPrefix(prefix)
                ?: return "Error: No trigger found matching '$prefix'."

            val title = (args["title"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            val conditionsJson = (args["conditions_json"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            val prompt = (args["prompt"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            val cooldownSeconds = (args["cooldown_seconds"] as? Number)?.toLong()
            val maxFires = (args["max_fires_per_day"] as? Number)?.toInt()
            val enabled = args["enabled"] as? Boolean
            val notifyEnabled = args["notify_enabled"] as? Boolean

            if (cooldownSeconds != null && cooldownSeconds < 30) return "Error: cooldown_seconds must be at least 30."
            if (maxFires != null && maxFires < 1) return "Error: max_fires_per_day must be at least 1."

            val updatedConditions = if (conditionsJson != null) {
                ConditionParser.parseConditions(conditionsJson)
                    .takeIf { it.isNotEmpty() }
                    ?: return "Error: conditions_json must contain at least one valid condition."
            } else null

            val updated = existing.copy(
                title = title ?: existing.title,
                conditions = updatedConditions ?: existing.conditions,
                prompt = prompt ?: existing.prompt,
                cooldownMs = cooldownSeconds?.let { it * 1000 } ?: existing.cooldownMs,
                maxFiresPerDay = maxFires ?: existing.maxFiresPerDay,
                enabled = enabled ?: existing.enabled,
                notifyEnabled = notifyEnabled ?: existing.notifyEnabled
            )

            repo.save(updated)

            // Re-schedule monitor if state changed
            ConditionMonitorScheduler(appContext).schedule()

            val changes = buildList {
                if (title != null) add("title")
                if (conditionsJson != null) add("conditions")
                if (prompt != null) add("prompt")
                if (cooldownSeconds != null) add("cooldown -> ${cooldownSeconds}s")
                if (maxFires != null) add("max_fires -> $maxFires")
                if (enabled != null) add("enabled -> $enabled")
                if (notifyEnabled != null) add("notify -> $notifyEnabled")
            }

            buildString {
                appendLine("Conditional trigger updated successfully!")
                appendLine("ID: ${updated.id}")
                appendLine("Changes: ${changes.joinToString(", ")}")
            }
        } catch (e: IllegalArgumentException) {
            Lumberjack.e("EditConditionalTriggerTool", "IllegalArgument editing trigger", e)
            "Error: ${e.message}"
        } catch (e: Exception) {
            Lumberjack.e("EditConditionalTriggerTool", "Error editing trigger", e)
            "Error editing trigger: ${e.message}"
        }
    }

}
