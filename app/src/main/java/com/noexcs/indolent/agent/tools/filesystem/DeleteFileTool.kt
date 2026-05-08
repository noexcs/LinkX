package com.noexcs.indolent.agent.tools.filesystem

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import java.io.File

class DeleteFileTool(private val context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "fs_delete"
    override val description = """
        Delete a file or directory on the device.

        With "All Files Access" granted, any file/directory on the device can be deleted.
        Without it, only app-sandboxed directories are allowed.

        Set recursive=true to delete a non-empty directory.
        Deleting a non-existent path returns a warning, not an error.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "path",
            type = "string",
            description = "Path to the file or directory to delete. Can be absolute, relative, ~/ prefixed, or a content:// URI."
        ),
        ToolParameter(
            name = "recursive",
            type = "boolean",
            required = false,
            defaultValue = false,
            description = "If true, recursively delete a directory and all its contents. Required for non-empty directories."
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val rawPath = args["path"] as? String ?: return "Error: path is required"
        val recursive = args["recursive"] as? Boolean ?: false

        if (FsUtils.isPathTraversal(rawPath)) {
            Lumberjack.w("DeleteFileTool", "Path traversal rejected: $rawPath")
            return "Error: Path traversal detected — '..' is not allowed"
        }

        Lumberjack.i("DeleteFileTool", "Deleting path=$rawPath recursive=$recursive")

        return try {
            if (rawPath.startsWith("content://")) {
                deleteSAF(rawPath)
            } else {
                deleteFile(rawPath, recursive)
            }
        } catch (e: SecurityException) {
            Lumberjack.e("DeleteFileTool", "Permission denied: $rawPath", e)
            "Error: Permission denied — $rawPath"
        } catch (e: Exception) {
            Lumberjack.e("DeleteFileTool", "Delete failed: $rawPath", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    private fun deleteFile(rawPath: String, recursive: Boolean): String {
        val file = FsUtils.resolveFile(rawPath, appContext)
            ?: return "Error: Path '$rawPath' is outside allowed directories. Use ~/ for app-local files, or a content:// URI for external storage."

        if (!file.exists()) {
            return "Warning: Path does not exist — ${file.absolutePath} (nothing to delete)"
        }

        if (file.isDirectory) {
            val contents = file.listFiles()
            if (contents != null && contents.isNotEmpty() && !recursive) {
                return "Error: Directory is not empty (${contents.size} items). Set recursive=true to delete anyway."
            }
            val success = if (recursive) file.deleteRecursively() else file.delete()
            return if (success) "Deleted directory: ${file.absolutePath}"
            else "Error: Failed to delete directory — ${file.absolutePath}"
        } else {
            val success = file.delete()
            return if (success) "Deleted file: ${file.absolutePath}"
            else "Error: Failed to delete file — ${file.absolutePath}"
        }
    }

    private fun deleteSAF(uri: String): String {
        val docFile = FsUtils.resolveDocumentFile(uri, appContext)
            ?: return "Error: Cannot resolve SAF URI — $uri"

        if (!docFile.exists()) {
            return "Warning: SAF URI does not exist — $uri (nothing to delete)"
        }

        val name = docFile.name ?: uri
        val success = docFile.delete()
        return if (success) "Deleted: $name"
        else "Error: Failed to delete — $name"
    }
}
