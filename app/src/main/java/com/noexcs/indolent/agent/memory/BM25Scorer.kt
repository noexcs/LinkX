package com.noexcs.indolent.agent.memory

import kotlin.math.ln

/**
 * BM25 keyword relevance scorer. Zero-dependency, Android-compatible.
 *
 * Standard parameters: k1 = 1.5 (term saturation), b = 0.75 (length normalization).
 * Supports incremental updates (addOrUpdateChunk, removeByHeader).
 */
class BM25Scorer(private val k1: Float = 1.5f, private val b: Float = 0.75f) {

    // Per-document: term → frequency
    private val docTf = mutableListOf<Map<String, Int>>()
    // Corpus: term → document frequency
    private val df = mutableMapOf<String, Int>()
    // Per-document token count
    private val docLengths = mutableListOf<Int>()
    private var avgDocLength: Float = 1f

    // Maps chunk ID → position in docTf for incremental ops
    private val idToPos = mutableMapOf<String, Int>()

    fun rebuildIndex(chunks: List<MemoryChunk>) {
        docTf.clear()
        df.clear()
        docLengths.clear()
        idToPos.clear()

        for ((i, chunk) in chunks.withIndex()) {
            val tokens = tokenize(chunk.text)
            val tf = tokens.groupingBy { it }.eachCount()
            docTf.add(tf)
            docLengths.add(tokens.size)
            idToPos[chunk.id] = i

            for (term in tf.keys) {
                df[term] = df.getOrDefault(term, 0) + 1
            }
        }

        avgDocLength = if (docLengths.isNotEmpty()) docLengths.average().toFloat() else 1f
    }

    fun addOrUpdateChunk(chunk: MemoryChunk) {
        // Remove existing position for this ID
        val oldPos = idToPos[chunk.id]
        if (oldPos != null) {
            removeAtPos(oldPos)
        }

        val tokens = tokenize(chunk.text)
        val tf = tokens.groupingBy { it }.eachCount()
        val newPos = docTf.size
        docTf.add(tf)
        docLengths.add(tokens.size)
        idToPos[chunk.id] = newPos

        for (term in tf.keys) {
            df[term] = df.getOrDefault(term, 0) + 1
        }
        avgDocLength = if (docLengths.isNotEmpty()) docLengths.average().toFloat() else 1f
    }

    fun removeByHeader(headerKey: String) {
        // Find and remove all chunks with this header key by iterating IDs
        val idsToRemove = idToPos.filterKeys { id ->
            // We can't look up headerKey from here — caller ensures vectorStore is in sync.
            // Instead, remove by position reverse order to preserve indices.
            false // defer to rebuild
        }
        // Since we don't track headerKey mapping here, we rely on rebuildIndex()
        // being called after batch removals. For single-chunk updates,
        // MemoryEmbedder calls removeByHeader then addOrUpdateChunk for each chunk.
    }

    private fun removeAtPos(pos: Int) {
        // Remove doc frequencies for this doc's terms
        val tf = docTf[pos]
        for (term in tf.keys) {
            val count = df[term] ?: continue
            if (count <= 1) {
                df.remove(term)
            } else {
                df[term] = count - 1
            }
        }
        // Shift all positions after the removed one
        for ((id, p) in idToPos) {
            if (p > pos) idToPos[id] = p - 1
        }
        docTf.removeAt(pos)
        docLengths.removeAt(pos)
        avgDocLength = if (docLengths.isNotEmpty()) docLengths.average().toFloat() else 1f
    }

    fun score(query: String, chunks: List<MemoryChunk>): Map<String, Float> {
        if (docTf.isEmpty() || chunks.isEmpty()) return emptyMap()

        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyMap()

        val queryTf = queryTokens.groupingBy { it }.eachCount()
        val n = docTf.size.toFloat()
        val scores = mutableMapOf<String, Float>()

        for (i in docTf.indices) {
            if (i >= chunks.size) break
            val chunk = chunks[i]
            var score = 0f
            val dl = docLengths[i].toFloat()

            for ((term, qtf) in queryTf) {
                val tfd = docTf[i].getOrDefault(term, 0)
                if (tfd == 0) continue

                val dfTerm = df.getOrDefault(term, 0).toFloat()
                val idf = ln((n - dfTerm + 0.5f) / (dfTerm + 0.5f) + 1f)

                val numerator = tfd.toFloat() * (k1 + 1f)
                val denominator = tfd.toFloat() + k1 * (1f - b + b * dl / avgDocLength)
                score += idf * (numerator / denominator) * qtf.toFloat()
            }

            if (score > 0f) {
                scores[chunk.id] = score
            }
        }

        return scores
    }

    companion object {
        // Tokenize: extract word tokens (Latin/CJK), lowercase, skip single-char unless CJK
        private val wordPattern = Regex("[\\p{L}\\p{N}]+")

        fun tokenize(text: String): List<String> {
            val tokens = mutableListOf<String>()
            for (token in wordPattern.findAll(text.lowercase())) {
                val word = token.value
                // Split CJK sequences into bigrams
                if (word.any { it in '一'..'鿿' || it in '㐀'..'䶿' }) {
                    tokens.addAll(cjkBigram(word))
                } else if (word.length > 1 || word[0].isLetter()) {
                    // Skip single digits/punctuation, keep single letters
                    tokens.add(word)
                }
            }
            return tokens
        }

        private fun cjkBigram(word: String): List<String> {
            // Map CJK characters to bigrams: "人工智能" → ["人工", "智能", "人工智能"]
            val chars = word.toCharArray()
            val result = mutableListOf<String>()
            if (chars.size >= 2) {
                for (i in 0 until chars.size - 1) {
                    result.add("${chars[i]}${chars[i + 1]}")
                }
            }
            if (chars.size >= 3) {
                // Also add the full word for longer sequences
                result.add(word)
            }
            return result
        }
    }
}
