package com.noexcs.indolent.agent.tools.common

import com.noexcs.indolent.agent.LLMMessage
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import java.io.File

class AgentClipboardTool(
    private val store: AgentClipboardStore,
    private val historyProvider: () -> List<LLMMessage>?
) : AgentTool {

    override val name = "agent_clipboard"
    override val description = """
        Manage the agent's internal clipboard with named slots (separate from the Android system clipboard).

        ## Slots
        Clipboard content is organized into named slots (namespace). The default slot is "default".
        Use the `ns` parameter to target a specific slot. Operations without `ns` use the default slot.

        ## Operations
        - "copy": Store content. Three ways:
          1. `text` — copy a string directly.
          2. `prefix` + `suffix` — extract content between these anchors from a single history message.
          3. `source` — read content from a file path.
          If `ns` is given, the content is stored in that slot; otherwise the default slot.
        - "paste": Display the content of a slot to the user in the conversation.
        - "clear": Clear a specific slot (with `ns`), or all slots (without `ns`).
        - "info": Show status of a specific slot (with `ns`), or list all slots (without `ns`).

        ## Interpolation
        Use {{agent_clipboard}} in any tool parameter to inject the default slot's content.
        Use {{agent_clipboard:slotname}} to inject a named slot's content.

        Shared with subagents.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "action",
            type = "string",
            description = "Operation: 'copy', 'paste', 'clear', or 'info'",
            required = true
        ),
        ToolParameter(
            name = "ns",
            type = "string",
            description = "Slot name / namespace. Defaults to 'default' if omitted. Use to organize multiple clipboard items.",
            required = false
        ),
        ToolParameter(
            name = "text",
            type = "string",
            description = "Text to store (for 'copy' action). Direct content to copy into the slot.",
            required = false
        ),
        ToolParameter(
            name = "prefix",
            type = "string",
            description = "Anchor before desired content in a history message. Must be used with 'suffix'. Matches exactly one message.",
            required = false
        ),
        ToolParameter(
            name = "suffix",
            type = "string",
            description = "Anchor after desired content in a history message. Must be used with 'prefix'. Matches exactly one message.",
            required = false
        ),
        ToolParameter(
            name = "source",
            type = "string",
            description = "File path to read content from (for 'copy' action). The file content is stored into the slot.",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val action = (args["action"] as? String)?.lowercase() ?: "info"
        val ns = (args["ns"] as? String)?.ifBlank { null } ?: AgentClipboardStore.DEFAULT_SLOT
        Lumberjack.i("AgentClipboardTool", "Executing action=$action ns=$ns")

        return try {
            when (action) {
                "copy" -> executeCopy(
                    ns = ns,
                    text = args["text"] as? String,
                    prefix = args["prefix"] as? String,
                    suffix = args["suffix"] as? String,
                    source = args["source"] as? String
                )
                "paste" -> executePaste(ns)
                "clear" -> executeClear(args["ns"] as? String)
                "info" -> executeInfo(args["ns"] as? String)
                else -> "Error: Unknown action '$action'. Use 'copy', 'paste', 'clear', or 'info'."
            }
        } catch (e: Exception) {
            Lumberjack.e("AgentClipboardTool", "Action=$action ns=$ns failed", e)
            "Error: ${e.message}"
        }
    }

    private fun executeCopy(
        ns: String,
        text: String?,
        prefix: String?,
        suffix: String?,
        source: String?
    ): String {
        // ── source (file) takes precedence ──
        if (!source.isNullOrBlank()) {
            return copyFromFile(ns, source, text)
        }

        // ── anchors ──
        val hasAnchors = prefix != null && suffix != null && prefix.isNotBlank() && suffix.isNotBlank()
        val hasPartial = (prefix != null && prefix.isNotBlank()) != (suffix != null && suffix.isNotBlank())

        if (hasPartial) {
            return "Error: Both 'prefix' and 'suffix' must be provided together, or omit both."
        }

        // ── text ──
        val hasText = text != null && text.isNotBlank()

        if (!hasText && !hasAnchors) {
            return "Error: Provide 'text', 'prefix'+'suffix', or 'source' for copy."
        }

        val extracted = if (hasAnchors) extractFromHistory(prefix, suffix) else ""
        if (hasAnchors && extracted.startsWith("Error")) return extracted

        val finalContent = if (hasText) {
            if (extracted.isNotEmpty()) extracted + text else text
        } else {
            extracted
        }

        store.write(finalContent, ns)
        return buildString {
            append("Agent clipboard written to slot '$ns' (${finalContent.length} chars).")
            if (hasAnchors) append(" Extracted from history using prefix/suffix.")
            append(" Use {{agent_clipboard${if (ns != AgentClipboardStore.DEFAULT_SLOT) ":$ns" else ""}}} to reference this content.")
        }
    }

    private fun copyFromFile(ns: String, path: String, appendText: String?): String {
        val file = File(path)
        if (!file.exists()) {
            return "Error: File not found: $path"
        }
        if (!file.isFile) {
            return "Error: Path is not a file: $path"
        }
        if (!file.canRead()) {
            return "Error: Cannot read file: $path"
        }

        val content = try {
            file.readText()
        } catch (e: Exception) {
            return "Error reading file: ${e.message}"
        }

        val finalContent = if (appendText != null && appendText.isNotBlank()) {
            content + appendText
        } else {
            content
        }

        store.write(finalContent, ns)
        return "Agent clipboard written to slot '$ns' from file (${file.name}, ${finalContent.length} chars). Use {{agent_clipboard${if (ns != AgentClipboardStore.DEFAULT_SLOT) ":$ns" else ""}}} to reference."
    }

    private fun extractFromHistory(prefix: String, suffix: String): String {
        val history = historyProvider()
        if (history.isNullOrEmpty()) {
            return "Error: No conversation history available for prefix/suffix matching."
        }

        val matches = mutableListOf<Pair<Int, String>>()

        for ((i, msg) in history.withIndex()) {
            val content = msg.content
            val prefixIdx = content.indexOf(prefix)
            if (prefixIdx == -1) continue

            val afterPrefix = prefixIdx + prefix.length
            val suffixIdx = content.indexOf(suffix, afterPrefix)
            if (suffixIdx == -1) continue

            val extracted = content.substring(afterPrefix, suffixIdx)
            matches.add(i to extracted)
        }

        return when (matches.size) {
            0 -> "Error: No message in history contains both prefix and suffix with prefix before suffix in the same message."
            1 -> matches[0].second
            else -> {
                val indices = matches.joinToString(", ") { (idx, _) -> idx.toString() }
                "Error: Ambiguous — ${matches.size} messages match (indices: $indices). Use more specific prefix/suffix to narrow to a single message."
            }
        }
    }

    private fun executePaste(ns: String): String {
        val content = store.read(ns)
        return if (content != null) {
            store.setPendingPasteContent(ns, content)
            "Pasted agent clipboard slot '$ns' (${content.length} chars) to the conversation."
        } else {
            "Agent clipboard slot '$ns' is empty. Use action=\"copy\" to store text first."
        }
    }

    private fun executeClear(nsParam: String?): String {
        if (nsParam != null && nsParam.isNotBlank()) {
            store.clear(nsParam)
            return "Agent clipboard slot '$nsParam' cleared."
        } else {
            store.clear()
            return "All agent clipboard slots cleared."
        }
    }

    private fun executeInfo(nsParam: String?): String {
        return buildString {
            if (nsParam != null && nsParam.isNotBlank()) {
                val content = store.read(nsParam)
                appendLine("Slot '$nsParam':")
                if (content != null) {
                    appendLine("- Size: ${content.length} chars")
                    appendLine("- Preview: ${content.take(100)}")
                } else {
                    appendLine("- Empty")
                }
            } else {
                val slotSizes = store.slotSizes()
                if (slotSizes.isEmpty()) {
                    appendLine("All slots are empty.")
                } else {
                    appendLine("Clipboard slots:")
                    for ((name, size) in slotSizes) {
                        appendLine("- '$name': $size chars")
                    }
                }
            }
            appendLine()
            appendLine("Use {{agent_clipboard}} or {{agent_clipboard:slotname}} in any tool parameter to paste.")
        }
    }
}
