package com.noexcs.indolent.agent.tools.common

import com.noexcs.indolent.agent.MemoryProvider
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter

class UpdateMemoryTool(private val memoryProvider: MemoryProvider) : AgentTool {
    override val name = "update_memory"
    override val description = "Store information in persistent memory"

    override val parameters = listOf(
        ToolParameter(
            name = "key",
            type = "string",
            description = "Memory key / section name"
        ),
        ToolParameter(
            name = "value",
            type = "string",
            description = "Memory content to store"
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val key = args["key"] as? String ?: ""
        val value = args["value"] as? String ?: ""
        memoryProvider.save(key, value)
        return "Memory updated successfully"
    }
}