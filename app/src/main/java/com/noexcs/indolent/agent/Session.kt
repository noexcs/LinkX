package com.noexcs.indolent.agent

import com.noexcs.indolent.agent.tools.AgentTool
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Configuration for the context that is injected into every LLM call
 * for this session. The session builds its system prompt from this config.
 */
data class ContextConfig(
    val baseInstruction: String = "You are a helpful Android assistant.",
    val userSystemPrompt: String = "",
    val memory: String = "",
    val retrievedMemory: String = "",
    val activeSkillContent: String = "",
    val clipboardInstruction: String = "",
    val screenInstruction: String = "",
    /** Model's maximum context window size in tokens. */
    val maxContextTokens: Int = 128_000
)

class Session(
    val sessionId: String = UUID.randomUUID().toString(),
    private val agent: Agent,
    private val persistence: SessionPersistence? = null,
    val type: SessionType = SessionType.CONVERSATION
) {
    val history: MutableList<LLMMessage> = mutableListOf()
    var title: String = ""

    /**
     * The context configuration for this session.
     * Update this before calling [run] or [execute] when external context
     * (memory, skills, instructions) changes.
     */
    var context: ContextConfig = ContextConfig()

    /**
     * Builds the full system prompt from the current [context].
     */
    fun buildSystemPrompt(): String {
        return SystemPromptBuilder.build(context)
    }

    /**
     * Streaming conversation run. Uses [context] to build the system prompt.
     */
    fun run(
        message: String,
        tools: List<AgentTool> = emptyList()
    ): Flow<AgentEvent> {
        return agent.run(history, message, buildSystemPrompt(), tools)
    }

    /**
     * Non-streaming execution. Uses [context] to build the system prompt.
     * Used for background tasks (scheduled execution, heartbeats, etc.).
     */
    suspend fun execute(
        message: String,
        tools: List<AgentTool> = emptyList()
    ): List<LLMMessage> {
        return agent.execute(history, message, buildSystemPrompt(), tools)
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
}
