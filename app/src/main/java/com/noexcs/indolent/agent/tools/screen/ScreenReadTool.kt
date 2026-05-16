package com.noexcs.indolent.agent.tools.screen

import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

object ScreenReadTool : AgentTool {

    override val name = "screen_read"
    override val description = """
        Read the current screen content via accessibility service.
        Returns a numbered list of UI elements with their class, text, content description,
        resource ID, bounds, and interactive flags (clickable, scrollable, editable).

        Use the index number from the output to target elements with screen_click, screen_scroll,
        or screen_input tools.

        Modes:
        - "summary" (default): Shows high-level interactive elements and container views. Good for navigation.
        - "interactive": Only clickable, scrollable, and editable elements with text.
        - "full": Complete UI tree with all visible elements. More detail but larger output.

        Use filter_text to show only elements whose text, content description, or resource ID
        contains the given string.

        Requires accessibility service to be enabled in system settings.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "mode",
            type = "string",
            description = "Display mode: 'summary' (default), 'interactive', or 'full'",
            required = false
        ),
        ToolParameter(
            name = "filter_text",
            type = "string",
            description = "Show only elements containing this text in their label, description, or ID",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val mode = (args["mode"] as? String)?.ifBlank { null } ?: "summary"
        val filterText = args["filter_text"] as? String
        Lumberjack.i("ScreenReadTool", "Reading screen mode=$mode filterText=${filterText ?: "none"}")

        val service = LinkXAccessibilityService.getInstance()
            ?: return "Error: Accessibility service is not running. Enable it in Settings → Accessibility → Indolent."

        return try {
            service.getScreenDescription(mode, filterText)
        } catch (e: Exception) {
            Lumberjack.e("ScreenReadTool", "Failed to read screen", e)
            "Error reading screen: ${e.message}"
        }
    }
}
