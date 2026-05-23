package com.noexcs.indolent.todo

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class TodoListRepository(
    context: Context,
    private val itemRepository: TodoItemRepository
) {
    private val dir = File(context.filesDir, "todo_lists").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun save(list: TodoList) = withContext(Dispatchers.IO) {
        File(dir, "${list.id}.json").writeText(json.encodeToString(list))
    }

    suspend fun load(id: String): TodoList? = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString<TodoList>(file.readText())
        } catch (e: Exception) {
            Lumberjack.e("TodoListRepository", "Error loading list $id", e)
            null
        }
    }

    suspend fun listAll(): List<TodoList> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "json" }
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

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        itemRepository.deleteByListId(id)
        File(dir, "$id.json").delete()
    }
}
