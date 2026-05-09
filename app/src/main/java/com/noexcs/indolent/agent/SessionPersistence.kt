package com.noexcs.indolent.agent

interface SessionPersistence {
    suspend fun save(sessionId: String, messages: List<LLMMessage>, title: String, type: SessionType)
    suspend fun load(sessionId: String): List<LLMMessage>?
    fun listSessions(): List<SessionMetadata>
    fun delete(sessionId: String)
    fun rename(sessionId: String, newTitle: String)
}

data class SessionMetadata(
    val sessionId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val type: SessionType
)
