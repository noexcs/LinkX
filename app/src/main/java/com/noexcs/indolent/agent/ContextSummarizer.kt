package com.noexcs.indolent.agent

import com.noexcs.indolent.logging.Lumberjack

/**
 * Summarizes earlier conversation turns into a compressed system message
 * so that context budget is preserved for recent interactions.
 *
 * Uses a lightweight non-streaming LLM call with a dedicated summarization prompt.
 */
object ContextSummarizer {

    /** Max tokens to allocate for the summary itself (the output). */
    private const val MAX_SUMMARY_TOKENS = 1024

    /** We summarize at most this many messages per batch. */
    private const val MAX_MESSAGES_PER_SUMMARY = 40

    private val summarySystemPrompt = """
        You are a conversation summarizer. Your task is to compress the following conversation
        turns into a concise but complete summary. Preserve:

        - The user's original request / goal
        - Key decisions made
        - Important facts, data, or code snippets referenced
        - Actions taken (tool calls and their outcomes), grouped logically
        - Any constraints or preferences the user expressed
        - Errors encountered and how they were resolved

        Format the summary as bullet points under clear section headings.
        Do NOT include meta-commentary like "The assistant did X" — state facts directly.
        Keep the summary under 800 words.

        Output ONLY the summary text, no preamble.
    """.trimIndent()

    /**
     * Summarizes [messages] into a single compressed [LLMMessage] with role="system".
     *
     * @param client the LLM client to use for the summarization call
     * @param model the model name to use for summarization
     * @param messages the conversation turns to summarize
     * @return a system message containing the summary, or null if summarization fails
     */
    suspend fun summarize(
        client: LLMClient,
        model: String,
        messages: List<LLMMessage>
    ): LLMMessage? {
        if (messages.isEmpty()) return null

        // Build a compact transcript of the turns to summarize
        val transcript = buildTranscript(messages)

        val request = LLMRequest(
            model = model,
            messages = listOf(
                LLMMessage(role = "system", content = summarySystemPrompt),
                LLMMessage(role = "user", content = transcript)
            ),
            stream = false,
            temperature = 0.3,
            maxTokens = MAX_SUMMARY_TOKENS,
            toolDefinitions = null
        )

        return try {
            val response = client.chat(request)
            val summary = response.content.trim()
            if (summary.isBlank()) {
                Lumberjack.w("ContextSummarizer", "Summarization returned empty content")
                null
            } else {
                Lumberjack.i("ContextSummarizer", "Summarized ${messages.size} messages into ${summary.length} chars")
                LLMMessage(
                    role = "system",
                    content = "<conversation_summary>\n$summary\n</conversation_summary>"
                )
            }
        } catch (e: Exception) {
            Lumberjack.e("ContextSummarizer", "Summarization failed", e)
            null
        }
    }

    /**
     * Builds a compact transcript string from messages for the summarizer input.
     */
    private fun buildTranscript(messages: List<LLMMessage>): String {
        val sb = StringBuilder()
        for (msg in messages.take(MAX_MESSAGES_PER_SUMMARY)) {
            when (msg.role) {
                "user" -> {
                    sb.appendLine("User: ${msg.content.take(2000)}")
                }
                "assistant" -> {
                    if (msg.content.isNotBlank()) {
                        sb.appendLine("Assistant: ${msg.content.take(2000)}")
                    }
                    msg.toolCalls?.forEach { tc ->
                        sb.appendLine("  → Tool call: ${tc.function.name}(${tc.function.arguments.take(500)})")
                    }
                }
                "tool" -> {
                    sb.appendLine("  → Result (${msg.toolCallId ?: "unknown"}): ${msg.content.take(1500)}")
                }
                "system" -> {
                    // Skip system messages in transcript — they're structural, not conversational
                    if (msg.content.startsWith("<conversation_summary>")) {
                        sb.appendLine(msg.content)
                    }
                }
            }
        }
        if (messages.size > MAX_MESSAGES_PER_SUMMARY) {
            sb.appendLine("… (${messages.size - MAX_MESSAGES_PER_SUMMARY} more messages omitted)")
        }
        return sb.toString()
    }
}
