package com.noexcs.indolent.agent.tools.scheduledTask

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.task.ScheduledTask
import com.noexcs.indolent.task.ScheduledTaskRepository
import java.text.SimpleDateFormat
import java.util.Date
import com.noexcs.indolent.logging.Lumberjack
import java.util.Locale

class ListScheduledTasksTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "list_scheduled_tasks"
    override val description = "List all scheduled tasks or get details of a specific task. The id can be a partial prefix."

    override val parameters = listOf(
        ToolParameter(
            name = "id",
            type = "string",
            description = "Task ID or prefix to get details of a specific task (omit to list all)",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val repo = ScheduledTaskRepository(appContext)
            val id = (args["id"] as? String)?.trim().orEmpty()

            if (id.isNotBlank()) {
                val task = repo.resolveByPrefix(id)
                    ?: return "Error: No task found matching '$id'."
                formatTask(task)
            } else {
                val tasks = repo.listAll()
                if (tasks.isEmpty()) return "No scheduled tasks found."

                buildString {
                    appendLine("Scheduled Tasks (${tasks.size}):")
                    appendLine("=".repeat(50))
                    tasks.forEach { task ->
                        val status = if (task.enabled) "active" else "disabled"
                        appendLine("[${task.id}] $status | ${task.title}")
                        appendLine("  ${task.frequency} at ${task.hour.toString().padStart(2, '0')}:${task.minute.toString().padStart(2, '0')}")
                        appendLine()
                    }
                }.trim()
            }
        } catch (e: IllegalArgumentException) {
            Lumberjack.e("ListScheduledTasksTool", "IllegalArgument in list tasks", e)
            "Error: ${e.message}"
        } catch (e: Exception) {
            Lumberjack.e("ListScheduledTasksTool", "Error listing tasks", e)
            "Error listing tasks: ${e.message}"
        }
    }

    private fun formatTask(task: ScheduledTask): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("Task Details:")
            appendLine("=".repeat(50))
            appendLine("ID: ${task.id}")
            appendLine("Title: ${task.title}")
            appendLine("Frequency: ${task.frequency}")
            appendLine("Time: ${task.hour.toString().padStart(2, '0')}:${task.minute.toString().padStart(2, '0')}")
            appendLine("Enabled: ${task.enabled}")
            appendLine("Notify: ${task.notifyEnabled}")
            appendLine("Created: ${dateFormat.format(Date(task.createdAt))}")
            appendLine()
            appendLine("Prompt:")
            appendLine(task.prompt)
        }
    }
}