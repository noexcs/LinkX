package com.noexcs.indolent.agent.tools.filesystem

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import java.io.File

class ReadFileTool(private val context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "fs_read"
    override val description = """
        Read the contents of a file on the device.

        With "All Files Access" granted, any path on the device is accessible (e.g. /storage/emulated/0/, /sdcard/).
        Without it, only app-sandboxed directories are allowed. Use ~/filename for files in the app's internal storage.

        The tool detects binary files and warns instead of outputting garbage.
        Output is truncated if it exceeds max_bytes (default 100000).
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "path",
            type = "string",
            description = "Path to the file. Can be absolute (/data/...), relative to app filesDir, ~/ prefixed, or a content:// URI."
        ),
        ToolParameter(
            name = "encoding",
            type = "string",
            required = false,
            defaultValue = "UTF-8",
            description = "Text encoding to use when reading. Default is UTF-8."
        ),
        ToolParameter(
            name = "max_bytes",
            type = "integer",
            required = false,
            defaultValue = 100000,
            description = "Maximum bytes to read before truncating. Default 100000."
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val rawPath = args["path"] as? String ?: return "Error: path is required"
        val maxBytes = (args["max_bytes"] as? Number)?.toInt() ?: 100000

        if (FsUtils.isPathTraversal(rawPath)) {
            Lumberjack.w("ReadFileTool", "Path traversal rejected: $rawPath")
            return "Error: Path traversal detected — '..' is not allowed"
        }

        Lumberjack.i("ReadFileTool", "Reading path=$rawPath maxBytes=$maxBytes")

        return try {
            if (rawPath.startsWith("content://")) {
                readSAF(rawPath, maxBytes)
            } else {
                readFile(rawPath, maxBytes)
            }
        } catch (e: SecurityException) {
            Lumberjack.e("ReadFileTool", "Permission denied: $rawPath", e)
            "Error: Permission denied — $rawPath"
        } catch (e: Exception) {
            Lumberjack.e("ReadFileTool", "Read failed: $rawPath", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    private fun readFile(rawPath: String, maxBytes: Int): String {
        val file = FsUtils.resolveFile(rawPath, appContext)
            ?: return "Error: Path '$rawPath' is outside allowed directories. Use ~/filename for app-local files, or a content:// URI for external storage."

        if (!file.exists()) return "Error: File not found — ${file.absolutePath}"
        if (!file.isFile) return "Error: Path is a directory — ${file.absolutePath}"
        if (!file.canRead()) return "Error: File is not readable — ${file.absolutePath}"

        val size = file.length()
        val bytes = if (size <= maxBytes) {
            file.readBytes()
        } else {
            file.inputStream().use { it.readNBytes(maxBytes) }
        }

        val binaryWarning = if (FsUtils.isBinaryContent(bytes)) {
            "\n[Warning: File appears to be binary, showing text preview only]\n"
        } else ""

        val text = String(bytes, Charsets.UTF_8)
        val truncated = if (size > maxBytes) "\n[Truncated at $maxBytes / $size bytes]" else ""

        return buildString {
            appendLine("File: ${file.absolutePath}")
            appendLine("Size: ${FsUtils.formatFileSize(size)}")
            appendLine("Permissions: ${FsUtils.formatPermissions(file)}")
            appendLine("Modified: ${FsUtils.formatTimestamp(file.lastModified())}")
            appendLine("---")
            append(binaryWarning)
            append(text)
            appendLine(truncated)
        }
    }

    private fun readSAF(uri: String, maxBytes: Int): String {
        val docFile = FsUtils.resolveDocumentFile(uri, appContext)
            ?: return "Error: Cannot resolve SAF URI — $uri"

        if (!docFile.exists()) return "Error: File not found at SAF URI"
        if (!docFile.isFile) return "Error: SAF URI points to a directory"

        val inputStream = appContext.contentResolver.openInputStream(docFile.uri)
            ?: return "Error: Cannot open input stream for SAF URI"

        val bytes = inputStream.use { it.readNBytes(maxBytes) }

        val binaryWarning = if (FsUtils.isBinaryContent(bytes)) {
            "\n[Warning: File appears to be binary, showing text preview only]\n"
        } else ""

        val text = String(bytes, Charsets.UTF_8)
        val truncated = if (bytes.size >= maxBytes) "\n[Truncated at $maxBytes bytes]" else ""

        return buildString {
            appendLine("File: ${docFile.name}")
            appendLine("Size: ${FsUtils.formatFileSize(docFile.length())}")
            appendLine("Modified: ${FsUtils.formatTimestamp(docFile.lastModified())}")
            appendLine("---")
            append(binaryWarning)
            append(text)
            appendLine(truncated)
        }
    }
}
