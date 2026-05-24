package com.noexcs.indolent.prompt

import kotlinx.serialization.Serializable

@Serializable
data class SystemPromptItem(
    val id: String,
    val name: String = "",
    val content: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
