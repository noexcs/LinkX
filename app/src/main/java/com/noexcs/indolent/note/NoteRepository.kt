package com.noexcs.indolent.note

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class NoteRepository(context: Context) {
    private val dir = File(context.filesDir, "notes").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun save(note: NoteItem) = withContext(Dispatchers.IO) {
        File(dir, "${note.id}.json").writeText(json.encodeToString(note))
    }

    suspend fun load(id: String): NoteItem? = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString<NoteItem>(file.readText())
        } catch (e: Exception) {
            Lumberjack.e("NoteRepository", "Error loading note $id", e)
            null
        }
    }

    private suspend fun listAll(): List<NoteItem> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<NoteItem>(file.readText())
                } catch (e: Exception) {
                    Lumberjack.e("NoteRepository", "Error decoding note in listAll", e)
                    null
                }
            }
            ?: emptyList()
    }

    suspend fun listActive(): List<NoteItem> {
        return listAll()
            .filter { !it.isArchived }
            .sortedWith(compareByDescending<NoteItem> { it.isPinned }.thenByDescending { it.updatedAt })
    }

    suspend fun listArchived(): List<NoteItem> {
        return listAll()
            .filter { it.isArchived }
            .sortedByDescending { it.updatedAt }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
    }
}
