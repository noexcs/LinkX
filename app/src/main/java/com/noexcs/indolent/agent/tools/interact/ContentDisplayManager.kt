package com.noexcs.indolent.agent.tools.interact

import androidx.compose.runtime.mutableStateOf

class ContentDisplayManager(
    private val maxStoredItems: Int = 20
) {
    private val contentStore = linkedMapOf<String, DisplayContent>()
    private val storeLock = Any()
    val currentContent = mutableStateOf<DisplayContent?>(null)

    fun store(content: DisplayContent) {
        synchronized(storeLock) {
            if (contentStore.size >= maxStoredItems) {
                contentStore.remove(contentStore.keys.first())
            }
            contentStore[content.id] = content
        }
    }

    fun show(id: String): Boolean {
        val content = synchronized(storeLock) { contentStore[id] } ?: return false
        currentContent.value = content
        return true
    }

    fun dismiss() {
        currentContent.value = null
    }

    fun getStoredContent(id: String): DisplayContent? = synchronized(storeLock) { contentStore[id] }
}
