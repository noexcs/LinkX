package com.noexcs.indolent.prompt

import kotlinx.serialization.Serializable

@Serializable
data class SystemPromptItem(
    val id: String,
    val name: String = "",
    val content: String = "",
    val color: Long = 0xFF6750A4,
    val icon: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
