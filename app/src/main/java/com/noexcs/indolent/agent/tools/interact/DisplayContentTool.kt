package com.noexcs.indolent.agent.tools.interact

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.agent.tools.filesystem.FsUtils
import com.noexcs.indolent.logging.Lumberjack
import java.util.UUID

class DisplayContentTool(
    private val appContext: Context,
    private val manager: ContentDisplayManager?
) : AgentTool {

    override val name = "display_content"
    override val description = """
        Display a file, image, web page, or text content to the user.
        Images, text, and PDFs appear in a bottom sheet that slides up from the bottom.
        The user can swipe down to dismiss, and tap the tool message in chat to re-display.

        Types:
        - "image": show an image file. Provide path (local file or content:// URI).
        - "text": show text content. Provide "content" for inline text, or "path" to read a file.
        - "pdf": render a PDF file. Provide path (local file or content:// URI).
        - "web": open a URL in Chrome Custom Tabs (opens as a bottom sheet on supported devices).
                 Provide url parameter.

        Paths must be within app-accessible directories (use ~/ for app filesDir)
        or a content:// URI from SAF storage access. Path traversal is blocked.
        Text content is limited to 1MB.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter("type", "string", "Content type: image, text, pdf, or web"),
        ToolParameter("title", "string", required = false, defaultValue = "",
            description = "Optional title for the displayed content"),
        ToolParameter("path", "string", required = false,
            description = "File path or content:// URI (for image, text, pdf)"),
        ToolParameter("content", "string", required = false,
            description = "Inline text to display (for text type, alternative to path)"),
        ToolParameter("url", "string", required = false,
            description = "Web URL to open (for web type)"),
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val typeStr = (args["type"] as? String)?.lowercase()
            ?: return "Error: 'type' is required. Supported: image, text, pdf, web"

        val title = (args["title"] as? String)?.takeIf { it.isNotBlank() }
        val id = UUID.randomUUID().toString()

        return try {
            // Web: launch Chrome Custom Tabs directly
            if (typeStr == "web") {
                return launchWebContent(title, args)
            }

            if (manager == null) {
                return "Error: Content display is not available in background execution mode."
            }

            val displayContent = when (typeStr) {
                "image" -> buildImageContent(id, title, args)
                "text" -> buildTextContent(id, title, args)
                "pdf" -> buildPdfContent(id, title, args)
                else -> return "Error: Unsupported type '$typeStr'. Supported: image, text, pdf, web"
            } ?: return "Error: Failed to create display content"

            manager.store(displayContent)
            manager.show(id)
            buildString {
                appendLine("Content displayed in bottom sheet.")
                if (title != null) appendLine("Title: $title")
                append("Content ID: $id | Type: $typeStr")
                if (displayContent.path != null) append(" | Path: ${displayContent.path}")
                if (displayContent.url != null) append(" | URL: ${displayContent.url}")
            }
        } catch (e: SecurityException) {
            Lumberjack.w("DisplayContentTool", "Security exception: ${e.message}")
            "Error: Permission denied — ${e.message}"
        } catch (e: Exception) {
            Lumberjack.e("DisplayContentTool", "Unexpected error", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    private fun buildImageContent(id: String, title: String?, args: Map<String, Any?>): DisplayContent? {
        val path = args["path"] as? String ?: return null
        val validatedPath = validatePath(path) ?: return null
        return DisplayContent(id = id, type = ContentType.IMAGE, title = title, path = validatedPath)
    }

    private fun buildTextContent(id: String, title: String?, args: Map<String, Any?>): DisplayContent? {
        val content = args["content"] as? String
        if (!content.isNullOrBlank()) {
            val truncated = if (content.length > MAX_TEXT_SIZE)
                content.take(MAX_TEXT_SIZE) + "\n\n[Truncated at $MAX_TEXT_SIZE bytes]"
            else content
            return DisplayContent(
                id = id, type = ContentType.TEXT, title = title,
                textContent = truncated
            )
        }

        val rawPath = args["path"] as? String ?: return null
        val validatedPath = validatePath(rawPath) ?: return null

        return try {
            val file = java.io.File(validatedPath)
            if (!file.exists()) throw IllegalStateException("File not found: ${file.absolutePath}")
            if (!file.isFile) throw IllegalStateException("Path is a directory: ${file.absolutePath}")
            if (!file.canRead()) throw IllegalStateException("File is not readable: ${file.absolutePath}")

            val size = file.length()
            val bytes = if (size <= MAX_TEXT_SIZE) file.readBytes()
            else file.inputStream().use { it.readNBytes(MAX_TEXT_SIZE.toInt()) }

            val binaryWarning = if (FsUtils.isBinaryContent(bytes))
                "\n[Warning: File appears to be binary, showing text preview]\n" else ""

            val text = String(bytes, Charsets.UTF_8)
            val truncated = if (size > MAX_TEXT_SIZE)
                "\n\n[Truncated at $MAX_TEXT_SIZE / $size bytes]" else ""

            DisplayContent(
                id = id, type = ContentType.TEXT, title = title,
                textContent = binaryWarning + text + truncated
            )
        } catch (e: Exception) {
            Lumberjack.w("DisplayContentTool", "Failed to read text: ${e.message}")
            throw IllegalStateException("Failed to read text file: ${e.message}")
        }
    }

    private fun buildPdfContent(id: String, title: String?, args: Map<String, Any?>): DisplayContent? {
        val path = args["path"] as? String ?: return null
        val validatedPath = validatePath(path) ?: return null

        // Verify PDF magic bytes
        try {
            val file = java.io.File(validatedPath)
            if (!file.exists()) throw IllegalStateException("File not found: ${file.absolutePath}")
            val header = file.inputStream().use { it.readNBytes(5) }
            if (header.size < 5 || String(header) != "%PDF-") {
                throw IllegalStateException("File does not appear to be a valid PDF")
            }
        } catch (e: IllegalStateException) {
            Lumberjack.w("DisplayContentTool", "PDF validation failed: ${e.message}")
            throw e
        }

        return DisplayContent(id = id, type = ContentType.PDF, title = title, path = validatedPath)
    }

    private fun launchWebContent(title: String?, args: Map<String, Any?>): String {
        val url = args["url"] as? String
        if (url.isNullOrBlank()) return "Error: 'url' parameter is required for web type."
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "Error: Invalid URL format. URL must start with http:// or https://"
        }

        val uri = Uri.parse(trimmed)

        return try {
            val intent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()
                .intent
            intent.data = uri
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Prefer Chrome so we get partial bottom-sheet behavior
            intent.setPackage("com.android.chrome")

            try {
                appContext.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                intent.setPackage(null)
                appContext.startActivity(intent)
            }

            val titleStr = title?.let { "\"$it\"" } ?: "web page"
            "Opened $titleStr in Chrome: $trimmed"
        } catch (e: ActivityNotFoundException) {
            Lumberjack.w("DisplayContentTool", "No browser found: ${e.message}")
            "Error: No browser app found on device to open the URL."
        } catch (e: Exception) {
            Lumberjack.e("DisplayContentTool", "Failed to open URL", e)
            "Error: Failed to open URL: ${e.message}"
        }
    }

    private fun validatePath(rawPath: String): String? {
        if (rawPath.isBlank()) return null
        if (FsUtils.isPathTraversal(rawPath)) {
            throw SecurityException("Path traversal detected")
        }
        if (rawPath.startsWith("content://")) {
            val docFile = FsUtils.resolveDocumentFile(rawPath, appContext)
                ?: throw SecurityException("Cannot resolve SAF URI")
            return rawPath
        }
        val file = FsUtils.resolveFile(rawPath, appContext)
            ?: throw SecurityException("Path is outside allowed directories")
        return file.absolutePath
    }

    companion object {
        private const val MAX_TEXT_SIZE = 1_048_576 // 1 MB
    }
}
