package com.noexcs.indolent.note

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.serialization.json.Json
import java.io.File

class NoteRepository(context: Context) {
    private val dir = File(context.filesDir, "notes").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun save(note: NoteItem) {
        File(dir, "${note.id}.json").writeText(json.encodeToString(note))
    }

    fun load(id: String): NoteItem? {
        val file = File(dir, "$id.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<NoteItem>(file.readText())
        } catch (e: Exception) {
            Lumberjack.e("NoteRepository", "Error loading note $id", e)
            null
        }
    }

    fun listAll(): List<NoteItem> {
        return dir.listFiles { f -> f.extension == "json" }
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

    fun listActive(): List<NoteItem> {
        return listAll()
            .filter { !it.isArchived }
            .sortedWith(compareByDescending<NoteItem> { it.isPinned }.thenByDescending { it.updatedAt })
    }

    fun listArchived(): List<NoteItem> {
        return listAll()
            .filter { it.isArchived }
            .sortedByDescending { it.updatedAt }
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }
}
