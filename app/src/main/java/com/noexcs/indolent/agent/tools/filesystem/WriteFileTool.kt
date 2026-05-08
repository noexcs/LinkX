package com.noexcs.indolent.agent.tools.filesystem

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import java.io.File

class WriteFileTool(private val context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "fs_write"
    override val description = """
        Write content to a file on the device.

        With "All Files Access" granted, any path on the device is writable (e.g. /storage/emulated/0/, /sdcard/).
        Without it, only app-sandboxed directories are allowed. Use ~/filename for files in the app's internal storage.

        Parent directories are created automatically (unless create_dirs is set to false).
        Set append to true to add content to an existing file instead of overwriting.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "path",
            type = "string",
            description = "Path to the file. Can be absolute, relative to app filesDir, ~/ prefixed, or a content:// URI."
        ),
        ToolParameter(
            name = "content",
            type = "string",
            description = "Content to write to the file."
        ),
        ToolParameter(
            name = "append",
            type = "boolean",
            required = false,
            defaultValue = false,
            description = "If true, append to the file instead of overwriting. Default false."
        ),
        ToolParameter(
            name = "create_dirs",
            type = "boolean",
            required = false,
            defaultValue = true,
            description = "If true, create parent directories if they don't exist. Default true."
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val rawPath = args["path"] as? String ?: return "Error: path is required"
        val content = args["content"] as? String ?: return "Error: content is required"
        val append = args["append"] as? Boolean ?: false
        val createDirs = args["create_dirs"] as? Boolean ?: true

        if (FsUtils.isPathTraversal(rawPath)) {
            Lumberjack.w("WriteFileTool", "Path traversal rejected: $rawPath")
            return "Error: Path traversal detected — '..' is not allowed"
        }

        val mode = if (append) "append" else "write"
        Lumberjack.i("WriteFileTool", "Writing path=$rawPath mode=$mode contentLen=${content.length}")

        return try {
            if (rawPath.startsWith("content://")) {
                writeSAF(rawPath, content, append)
            } else {
                writeFile(rawPath, content, append, createDirs)
            }
        } catch (e: SecurityException) {
            Lumberjack.e("WriteFileTool", "Permission denied: $rawPath", e)
            "Error: Permission denied — $rawPath"
        } catch (e: Exception) {
            Lumberjack.e("WriteFileTool", "Write failed: $rawPath", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    private fun writeFile(rawPath: String, content: String, append: Boolean, createDirs: Boolean): String {
        val file = FsUtils.resolveFile(rawPath, appContext)
            ?: return "Error: Path '$rawPath' is outside allowed directories. Use ~/filename for app-local files, or a content:// URI for external storage."

        if (file.exists() && file.isDirectory) {
            return "Error: Path is a directory — ${file.absolutePath}"
        }

        if (createDirs) {
            file.parentFile?.mkdirs()
        } else if (!file.parentFile?.exists()!!) {
            return "Error: Parent directory does not exist — ${file.parentFile?.absolutePath}. Set create_dirs=true to auto-create."
        }

        val existedBefore = file.exists()
        if (append) {
            file.appendText(content)
        } else {
            file.writeText(content)
        }

        val finalSize = file.length()
        val status = if (existedBefore) {
            if (append) "Appended" else "Overwritten"
        } else {
            "Created"
        }
        return "$status file: ${file.absolutePath} (${FsUtils.formatFileSize(finalSize)})"
    }

    private fun writeSAF(uri: String, content: String, append: Boolean): String {
        val docFile = FsUtils.resolveDocumentFile(uri, appContext)
            ?: return "Error: Cannot resolve SAF URI — $uri"

        if (docFile.exists() && docFile.isDirectory) {
            return "Error: SAF URI points to a directory"
        }

        val mode = if (append) "wa" else "wt"
        val outputStream = appContext.contentResolver.openOutputStream(docFile.uri, mode)
            ?: return "Error: Cannot open output stream for SAF URI"

        val bytes = content.toByteArray(Charsets.UTF_8)
        outputStream.use { it.write(bytes) }

        val status = if (append) "Appended" else "Written"
        return "$status to SAF file: ${docFile.name} (${FsUtils.formatFileSize(bytes.size.toLong())})"
    }
}
