package com.noexcs.indolent.agent.tools.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

class ClipboardTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override val name = "clipboard"
    override val description = """
        Read from and write to the system clipboard.

        Operations:
        - "read": Get the current clipboard text content, or report if clipboard is empty / non-text
        - "write": Set clipboard text content
        - "clear": Clear the clipboard

        IMPORTANT:
        - Reading clipboard on Android 12+ shows a system toast confirming the read. Use sparingly.
        - Writing clipboard is silent; the user can paste the content anywhere.
        - Only plain text is supported (no HTML, images, or files).
        - Use write for sharing results: after computation, put the answer directly in clipboard.

        Common patterns:
        - AI computes a result → write to clipboard → user pastes where needed
        - Read clipboard to understand what the user copied from another app
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "action",
            type = "string",
            description = "Operation: 'read', 'write', or 'clear'",
            required = true
        ),
        ToolParameter(
            name = "text",
            type = "string",
            description = "Text to write to clipboard (required for 'write' action)",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val action = (args["action"] as? String)?.lowercase() ?: "read"

            when (action) {
                "read" -> executeRead()
                "write" -> executeWrite(args["text"] as? String)
                "clear" -> executeClear()
                else -> "Error: Unknown action '$action'. Use 'read', 'write', or 'clear'."
            }
        } catch (e: SecurityException) {
            Lumberjack.e("ClipboardTool", "SecurityException accessing clipboard", e)
            "Error: Clipboard access denied."
        } catch (e: Exception) {
            Lumberjack.e("ClipboardTool", "Error accessing clipboard", e)
            "Error accessing clipboard: ${e.message}"
        }
    }

    private fun executeRead(): String {
        val clip = cm.primaryClip ?: return "Clipboard is empty."
        if (clip.itemCount == 0) return "Clipboard is empty."

        val item = clip.getItemAt(0)
        val text = item.text?.toString()

        return if (text != null) {
            buildString {
                if (text.length > 5000) {
                    appendLine("Clipboard content (${text.length} chars, truncated):")
                    appendLine(text.take(5000))
                    appendLine("... (${text.length - 5000} more chars)")
                } else {
                    appendLine("Clipboard content:")
                    appendLine(text)
                }
            }
        } else if (item.uri != null) {
            "Clipboard contains a URI: ${item.uri}"
        } else if (item.htmlText != null) {
            "Clipboard contains HTML content (${item.htmlText.length} chars, plain text not available)."
        } else {
            "Clipboard contains non-text data (mime: ${clip.description.getMimeType(0)})."
        }
    }

    private fun executeWrite(text: String?): String {
        if (text.isNullOrBlank()) {
            return "Error: 'text' parameter is required for 'write' action."
        }
        val clip = ClipData.newPlainText("AI output", text)
        cm.setPrimaryClip(clip)
        return "Clipboard written (${text.length} chars)."
    }

    private fun executeClear(): String {
        cm.clearPrimaryClip()
        return "Clipboard cleared."
    }
}