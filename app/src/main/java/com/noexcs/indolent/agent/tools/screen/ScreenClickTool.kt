package com.noexcs.indolent.agent.tools.screen

import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

object ScreenClickTool : AgentTool {

    override val name = "screen_click"
    override val description = """
        Click on a UI element on the current screen via accessibility service.
        Provide ONE targeting method — the first provided in this priority will be used:
        index > text/content_desc/resource_id > x+y coordinates.

        Targeting methods:
        - `index`: The number from screen_read output (most reliable).
        - `text`: Match element by visible text (substring match).
        - `content_desc`: Match element by content description / accessibility label.
        - `resource_id`: Match element by resource ID.
        - `x` + `y`: Click at specific screen coordinates (fallback for elements not in the accessibility tree).

        Tip: Use screen_read first to see available elements, then click by index.

        Requires accessibility service to be enabled.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "index",
            type = "integer",
            description = "Element index from screen_read output",
            required = false
        ),
        ToolParameter(
            name = "text",
            type = "string",
            description = "Click element whose visible text contains this string",
            required = false
        ),
        ToolParameter(
            name = "content_desc",
            type = "string",
            description = "Click element whose content description contains this string",
            required = false
        ),
        ToolParameter(
            name = "resource_id",
            type = "string",
            description = "Click element with this resource ID",
            required = false
        ),
        ToolParameter(
            name = "x",
            type = "integer",
            description = "X coordinate on screen (use with y)",
            required = false
        ),
        ToolParameter(
            name = "y",
            type = "integer",
            description = "Y coordinate on screen (use with x)",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val index = (args["index"] as? Number)?.toInt()
        val text = args["text"] as? String
        val contentDesc = args["content_desc"] as? String
        val resourceId = args["resource_id"] as? String
        val x = (args["x"] as? Number)?.toInt()
        val y = (args["y"] as? Number)?.toInt()

        Lumberjack.i("ScreenClickTool", "Click index=$index text=$text desc=$contentDesc id=$resourceId xy=($x,$y)")

        val service = LinkXAccessibilityService.getInstance()
            ?: return "Error: Accessibility service is not running. Enable it in Settings → Accessibility → Indolent."

        return try {
            val findResult = service.findNode(text, contentDesc, resourceId, index, x, y)

            if (findResult.error != null) {
                return findResult.error
            }

            val node = findResult.nodeInfo
                ?: return "Error: No element found to click."

            service.clickNode(node)
        } catch (e: Exception) {
            Lumberjack.e("ScreenClickTool", "Click failed", e)
            "Error: ${e.message}"
        }
    }
}
