package com.noexcs.indolent.agent.tools.scheduledTask

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.task.ScheduledTaskRepository
import com.noexcs.indolent.task.TaskExecutionRepository
import com.noexcs.indolent.task.scheduler.TaskScheduler
import com.noexcs.indolent.logging.Lumberjack

class DeleteScheduledTaskTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "delete_scheduled_task"
    override val description = "Delete a scheduled task permanently. The id can be a prefix — it will be matched automatically."

    override val parameters = listOf(
        ToolParameter(
            name = "id",
            type = "string",
            description = "Task ID or prefix (e.g. first 4+ characters) of the task to delete"
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val prefix = (args["id"] as? String)?.trim().orEmpty()
            if (prefix.isBlank()) return "Error: id is required."

            val repo = ScheduledTaskRepository(appContext)
            val task = repo.resolveByPrefix(prefix)
                ?: return "Error: No task found matching '$prefix'."

            TaskScheduler(appContext).cancel(task.id)
            TaskExecutionRepository(appContext).deleteByTaskId(task.id)
            repo.delete(task.id)

            "Task '${task.title}' (ID: ${task.id}) has been deleted."
        } catch (e: IllegalArgumentException) {
            Lumberjack.e("DeleteScheduledTaskTool", "IllegalArgument deleting task", e)
            "Error: ${e.message}"
        } catch (e: Exception) {
            Lumberjack.e("DeleteScheduledTaskTool", "Error deleting task", e)
            "Error deleting task: ${e.message}"
        }
    }
}
