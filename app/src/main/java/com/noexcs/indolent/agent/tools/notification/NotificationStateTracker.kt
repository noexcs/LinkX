package com.noexcs.indolent.agent.tools.notification

import java.util.concurrent.ConcurrentHashMap

data class NotificationState(
    val id: Int,
    val key: String,
    val channelId: String,
    val groupId: String,
    val title: String,
    val content: String,
    val postedAt: Long,
    val status: String // "active", "cancelled"
)

/**
 * In-memory tracker for notification state. Used by notification tools
 * to remember what was posted, so we can query by ID, cancel by channel,
 * preserve channel on update, and return history.
 */
object NotificationStateTracker {

    private const val MAX_ENTRIES = 200
    private val store = ConcurrentHashMap<Int, NotificationState>()

    fun put(id: Int, key: String, channelId: String, groupId: String, title: String, content: String) {
        store[id] = NotificationState(
            id = id,
            key = key,
            channelId = channelId,
            groupId = groupId,
            title = title,
            content = content,
            postedAt = System.currentTimeMillis(),
            status = "active"
        )
        evictIfNeeded()
    }

    fun get(id: Int): NotificationState? = store[id]

    fun markCancelled(id: Int) {
        store[id]?.let { store[id] = it.copy(status = "cancelled") }
    }

    fun markCancelledAll(channelId: String? = null) {
        store.forEach { (id, state) ->
            if (channelId == null || state.channelId == channelId) {
                store[id] = state.copy(status = "cancelled")
            }
        }
    }

    fun getByChannel(channelId: String): List<NotificationState> =
        store.values.filter { it.channelId == channelId && it.status == "active" }

    fun getByGroup(groupId: String): List<NotificationState> =
        store.values.filter { it.groupId == groupId && it.status == "active" }

    fun getHistory(limit: Int = 50): List<NotificationState> =
        store.values.sortedByDescending { it.postedAt }.take(limit)

    fun getActiveHistory(limit: Int = 50): List<NotificationState> =
        store.values.filter { it.status == "active" }.sortedByDescending { it.postedAt }.take(limit)

    fun clear() = store.clear()

    fun generateKey(id: Int): String = "indolent|$id|${System.currentTimeMillis()}"

    private fun evictIfNeeded() {
        if (store.size <= MAX_ENTRIES) return
        // Remove oldest cancelled first, then oldest active
        val sorted = store.values.sortedBy { it.postedAt }
        for (state in sorted) {
            if (store.size <= MAX_ENTRIES) break
            if (state.status == "cancelled") {
                store.remove(state.id)
            }
        }
        for (state in sorted) {
            if (store.size <= MAX_ENTRIES) break
            store.remove(state.id)
        }
    }
}
