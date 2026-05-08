package com.noexcs.indolent.agent.tools.filesystem

import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import java.io.File
import java.util.regex.Pattern

class FindFilesTool(private val context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "fs_find"
    override val description = """
        Recursively find files and directories (like the Unix 'find' command).

        Combines find + grep: search by name, type, size, modification time, and
        optionally match file contents with a regex pattern.

        Parameters (all optional except path):
        - path: directory to start search from (required)
        - pattern: glob pattern for filename matching (supports *, ?, [abc])
        - type: "file", "dir", or any (default any)
        - min_size / max_size: file size in bytes (integers)
        - modified_within: time interval like "24h", "7d", "30m", "1h30m"
        - newer_than / older_than: Unix timestamp in seconds
        - content_regex: search text content of files (only for text files < 10MB)
        - content_ignore_case: case-insensitive content matching (default true)
        - max_depth: maximum recursion depth (default unlimited)
        - limit: max results (default 100)
        - sort: "name", "size", "time" (default "name")

        Output includes path, size, permissions, and last-modified time for each match.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "path",
            type = "string",
            description = "Directory path to search. Required."
        ),
        ToolParameter(
            name = "pattern",
            type = "string",
            required = false,
            description = "Glob pattern to match filenames. Supports *, ?, [abc], [^abc]. Example: \"*.kt\"."
        ),
        ToolParameter(
            name = "type",
            type = "string",
            required = false,
            description = "Filter by type: \"file\" or \"dir\". Default: any."
        ),
        ToolParameter(
            name = "min_size",
            type = "integer",
            required = false,
            description = "Minimum file size in bytes."
        ),
        ToolParameter(
            name = "max_size",
            type = "integer",
            required = false,
            description = "Maximum file size in bytes."
        ),
        ToolParameter(
            name = "modified_within",
            type = "string",
            required = false,
            description = "Only match files modified within this duration. Suffix: s=seconds, m=minutes, h=hours, d=days. Example: \"24h\", \"30m\", \"7d\"."
        ),
        ToolParameter(
            name = "newer_than",
            type = "integer",
            required = false,
            description = "Only match files modified after this Unix timestamp (seconds). Overrides modified_within if both given."
        ),
        ToolParameter(
            name = "older_than",
            type = "integer",
            required = false,
            description = "Only match files modified before this Unix timestamp (seconds)."
        ),
        ToolParameter(
            name = "content_regex",
            type = "string",
            required = false,
            description = "Search file contents for lines matching this regex. Only applies to text files. Returns matching lines with context."
        ),
        ToolParameter(
            name = "content_ignore_case",
            type = "boolean",
            required = false,
            description = "Case-insensitive content matching. Default true."
        ),
        ToolParameter(
            name = "max_depth",
            type = "integer",
            required = false,
            description = "Maximum recursion depth. Default: unlimited."
        ),
        ToolParameter(
            name = "limit",
            type = "integer",
            required = false,
            defaultValue = 100,
            description = "Maximum number of results to return. Default 100."
        ),
        ToolParameter(
            name = "sort",
            type = "string",
            required = false,
            description = "Sort results by \"name\", \"size\", or \"time\". Default \"name\"."
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val path = args["path"] as? String ?: return "Error: path is required"
        val pattern = (args["pattern"] as? String)?.takeIf { it.isNotBlank() }
        val typeFilter = (args["type"] as? String)?.trim()?.lowercase()
        val minSize = (args["min_size"] as? Number)?.toLong()
        val maxSize = (args["max_size"] as? Number)?.toLong()
        val modifiedWithin = args["modified_within"] as? String
        val newerThan = (args["newer_than"] as? Number)?.toLong()?.let { it * 1000 }
        val olderThan = (args["older_than"] as? Number)?.toLong()?.let { it * 1000 }
        val contentRegex = (args["content_regex"] as? String)?.takeIf { it.isNotBlank() }
        val contentIgnoreCase = args["content_ignore_case"] as? Boolean ?: true
        val maxDepth = (args["max_depth"] as? Number)?.toInt() ?: Int.MAX_VALUE
        val limit = (args["limit"] as? Number)?.toInt() ?: 100
        val sort = (args["sort"] as? String)?.trim()?.lowercase() ?: "name"

        if (FsUtils.isPathTraversal(path)) {
            return "Error: Path traversal detected — '..' is not allowed"
        }

        val dir = FsUtils.resolveFile(path, appContext)
            ?: return "Error: Path '$path' is outside allowed directories."
        if (!dir.exists()) return "Error: Directory not found — ${dir.absolutePath}"
        if (!dir.isDirectory) return "Error: Not a directory — ${dir.absolutePath}"

        Lumberjack.i("FindFilesTool", "find from=$path pattern=$pattern type=$typeFilter sort=$sort")

        return try {
            // Time filter: convert modified_within to timestamp
            val newerThanEffective = newerThan ?: modifiedWithin?.let { parseDuration(it)?.let { ms -> System.currentTimeMillis() - ms } }

            val contentPattern = contentRegex?.let { regex ->
                try {
                    val flags = if (contentIgnoreCase) Pattern.CASE_INSENSITIVE else 0
                    Pattern.compile(regex, flags or Pattern.MULTILINE)
                } catch (e: Exception) {
                    return "Error: Invalid content_regex pattern: ${e.message}"
                }
            }

            val results = mutableListOf<FindResult>()
            search(dir, pattern, typeFilter, minSize, maxSize, newerThanEffective, olderThan,
                contentPattern, maxDepth, 0, limit, results)

            if (results.isEmpty()) {
                return buildString {
                    appendLine("No matches in: ${dir.absolutePath}")
                    appendLine("Filters: pattern=${pattern ?: "*"}, type=${typeFilter ?: "any"}" +
                        buildSizeFilter(minSize, maxSize) +
                        buildTimeFilter(newerThanEffective, olderThan, modifiedWithin) +
                        buildContentFilter(contentRegex))
                }
            }

            val sorted = sortResults(results, sort)

            buildString {
                appendLine("${sorted.size} match(es) in ${dir.absolutePath}")
                appendLine("Filters: pattern=${pattern ?: "*"}, type=${typeFilter ?: "any"}" +
                    buildSizeFilter(minSize, maxSize) +
                    buildTimeFilter(newerThanEffective, olderThan, modifiedWithin) +
                    buildContentFilter(contentRegex) + ", sort=$sort")
                appendLine("─".repeat(80))
                sorted.forEach { result ->
                    append(formatResult(result))
                }
                if (results.size > limit) {
                    appendLine("... and ${results.size - limit} more (increase limit to see all)")
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("FindFilesTool", "Search failed: $path", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    private data class FindResult(
        val file: File,
        val contentMatches: List<ContentMatch>? = null
    )

    private data class ContentMatch(
        val lineNumber: Int,
        val line: String
    )

    private fun search(
        dir: File,
        namePattern: String?,
        typeFilter: String?,
        minSize: Long?,
        maxSize: Long?,
        newerThan: Long?,
        olderThan: Long?,
        contentPattern: Pattern?,
        maxDepth: Int,
        currentDepth: Int,
        limit: Int,
        out: MutableList<FindResult>
    ) {
        if (currentDepth > maxDepth || out.size >= limit) return

        val children = dir.listFiles() ?: return
        for (child in children.sortedBy { it.name }) {
            if (out.size >= limit) break

            val matchesName = matchesGlob(child.name, namePattern ?: "*")
            if (!matchesName) continue

            val matchesType = when (typeFilter) {
                "file", "f" -> child.isFile
                "dir", "d", "directory" -> child.isDirectory
                else -> true
            }
            if (!matchesType) continue

            if (child.isFile) {
                if (!matchesSizeFilter(child, minSize, maxSize)) continue
                if (!matchesTimeFilter(child, newerThan, olderThan)) continue
            }

            if (contentPattern != null && child.isFile) {
                val matches = matchContent(child, contentPattern)
                if (matches.isNotEmpty()) {
                    out.add(FindResult(child, matches))
                }
            } else {
                out.add(FindResult(child))
            }

            if (child.isDirectory && currentDepth < maxDepth && out.size < limit) {
                search(child, namePattern, typeFilter, minSize, maxSize,
                    newerThan, olderThan, contentPattern, maxDepth, currentDepth + 1, limit, out)
            }
        }
    }

    private fun matchesGlob(name: String, pattern: String): Boolean {
        if (pattern == "*" || pattern.isEmpty()) return true
        // Convert glob to regex: *, ?, [abc], [^abc], [a-z]
        val regex = StringBuilder("^")
        var i = 0
        while (i < pattern.length) {
            when (val c = pattern[i]) {
                '*' -> regex.append(".*")
                '?' -> regex.append('.')
                '[' -> {
                    val end = pattern.indexOf(']', i)
                    if (end > i) {
                        val charClass = pattern.substring(i + 1, end)
                        if (charClass.startsWith("^")) {
                            regex.append("[^${Regex.escape(charClass.substring(1))}]")
                        } else {
                            regex.append("[${Regex.escape(charClass)}]")
                        }
                        i = end
                    } else {
                        regex.append("\\[")
                    }
                }
                '.', '(', ')', '+', '\\', '^', '$', '{', '}', '|' -> {
                    regex.append('\\').append(c)
                }
                else -> regex.append(c)
            }
            i++
        }
        regex.append('$')
        return try {
            Regex(regex.toString(), RegexOption.IGNORE_CASE).matches(name)
        } catch (_: Exception) {
            name.contains(pattern, ignoreCase = true)
        }
    }

    private fun matchesSizeFilter(file: File, minSize: Long?, maxSize: Long?): Boolean {
        val size = file.length()
        if (minSize != null && size < minSize) return false
        if (maxSize != null && size > maxSize) return false
        return true
    }

    private fun matchesTimeFilter(file: File, newerThan: Long?, olderThan: Long?): Boolean {
        val mod = file.lastModified()
        if (newerThan != null && mod < newerThan) return false
        if (olderThan != null && mod > olderThan) return false
        return true
    }

    private fun matchContent(file: File, pattern: Pattern): List<ContentMatch> {
        val maxContentSize = 10 * 1024 * 1024 // 10MB limit for content search
        if (file.length() > maxContentSize) return emptyList()

        val matches = mutableListOf<ContentMatch>()
        try {
            val bytes = file.readBytes()
            if (FsUtils.isBinaryContent(bytes)) return emptyList()

            String(bytes, Charsets.UTF_8).lineSequence().forEachIndexed { idx, line ->
                if (pattern.matcher(line).find()) {
                    matches.add(ContentMatch(idx + 1, line.trim().take(200)))
                }
            }
        } catch (_: Exception) {
            // Skip files that can't be read as text
        }
        return matches.take(10) // Limit content matches per file
    }

    private fun parseDuration(input: String): Long? {
        val regex = Regex("""(\d+)([dhms])""", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(input.replace("\\s".toRegex(), ""))
        var totalMs = 0L
        var found = false
        matches.forEach { match ->
            found = true
            val value = match.groupValues[1].toLongOrNull() ?: return@forEach
            val unit = match.groupValues[2].lowercase()
            totalMs += when (unit) {
                "d" -> value * 24 * 3600 * 1000
                "h" -> value * 3600 * 1000
                "m" -> value * 60 * 1000
                "s" -> value * 1000
                else -> 0
            }
        }
        return if (found) totalMs else null
    }

    private fun sortResults(results: List<FindResult>, sort: String): List<FindResult> {
        return when (sort) {
            "size" -> results.sortedWith(compareBy({ it.file.isDirectory }, { -it.file.length() }))
            "time" -> results.sortedWith(compareBy({ it.file.isDirectory }, { -it.file.lastModified() }))
            else -> results.sortedWith(compareBy({ it.file.isDirectory }, { it.file.name.lowercase() }))
        }
    }

    private fun formatResult(result: FindResult): String {
        val f = result.file
        val type = if (f.isDirectory) "DIR " else "FILE"
        val size = if (f.isFile) FsUtils.formatFileSize(f.length()) else "-"
        val perm = FsUtils.formatPermissions(f)
        val time = FsUtils.formatTimestamp(f.lastModified())
        val matches = result.contentMatches?.let { matches ->
            val lines = matches.joinToString("; ") { "L${it.lineNumber}: ${it.line}" }
            "\n   >>> $lines"
        } ?: ""
        return "$type $perm  ${size.padStart(8)}  $time  ${f.absolutePath}$matches\n"
    }

    private fun buildSizeFilter(minSize: Long?, maxSize: Long?): String {
        val parts = mutableListOf<String>()
        if (minSize != null) parts.add("min_size=${FsUtils.formatFileSize(minSize)}")
        if (maxSize != null) parts.add("max_size=${FsUtils.formatFileSize(maxSize)}")
        return if (parts.isNotEmpty()) ", " + parts.joinToString() else ""
    }

    private fun buildTimeFilter(newerThan: Long?, olderThan: Long?, modifiedWithin: String?): String {
        val parts = mutableListOf<String>()
        if (modifiedWithin != null) parts.add("modified_within=$modifiedWithin")
        if (newerThan != null) parts.add("newer_than=${FsUtils.formatTimestamp(newerThan)}")
        if (olderThan != null) parts.add("older_than=${FsUtils.formatTimestamp(olderThan)}")
        return if (parts.isNotEmpty()) ", " + parts.joinToString() else ""
    }

    private fun buildContentFilter(contentRegex: String?): String {
        return if (contentRegex != null) ", content_regex=\"$contentRegex\"" else ""
    }

    // Pad string to exact visible width
    private fun String.padStart(n: Int): String {
        if (length >= n) return this
        return " ".repeat(n - length) + this
    }
}
