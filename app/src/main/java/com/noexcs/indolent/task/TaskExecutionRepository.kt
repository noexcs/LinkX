package com.noexcs.indolent.task

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.serialization.json.Json
import java.io.File

class TaskExecutionRepository(context: Context) {
    private val dir = File(context.filesDir, "scheduled_tasks/executions").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun save(record: TaskExecutionRecord) {
        File(dir, "${record.id}.json").writeText(json.encodeToString(record))
    }

    fun listByTaskId(taskId: String): List<TaskExecutionRecord> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<TaskExecutionRecord>(file.readText())
                } catch (e: Exception) {
                    Lumberjack.e("TaskExecutionRepository", "Error decoding task execution record listing by task ID", e)
                    null
                }
            }
            ?.filter { it.taskId == taskId }
            ?.sortedByDescending { it.executedAt }
            ?: emptyList()
    }

    fun listAll(): List<TaskExecutionRecord> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<TaskExecutionRecord>(file.readText())
                } catch (e: Exception) {
                    Lumberjack.e("TaskExecutionRepository", "Error decoding task execution record in listAll", e)
                    null
                }
            }
            ?.sortedByDescending { it.executedAt }
            ?: emptyList()
    }

    fun deleteByTaskId(taskId: String) {
        dir.listFiles { f -> f.extension == "json" }
            ?.filter { file ->
                try {
                    json.decodeFromString<TaskExecutionRecord>(file.readText()).taskId == taskId
                } catch (e: Exception) {
                    Lumberjack.e("TaskExecutionRepository", "Error decoding task execution record in deleteByTaskId", e)
                    false
                }
            }
            ?.forEach { it.delete() }
    }

    fun pruneOldRecords(taskId: String, keep: Int = 50) {
        val records = listByTaskId(taskId)
        if (records.size > keep) {
            records.drop(keep).forEach { record ->
                File(dir, "${record.id}.json").delete()
            }
        }
    }
}
