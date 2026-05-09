package com.noexcs.indolent.agent

import kotlinx.serialization.Serializable

@Serializable
data class PersistedSession(
    val sessionId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<LLMMessage>,
    val type: SessionType = SessionType.CONVERSATION
)
