package com.noexcs.indolent.agent.mcp

import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpToolAdapter(
    private val serverId: String,
    private val serverPrefix: String,
    private val mcpTool: Tool,
    private val client: Client,
) : AgentTool {

    override val name: String = "${serverPrefix}_${mcpTool.name}"

    override val description: String = mcpTool.description ?: "MCP tool: ${mcpTool.name}"

    override val parameters: List<ToolParameter> = convertParameters(mcpTool.inputSchema)

    override suspend fun execute(args: Map<String, Any?>): String {
        return McpClientManager.callTool(serverId, mcpTool.name, args)
    }

    private fun convertParameters(schema: ToolSchema): List<ToolParameter> {
        val properties: JsonObject = schema.properties ?: return emptyList()
        val requiredSet: Set<String> = schema.required?.toSet() ?: emptySet()

        return properties.map { (propName, propElement) ->
            val obj = propElement.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: "string"
            val description = obj["description"]?.jsonPrimitive?.content ?: ""
            ToolParameter(
                name = propName,
                type = type,
                description = description,
                required = propName in requiredSet
            )
        }
    }
}
