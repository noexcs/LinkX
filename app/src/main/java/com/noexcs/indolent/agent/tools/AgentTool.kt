package com.noexcs.indolent.agent.tools

/**
 * Base interface for all tools that can be used by the AI agent.
 */
interface AgentTool {
    /** The name of the tool (used for identification). */
    val name: String

    /** Description of what the tool does. */
    val description: String

    /** Declared parameters the LLM should supply. */
    val parameters: List<ToolParameter>
        get() = emptyList()

    /** Execute the tool with the given arguments. */
    suspend fun execute(args: Map<String, Any?>): String
}

/**
 * Describes a single parameter of a tool for LLM tool-calling.
 */
data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val defaultValue: Any? = null
)
