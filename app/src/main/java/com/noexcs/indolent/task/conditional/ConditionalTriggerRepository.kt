package com.noexcs.indolent.task.conditional

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.serialization.json.Json
import java.io.File

class ConditionalTriggerRepository(context: Context) {
    private val dir = File(context.filesDir, "conditional_triggers").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun save(trigger: ConditionalTrigger) {
        File(dir, "${trigger.id}.json").writeText(json.encodeToString(trigger))
    }

    fun load(id: String): ConditionalTrigger? {
        val file = File(dir, "$id.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<ConditionalTrigger>(file.readText())
        } catch (e: Exception) {
            Lumberjack.e(TAG, "Error loading trigger $id", e)
            null
        }
    }

    fun listAll(): List<ConditionalTrigger> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<ConditionalTrigger>(file.readText())
                } catch (e: Exception) {
                    Lumberjack.e(TAG, "Error decoding trigger in listAll", e)
                    null
                }
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    fun listEnabled(): List<ConditionalTrigger> = listAll().filter { it.enabled }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    fun resolveByPrefix(prefix: String): ConditionalTrigger? {
        load(prefix)?.let { return it }
        val matches = listAll().filter { it.id.startsWith(prefix) }
        return when (matches.size) {
            0 -> null
            1 -> matches.first()
            else -> throw IllegalArgumentException(
                "Ambiguous prefix: ${matches.size} triggers match '$prefix'. Use a longer prefix."
            )
        }
    }

    companion object {
        private const val TAG = "ConditionalTriggerRepository"
    }
}
