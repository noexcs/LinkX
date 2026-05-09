package com.noexcs.indolent.agent.tools.python

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.agent.tools.finance.PythonInit
import com.noexcs.indolent.logging.Lumberjack

class PythonExecuteTool : AgentTool {
    override val name = "execute_python"
    override val description = """
        Execute arbitrary Python code. A full Python 3.12 runtime with many scientific and data-processing packages pre-installed.

        Available packages: pandas, numpy, requests, beautifulsoup4 (bs4), openpyxl, xlrd, tabulate, python-dateutil, pytz, tqdm, and more.

        Characteristics:
        - The Python interpreter persists across calls — variables, imports, and function definitions remain available in subsequent executions (like a REPL).
        - Output from print() is captured and returned. stderr is also captured.
        - Use for data processing, math, string manipulation, web scraping, file parsing, or any computation where Python is more expressive than shell commands.
        - If you need to read/write files, use the filesystem tools and pass paths to Python.

        Constraints:
        - No GUI or interactive input — code must run to completion.
        - Large computations may take time; keep within reasonable limits.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "code",
            type = "string",
            description = "Python code to execute"
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val code = args["code"] as? String ?: return "Error: no code provided"

        Lumberjack.i(TAG, "Executing Python:\n$code")

        return try {
            PythonInit.ensureStarted()
            val py = Python.getInstance()
            val result = py.getModule("_capture").callAttr("run", code)
            val list = result.asList()
            val stdout = list[0].toString()
            val stderr = list[1].toString()

            if (stderr.isNotEmpty()) {
                Lumberjack.w(TAG, "Python stderr:\n${stderr.trimEnd()}")
            }

            buildString {
                if (stdout.isNotEmpty()) append(stdout.trimEnd())
                if (stderr.isNotEmpty()) {
                    if (stdout.isNotEmpty()) appendLine()
                    append("STDERR: ").append(stderr.trimEnd())
                }
                if (stdout.isEmpty() && stderr.isEmpty()) append("(no output)")
            }.trim()
        } catch (e: Exception) {
            Lumberjack.e(TAG, "Python execution error", e)
            val msg = e.message ?: e.toString()
            if (msg.contains("RuntimeError")) {
                msg.substringAfter("RuntimeError: ").substringBefore("\n\tat <python>")
                    .ifEmpty { msg }
            } else {
                msg
            }
        }
    }

    companion object {
        private const val TAG = "PythonExecuteTool"
    }
}
