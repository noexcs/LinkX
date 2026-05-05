package com.noexcs.indolent.agent.tools.self

import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Level
import com.noexcs.indolent.logging.LogFilter
import com.noexcs.indolent.logging.Lumberjack

class LogQueryTool : AgentTool {

    override val name = "query_logs"
    override val description = """
        Query the application's internal log buffer to inspect recent runtime events.

        This gives access to ALL app-internal logs: agent decisions, tool calls, API
        requests, task scheduling, crash traces, and every Lumberjack call site across
        the codebase. Use it to:

        - Diagnose why a previous tool call failed
        - Inspect recent execution history and error traces
        - Verify that background tasks ran as expected
        - Search for specific events by tag or keyword
        - Check crash history (level=F)

        Parameters (all optional — defaults to latest 50 entries of any level):
        - count:   Max entries to return (default 50, max 500)
        - level:   Min level: V, D, I, W, E, F (e.g. "W" returns W+E+F)
        - tag:     Substring match on tag field (e.g. "Agent", "TaskScheduler")
        - query:   Free-text search in log message body
        - since:   Start of time window: relative ("5m", "1h", "30s") or epoch ms
        - before:  End of time window (same format as since)
        - offset:  Pagination offset (default 0)
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "count",
            type = "integer",
            description = "Max entries to return (default 50, max 500)",
            required = false,
            defaultValue = 50
        ),
        ToolParameter(
            name = "level",
            type = "string",
            description = "Min log level to return: V(erbose), D(ebug), I(nfo), W(arn), E(rror), F(atal). E.g. 'W' returns W+E+F.",
            required = false
        ),
        ToolParameter(
            name = "tag",
            type = "string",
            description = "Filter by tag substring (case-insensitive). E.g. 'Agent', 'TaskScheduler', 'Termux'",
            required = false
        ),
        ToolParameter(
            name = "query",
            type = "string",
            description = "Free-text search in the log message body (case-insensitive)",
            required = false
        ),
        ToolParameter(
            name = "since",
            type = "string",
            description = "Start of time window. Relative: '5m', '1h', '30s', '2d'. Or epoch millis.",
            required = false
        ),
        ToolParameter(
            name = "before",
            type = "string",
            description = "End of time window. Same format as since.",
            required = false
        ),
        ToolParameter(
            name = "offset",
            type = "integer",
            description = "Pagination offset (default 0). Use with count to page through results.",
            required = false,
            defaultValue = 0
        ),
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val count = ((args["count"] as? Number)?.toInt() ?: 50).coerceIn(1, LogFilter.Companion.MAX_COUNT)
            val level = (args["level"] as? String)?.let { Level.Companion.fromLabel(it) }
            val tag = (args["tag"] as? String)?.takeIf { it.isNotBlank() }
            val query = (args["query"] as? String)?.takeIf { it.isNotBlank() }
            val since = (args["since"] as? String)?.let { LogFilter.Companion.parseSince(it) }
            val before = (args["before"] as? String)?.let { LogFilter.Companion.parseSince(it) }
            val offset = ((args["offset"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)

            val filter = LogFilter(
                count = count,
                level = level,
                tag = tag,
                query = query,
                since = since,
                before = before,
                offset = offset
            )

            val result = Lumberjack.query(filter)

            buildString {
                if (result.entries.isEmpty()) {
                    appendLine("No log entries match the filter.")
                    appendLine("filter: count=$count, level=${level?.label ?: "any"}, tag=${tag ?: "-"}, query=${query ?: "-"}")
                    appendLine("buffer has ${result.bufferSize} entries total (${result.totalWritten} written since startup)")
                    return@buildString
                }

                appendLine("Log entries (${result.entries.size} of ${result.totalMatched} matching):")
                result.entries.forEach { entry ->
                    appendLine(entry.toShortString())
                }

                if (result.hasMore) {
                    val nextOffset = offset + result.entries.size
                    appendLine()
                    appendLine("--- more results available (offset=$offset, returned=${result.entries.size}, total=${result.totalMatched}) ---")
                    appendLine("Use offset=$nextOffset for next page.")
                }
            }
        } catch (e: Exception) {
            "Error querying logs: ${e.message}"
        }
    }
}