package com.noexcs.indolent.agent.tools.common

import com.noexcs.indolent.agent.Agent
import com.noexcs.indolent.agent.LLMMessage
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.agent.tools.common.AgentClipboardStore

class SubagentTool : AgentTool {
    override val name = "agent"
    override val description = """
        Launch a new agent to handle complex, multi-step tasks autonomously.
        The subagent has access to the same tools and can reason through problems independently.
        Use this for tasks that require multiple steps, independent research, or parallel work.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "description",
            type = "string",
            description = "A description of the task"
        ),
        ToolParameter(
            name = "prompt",
            type = "string",
            description = "The task for the subagent to perform. Be specific about what you need."
        ),
    )

    private var baseUrl: String = ""
    private var apiKey: String = ""
    private var model: String = ""
    private var tools: List<AgentTool> = emptyList()
    private var defaultMaxIterations: Int = 500
    private var thinkingEnabled: Boolean = true
    private var reasoningEffort: String = "high"
    var clipboardStore: AgentClipboardStore? = null

    fun init(
        baseUrl: String,
        apiKey: String,
        model: String,
        tools: List<AgentTool> = emptyList(),
        thinkingEnabled: Boolean = true,
        reasoningEffort: String = "high",
    ) {
        this.baseUrl = baseUrl
        this.apiKey = apiKey
        this.model = model
        this.tools = tools
        this.thinkingEnabled = thinkingEnabled
        this.reasoningEffort = reasoningEffort
    }

    override suspend fun execute(args: Map<String, Any?>): String {
        val prompt = args["prompt"] as? String
            ?: return "Error: 'prompt' is required"

        val maxIterations = defaultMaxIterations

        val subagent = Agent(baseUrl, apiKey, model, thinkingEnabled, reasoningEffort, clipboardStore = clipboardStore, maxIterations = maxIterations)
        val history = mutableListOf<LLMMessage>()
        val systemPrompt = buildString {
            append("You are a subagent. Complete the assigned task autonomously and return a concise result. Use tools as needed. Do not ask follow-up questions — just do the work and report back.")
            if (clipboardStore != null) {
                append("\n\n# Agent Clipboard\nYou share an agent clipboard with the parent agent. Use the agent_clipboard tool or {{agent_clipboard}} syntax.")
            }
        }
        val result = subagent.execute(
            history = history,
            message = prompt,
            systemPrompt = systemPrompt,
            tools = tools
        )
        return result.lastOrNull { it.role == "assistant" }?.content
            ?: result.firstOrNull()?.content
            ?: ""
    }
}