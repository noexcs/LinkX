package com.noexcs.indolent.agent

import kotlinx.serialization.Serializable

@Serializable
enum class MessageRole { User, Assistant, System, Thinking, ToolInfo }

@Serializable
enum class SessionType { CONVERSATION, SCHEDULED_TASK, HEARTBEAT, CONDITIONAL_TRIGGER }

// ── LLM wire models ──

@Serializable
data class LLMMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val reasoningContent: String? = null,
    val displayContentJson: String? = null
)

data class LLMRequest(
    val model: String,
    val messages: List<LLMMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val stream: Boolean = false,
    val toolDefinitions: List<ToolDefinition>? = null,
    val toolChoice: ToolChoice? = null,
    val thinkingEnabled: Boolean? = null,
    val reasoningEffort: String? = null,
    val responseFormat: String? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null
)

data class LLMResponse(
    val content: String,
    val model: String = "",
    val usage: TokenUsage? = null,
    val toolCalls: List<ToolCall>? = null,
    val reasoningContent: String? = null
)

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

// ── Provider model (extensible) ──

sealed class LLMProvider(val id: String, val displayName: String) {
    data object DeepSeek : LLMProvider("deepseek", "DeepSeek")

    companion object {
        fun all(): List<LLMProvider> = listOf(DeepSeek)
    }
}
