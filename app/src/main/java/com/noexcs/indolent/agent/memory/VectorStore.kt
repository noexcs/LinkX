package com.noexcs.indolent.agent.memory

import kotlin.math.sqrt

class VectorStore {
    private val chunks = mutableListOf<MemoryChunk>()
    private val lock = Any()

    fun replaceAll(newChunks: List<MemoryChunk>) {
        synchronized(lock) {
            chunks.clear()
            chunks.addAll(newChunks)
        }
    }

    fun addOrUpdate(chunk: MemoryChunk) {
        synchronized(lock) {
            val idx = chunks.indexOfFirst { it.id == chunk.id || it.headerKey == chunk.headerKey }
            if (idx >= 0) {
                chunks[idx] = chunk
            } else {
                chunks.add(chunk)
            }
        }
    }

    fun removeByHeader(headerKey: String) {
        synchronized(lock) {
            chunks.removeAll { it.headerKey == headerKey }
        }
    }

    fun search(query: FloatArray, k: Int = 5): List<ScoredChunk> {
        synchronized(lock) {
            if (chunks.isEmpty()) return emptyList()
            return chunks.map { chunk ->
                ScoredChunk(chunk, cosineSimilarity(query, chunk.embedding))
            }.sortedByDescending { it.score }
                .take(k)
        }
    }

    fun size(): Int = synchronized(lock) { chunks.size }
    fun all(): List<MemoryChunk> = synchronized(lock) { chunks.toList() }
    fun clear() { synchronized(lock) { chunks.clear() } }

    companion object {
        internal fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            var dot = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denom = sqrt(normA) * sqrt(normB)
            return if (denom > 1e-8f) dot / denom else 0f
        }
    }
}
