package com.noexcs.indolent.todo

import kotlinx.serialization.Serializable

@Serializable
data class TodoList(
    val id: String,
    val name: String,
    val color: Long? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
