package com.noexcs.indolent.logging

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class LogBuffer(private val capacity: Int = 2000) {

    private val entries = ArrayDeque<LogEntry>(capacity)
    private val lock = ReentrantReadWriteLock()

    /** Total entries ever written (survives eviction). Used as a cursor for offset queries. */
    private var totalWritten: Long = 0

    fun append(entry: LogEntry) {
        lock.write {
            if (entries.size >= capacity) {
                entries.removeFirst()
            }
            entries.addLast(entry)
            totalWritten++
        }
    }

    fun query(filter: LogFilter): QueryResult {
        return lock.read {
            // Collect matching entries from newest to oldest
            val levelThreshold = filter.level
            val tagFilter = filter.tag?.lowercase()
            val queryFilter = filter.query?.lowercase()
            val since = filter.since
            val before = filter.before
            val count = filter.count.coerceIn(1, LogFilter.MAX_COUNT)
            val offset = filter.offset.coerceAtLeast(0)

            val matched = mutableListOf<LogEntry>()
            var skipped = 0
            var totalMatched = 0

            // Iterate newest-first
            for (i in entries.indices.reversed()) {
                val entry = entries[i]

                if (!matches(entry, levelThreshold, tagFilter, queryFilter, since, before)) continue

                totalMatched++
                if (skipped < offset) {
                    skipped++
                    continue
                }
                if (matched.size < count) {
                    matched.add(entry)
                }
            }

            QueryResult(
                entries = matched,
                totalMatched = totalMatched,
                offset = offset,
                hasMore = (offset + matched.size) < totalMatched,
                bufferSize = entries.size,
                totalWritten = totalWritten
            )
        }
    }

    fun size(): Int = lock.read { entries.size }

    fun clear() = lock.write { entries.clear(); totalWritten = 0 }

    private fun matches(
        entry: LogEntry,
        levelThreshold: Level?,
        tagFilter: String?,
        queryFilter: String?,
        since: Long?,
        before: Long?
    ): Boolean {
        if (levelThreshold != null && entry.level.priority < levelThreshold.priority) return false
        if (tagFilter != null && !entry.tag.lowercase().contains(tagFilter)) return false
        if (queryFilter != null && !entry.message.lowercase().contains(queryFilter)) return false
        if (since != null && entry.timestamp < since) return false
        if (before != null && entry.timestamp > before) return false
        return true
    }

    data class QueryResult(
        val entries: List<LogEntry>,
        val totalMatched: Int,
        val offset: Int,
        val hasMore: Boolean,
        val bufferSize: Int,
        val totalWritten: Long
    )
}
