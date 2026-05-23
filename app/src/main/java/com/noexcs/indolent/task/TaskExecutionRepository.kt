package com.noexcs.indolent.task

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.serialization.json.Json
import java.io.File

class TaskExecutionRepository(context: Context) {
    private val dir = File(context.filesDir, "scheduled_tasks/executions").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Synchronized fun save(record: TaskExecutionRecord) {
        File(dir, "${record.id}.json").writeText(json.encodeToString(record))
    }

    @Synchronized fun listByTaskId(taskId: String): List<TaskExecutionRecord> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<TaskExecutionRecord>(file.readText())
                } catch (_: Exception) { null }
            }
            ?.filter { it.taskId == taskId }
            ?.sortedByDescending { it.executedAt }
            ?: emptyList()
    }

    @Synchronized fun listAll(): List<TaskExecutionRecord> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<TaskExecutionRecord>(file.readText())
                } catch (_: Exception) { null }
            }
            ?.sortedByDescending { it.executedAt }
            ?: emptyList()
    }

    @Synchronized fun deleteByTaskId(taskId: String) {
        dir.listFiles { f -> f.extension == "json" }
            ?.filter { file ->
                try {
                    json.decodeFromString<TaskExecutionRecord>(file.readText()).taskId == taskId
                } catch (_: Exception) { false }
            }
            ?.forEach { if (!it.delete()) Lumberjack.w("TaskExecutionRepository", "Failed to delete: ${it.name}") }
    }

    @Synchronized fun pruneOldRecords(taskId: String, keep: Int = 50) {
        val records = listByTaskId(taskId)
        if (records.size > keep) {
            records.drop(keep).forEach { record ->
                if (!File(dir, "${record.id}.json").delete()) {
                    Lumberjack.w("TaskExecutionRepository", "Failed to delete record: ${record.id}")
                }
            }
        }
    }
}
