package com.noexcs.indolent.agent.mcp

import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.logging.Lumberjack
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

object McpClientManager {

    private val json = Json { ignoreUnknownKeys = true }

    private val connections = ConcurrentHashMap<String, McpConnection>()

    @Volatile
    private var cachedTools: List<AgentTool> = emptyList()

    @Volatile
    private var configHash: Int = 0

    private val rebuildLock = Mutex()

    private val ktorClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(10, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                }
            }
        }
    }

    data class McpConnection(
        val config: McpServerConfig,
        val client: Client,
    )

    suspend fun getTools(settings: SettingsManager): List<AgentTool> {
        val configs = loadConfigs(settings).filter { it.enabled }
        val newHash = configs.hashCode()
        if (newHash == configHash && cachedTools.isNotEmpty()) {
            return cachedTools
        }

        rebuildLock.withLock {
            // Double-check inside the lock
            if (newHash == configHash && cachedTools.isNotEmpty()) {
                return cachedTools
            }

            disconnectAll()
            val allTools = mutableListOf<AgentTool>()
            for (config in configs) {
                try {
                    val client = Client(
                        clientInfo = Implementation(
                            name = "indolent-mcp",
                            version = "1.0.0"
                        )
                    )
                    val transport = StreamableHttpClientTransport(
                        client = ktorClient,
                        url = config.url,
                        reconnectionTime = 30.seconds,
                    )

                    try {
                        client.connect(transport)
                        val listResult = client.listTools(ListToolsRequest())
                        connections[config.id] = McpConnection(config, client)
                        val serverPrefix = config.name.lowercase().replace(Regex("[\\s]+"), "_")
                        for (tool in listResult.tools) {
                            allTools.add(McpToolAdapter(config.id, serverPrefix, tool, client))
                        }
                        Lumberjack.i("McpClientManager",
                            "Connected to '${config.name}' (${config.url}), discovered ${listResult.tools.size} tools")
                    } catch (e: Exception) {
                        try { client.close() } catch (_: Exception) {}
                        throw e
                    }
                } catch (e: Exception) {
                    Lumberjack.e("McpClientManager",
                        "Failed to connect to MCP server '${config.name}' (${config.url})", e)
                }
            }

            cachedTools = allTools
            configHash = newHash
            return allTools
        }
    }

    suspend fun callTool(serverId: String, toolName: String, args: Map<String, Any?>): String {
        val conn = connections[serverId]
            ?: return "Error: MCP server '$serverId' is not connected. Please reconnect."

        return try {
            val result = conn.client.callTool(toolName, args)
            result.content.joinToString("\n") { content ->
                when (content) {
                    is TextContent -> content.text
                    else -> content.toString()
                }
            }.ifEmpty { "(empty response)" }
        } catch (e: Exception) {
            Lumberjack.e("McpClientManager", "Tool '$toolName' on server '$serverId' failed", e)
            "Error calling '$toolName': ${e.message ?: "Unknown error"}"
        }
    }

    suspend fun disconnectAll() {
        connections.values.forEach { conn ->
            try {
                conn.client.close()
            } catch (e: Exception) {
                Lumberjack.w("McpClientManager", "Error disconnecting '${conn.config.name}': ${e.message}")
            }
        }
        connections.clear()
        cachedTools = emptyList()
        configHash = 0
    }

    private fun loadConfigs(settings: SettingsManager): List<McpServerConfig> {
        val raw = settings.mcpServerConfigsJson
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<McpServerConfig>>(raw)
        } catch (e: Exception) {
            Lumberjack.e("McpClientManager", "Failed to parse MCP server configs", e)
            emptyList()
        }
    }
}
