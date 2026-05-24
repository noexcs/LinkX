package com.noexcs.indolent.prompt

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class SystemPromptRepository(context: Context) {
    private val dir = File(context.filesDir, "system_prompts").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun save(item: SystemPromptItem) = withContext(Dispatchers.IO) {
        File(dir, "${item.id}.json").writeText(json.encodeToString(item))
    }

    suspend fun load(id: String): SystemPromptItem? = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString<SystemPromptItem>(file.readText())
        } catch (e: Exception) {
            Lumberjack.e("SystemPromptRepository", "Error loading prompt $id", e)
            null
        }
    }

    suspend fun listAll(): List<SystemPromptItem> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<SystemPromptItem>(file.readText())
                } catch (e: Exception) {
                    Lumberjack.e("SystemPromptRepository", "Error decoding prompt in listAll", e)
                    null
                }
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
    }
}
