package com.noexcs.indolent.agent.memory

import kotlin.math.ln

/**
 * BM25 keyword relevance scorer.
 * Zero-dependency, pure Kotlin implementation.
 *
 * Standard parameters: k1 = 1.5 (term saturation), b = 0.75 (length normalization).
 */
class BM25Scorer(private val k1: Float = 1.5f, private val b: Float = 0.75f) {

    // Per-document: term → frequency
    private val docTf = mutableListOf<Map<String, Int>>()
    // Corpus: term → document frequency (how many docs contain this term)
    private val df = mutableMapOf<String, Int>()
    // Per-document token count
    private val docLengths = mutableListOf<Int>()
    private var avgDocLength: Float = 0f

    /** Rebuild the entire index from the given chunks. */
    fun rebuildIndex(chunks: List<MemoryChunk>) {
        docTf.clear()
        df.clear()
        docLengths.clear()

        for (chunk in chunks) {
            val tokens = tokenize(chunk.text)
            val tf = tokens.groupingBy { it }.eachCount()
            docTf.add(tf)
            docLengths.add(tokens.size)

            for (term in tf.keys) {
                df[term] = df.getOrDefault(term, 0) + 1
            }
        }

        avgDocLength = if (docLengths.isNotEmpty()) docLengths.average().toFloat() else 1f
    }

    /** Score all candidate chunks against a query, returning scores keyed by chunk ID. */
    fun score(query: String, chunks: List<MemoryChunk>): Map<String, Float> {
        if (docTf.isEmpty() || chunks.isEmpty()) return emptyMap()

        // Rebuild index from current chunks (lightweight, runs in <1ms for <500 chunks)
        rebuildIndex(chunks)

        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyMap()

        val queryTf = queryTokens.groupingBy { it }.eachCount()
        val n = docTf.size.toFloat()
        val scores = mutableMapOf<String, Float>()

        for (i in docTf.indices) {
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

            scores[chunk.id] = score
        }

        return scores
    }

    companion object {
        private val tokenPattern = Regex("[\\p{L}\\p{N}]+")

        fun tokenize(text: String): List<String> {
            return tokenPattern.findAll(text.lowercase()).map { it.value }.toList()
        }
    }
}
