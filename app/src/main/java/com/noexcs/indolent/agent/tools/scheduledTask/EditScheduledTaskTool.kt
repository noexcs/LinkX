package com.noexcs.indolent.agent.tools.scheduledTask

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.task.ScheduledTaskRepository
import com.noexcs.indolent.task.TaskFrequency
import com.noexcs.indolent.task.scheduler.TaskScheduler
import com.noexcs.indolent.logging.Lumberjack

class EditScheduledTaskTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "edit_scheduled_task"
    override val description = """
        Modify an existing scheduled task. The id can be a prefix — it will be matched automatically.
        Only the provided fields will be updated — all other fields keep their current values.
        To disable a task, set enabled to false. To enable it, set enabled to true.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "id",
            type = "string",
            description = "Task ID or prefix (e.g. first 4+ characters) of the task to edit"
        ),
        ToolParameter(
            name = "title",
            type = "string",
            description = "New title for the task",
            required = false
        ),
        ToolParameter(
            name = "frequency",
            type = "string",
            description = "New frequency: DAILY, WEEKDAYS, WEEKLY, ONCE",
            required = false
        ),
        ToolParameter(
            name = "hour",
            type = "integer",
            description = "New hour of day to execute (0-23)",
            required = false
        ),
        ToolParameter(
            name = "minute",
            type = "integer",
            description = "New minute of hour to execute (0-59)",
            required = false
        ),
        ToolParameter(
            name = "prompt",
            type = "string",
            description = "New prompt/instructions for the task",
            required = false
        ),
        ToolParameter(
            name = "enabled",
            type = "boolean",
            description = "Enable or disable the task",
            required = false
        ),
        ToolParameter(
            name = "notifyEnabled",
            type = "boolean",
            description = "Whether to show a notification when the task completes",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val prefix = (args["id"] as? String)?.trim().orEmpty()
            if (prefix.isBlank()) return "Error: id is required."

            val repo = ScheduledTaskRepository(appContext)
            val existing = repo.resolveByPrefix(prefix)
                ?: return "Error: No task found matching '$prefix'."

            val title = (args["title"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            val frequencyStr = (args["frequency"] as? String)?.trim()?.uppercase()
            val hour = (args["hour"] as? Number)?.toInt()
            val minute = (args["minute"] as? Number)?.toInt()
            val prompt = (args["prompt"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            val enabled = args["enabled"] as? Boolean
            val notifyEnabled = args["notifyEnabled"] as? Boolean

            if (hour != null && hour !in 0..23) return "Error: hour must be 0-23, got $hour."
            if (minute != null && minute !in 0..59) return "Error: minute must be 0-59, got $minute."

            val frequency = if (frequencyStr != null) {
                try { TaskFrequency.valueOf(frequencyStr) }
                catch (e: IllegalArgumentException) {
                    Lumberjack.e("EditScheduledTaskTool", "Invalid frequency: $frequencyStr", e)
                    return "Error: frequency must be one of DAILY, WEEKDAYS, WEEKLY, ONCE. Got: '$frequencyStr'."
                }
            } else null

            val updated = existing.copy(
                title = title ?: existing.title,
                frequency = frequency ?: existing.frequency,
                hour = hour ?: existing.hour,
                minute = minute ?: existing.minute,
                prompt = prompt ?: existing.prompt,
                enabled = enabled ?: existing.enabled,
                notifyEnabled = notifyEnabled ?: existing.notifyEnabled
            )

            repo.save(updated)

            // Cancel old alarm and reschedule if enabled
            val scheduler = TaskScheduler(appContext)
            scheduler.cancel(existing.id)
            val scheduled = if (updated.enabled) scheduler.schedule(updated) else true

            val changes = buildList {
                if (title != null) add("title")
                if (frequencyStr != null) add("frequency")
                if (hour != null || minute != null) add("time")
                if (prompt != null) add("prompt")
                if (enabled != null) add("enabled -> $enabled")
                if (notifyEnabled != null) add("notifyEnabled -> $notifyEnabled")
            }

            buildString {
                appendLine("Task updated successfully!")
                appendLine("ID: ${updated.id}")
                appendLine("Changes: ${changes.joinToString(", ")}")
                if (!scheduled) {
                    appendLine()
                    appendLine("Note: The task was updated but could not be scheduled because 'exact alarm' permission is not granted.")
                }
            }
        } catch (e: IllegalArgumentException) {
            Lumberjack.e("EditScheduledTaskTool", "IllegalArgument editing task", e)
            "Error: ${e.message}"
        } catch (e: Exception) {
            Lumberjack.e("EditScheduledTaskTool", "Error editing task", e)
            "Error editing task: ${e.message}"
        }
    }
}
