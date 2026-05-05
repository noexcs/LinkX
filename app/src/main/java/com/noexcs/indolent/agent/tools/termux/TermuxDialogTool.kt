package com.noexcs.indolent.agent.tools.termux

import com.noexcs.indolent.agent.termux.TermuxExecutor
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

class TermuxDialogTool(private val executor: TermuxExecutor) : AgentTool {
    override val name = "show_dialog"
    override val description = """
        Display interactive dialog widgets to get user input.

        Supported widget types:
        - text: Input text (single or multiple lines, password, numbers)
        - confirm: Yes/No confirmation dialog
        - checkbox: Select multiple values using checkboxes
        - radio: Pick a single value from radio buttons
        - spinner: Pick a single value from dropdown
        - sheet: Pick a value from sliding bottom sheet
        - counter: Pick a number in specified range
        - date: Pick a date
        - time: Pick a time
        - speech: Obtain speech using microphone

        Returns JSON with user's selection in 'text' field.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "widget",
            type = "string",
            description = "Dialog widget type: text, confirm, checkbox, radio, spinner, sheet, counter, date, time, speech"
        ),
        ToolParameter(
            name = "title",
            type = "string",
            description = "Dialog title",
            required = false
        ),
        ToolParameter(
            name = "hint",
            type = "string",
            description = "Hint text or confirmation message",
            required = false
        ),
        ToolParameter(
            name = "values",
            type = "string",
            description = "Comma-separated values for checkbox/radio/spinner/sheet widgets",
            required = false
        ),
        ToolParameter(
            name = "range",
            type = "string",
            description = "Range for counter widget, e.g. '1,100'",
            required = false
        ),
        ToolParameter(
            name = "dateFormat",
            type = "string",
            description = "Date format for date widget",
            required = false
        ),
        ToolParameter(
            name = "multiLine",
            type = "boolean",
            description = "Enable multi-line input for text widget",
            required = false
        ),
        ToolParameter(
            name = "numeric",
            type = "boolean",
            description = "Enable numeric input for text widget",
            required = false
        ),
        ToolParameter(
            name = "password",
            type = "boolean",
            description = "Enable password input for text widget",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val widget = args["widget"] as? String ?: "text"
            val title = args["title"] as? String ?: ""
            val hint = args["hint"] as? String ?: ""
            val values = args["values"] as? String ?: ""
            val range = args["range"] as? String ?: ""
            val dateFormat = args["dateFormat"] as? String ?: ""
            val multiLine = args["multiLine"] as? Boolean ?: false
            val numeric = args["numeric"] as? Boolean ?: false
            val password = args["password"] as? Boolean ?: false

            val command = buildCommand(widget, title, hint, values, range, dateFormat, multiLine, numeric, password)
            val result = executor.execute(command)

            if (result.exitCode != 0) {
                return "Error showing dialog: ${result.stderr.ifEmpty { "Unknown error" }}"
            }

            val output = result.stdout.trim()
            if (output.isEmpty()) {
                return "Dialog was cancelled or no input provided."
            }

            parseDialogResult(output)
        } catch (e: Exception) {
            Lumberjack.e("TermuxDialogTool", "Error executing dialog", e)
            "Error: ${e.message}"
        }
    }

    private fun buildCommand(
        widget: String,
        title: String,
        hint: String,
        values: String,
        range: String,
        dateFormat: String,
        multiLine: Boolean,
        numeric: Boolean,
        password: Boolean
    ): String {
        val cmd = StringBuilder("termux-dialog $widget")

        if (title.isNotBlank()) {
            cmd.append(" -t \"$title\"")
        }

        when (widget.lowercase()) {
            "text" -> {
                if (hint.isNotBlank()) cmd.append(" -i \"$hint\"")
                if (multiLine) cmd.append(" -m")
                if (numeric) cmd.append(" -n")
                if (password) cmd.append(" -p")
            }
            "speech" -> {
                if (hint.isNotBlank()) cmd.append(" -i \"$hint\"")
            }
            "checkbox", "radio", "spinner", "sheet" -> {
                if (values.isBlank()) throw IllegalArgumentException("Values are required for $widget widget")
                cmd.append(" -v \"$values\"")
            }
            "counter" -> {
                if (range.isNotBlank()) cmd.append(" -r \"$range\"")
            }
            "date" -> {
                if (dateFormat.isNotBlank()) cmd.append(" -d \"$dateFormat\"")
            }
            "confirm" -> {
                if (hint.isNotBlank()) cmd.append(" -i \"$hint\"")
            }
            "time" -> { }
            else -> throw IllegalArgumentException("Unsupported widget type: $widget")
        }

        return cmd.toString()
    }

    private fun parseDialogResult(jsonOutput: String): String {
        return try {
            val textMatch = Regex("\"text\"\\s*:\\s*\"([^\"]*)\"").find(jsonOutput)
            val codeMatch = Regex("\"code\"\\s*:\\s*(\\d+)").find(jsonOutput)

            val code = codeMatch?.groupValues?.get(1)?.toIntOrNull()

            if (code == 1) return "Dialog was cancelled by user."

            val text = textMatch?.groupValues?.get(1) ?: ""
            if (text.isEmpty()) return "No input provided."

            "User input: $text"
        } catch (e: Exception) {
            Lumberjack.e("TermuxDialogTool", "Error parsing dialog result", e)
            "Dialog result: $jsonOutput"
        }
    }
}
