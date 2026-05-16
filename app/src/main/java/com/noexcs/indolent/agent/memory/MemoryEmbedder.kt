package com.noexcs.indolent.agent.memory

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import java.io.File

class MemoryEmbedder(
    private val context: Context,
    private val tokenizer: WordPieceTokenizer,
    private val model: EmbeddingModel,
    private val vectorStore: VectorStore,
    private val index: MemoryIndex
) {
    private val indexFile = File(context.filesDir, "memory_index.json")

    fun isReady(): Boolean = model.isReady()

    /**
     * Embeds all sections from the memory text and populates the vector store.
     */
    suspend fun embedAll(memoryText: String): List<MemoryChunk> {
        val chunkInputs = MemoryChunker.chunk(memoryText)
        if (chunkInputs.isEmpty()) return emptyList()

        val chunks = mutableListOf<MemoryChunk>()
        for (input in chunkInputs) {
            val embedding = embedText(input.text)
            chunks.add(
                MemoryChunk(
                    headerKey = input.headerKey,
                    text = input.text,
                    embedding = embedding
                )
            )
        }
        vectorStore.replaceAll(chunks)
        return chunks
    }

    /**
     * Embeds a single section and updates the vector store.
     * Removes any existing chunks for this header key first.
     */
    suspend fun embedSingle(headerKey: String, text: String): List<MemoryChunk> {
        val chunkInputs = MemoryChunker.chunk("## $headerKey\n$text")
        val chunks = chunkInputs.map { input ->
            val embedding = embedText(input.text)
            MemoryChunk(
                headerKey = input.headerKey,
                text = input.text,
                embedding = embedding
            )
        }
        // Replace all chunks for this header key
        vectorStore.removeByHeader(headerKey)
        for (chunk in chunks) {
            vectorStore.addOrUpdate(chunk)
        }
        return chunks
    }

    /**
     * Embeds a query string (used for search).
     */
    suspend fun embedQuery(text: String): FloatArray {
        return embedText(text)
    }

    /**
     * Searches for the top-K most relevant chunks for the given query text.
     */
    suspend fun search(query: String, k: Int = 5): List<ScoredChunk> {
        if (!isReady()) return emptyList()
        if (vectorStore.size() == 0) return emptyList()
        val queryEmbedding = embedQuery(query)
        return vectorStore.search(queryEmbedding, k)
    }

    fun loadIndex(): Boolean {
        val loaded = index.load(indexFile) ?: return false
        if (loaded.isEmpty()) return false
        vectorStore.replaceAll(loaded)
        Lumberjack.i("MemoryEmbedder", "Loaded index with ${loaded.size} chunks")
        return true
    }

    fun saveIndex() {
        val chunks = vectorStore.all()
        if (chunks.isNotEmpty()) {
            index.save(chunks, indexFile)
            Lumberjack.i("MemoryEmbedder", "Saved index with ${chunks.size} chunks")
        }
    }

    fun chunkCount(): Int = vectorStore.size()

    private suspend fun embedText(text: String): FloatArray {
        val tokens = tokenizer.encode(text)
        return model.embed(tokens.inputIds, tokens.attentionMask, tokens.tokenTypeIds)
    }
}
