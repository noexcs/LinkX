package com.noexcs.indolent.note

import kotlinx.serialization.Serializable

@Serializable
data class NoteItem(
    val id: String,
    val title: String = "",
    val content: String = "",
    val color: Long = 0xFF2D2D2D,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val labels: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
