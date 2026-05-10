package com.noexcs.indolent.todo

import kotlinx.serialization.Serializable

@Serializable
enum class Priority {
    NONE, LOW, MEDIUM, HIGH
}

@Serializable
data class Subtask(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false
)

@Serializable
data class TodoItem(
    val id: String,
    val listId: String,
    val title: String,
    val note: String = "",
    val dueDate: Long? = null,
    val priority: Priority = Priority.NONE,
    val isCompleted: Boolean = false,
    val isImportant: Boolean = false,
    val isMyDay: Boolean = false,
    val myDayDate: Long? = null,
    val subtasks: List<Subtask> = emptyList(),
    val reminder: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)
