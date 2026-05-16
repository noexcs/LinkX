package com.noexcs.indolent.data

import android.content.Context
import com.noexcs.indolent.agent.MemoryProvider
import com.noexcs.indolent.agent.memory.EmbeddingModel
import com.noexcs.indolent.agent.memory.MemoryEmbedder
import com.noexcs.indolent.agent.memory.MemoryIndex
import com.noexcs.indolent.agent.memory.VectorStore
import com.noexcs.indolent.agent.memory.WordPieceTokenizer
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MemoryManager(private val context: Context) : MemoryProvider {
    private val file = File(context.filesDir, "memory.md")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Lazy-initialized vector retrieval system
    @Volatile private var embedder: MemoryEmbedder? = null
    @Volatile private var embedderInitFailed = false
    @Volatile private var isInitializing = false
    private val initLock = Any()

    override fun read(): String = if (file.exists()) file.readText() else ""

    fun write(content: String) {
        file.writeText(content)
        // Trigger full re-index in background
        scope.launch {
            ensureEmbedder()?.let { emb ->
                try {
                    emb.embedAll(content)
                    emb.saveIndex()
                    Lumberjack.i("MemoryManager", "Full re-index complete: ${emb.chunkCount()} chunks")
                } catch (e: Exception) {
                    Lumberjack.e("MemoryManager", "Re-index failed after write", e)
                }
            }
        }
    }

    override fun save(key: String, value: String) {
        val current = read()
        val marker = "\n## $key\n"
        val existingIdx = current.indexOf(marker)
        val updated = if (existingIdx >= 0) {
            val nextMarker = current.indexOf("\n## ", existingIdx + marker.length)
            val prefix = current.substring(0, existingIdx)
            val suffix = if (nextMarker >= 0) current.substring(nextMarker) else ""
            "$prefix$marker$value\n$suffix"
        } else {
            "${current.trimEnd()}$marker$value\n"
        }
        file.writeText(updated)

        // Re-embed just the changed section in background
        scope.launch {
            ensureEmbedder()?.let { emb ->
                try {
                    emb.embedSingle(key, value)
                    emb.saveIndex()
                    Lumberjack.i("MemoryManager", "Section '$key' re-embedded")
                } catch (e: Exception) {
                    Lumberjack.e("MemoryManager", "Failed to re-embed section '$key'", e)
                }
            }
        }
    }

    fun saveMemory(key: String, value: String) = save(key, value)

    override suspend fun search(query: String, k: Int, bm25Weight: Float, vectorWeight: Float): List<String> {
        val emb = ensureEmbedder()
        if (emb == null || !emb.isReady() || emb.chunkCount() == 0) {
            // Fallback to full dump
            val full = read()
            return if (full.isNotBlank()) listOf(full) else emptyList()
        }
        return try {
            val results = emb.search(query, k, bm25Weight, vectorWeight)
            results.map { scored ->
                val header = scored.chunk.headerKey
                val text = scored.chunk.text
                buildString {
                    appendLine("--- Entry: $header (score: ${"%.2f".format(scored.score)})")
                    append(text)
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("MemoryManager", "Vector search failed, falling back to full dump", e)
            val full = read()
            if (full.isNotBlank()) listOf(full) else emptyList()
        }
    }

    fun isRetrievalReady(): Boolean = embedder?.isReady() == true

    /**
     * Attempts a background warm-up: loads the model and builds the index if needed.
     * Returns immediately; indexing runs on a background coroutine.
     */
    fun warmUp() {
        scope.launch {
            ensureEmbedder()
        }
    }

    private suspend fun ensureEmbedder(): MemoryEmbedder? {
        // Fast path: already loaded
        embedder?.let { if (it.isReady()) return it }

        // Fast path: previously failed or currently initializing
        if (embedderInitFailed || isInitializing) return null

        synchronized(initLock) {
            embedder?.let { if (it.isReady()) return it }
            if (embedderInitFailed) return null
            if (isInitializing) return null
            isInitializing = true
        }

        // Run heavy init (assets read + ONNX session creation) off the main thread
        return withContext(Dispatchers.Default) {
            try {
                val tokenizer = WordPieceTokenizer.load(
                    context.assets.open("models/tokenizer.json").bufferedReader().readText()
                )
                val model = EmbeddingModel().apply {
                    load(context, "models/model.onnx")
                }
                val store = VectorStore()
                val memIndex = MemoryIndex()
                val emb = MemoryEmbedder(context, tokenizer, model, store, memIndex)

                // Try loading existing index first
                if (!emb.loadIndex()) {
                    val content = read()
                    if (content.isNotBlank()) {
                        emb.embedAll(content)
                        emb.saveIndex()
                        Lumberjack.i("MemoryManager", "Initial index built: ${emb.chunkCount()} chunks")
                    }
                }

                synchronized(initLock) {
                    embedder = emb
                    isInitializing = false
                }
                Lumberjack.i("MemoryManager", "Vector retrieval ready: ${emb.chunkCount()} chunks")
                emb
            } catch (e: Exception) {
                Lumberjack.e("MemoryManager", "Failed to initialize embedder, vector search disabled", e)
                synchronized(initLock) {
                    embedderInitFailed = true
                    isInitializing = false
                }
                null
            }
        }
    }
}
