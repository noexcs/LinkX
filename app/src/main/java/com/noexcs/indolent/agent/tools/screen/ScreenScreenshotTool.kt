package com.noexcs.indolent.agent.tools.screen

import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import java.io.File

object ScreenScreenshotTool : AgentTool {

    override val name = "screen_screenshot"
    override val description = """
        Capture a screenshot of the current screen and save it as a PNG file.
        Returns the file path which can be used with `display_content` to show the screenshot,
        or with `fs_read` / `agent_clipboard(source=...)` for further processing.

        Requires accessibility service to be enabled.

        On Android 14+ uses the system screenshot API. On older versions falls back to the
        screencap shell command.
    """.trimIndent()

    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(args: Map<String, Any?>): String {
        Lumberjack.i("ScreenScreenshotTool", "Capturing screenshot")

        val service = IndolentAccessibilityService.getInstance()
            ?: return "Error: Accessibility service is not running. Enable it in Settings → Accessibility → Indolent."

        return try {
            val outputDir = File(service.filesDir, "screenshots")
            service.captureScreenshot(outputDir)
        } catch (e: Exception) {
            Lumberjack.e("ScreenScreenshotTool", "Screenshot failed", e)
            "Error capturing screenshot: ${e.message}"
        }
    }
}
