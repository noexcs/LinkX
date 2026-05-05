package com.noexcs.indolent.agent.tools.filesystem

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import java.io.File

class ListFilesTool(private val context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "fs_list"
    override val description = """
        List files and directories at a given path.

        Supports:
        - Regular paths within app-accessible directories.
        - SAF content URIs (content://...) if the user has granted document tree access.

        Results include name, size, type (file/directory), modification time, and permissions.
        Use 'pattern' to filter by simple glob (e.g. "*.txt", "log*").
        Set recursive=true to traverse subdirectories (respects max_depth).
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "path",
            type = "string",
            description = "Directory path to list. Can be absolute, relative, ~/ prefixed, or a content:// URI."
        ),
        ToolParameter(
            name = "pattern",
            type = "string",
            required = false,
            defaultValue = "*",
            description = "Simple glob pattern to filter entries by name. Supports * and ? wildcards. Default '*' (all)."
        ),
        ToolParameter(
            name = "recursive",
            type = "boolean",
            required = false,
            defaultValue = false,
            description = "Whether to list subdirectories recursively. Default false."
        ),
        ToolParameter(
            name = "max_depth",
            type = "integer",
            required = false,
            defaultValue = 3,
            description = "Maximum recursion depth when recursive=true. Default 3."
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val rawPath = args["path"] as? String ?: return "Error: path is required"
        val pattern = args["pattern"] as? String ?: "*"
        val recursive = args["recursive"] as? Boolean ?: false
        val maxDepth = (args["max_depth"] as? Number)?.toInt() ?: 3

        if (FsUtils.isPathTraversal(rawPath)) {
            Lumberjack.w("ListFilesTool", "Path traversal rejected: $rawPath")
            return "Error: Path traversal detected — '..' is not allowed"
        }

        Lumberjack.i("ListFilesTool", "Listing path=$rawPath pattern=$pattern recursive=$recursive maxDepth=$maxDepth")

        return try {
            if (rawPath.startsWith("content://")) {
                listSAF(rawPath, pattern, recursive, maxDepth)
            } else {
                listFile(rawPath, pattern, recursive, maxDepth)
            }
        } catch (e: SecurityException) {
            Lumberjack.e("ListFilesTool", "Permission denied: $rawPath", e)
            "Error: Permission denied — $rawPath"
        } catch (e: Exception) {
            Lumberjack.e("ListFilesTool", "List failed: $rawPath", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    private fun listFile(rawPath: String, pattern: String, recursive: Boolean, maxDepth: Int): String {
        val dir = FsUtils.resolveFile(rawPath, appContext)
            ?: return "Error: Path '$rawPath' is outside allowed directories. Use ~/ for app-local files, or a content:// URI for external storage."

        if (!dir.exists()) return "Error: Directory not found — ${dir.absolutePath}"
        if (!dir.isDirectory) return "Error: Path is not a directory — ${dir.absolutePath}"

        val entries = mutableListOf<EntryInfo>()
        collectFileEntries(dir, pattern, recursive, maxDepth, 0, "", entries)

        return formatListing(dir.absolutePath, entries)
    }

    private fun collectFileEntries(
        dir: File,
        pattern: String,
        recursive: Boolean,
        maxDepth: Int,
        currentDepth: Int,
        prefix: String,
        out: MutableList<EntryInfo>
    ) {
        val children = dir.listFiles() ?: return
        children.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name }).forEach { child ->
            val name = child.name
            if (matchesPattern(name, pattern)) {
                out.add(
                    EntryInfo(
                        path = prefix + name,
                        size = if (child.isFile) child.length() else 0,
                        isDirectory = child.isDirectory,
                        lastModified = child.lastModified(),
                        permissions = FsUtils.formatPermissions(child)
                    )
                )
            }
            if (recursive && child.isDirectory && currentDepth < maxDepth) {
                collectFileEntries(child, pattern, recursive, maxDepth, currentDepth + 1, "$prefix$name/", out)
            }
        }
    }

    private fun listSAF(uri: String, pattern: String, recursive: Boolean, maxDepth: Int): String {
        val docDir = FsUtils.resolveDocumentFile(uri, appContext)
            ?: return "Error: Cannot resolve SAF URI — $uri"

        if (!docDir.exists()) return "Error: Directory not found at SAF URI"
        if (!docDir.isDirectory) return "Error: SAF URI is not a directory"

        val entries = mutableListOf<EntryInfo>()
        collectSAFEntries(docDir, pattern, recursive, maxDepth, 0, "", entries)

        return formatListing(docDir.name ?: uri, entries)
    }

    private fun collectSAFEntries(
        dir: DocumentFile,
        pattern: String,
        recursive: Boolean,
        maxDepth: Int,
        currentDepth: Int,
        prefix: String,
        out: MutableList<EntryInfo>
    ) {
        val children = dir.listFiles()
        children.sortedWith(compareBy<DocumentFile> { !it.isDirectory }.thenBy { it.name }).forEach { child ->
            val name = child.name ?: return@forEach
            if (matchesPattern(name, pattern)) {
                out.add(
                    EntryInfo(
                        path = prefix + name,
                        size = if (child.isFile) child.length() else 0,
                        isDirectory = child.isDirectory,
                        lastModified = child.lastModified(),
                        permissions = if (child.isFile) {
                            if (child.canWrite()) "rw" else "r-"
                        } else "rwx"
                    )
                )
            }
            if (recursive && child.isDirectory && currentDepth < maxDepth) {
                collectSAFEntries(child, pattern, recursive, maxDepth, currentDepth + 1, "$prefix$name/", out)
            }
        }
    }

    private fun matchesPattern(name: String, pattern: String): Boolean {
        if (pattern == "*" || pattern.isEmpty()) return true
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
            .let { "^$it$" }
        return try {
            Regex(regex, RegexOption.IGNORE_CASE).matches(name)
        } catch (_: Exception) {
            name.contains(pattern, ignoreCase = true)
        }
    }

    private fun formatListing(rootPath: String, entries: List<EntryInfo>): String {
        if (entries.isEmpty()) return "Directory is empty: $rootPath"
        val fileCount = entries.count { !it.isDirectory }
        val dirCount = entries.count { it.isDirectory }
        return buildString {
            appendLine("Directory: $rootPath")
            appendLine("${entries.size} entries ($fileCount files, $dirCount dirs)")
            appendLine("─".repeat(60))
            entries.forEach { entry ->
                val type = if (entry.isDirectory) " DIR" else "FILE"
                val size = if (entry.isDirectory) "" else FsUtils.formatFileSize(entry.size).let { "  $it".takeLast(8) }
                val perm = entry.permissions
                val time = FsUtils.formatTimestamp(entry.lastModified)
                appendLine("$type $perm $size $time  ${entry.path}")
            }
        }
    }

    private data class EntryInfo(
        val path: String,
        val size: Long,
        val isDirectory: Boolean,
        val lastModified: Long,
        val permissions: String
    )
}
