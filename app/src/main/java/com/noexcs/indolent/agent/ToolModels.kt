package com.noexcs.indolent.agent

import com.noexcs.indolent.agent.tools.ToolParameter
import kotlinx.serialization.Serializable

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolFunction
)

@Serializable
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
