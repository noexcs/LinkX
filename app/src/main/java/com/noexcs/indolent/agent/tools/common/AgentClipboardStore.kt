package com.noexcs.indolent.agent.tools.common

import com.noexcs.indolent.logging.Lumberjack
import java.util.Collections

class AgentClipboardStore {

    companion object {
        const val DEFAULT_SLOT = "default"
    }

    private val slots: MutableMap<String, String> = Collections.synchronizedMap(LinkedHashMap())

    @Volatile
    private var pendingPasteNs: String? = null

    @Volatile
    private var pendingPasteContent: String? = null

    fun read(ns: String = DEFAULT_SLOT): String? = slots[ns]

    fun write(text: String, ns: String = DEFAULT_SLOT) {
        slots[ns] = text
        Lumberjack.i("AgentClipboard", "Written ${text.length} chars to slot '$ns'")
    }

    fun clear(ns: String? = null) {
        if (ns != null) {
            slots.remove(ns)
            Lumberjack.i("AgentClipboard", "Cleared slot '$ns'")
        } else {
            slots.clear()
            Lumberjack.i("AgentClipboard", "Cleared all slots")
        }
    }

    fun hasContent(ns: String = DEFAULT_SLOT): Boolean = slots.containsKey(ns)

    fun size(ns: String = DEFAULT_SLOT): Int = slots[ns]?.length ?: 0

    fun slotNames(): Set<String> = slots.keys.toSet()

    fun slotSizes(): Map<String, Int> = slots.mapValues { it.value.length }

    fun setPendingPasteContent(ns: String, content: String) {
        pendingPasteNs = ns
        pendingPasteContent = content
    }

    fun consumePendingPasteContent(): Pair<String, String>? {
        val ns = pendingPasteNs ?: return null
        val c = pendingPasteContent ?: return null
        pendingPasteNs = null
        pendingPasteContent = null
        return ns to c
    }
}
