package com.noexcs.indolent.agent

import com.noexcs.indolent.agent.tools.ToolParameter
import kotlinx.serialization.Serializable

// ── Conversation / persistence models ──

@Serializable
enum class MessageRole { User, Assistant, System, Thinking, ToolInfo }

@Serializable
data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val displayContentJson: String? = null
)

@Serializable
data class ConversationSession(
    val sessionId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage>
)

// ── LLM wire models ──

data class LLMMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val reasoningContent: String? = null
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
}

data class AIModel(
    val id: String,
    val provider: LLMProvider,
    val name: String = id,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7
)

// ── Tool-calling types ──

data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolFunction
)

data class ToolFunction(
    val name: String,
    val arguments: String
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>
)

sealed class ToolChoice {
    data object Auto : ToolChoice()
    data object None : ToolChoice()
    data object Required : ToolChoice()
    data class Named(val name: String) : ToolChoice()
}

// ── Agent streaming events ──

sealed class AgentEvent {
    data class Text(val content: String) : AgentEvent()
    data class Reasoning(val content: String) : AgentEvent()
    data class ToolCallStart(val callId: String, val name: String) : AgentEvent()
    data class ToolCallDelta(val callId: String, val name: String, val argumentsDelta: String) : AgentEvent()
    data class ToolCallBegin(val callId: String, val name: String, val arguments: Map<String, Any?>) : AgentEvent()
    data class ToolResult(val callId: String, val name: String, val args: Map<String, Any?>, val result: String) : AgentEvent()
    data class Usage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
    data class Truncated(val reason: String) : AgentEvent()
}
