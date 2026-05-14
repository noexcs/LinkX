package com.noexcs.indolent.agent.tools.screen

import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

object ScreenScrollTool : AgentTool {

    override val name = "screen_scroll"
    override val description = """
        Scroll on the current screen via accessibility service.
        Finds a scrollable container and performs a scroll action.

        Targeting (optional — by default scrolls the first scrollable container):
        - `container_index`: Index of the scrollable container from screen_read output.
        - `container_text`: Scroll the container whose text or content description contains this.

        Directions: "up" (scroll backward / content moves down), "down" (scroll forward / content moves up),
        "left", "right".

        Requires accessibility service to be enabled.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "direction",
            type = "string",
            description = "Scroll direction: 'up', 'down', 'left', or 'right'",
            required = true
        ),
        ToolParameter(
            name = "container_index",
            type = "integer",
            description = "Index of scrollable container from screen_read output",
            required = false
        ),
        ToolParameter(
            name = "container_text",
            type = "string",
            description = "Scroll the container whose text or description contains this",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val direction = (args["direction"] as? String)?.lowercase() ?: "down"
        val containerIndex = (args["container_index"] as? Number)?.toInt()
        val containerText = args["container_text"] as? String

        Lumberjack.i("ScreenScrollTool", "Scroll $direction index=$containerIndex text=$containerText")

        val service = IndolentAccessibilityService.getInstance()
            ?: return "Error: Accessibility service is not running. Enable it in Settings → Accessibility → Indolent."

        return try {
            service.scroll(direction, containerText, containerIndex)
        } catch (e: Exception) {
            Lumberjack.e("ScreenScrollTool", "Scroll failed", e)
            "Error: ${e.message}"
        }
    }
}
