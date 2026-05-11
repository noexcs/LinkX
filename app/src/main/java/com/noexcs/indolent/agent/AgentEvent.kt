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
    data class Truncated(val reason: String) : AgentEvent()
    data class PasteContent(val content: String) : AgentEvent()
}
