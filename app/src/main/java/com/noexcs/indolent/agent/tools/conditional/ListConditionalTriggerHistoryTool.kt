package com.noexcs.indolent.agent.tools.conditional

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.task.TaskExecutionRecord
import com.noexcs.indolent.task.resultPreview
import com.noexcs.indolent.task.TaskExecutionRepository
import com.noexcs.indolent.task.conditional.ConditionalTriggerRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListConditionalTriggerHistoryTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "list_conditional_trigger_history"
    override val description = """
        View execution history of conditional triggers. You can list all records,
        filter by a trigger ID/prefix, or view a specific execution record.
        If no id is provided, lists all execution records across all triggers.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "trigger_id",
            type = "string",
            description = "Trigger ID or prefix to filter history for a specific trigger (omit to list all)",
            required = false
        ),
        ToolParameter(
            name = "execution_id",
            type = "string",
            description = "Specific execution record ID or prefix to view detail (omit to list)",
            required = false
        ),
        ToolParameter(
            name = "limit",
            type = "integer",
            description = "Max number of records to show (default: 20)",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val triggerId = (args["trigger_id"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            val executionId = (args["execution_id"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            val limit = (args["limit"] as? Number)?.toInt() ?: 20

            val execRepo = TaskExecutionRepository(appContext)

            if (executionId != null) {
                // View specific execution detail
                val records = execRepo.listAll()
                val record = records.find {
                    it.id == executionId || it.id.startsWith(executionId)
                } ?: return "Error: No execution record found matching '$executionId'."
                formatRecordDetail(record)
            } else if (triggerId != null) {
                // Filter by trigger
                val triggerRepo = ConditionalTriggerRepository(appContext)
                val trigger = triggerRepo.resolveByPrefix(triggerId)
                if (trigger == null) return "Error: No conditional trigger found matching '$triggerId'."

                val records = execRepo.listByTaskId(trigger.id)
                if (records.isEmpty()) return "No execution records for trigger '${trigger.title}'."

                formatRecordList(records, limit, "for trigger '${trigger.title}'")
            } else {
                // List all
                val records = execRepo.listAll()
                if (records.isEmpty()) return "No execution records found."

                // Try to identify which are conditional trigger records
                formatRecordList(records, limit, "across all triggers")
            }
        } catch (e: Exception) {
            Lumberjack.e("ListConditionalTriggerHistoryTool", "Error listing history", e)
            "Error listing trigger history: ${e.message}"
        }
    }

    private fun formatRecordList(records: List<TaskExecutionRecord>, limit: Int, scope: String): String {
        val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
        val shown = records.take(limit)

        return buildString {
            appendLine("Execution History ($scope, ${shown.size} of ${records.size} records):")
            appendLine("=".repeat(60))
            shown.forEach { record ->
                val status = if (record.status.name == "SUCCESS") "OK" else "FAIL"
                val time = dateFormat.format(Date(record.executedAt))
                val duration = if (record.durationMs >= 1000) "${record.durationMs / 1000}s" else "${record.durationMs}ms"
                appendLine("[${record.id.take(8)}] $status | ${record.taskTitle} | $time | $duration")
                if (record.errorMessage.isNotBlank()) {
                    appendLine("  Error: ${record.errorMessage.take(120)}")
                } else {
                    appendLine("  ${record.resultPreview.take(150).replace("\n", " ")}")
                }
                appendLine()
            }
            if (records.size > limit) {
                appendLine("... and ${records.size - limit} more records. Use limit or execution_id to narrow down.")
            }
        }.trim()
    }

    private fun formatRecordDetail(record: TaskExecutionRecord): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("Execution Record Detail:")
            appendLine("=".repeat(60))
            appendLine("ID: ${record.id}")
            appendLine("Trigger ID: ${record.taskId}")
            appendLine("Trigger Title: ${record.taskTitle}")
            appendLine("Status: ${record.status.name}")
            appendLine("Executed at: ${dateFormat.format(Date(record.executedAt))}")
            appendLine("Duration: ${record.durationMs}ms")
            if (record.errorMessage.isNotBlank()) {
                appendLine()
                appendLine("Error:")
                appendLine(record.errorMessage)
            }
            appendLine()
            appendLine("Result:")
            appendLine(record.resultPreview)
        }
    }
}
