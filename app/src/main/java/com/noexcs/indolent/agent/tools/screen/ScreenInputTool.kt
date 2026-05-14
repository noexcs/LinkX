package com.noexcs.indolent.agent.tools.screen

import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

object ScreenInputTool : AgentTool {

    override val name = "screen_input"
    override val description = """
        Type text into an input field on the current screen via accessibility service.
        Finds an editable text field (EditText) and sets its text content.

        Targeting (optional — by default uses the first editable field):
        - `target_index`: Index of the editable field from screen_read output.
        - `target_text`: Find input field near a label containing this text.
          For example, target_text="Password" finds the input next to a "Password" label.

        Requires accessibility service to be enabled.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "text",
            type = "string",
            description = "Text to type into the input field",
            required = true
        ),
        ToolParameter(
            name = "target_index",
            type = "integer",
            description = "Index of editable field from screen_read output",
            required = false
        ),
        ToolParameter(
            name = "target_text",
            type = "string",
            description = "Find the input field near a label containing this text",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val text = args["text"] as? String
            ?: return "Error: 'text' parameter is required."
        val targetIndex = (args["target_index"] as? Number)?.toInt()
        val targetText = args["target_text"] as? String

        Lumberjack.i("ScreenInputTool", "Input ${text.length} chars targetIndex=$targetIndex targetText=$targetText")

        val service = IndolentAccessibilityService.getInstance()
            ?: return "Error: Accessibility service is not running. Enable it in Settings → Accessibility → Indolent."

        return try {
            service.inputText(text, targetText, targetIndex)
        } catch (e: Exception) {
            Lumberjack.e("ScreenInputTool", "Input failed", e)
            "Error: ${e.message}"
        }
    }
}
