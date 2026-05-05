package com.noexcs.indolent.agent.tools.conditional

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.task.conditional.ConditionalTriggerRepository

class DeleteConditionalTriggerTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "delete_conditional_trigger"
    override val description = "Delete a conditional trigger permanently. The id can be a prefix — it will be matched automatically."

    override val parameters = listOf(
        ToolParameter(
            name = "id",
            type = "string",
            description = "Trigger ID or prefix (e.g. first 4+ characters) of the trigger to delete"
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val prefix = (args["id"] as? String)?.trim().orEmpty()
            if (prefix.isBlank()) return "Error: id is required."

            val repo = ConditionalTriggerRepository(appContext)
            val trigger = repo.resolveByPrefix(prefix)
                ?: return "Error: No conditional trigger found matching '$prefix'."

            repo.delete(trigger.id)

            "Conditional trigger '${trigger.title}' (ID: ${trigger.id}) has been deleted."
        } catch (e: IllegalArgumentException) {
            Lumberjack.e("DeleteConditionalTriggerTool", "IllegalArgument deleting trigger", e)
            "Error: ${e.message}"
        } catch (e: Exception) {
            Lumberjack.e("DeleteConditionalTriggerTool", "Error deleting trigger", e)
            "Error deleting trigger: ${e.message}"
        }
    }
}
