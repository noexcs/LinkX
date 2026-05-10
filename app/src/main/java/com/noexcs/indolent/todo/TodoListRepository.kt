package com.noexcs.indolent.todo

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.serialization.json.Json
import java.io.File

class TodoListRepository(context: Context) {
    private val dir = File(context.filesDir, "todo_lists").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun save(list: TodoList) {
        File(dir, "${list.id}.json").writeText(json.encodeToString(list))
    }

    fun load(id: String): TodoList? {
        val file = File(dir, "$id.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<TodoList>(file.readText())
        } catch (e: Exception) {
            Lumberjack.e("TodoListRepository", "Error loading list $id", e)
            null
        }
    }

    fun listAll(): List<TodoList> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<TodoList>(file.readText())
                } catch (e: Exception) {
                    Lumberjack.e("TodoListRepository", "Error decoding list in listAll", e)
                    null
                }
            }
            ?.sortedBy { it.sortOrder }
            ?: emptyList()
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }
}
