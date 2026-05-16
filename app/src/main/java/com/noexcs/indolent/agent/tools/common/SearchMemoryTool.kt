package com.noexcs.indolent.agent.tools.common

import com.noexcs.indolent.agent.MemoryProvider
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter

class SearchMemoryTool(private val memoryProvider: MemoryProvider) : AgentTool {
    override val name = "search_memory"
    override val description = "Search persistent memory for semantically relevant information. " +
            "Use this when you need to find specific information that may not have been automatically injected into the current context, " +
            "such as details from past conversations, stored preferences, or project notes. " +
            "Try different query phrasings if the first search doesn't find what you need."

    override val parameters = listOf(
        ToolParameter(
            name = "query",
            type = "string",
            description = "Search query describing what information you're looking for"
        ),
        ToolParameter(
            name = "top_k",
            type = "int",
            description = "Number of results to return (default 5, max 20)"
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val query = args["query"] as? String ?: ""
        if (query.isBlank()) return "Error: query is required"

        val k = ((args["top_k"] as? Number)?.toInt() ?: 5).coerceIn(1, 20)
        val results = memoryProvider.search(query, k)

        if (results.isEmpty()) {
            return "No matching memories found for query: \"$query\""
        }
        if (results.size == 1 && results.first() == memoryProvider.read()) {
            // Fallback: full dump was returned (no semantic index available)
            return "Memory search is not available (semantic index not loaded). " +
                    "Use update_memory to see what's stored."
        }

        return buildString {
            appendLine("Found ${results.size} result(s) for \"$query\":")
            appendLine()
            results.forEachIndexed { i, chunk ->
                appendLine("--- Result ${i + 1} ---")
                appendLine(chunk)
                appendLine()
            }
        }
    }
}
