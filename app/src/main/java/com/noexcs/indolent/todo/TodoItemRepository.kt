package com.noexcs.indolent.todo

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

class TodoItemRepository(context: Context) {
    private val dir = File(context.filesDir, "todo_items").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun save(item: TodoItem) {
        File(dir, "${item.id}.json").writeText(json.encodeToString(item))
    }

    fun load(id: String): TodoItem? {
        val file = File(dir, "$id.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<TodoItem>(file.readText())
        } catch (e: Exception) {
            Lumberjack.e("TodoItemRepository", "Error loading item $id", e)
            null
        }
    }

    fun listByListId(listId: String): List<TodoItem> {
        return listAll().filter { it.listId == listId }
            .sortedWith(compareBy({ it.isCompleted }, { it.sortOrder }))
    }

    fun listAll(): List<TodoItem> {
        return dir.listFiles { f -> f.extension == "json" }
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

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    fun deleteByListId(listId: String) {
        listByListId(listId).forEach { delete(it.id) }
    }

    fun listMyDayItems(): List<TodoItem> {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return listAll().filter { it.isMyDay && (it.myDayDate ?: 0) >= todayStart }
    }

    fun listImportantItems(): List<TodoItem> {
        return listAll().filter { it.isImportant && !it.isCompleted }
    }

    fun listPlannedItems(): List<TodoItem> {
        return listAll().filter { it.dueDate != null && !it.isCompleted }
    }
}
