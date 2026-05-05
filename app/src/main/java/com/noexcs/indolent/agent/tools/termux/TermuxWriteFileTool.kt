package com.noexcs.indolent.agent.tools.termux

import com.noexcs.indolent.agent.termux.TermuxExecutor
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter

class TermuxWriteFileTool(private val executor: TermuxExecutor) : AgentTool {
    override val name = "write_file"
    override val description = "Write content to a file"

    override val parameters = listOf(
        ToolParameter(
            name = "path",
            type = "string",
            description = "Absolute path to the file to write"
        ),
        ToolParameter(
            name = "content",
            type = "string",
            description = "Content to write to the file"
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val path = args["path"] as? String ?: ""
        val content = args["content"] as? String ?: ""
        val escapedPath = path.replace("'", "'\\''")
        val escapedContent = content.replace("'", "'\\''")
        val result = executor.execute("echo '$escapedContent' > '$escapedPath'")
        return if (result.exitCode == 0) "File written successfully" else "Error: ${result.stderr}"
    }
}
