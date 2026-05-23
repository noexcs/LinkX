package com.noexcs.indolent.agent.tools.scheduledTask

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.task.ScheduledTask
import com.noexcs.indolent.task.ScheduledTaskRepository
import com.noexcs.indolent.task.TaskFrequency
import com.noexcs.indolent.task.scheduler.TaskScheduler
import com.noexcs.indolent.logging.Lumberjack
import java.util.UUID

class CreateScheduledTaskTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "create_scheduled_task"
    override val description = """
        Create a scheduled task that will be executed automatically at the specified time.
        The task runs with the same tools and capabilities as the current agent (including Termux, finance tools, etc.).
        Use this for reminders, periodic checks, automated reports, or any task that should run at a future time.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "title",
            type = "string",
            description = "Short title for the task"
        ),
        ToolParameter(
            name = "frequency",
            type = "string",
            description = "How often the task runs: DAILY (every day), WEEKDAYS (Mon-Fri), WEEKLY (same day each week), ONCE (one-time)"
        ),
        ToolParameter(
            name = "hour",
            type = "integer",
            description = "Hour of day to execute (0-23)"
        ),
        ToolParameter(
            name = "minute",
            type = "integer",
            description = "Minute of hour to execute (0-59)"
        ),
        ToolParameter(
            name = "prompt",
            type = "string",
            description = "The full prompt/instructions for the task. Be detailed — include what tools to use, what to check, what to report."
        ),
        ToolParameter(
            name = "notify_enabled",
            type = "boolean",
            description = "Whether to show a notification when the task completes (default: true)",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val title = (args["title"] as? String)?.trim().orEmpty()
            val frequencyStr = (args["frequency"] as? String)?.trim()?.uppercase().orEmpty()
            val hour = (args["hour"] as? Number)?.toInt() ?: -1
            val minute = (args["minute"] as? Number)?.toInt() ?: -1
            val prompt = (args["prompt"] as? String)?.trim().orEmpty()
            val notifyEnabled = args["notify_enabled"] as? Boolean ?: true

            // Validate
            if (title.isBlank()) return "Error: title is required and must not be blank."
            if (prompt.isBlank()) return "Error: prompt is required and must not be blank."
            if (hour !in 0..23) return "Error: hour must be 0-23, got $hour."
            if (minute !in 0..59) return "Error: minute must be 0-59, got $minute."

            val frequency = try {
                TaskFrequency.valueOf(frequencyStr)
            } catch (e: IllegalArgumentException) {
                Lumberjack.e("CreateScheduledTaskTool", "Invalid frequency: $frequencyStr", e)
                return "Error: frequency must be one of DAILY, WEEKDAYS, WEEKLY, ONCE. Got: '$frequencyStr'."
            }

            val task = ScheduledTask(
                id = UUID.randomUUID().toString(),
                title = title,
                frequency = frequency,
                hour = hour,
                minute = minute,
                prompt = prompt,
                notifyEnabled = notifyEnabled,
                enabled = true
            )

            val repo = ScheduledTaskRepository(appContext)
            repo.save(task)

            val scheduler = TaskScheduler(appContext)
            val scheduled = scheduler.schedule(task)

            buildString {
                appendLine("Task created successfully!")
                appendLine("ID: ${task.id}")
                appendLine("Title: $title")
                appendLine("Frequency: $frequency")
                appendLine("Time: ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}")
                if (!scheduled) {
                    appendLine()
                    appendLine("Note: The task was saved but could not be scheduled because 'exact alarm' permission is not granted.")
                    appendLine("The task will not execute until the user grants this permission in system settings.")
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("CreateScheduledTaskTool", "Error creating task", e)
            "Error creating task: ${e.message}"
        }
    }
}
