package com.noexcs.indolent.agent.mcp

import kotlinx.serialization.Serializable

@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
)
