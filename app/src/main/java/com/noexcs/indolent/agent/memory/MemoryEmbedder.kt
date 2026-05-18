package com.noexcs.indolent.agent.memory

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.tantivy.Doc
import com.noexcs.tantivy.TantivyBM25
import java.io.File

class MemoryEmbedder(
    private val context: Context,
    private val tokenizer: WordPieceTokenizer,
    private val model: EmbeddingModel,
    private val vectorStore: VectorStore,
    private val index: MemoryIndex
) {
    private val indexFile = File(context.filesDir, "memory_index.json")
    private val bm25 = TantivyBM25()

    fun isReady(): Boolean = model.isReady()

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
        bm25.rebuildIndex(chunks.map { it.toDoc() })
        return chunks
    }

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
        vectorStore.removeByHeader(headerKey)
        bm25.removeByHeader(headerKey)
        for (chunk in chunks) {
            vectorStore.addOrUpdate(chunk)
            bm25.addOrUpdate(chunk.toDoc())
        }
        return chunks
    }

    suspend fun embedQuery(text: String): FloatArray {
        return embedText(text)
    }

    suspend fun search(
        query: String,
        k: Int = 5,
        bm25Weight: Float = 0.6f,
        vectorWeight: Float = 0.4f
    ): List<ScoredChunk> {
        if (!isReady()) return emptyList()
        if (vectorStore.size() == 0) return emptyList()

        val chunks = vectorStore.all()

        // Vector scores (cosine similarity)
        val queryEmbedding = embedQuery(query)
        val vectorScores = mutableMapOf<String, Float>()
        for (chunk in chunks) {
            vectorScores[chunk.id] = VectorStore.cosineSimilarity(queryEmbedding, chunk.embedding)
        }

        // BM25 keyword scores (Tantivy manages its own index, no need to pass chunks)
        val bm25Scores = try {
            bm25.search(query, chunks.size)
        } catch (e: Exception) {
            Lumberjack.e("MemoryEmbedder", "BM25 search via Tantivy failed", e)
            emptyMap()
        }

        if (bm25Scores.isEmpty()) {
            return chunks.map { ScoredChunk(it, vectorScores[it.id] ?: 0f) }
                .sortedByDescending { it.score }
                .take(k)
        }

        val bm25Max = bm25Scores.values.maxOrNull() ?: 0f
        val bm25Normalized = if (bm25Max > 0f) {
            bm25Scores.mapValues { it.value / bm25Max }
        } else {
            emptyMap()
        }

        val blended = chunks.map { chunk ->
            val vecScore = vectorScores[chunk.id] ?: 0f
            val bmScore = bm25Normalized[chunk.id] ?: 0f
            val finalScore = bm25Weight * bmScore + vectorWeight * vecScore
            ScoredChunk(chunk, finalScore)
        }.sortedByDescending { it.score }
            .take(k)

        return blended
    }

    fun loadIndex(): Boolean {
        val loaded = index.load(indexFile) ?: return false
        if (loaded.isEmpty()) return false
        vectorStore.replaceAll(loaded)
        bm25.rebuildIndex(loaded.map { it.toDoc() })
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

    private fun MemoryChunk.toDoc() = Doc(id, headerKey, text)
}
