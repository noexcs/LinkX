package com.noexcs.indolent.agent.memory

import java.util.UUID

data class MemoryChunk(
    val id: String = UUID.randomUUID().toString(),
    val headerKey: String,
    val text: String,
    val embedding: FloatArray,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryChunk) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class ScoredChunk(
    val chunk: MemoryChunk,
    val score: Float
)
