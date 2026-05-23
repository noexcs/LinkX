package com.noexcs.indolent.todo

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

class TodoItemRepository(context: Context) {
    private val dir = File(context.filesDir, "todo_items").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun save(item: TodoItem) = withContext(Dispatchers.IO) {
        File(dir, "${item.id}.json").writeText(json.encodeToString(item))
    }

    suspend fun load(id: String): TodoItem? = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString<TodoItem>(file.readText())
        } catch (e: Exception) {
            Lumberjack.e("TodoItemRepository", "Error loading item $id", e)
            null
        }
    }

    suspend fun listByListId(listId: String): List<TodoItem> = withContext(Dispatchers.IO) {
        listAll().filter { it.listId == listId }
            .sortedWith(compareBy({ it.isCompleted }, { it.sortOrder }))
    }

    suspend fun listAll(): List<TodoItem> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<TodoItem>(file.readText())
                } catch (e: Exception) {
                    Lumberjack.e("TodoItemRepository", "Error decoding item in listAll", e)
                    null
                }
            }
            ?: emptyList()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
    }

    suspend fun deleteByListId(listId: String) = withContext(Dispatchers.IO) {
        listByListId(listId).forEach { delete(it.id) }
    }

    suspend fun listMyDayItems(): List<TodoItem> = withContext(Dispatchers.IO) {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        listAll().filter { it.isMyDay && (it.myDayDate ?: 0) >= todayStart }
    }

    suspend fun listImportantItems(): List<TodoItem> = withContext(Dispatchers.IO) {
        listAll().filter { it.isImportant && !it.isCompleted }
    }

    suspend fun listPlannedItems(): List<TodoItem> = withContext(Dispatchers.IO) {
        listAll().filter { it.dueDate != null && !it.isCompleted }
    }
}
