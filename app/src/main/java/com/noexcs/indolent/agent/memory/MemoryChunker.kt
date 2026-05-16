package com.noexcs.indolent.agent.memory

object MemoryChunker {

    /** Rough chars-to-tokens estimate for sliding-window chunking. */
    private const val CHARS_PER_TOKEN = 4

    data class ChunkInput(
        val headerKey: String,
        val text: String
    )

    fun chunk(memoryText: String, maxTokens: Int = 128, overlapTokens: Int = 32): List<ChunkInput> {
        if (memoryText.isBlank()) return emptyList()

        val chunks = mutableListOf<ChunkInput>()

        // Split into sections at "## " headers (must be at start of line)
        val sections = memoryText.split(Regex("(?=\\n## )", RegexOption.MULTILINE))

        for (section in sections) {
            val trimmed = section.trimStart()
            if (trimmed.isBlank()) continue

            // Extract header key
            val lines = trimmed.split("\n", limit = 2)
            val headerLine = lines[0].trim()
            val body = if (lines.size > 1) lines[1].trim() else ""

            val headerKey = if (headerLine.startsWith("## ")) {
                headerLine.removePrefix("## ").trim()
            } else {
                // Content without a ## header — use first line as key
                headerLine.take(60)
            }

            val fullText = if (headerLine.startsWith("## ")) {
                "$headerLine\n$body"
            } else {
                trimmed
            }

            // Estimate token count
            val tokenEstimate = fullText.length / CHARS_PER_TOKEN

            if (tokenEstimate <= maxTokens) {
                chunks.add(ChunkInput(headerKey, fullText))
            } else {
                // Sliding window overflow
                val maxChars = maxTokens * CHARS_PER_TOKEN
                val overlapChars = overlapTokens * CHARS_PER_TOKEN
                val headerPrefix = "## $headerKey\n"

                var offset = headerPrefix.length
                while (offset < fullText.length) {
                    val end = minOf(offset + maxChars, fullText.length)
                    val chunkText = if (offset == headerPrefix.length) {
                        fullText.substring(0, end)
                    } else {
                        headerPrefix + fullText.substring(offset, end)
                    }
                    chunks.add(ChunkInput(headerKey, chunkText))
                    if (end >= fullText.length) break
                    offset = end - overlapChars
                }
            }
        }

        return chunks
    }
}
