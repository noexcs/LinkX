package com.noexcs.indolent.task

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.serialization.json.Json
import java.io.File

class ScheduledTaskRepository(context: Context) {
    private val dir = File(context.filesDir, "scheduled_tasks").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Synchronized fun save(task: ScheduledTask) {
        File(dir, "${task.id}.json").writeText(json.encodeToString(task))
    }

    @Synchronized fun load(id: String): ScheduledTask? {
        val file = File(dir, "$id.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<ScheduledTask>(file.readText())
        } catch (e: Exception) {
            Lumberjack.e("ScheduledTaskRepository", "Error loading task $id", e)
            null
        }
    }

    @Synchronized fun listAll(): List<ScheduledTask> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<ScheduledTask>(file.readText())
                } catch (e: Exception) {
                    Lumberjack.e("ScheduledTaskRepository", "Error decoding task in listAll", e)
                    null
                }
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    @Synchronized fun delete(id: String) {
        if (!File(dir, "$id.json").delete()) {
            Lumberjack.w("ScheduledTaskRepository", "Failed to delete: $id")
        }
    }

    @Synchronized fun resolveByPrefix(prefix: String): ScheduledTask? {
        // Try exact match first
        load(prefix)?.let { return it }
        // Prefix match
        val matches = listAll().filter { it.id.startsWith(prefix) }
        return when (matches.size) {
            0 -> null
            1 -> matches.first()
            else -> throw IllegalArgumentException(
                "Ambiguous prefix: ${matches.size} tasks match '$prefix'. Use a longer prefix."
            )
        }
    }
}
