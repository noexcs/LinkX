package com.noexcs.indolent.agent

import com.noexcs.indolent.agent.tools.AgentTool
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class Session(
    val sessionId: String = UUID.randomUUID().toString(),
    private val agent: Agent,
    private val persistence: SessionPersistence? = null,
    val type: SessionType = SessionType.CONVERSATION
) {
    val history: MutableList<LLMMessage> = mutableListOf()
    var title: String = ""

    fun run(
        message: String,
        systemPrompt: String,
        tools: List<AgentTool> = emptyList(),
        maxIterations: Int = 1000
    ): Flow<AgentEvent> {
        return agent.run(history, message, systemPrompt, tools, maxIterations)
    }

    suspend fun execute(
        message: String,
        systemPrompt: String,
        tools: List<AgentTool> = emptyList(),
        maxIterations: Int = 100,
        completeProcess: Boolean = false
    ): String {
        return agent.execute(history, message, systemPrompt, tools, maxIterations, completeProcess)
    }

    fun setHistory(messages: List<LLMMessage>) {
        history.clear()
        history.addAll(messages)
    }

    fun clear() {
        history.clear()
        title = ""
    }

    suspend fun save() {
        if (title.isBlank() && history.isNotEmpty()) {
            title = history.firstOrNull { it.role == "user" }?.content?.take(50) ?: sessionId
        }
        persistence?.save(sessionId, history.toList(), title, type)
    }

    suspend fun load(id: String): Boolean {
        val messages = persistence?.load(id) ?: return false
        history.clear()
        history.addAll(messages)
        return true
    }

    suspend fun delete() {
        persistence?.delete(sessionId)
    }

    fun toChatMessages(): List<ChatMessage> {
        return history.map { msg ->
            ChatMessage(
                role = MessageRoleMapper.toMessageRole(msg.role),
                content = msg.content,
                displayContentJson = msg.displayContentJson
            )
        }
    }
}
