package com.noexcs.indolent.agent.tools.termux

import com.noexcs.indolent.agent.termux.TermuxExecutor
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter

class TermuxExecuteCommandTool(private val executor: TermuxExecutor) : AgentTool {
    override val name = "execute_command"
    override val description = """
        Execute a shell command in Termux.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "command",
            type = "string",
            description = "The shell command to execute"
        ),
        ToolParameter(
            name = "workdir",
            type = "string",
            description = "Working directory for the command (default: /data/data/com.termux/files/home)",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val command = args["command"] as? String ?: ""
        val workdir = args["workdir"] as? String ?: "/data/data/com.termux/files/home"

        val result = executor.execute(command, workdir)
        return buildString {
            if (result.stdout.isNotEmpty()) appendLine(result.stdout)
            if (result.stderr.isNotEmpty()) appendLine("STDERR: ${result.stderr}")
            appendLine("Exit code: ${result.exitCode}")
        }.trim()
    }
}
