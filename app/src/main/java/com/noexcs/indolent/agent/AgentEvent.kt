package com.noexcs.indolent.agent

sealed class AgentEvent {
    data class Text(val content: String) : AgentEvent()
    data class Reasoning(val content: String) : AgentEvent()
    data class ToolCallStart(val callId: String, val name: String) : AgentEvent()
    data class ToolCallDelta(val callId: String, val name: String, val argumentsDelta: String) : AgentEvent()
    data class ToolCallBegin(val callId: String, val name: String, val arguments: Map<String, Any?>) : AgentEvent()
    data class ToolResult(val callId: String, val name: String, val args: Map<String, Any?>, val result: String) : AgentEvent()
    data class Usage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
    data class StreamRetry(val attempt: Int, val reason: String) : AgentEvent()
    data class Truncated(val reason: String) : AgentEvent()
    data class PasteContent(val content: String) : AgentEvent()

    /**
     * Emitted when the context window is approaching its budget limit.
     * The caller may choose to summarize or truncate history.
     */
    data class ContextWarning(
        val estimatedTokens: Long,
        val budgetTokens: Int,
        val message: String
    ) : AgentEvent()

    /**
     * Emitted after history has been summarized to fit the context budget.
     */
    data class ContextSummarized(
        val messagesBefore: Int,
        val messagesAfter: Int,
        val summaryLength: Int
    ) : AgentEvent()
}
