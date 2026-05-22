package com.noexcs.indolent.agent

import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.common.AgentClipboardStore
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject

class Agent(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val thinkingEnabled: Boolean = true,
    private val reasoningEffort: String = "high",
    val clipboardStore: AgentClipboardStore? = null,
    /** Maximum context window budget in tokens. Defaults to 128K. */
    private val contextBudgetTokens: Int = 128_000,
    /** Maximum streaming retries on IO errors. */
    private val maxRetries: Int = 3,
    /** Maximum conversation rounds per run/execute call. */
    private val maxIterations: Int = 100
) {
    private val client = LLMClient(baseUrl, apiKey)

    // ── Token tracking for calibration ──

    /** Running actual token count from API usage responses (approximate). */
    private var lastActualPromptTokens: Int = 0

    fun run(
        history: MutableList<LLMMessage>,
        message: String,
        systemPrompt: String,
        tools: List<AgentTool> = emptyList()
    ): Flow<AgentEvent> = flow {
        history += LLMMessage(role = "user", content = message)
        val toolMap = tools.associateBy { it.name }

        for (round in 0 until maxIterations) {
            maybeManageContext(history, systemPrompt, tools) { event -> emit(event) }

            val textBuf = StringBuilder()
            val reasoningBuf = StringBuilder()
            val toolAcc = mutableMapOf<Int, ToolCallBuilder>()
            var finishReason: String? = null

            val builtMessages = buildMessages(systemPrompt, history)
            val request = LLMRequest(
                model = model,
                messages = builtMessages,
                stream = true,
                toolDefinitions = toolDefs(tools),
                thinkingEnabled = if (thinkingEnabled) true else null,
                reasoningEffort = if (reasoningEffort.isNotEmpty()) reasoningEffort else null
            )

            var streamError: Exception? = null
            for (attempt in 0 until maxRetries) {
                try {
                    client.stream(request).collect { chunk ->
                        finishReason = processStreamChunk(chunk, textBuf, reasoningBuf, toolAcc) { emit(it) }
                    }
                    streamError = null
                    break
                } catch (e: Exception) {
                    streamError = e
                    if (attempt < maxRetries - 1 && e is java.io.IOException) {
                        emit(AgentEvent.StreamRetry(attempt + 2, e.message ?: "IO error"))
                        textBuf.clear()
                        reasoningBuf.clear()
                        toolAcc.clear()
                        delay((1L shl attempt) * 1000L)
                        continue
                    }
                    break
                }
            }
            if (streamError != null) {
                Lumberjack.e("Agent", "Stream error", streamError)
                emit(AgentEvent.Error(streamError.message ?: "Stream error"))
                return@flow
            }

            val toolCalls = buildToolCalls(toolAcc) { emit(it) }

            history += LLMMessage(
                role = "assistant",
                content = textBuf.toString(),
                toolCalls = toolCalls.ifEmpty { null },
                reasoningContent = reasoningBuf.toString().ifEmpty { null }
            )

            when (finishReason) {
                "stop" -> return@flow
                "content_filter" -> {
                    emit(AgentEvent.Error("Content filtered by safety system"))
                    return@flow
                }
                "insufficient_system_resource" -> {
                    emit(AgentEvent.Error("Insufficient system resources on server"))
                    return@flow
                }
                "tool_calls", null -> {
                    if (toolCalls.isEmpty()) {
                        if (finishReason == "tool_calls") {
                            emit(AgentEvent.Error("Model returned tool_calls finish reason but no tool calls"))
                        }
                        return@flow
                    }
                    processToolCalls(toolCalls, toolMap, history) { emit(it) }
                    structuredTrim(history)
                }
                "length" -> {
                    emit(AgentEvent.Truncated("length"))
                    return@flow
                }
                else -> return@flow
            }
        }
    }

    /**
     * Non-streaming execution — returns the final history.
     * Used for background tasks (scheduled execution, etc.) where
     * streaming UI updates are not needed.
     */
    suspend fun execute(
        history: MutableList<LLMMessage>,
        message: String,
        systemPrompt: String,
        tools: List<AgentTool> = emptyList()
    ): List<LLMMessage> {
        // Store system prompt in history so it survives save/load cycles.
        if (history.isEmpty() || history.first().role != "system") {
            history.add(0, LLMMessage(role = "system", content = systemPrompt))
        }
        history += LLMMessage(role = "user", content = message)
        val toolMap = tools.associateBy { it.name }

        for (round in 0 until maxIterations) {
            val request = LLMRequest(
                model = model,
                messages = buildMessages(systemPrompt, history),
                stream = false,
                toolDefinitions = toolDefs(tools),
                thinkingEnabled = if (thinkingEnabled) true else null,
                reasoningEffort = if (reasoningEffort.isNotEmpty()) reasoningEffort else null
            )

            val response = try {
                client.chat(request)
            } catch (e: Exception) {
                Lumberjack.e("Agent", "API error in execute", e)
                history += LLMMessage(role = "system", content = "Error: ${e.message}")
                return history.toList()
            }

            response.usage?.let { lastActualPromptTokens = it.promptTokens }

            history += LLMMessage(
                role = "assistant",
                content = response.content,
                toolCalls = response.toolCalls,
                reasoningContent = response.reasoningContent
            )

            if (response.toolCalls.isNullOrEmpty()) {
                return history.toList()
            }

            processToolCalls(response.toolCalls, toolMap, history)
            structuredTrim(history)
        }
        history.add(LLMMessage(role = "system", content = "(max iterations reached)"))
        return history.toList()
    }

    // ── Shared tool-call execution ──

    /**
     * Executes tool calls in parallel and appends results to [history].
     * The optional [emit] callback drives streaming UI; when null (execute()
     * path) only history is mutated.
     */
    private suspend fun processToolCalls(
        toolCalls: List<ToolCall>,
        toolMap: Map<String, AgentTool>,
        history: MutableList<LLMMessage>,
        emit: (suspend (AgentEvent) -> Unit)? = null
    ) {
        val toolArgs = toolCalls.map { tc -> tc to parseArgs(tc.function.arguments) }
        val interpolated = toolArgs.map { (tc, args) -> tc to interpolateClipboard(args) }

        if (emit != null) {
            for ((tc, args) in interpolated) {
                emit(AgentEvent.ToolCallBegin(tc.id, tc.function.name, args))
            }
        }

        val results = coroutineScope {
            interpolated.map { (tc, args) ->
                async {
                    val tool = toolMap[tc.function.name]
                    val result = try {
                        tool?.execute(args) ?: "Tool '${tc.function.name}' not found"
                    } catch (e: Exception) {
                        Lumberjack.e("Agent", "Tool '${tc.function.name}' failed", e)
                        "Error: ${e.message}"
                    }
                    Triple(tc, args, result)
                }
            }.awaitAll()
        }

        for ((tc, args, result) in results) {
            history += LLMMessage(role = "tool", content = result, toolCallId = tc.id)
            emit?.invoke(AgentEvent.ToolResult(tc.id, tc.function.name, args, result))

            if (tc.function.name == "agent_clipboard") {
                val paste = clipboardStore?.consumePendingPasteContent()
                if (paste != null) {
                    history += LLMMessage(role = "assistant", content = paste.second)
                    emit?.invoke(AgentEvent.PasteContent(paste.second))
                }
            }
        }
    }

    // ── Streaming helpers ──

    /**
     * Processes a single streaming SSE chunk: text, reasoning, tool-call
     * deltas, finish reason, and usage stats. Returns finish reason if
     * present in this chunk, null otherwise.
     */
    private suspend fun processStreamChunk(
        chunk: String,
        textBuf: StringBuilder,
        reasoningBuf: StringBuilder,
        toolAcc: MutableMap<Int, ToolCallBuilder>,
        emit: suspend (AgentEvent) -> Unit
    ): String? {
        val json = JSONObject(chunk)
        val choice = json.getJSONArray("choices").optJSONObject(0) ?: return null
        val delta = choice.optJSONObject("delta") ?: return null

        // Text tokens
        val token = if (delta.has("content") && !delta.isNull("content"))
            delta.optString("content", "") else null
        if (!token.isNullOrEmpty()) {
            textBuf.append(token)
            emit(AgentEvent.Text(token))
        }

        // Reasoning / thinking tokens (DeepSeek, etc.)
        val reasoning = if (delta.has("reasoning_content") && !delta.isNull("reasoning_content"))
            delta.optString("reasoning_content", "") else null
        if (!reasoning.isNullOrEmpty()) {
            reasoningBuf.append(reasoning)
            emit(AgentEvent.Reasoning(reasoning))
        }

        // Tool-call deltas
        accumulateToolCallDeltas(delta, toolAcc, emit)

        // Usage stats (final chunk with stream_options.include_usage)
        json.optJSONObject("usage")?.let { u ->
            lastActualPromptTokens = u.optInt("prompt_tokens", 0)
            emit(AgentEvent.Usage(
                promptTokens = lastActualPromptTokens,
                completionTokens = u.optInt("completion_tokens", 0),
                totalTokens = u.optInt("total_tokens", 0)
            ))
        }

        return if (choice.has("finish_reason") && !choice.isNull("finish_reason"))
            choice.optString("finish_reason") else null
    }

    private suspend fun accumulateToolCallDeltas(
        delta: JSONObject,
        toolAcc: MutableMap<Int, ToolCallBuilder>,
        emit: suspend (AgentEvent) -> Unit
    ) {
        delta.optJSONArray("tool_calls")?.let { tcs ->
            for (i in 0 until tcs.length()) {
                val tc = tcs.getJSONObject(i)
                val idx = tc.getInt("index")
                val acc = toolAcc.getOrPut(idx) { ToolCallBuilder() }

                if (tc.has("id")) {
                    acc.id = tc.optString("id")
                    if (acc.fnName != null && !acc.nameEmitted) {
                        acc.nameEmitted = true
                        emit(AgentEvent.ToolCallStart(acc.id!!, acc.fnName!!))
                    }
                }
                if (tc.has("type")) acc.type = tc.optString("type")

                tc.optJSONObject("function")?.let { fn ->
                    if (fn.has("name")) {
                        acc.fnName = fn.optString("name")
                        if (!acc.nameEmitted && acc.id != null) {
                            acc.nameEmitted = true
                            emit(AgentEvent.ToolCallStart(acc.id!!, acc.fnName!!))
                        }
                    }
                    if (fn.has("arguments")) {
                        val argDelta = fn.optString("arguments")
                        acc.fnArgs = (acc.fnArgs ?: "") + argDelta
                        emit(AgentEvent.ToolCallDelta(
                            acc.id ?: "call_$idx",
                            acc.fnName ?: "unknown",
                            argDelta
                        ))
                    }
                }
            }
        }
    }

    /** Builds sorted tool calls from accumulated deltas and emits deferred ToolCallStart events. */
    private suspend fun buildToolCalls(
        toolAcc: Map<Int, ToolCallBuilder>,
        emit: suspend (AgentEvent) -> Unit
    ): List<ToolCall> {
        val toolCalls = toolAcc.entries
            .sortedBy { it.key }
            .mapNotNull { (_, b) -> b.build() }

        for (tc in toolCalls) {
            val acc = toolAcc.values.firstOrNull { it.id == tc.id }
            if (acc != null && !acc.nameEmitted) {
                acc.nameEmitted = true
                emit(AgentEvent.ToolCallStart(tc.id, tc.function.name))
            }
        }
        return toolCalls
    }

    // ── helpers ──

    private fun buildMessages(systemPrompt: String, history: List<LLMMessage>): List<LLMMessage> {
        val hasSystemInHistory = history.firstOrNull()?.role == "system"
        return ArrayList<LLMMessage>(history.size + (if (hasSystemInHistory) 0 else 1)).apply {
            if (!hasSystemInHistory) {
                add(LLMMessage(role = "system", content = systemPrompt))
            }
            addAll(history)
        }
    }

    private fun toolDefs(tools: List<AgentTool>): List<ToolDefinition>? {
        if (tools.isEmpty()) return null
        return tools.map { tool ->
            ToolDefinition(
                name = tool.name,
                description = tool.description,
                parameters = tool.parameters
            )
        }
    }

    private fun parseArgs(json: String): Map<String, Any?> {
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { key ->
                val v = obj.get(key)
                if (v === JSONObject.NULL) null else v
            }
        } catch (e: Exception) {
            Lumberjack.e("Agent", "Failed to parse tool arguments JSON", e)
            emptyMap()
        }
    }

    private val clipboardPlaceholder = Regex("""\{\{agent_clipboard(?::(\w+))?\}\}""")

    private fun interpolateClipboard(args: Map<String, Any?>): Map<String, Any?> {
        val store = clipboardStore ?: return args
        return args.mapValues { (_, value) ->
            if (value is String && value.contains("{{agent_clipboard")) {
                clipboardPlaceholder.replace(value) { match ->
                    val slot = match.groupValues.getOrNull(1)?.ifBlank { null }
                        ?: AgentClipboardStore.DEFAULT_SLOT
                    store.read(slot) ?: match.value
                }
            } else {
                value
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Context budget management
    // ═══════════════════════════════════════════════════════════════

    /** Fraction of context budget at which we emit a warning. */
    private val warningThreshold = 0.75

    /** Fraction of context budget at which we trigger summarization. */
    private val summarizeThreshold = 0.85

    private suspend fun maybeManageContext(
        history: MutableList<LLMMessage>,
        systemPrompt: String,
        tools: List<AgentTool>,
        emit: suspend (AgentEvent) -> Unit
    ) {
        val estimated = estimateTokens(history) + estimateSystemTokens(systemPrompt)
        val budget = contextBudgetTokens.toLong()

        if (estimated > budget * warningThreshold) {
            emit(
                AgentEvent.ContextWarning(
                    estimatedTokens = estimated,
                    budgetTokens = contextBudgetTokens,
                    message = "Context at ${(estimated * 100 / budget)}% of budget"
                )
            )
        }

        if (estimated > budget * summarizeThreshold) {
            val messagesBefore = history.size
            summarizeHistory(history, systemPrompt)
            val messagesAfter = history.size
            if (messagesBefore != messagesAfter) {
                Lumberjack.i("Agent", "Summarized history: $messagesBefore → $messagesAfter messages")
                emit(
                    AgentEvent.ContextSummarized(
                        messagesBefore = messagesBefore,
                        messagesAfter = messagesAfter,
                        summaryLength = history.firstOrNull { it.content.startsWith("<conversation_summary>") }?.content?.length ?: 0
                    )
                )
            }
        }

        if (estimateTokens(history) + estimateSystemTokens(systemPrompt) > budget) {
            structuredTrim(history)
        }
    }

    /**
     * Summarizes older conversation turns into a compressed system message
     * and replaces them in [history]. Preserves the most recent turns intact.
     */
    private suspend fun summarizeHistory(
        history: MutableList<LLMMessage>,
        systemPrompt: String
    ) {
        if (history.size < 6) return

        val startIdx = history.indexOfFirst { it.role == "user" }
        if (startIdx < 0) return

        val budget = contextBudgetTokens.toLong()
        val keepBudget = (budget * 0.35).toLong()
        var keepCount = 0
        var keepTokens = 0L
        for (i in history.size - 1 downTo startIdx) {
            keepTokens += estimateMessageTokens(history[i])
            keepCount++
            if (keepTokens >= keepBudget) break
        }
        val keepFromIndex = history.size - keepCount

        val toSummarize = history.subList(startIdx, keepFromIndex).toList()
        if (toSummarize.isEmpty() || toSummarize.size < 3) return

        Lumberjack.i("Agent", "Summarizing ${toSummarize.size} turns, keeping $keepCount recent messages")

        val summaryMsg = ContextSummarizer.summarize(client, model, toSummarize)
        if (summaryMsg == null) {
            Lumberjack.w("Agent", "Summarization returned null, falling back to trim")
            return
        }

        val systemCount = if (history.firstOrNull()?.role == "system") 1 else 0
        val removeCount = keepFromIndex - systemCount
        repeat(removeCount) { history.removeAt(systemCount) }
        history.add(systemCount, summaryMsg)
    }

    /**
     * Structured trimming: removes the oldest complete conversation turns
     * (user → assistant → tool results) while preserving:
     * - The first system message (if present)
     * - Any existing summary message
     * - The most recent turns
     */
    private fun structuredTrim(history: MutableList<LLMMessage>) {
        val estimated = estimateTokens(history)
        if (estimated <= contextBudgetTokens * 0.9) return

        var firstRemovableIdx = 0
        if (history.firstOrNull()?.role == "system") {
            firstRemovableIdx = 1
            if (history.size > 1 && history[1].role == "system" &&
                history[1].content.startsWith("<conversation_summary>")
            ) {
                firstRemovableIdx = 2
            }
        }

        val turnStarts = history.indices
            .filter { i -> i >= firstRemovableIdx && history[i].role == "user" }
            .toMutableList()

        if (turnStarts.size <= 1) {
            while (estimateTokens(history) > contextBudgetTokens * 0.9 &&
                   history.size > firstRemovableIdx + 2) {
                history.removeAt(firstRemovableIdx)
            }
            return
        }

        while (estimateTokens(history) > contextBudgetTokens * 0.9 &&
               turnStarts.size > 1 &&
               history.size > firstRemovableIdx + 4) {
            val nextTurnStart = turnStarts.getOrElse(1) {
                history.removeAt(firstRemovableIdx)
                history.size
            }
            val removeCount = nextTurnStart - firstRemovableIdx
            repeat(removeCount) { history.removeAt(firstRemovableIdx) }

            turnStarts.removeAt(0)
            for (i in turnStarts.indices) {
                turnStarts[i] -= removeCount
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Token estimation (role-aware)
    // ═══════════════════════════════════════════════════════════════

    private fun estimateTokens(messages: List<LLMMessage>): Long {
        return messages.fold(0L) { acc, msg -> acc + estimateMessageTokens(msg) }
    }

    private fun estimateMessageTokens(msg: LLMMessage): Long {
        var tokens = (msg.content.length / charsPerTokenForRole(msg.role)).toLong()

        msg.toolCalls?.forEach { tc ->
            tokens += (tc.function.name.length + tc.function.arguments.length) / 3L
        }

        if (!msg.reasoningContent.isNullOrBlank()) {
            tokens += msg.reasoningContent.length / 3L
        }

        if (msg.toolCallId != null) {
            tokens += msg.toolCallId.length / 3L
        }

        tokens += 4 // message framing overhead
        return tokens
    }

    private fun charsPerTokenForRole(role: String): Double {
        return when (role) {
            "system" -> 2.5
            "user" -> 3.0
            "assistant" -> 3.0
            "tool" -> 3.0
            else -> 3.0
        }
    }

    private fun estimateSystemTokens(systemPrompt: String): Long {
        return systemPrompt.length / 2.5.toLong()
    }

    // ── internal accumulator for streaming tool calls ──

    private class ToolCallBuilder {
        var id: String? = null
        var type: String? = null
        var fnName: String? = null
        var fnArgs: String? = null
        var nameEmitted = false

        fun build(): ToolCall? {
            val name = fnName ?: return null
            return ToolCall(
                id = id ?: "",
                type = type ?: "function",
                function = ToolFunction(name = name, arguments = fnArgs ?: "{}")
            )
        }
    }
}
