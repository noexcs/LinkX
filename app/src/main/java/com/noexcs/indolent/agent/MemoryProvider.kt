package com.noexcs.indolent.agent

interface MemoryProvider {
    fun read(): String
    fun save(key: String, value: String)

    suspend fun search(
        query: String,
        k: Int = 5,
        bm25Weight: Float = 0.6f,
        vectorWeight: Float = 0.4f
    ): List<String> {
        val full = read()
        return if (full.isNotBlank()) listOf(full) else emptyList()
    }
}
