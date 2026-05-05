package com.noexcs.indolent.agent.tools.termux

import com.noexcs.indolent.agent.termux.TermuxExecutor
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter

class TermuxReadFileTool(private val executor: TermuxExecutor) : AgentTool {
    override val name = "read_file"
    override val description = "Read the contents of a file"

    override val parameters = listOf(
        ToolParameter(
            name = "path",
            type = "string",
            description = "Absolute path to the file to read"
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val path = args["path"] as? String ?: ""
        val result = executor.execute("cat '${path.replace("'", "'\\''")}'")
        return if (result.exitCode == 0) result.stdout else "Error: ${result.stderr}"
    }
}
