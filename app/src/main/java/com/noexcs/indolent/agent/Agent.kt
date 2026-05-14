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
    private val contextBudgetTokens: Int = 128_000
) {
    private val client = LLMClient(baseUrl, apiKey)

    // ── Token tracking for calibration ──

    /** Running actual token count from API usage responses (approximate). */
    private var lastActualPromptTokens: Int = 0

    fun run(
        history: MutableList<LLMMessage>,
        message: String,
        systemPrompt: String,
        tools: List<AgentTool> = emptyList(),
        maxIterations: Int = 1000
    ): Flow<AgentEvent> = flow {
        history += LLMMessage(role = "user", content = message)
        val toolMap = tools.associateBy { it.name }

        for (round in 0 until maxIterations) {
            // ── manage context budget before this round ──
            maybeManageContext(history, systemPrompt, tools, emit)

            val textBuf = StringBuilder()
            val reasoningBuf = StringBuilder()
            val toolAcc = mutableMapOf<Int, ToolCallBuilder>()
            var finishReason: String? = null

            // ── stream from LLM ──
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
            for (attempt in 0..2) {
                try {
                    client.stream(request).collect { chunk ->
                    val json = JSONObject(chunk)
                    val choice = json.getJSONArray("choices").optJSONObject(0) ?: return@collect
                    val delta = choice.optJSONObject("delta") ?: return@collect

                    // text tokens
                    val token = if (delta.has("content") && !delta.isNull("content"))
                        delta.optString("content", "") else null
                    if (!token.isNullOrEmpty()) {
                        textBuf.append(token)
                        emit(AgentEvent.Text(token))
                    }

                    // reasoning / thinking tokens (DeepSeek, etc.)
                    val reasoning =
                        if (delta.has("reasoning_content") && !delta.isNull("reasoning_content"))
                            delta.optString("reasoning_content", "") else null
                    if (!reasoning.isNullOrEmpty()) {
                        reasoningBuf.append(reasoning)
                        emit(AgentEvent.Reasoning(reasoning))
                    }

                    // tool call deltas (accumulate across chunks by index)
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
                                    val delta = fn.optString("arguments")
                                    acc.fnArgs = (acc.fnArgs ?: "") + delta
                                    emit(
                                        AgentEvent.ToolCallDelta(
                                            acc.id ?: "call_$idx",
                                            acc.fnName ?: "unknown",
                                            delta
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // finish reason signals the end of this round
                    if (choice.has("finish_reason") && !choice.isNull("finish_reason")) {
                        finishReason = choice.optString("finish_reason")
                    }

                    // usage stats (sent in final chunk when stream_options.include_usage is set)
                    json.optJSONObject("usage")?.let { u ->
                        lastActualPromptTokens = u.optInt("prompt_tokens", 0)
                        emit(AgentEvent.Usage(
                            promptTokens = lastActualPromptTokens,
                            completionTokens = u.optInt("completion_tokens", 0),
                            totalTokens = u.optInt("total_tokens", 0)
                        ))
                    }
                    }
                    streamError = null
                    break
                } catch (e: Exception) {
                    streamError = e
                    if (attempt < 2 && e is java.io.IOException) {
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

            // ── build the complete tool calls ──
            val toolCalls = toolAcc.entries
                .sortedBy { it.key }
                .mapNotNull { (_, b) -> b.build() }

            // Emit any deferred ToolCallStart events (ID arrived late or not at all)
            for (tc in toolCalls) {
                val acc = toolAcc.values.firstOrNull { it.id == tc.id }
                if (acc != null && !acc.nameEmitted) {
                    acc.nameEmitted = true
                    emit(AgentEvent.ToolCallStart(tc.id, tc.function.name))
                }
            }

            // add assistant message to history
            history += LLMMessage(
                role = "assistant",
                content = textBuf.toString(),
                toolCalls = toolCalls.ifEmpty { null },
                reasoningContent = reasoningBuf.toString().ifEmpty { null }
            )

            // ── decide next action ──
            when (finishReason) {
                "stop", null -> return@flow
                "content_filter" -> {
                    emit(AgentEvent.Error("Content filtered by safety system"))
                    return@flow
                }

                "insufficient_system_resource" -> {
                    emit(AgentEvent.Error("Insufficient system resources on server"))
                    return@flow
                }

                "tool_calls" -> {
                    if (toolCalls.isEmpty()) {
                        emit(AgentEvent.Error("Model returned tool_calls finish reason but no tool calls"))
                        return@flow
                    }

                    // Emit ToolCallBegin for all calls before parallel execution
                    val toolArgs = toolCalls.map { tc ->
                        tc to parseArgs(tc.function.arguments)
                    }
                    val interpolatedToolArgs = toolArgs.map { (tc, args) ->
                        tc to interpolateClipboard(args)
                    }
                    for ((tc, args) in interpolatedToolArgs) {
                        emit(AgentEvent.ToolCallBegin(tc.id, tc.function.name, args))
                    }

                    val results = executeToolsInParallel(interpolatedToolArgs, toolMap)

                    for ((tc, args, result) in results) {
                        history += LLMMessage(
                            role = "tool",
                            content = result,
                            toolCallId = tc.id
                        )
                        emit(AgentEvent.ToolResult(tc.id, tc.function.name, args, result))

                        if (tc.function.name == "agent_clipboard") {
                            val paste = clipboardStore?.consumePendingPasteContent()
                            if (paste != null) {
                                history += LLMMessage(
                                    role = "assistant",
                                    content = paste.second,
                                )
                                emit(AgentEvent.PasteContent(paste.second))
                            }
                        }
                    }
                    structuredTrim(history)
                    // loop continues → LLM sees tool results
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
     * Non-streaming execution — returns the final text result.
     * Used for background tasks (scheduled execution, etc.) where
     * streaming UI updates are not needed.
     */
    suspend fun execute(
        history: MutableList<LLMMessage>,
        message: String,
        systemPrompt: String,
        tools: List<AgentTool> = emptyList(),
        maxIterations: Int = 100
    ): List<LLMMessage> {
        history += LLMMessage(role = "system", content = systemPrompt)
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
                history += LLMMessage(
                    role = "system",
                    content = "Error: ${e.message}"
                )
                return history.toList()
            }

            // Track actual usage for calibration
            response.usage?.let { lastActualPromptTokens = it.promptTokens }

            val content = response.content
            val toolCalls = response.toolCalls

            history += LLMMessage(
                role = "assistant",
                content = content,
                toolCalls = toolCalls,
                reasoningContent = response.reasoningContent
            )

            if (toolCalls.isNullOrEmpty()) {
                return history.toList()
            }

            val toolArgs = toolCalls.map { tc ->
                tc to parseArgs(tc.function.arguments)
            }
            val interpolatedToolArgs = toolArgs.map { (tc, args) ->
                tc to interpolateClipboard(args)
            }
            val results = executeToolsInParallel(interpolatedToolArgs, toolMap, " in execute")
            for ((tc, _, result) in results) {
                history += LLMMessage(
                    role = "tool",
                    content = result,
                    toolCallId = tc.id
                )

                if (tc.function.name == "agent_clipboard") {
                    val paste = clipboardStore?.consumePendingPasteContent()
                    if (paste != null) {
                        history += LLMMessage(
                            role = "assistant",
                            content = paste.second,
                        )
                    }
                }
            }
            structuredTrim(history)
        }
        history.add(LLMMessage(role = "system", content = "(max iterations reached)"))
        return history.toList()
    }

    // ── helpers ──

    private fun buildMessages(systemPrompt: String, history: List<LLMMessage>): List<LLMMessage> {
        // If history already starts with a system message (persisted from execute()),
        // don't duplicate it — use the one in history as the system prompt for the API
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

    private suspend fun executeToolsInParallel(
        toolArgs: List<Pair<ToolCall, Map<String, Any?>>>,
        toolMap: Map<String, AgentTool>,
        logSuffix: String = ""
    ): List<Triple<ToolCall, Map<String, Any?>, String>> {
        return coroutineScope {
            toolArgs.map { (tc, args) ->
                async {
                    val tool = toolMap[tc.function.name]
                    val result = try {
                        tool?.execute(args) ?: "Tool '${tc.function.name}' not found"
                    } catch (e: Exception) {
                        Lumberjack.e("Agent", "Tool '${tc.function.name}' failed$logSuffix", e)
                        "Error: ${e.message}"
                    }
                    Triple(tc, args, result)
                }
            }.awaitAll()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Context budget management
    // ═══════════════════════════════════════════════════════════════

    /** Fraction of context budget at which we emit a warning. */
    private val warningThreshold = 0.75

    /** Fraction of context budget at which we trigger summarization. */
    private val summarizeThreshold = 0.85

    /**
     * Checks whether the current history + system prompt fits within the
     * context budget, and manages it if not: emit warnings, summarize old
     * turns, or trim as a last resort.
     */
    private suspend fun maybeManageContext(
        history: MutableList<LLMMessage>,
        systemPrompt: String,
        tools: List<AgentTool>,
        emit: suspend (AgentEvent) -> Unit
    ) {
        val estimated = estimateTokens(history) + estimateSystemTokens(systemPrompt)
        val budget = contextBudgetTokens.toLong()

        // Emit warning when approaching the limit
        if (estimated > budget * warningThreshold) {
            emit(
                AgentEvent.ContextWarning(
                    estimatedTokens = estimated,
                    budgetTokens = contextBudgetTokens,
                    message = "Context at ${(estimated * 100 / budget)}% of budget"
                )
            )
        }

        // Trigger summarization when exceeding the threshold
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

        // If still over budget (unlikely after summarization), trim
        if (estimateTokens(history) + estimateSystemTokens(systemPrompt) > budget) {
            structuredTrim(history)
        }
    }

    /**
     * Summarizes older conversation turns into a compressed system message
     * and replaces them in [history]. Preserves the most recent turns intact.
     *
     * Strategy:
     * 1. Find the system message at the front (keep it)
     * 2. Take the next N turns that collectively exceed half the budget
     * 3. Ask the LLM to summarize those turns
     * 4. Replace them with the summary
     */
    private suspend fun summarizeHistory(
        history: MutableList<LLMMessage>,
        systemPrompt: String
    ) {
        // Need at least a few turns to make summarization worthwhile
        if (history.size < 6) return

        // Find the first user message — everything from here onward is conversation
        val startIdx = history.indexOfFirst { it.role == "user" }
        if (startIdx < 0) return

        // Calculate how many messages to keep (the most recent ~35% of budget)
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

        // Messages to summarize: from startIdx to keepFromIndex (exclusive)
        val toSummarize = history.subList(startIdx, keepFromIndex).toList()
        if (toSummarize.isEmpty()) return

        // Don't bother summarizing very short histories
        if (toSummarize.size < 3) return

        Lumberjack.i("Agent", "Summarizing ${toSummarize.size} turns, keeping $keepCount recent messages")

        val summaryMsg = ContextSummarizer.summarize(client, model, toSummarize)
        if (summaryMsg == null) {
            Lumberjack.w("Agent", "Summarization returned null, falling back to trim")
            return
        }

        // Remove old messages and insert summary after the system message (if any)
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
     *
     * A "turn" starts with a user message and includes all assistant and tool
     * messages until the next user message (or end of history).
     */
    private fun structuredTrim(history: MutableList<LLMMessage>) {
        val estimated = estimateTokens(history)
        if (estimated <= contextBudgetTokens * 0.9) return  // still comfortable

        // Keep the first system message and summary if present
        var firstRemovableIdx = 0
        if (history.firstOrNull()?.role == "system") {
            firstRemovableIdx = 1
            // Also keep a conversation summary if it follows the system message
            if (history.size > 1 && history[1].role == "system" &&
                history[1].content.startsWith("<conversation_summary>")
            ) {
                firstRemovableIdx = 2
            }
        }

        // Identify turn boundaries (positions of user messages)
        val turnStarts = history.indices
            .filter { i -> i >= firstRemovableIdx && history[i].role == "user" }
            .toMutableList()

        if (turnStarts.size <= 1) {
            // Only one turn — can't trim by turns, fall back to simple removal
            while (estimateTokens(history) > contextBudgetTokens * 0.9 &&
                   history.size > firstRemovableIdx + 2) {
                history.removeAt(firstRemovableIdx)
            }
            return
        }

        // Remove the oldest complete turn(s) until we're within budget
        while (estimateTokens(history) > contextBudgetTokens * 0.9 &&
               turnStarts.size > 1 &&
               history.size > firstRemovableIdx + 4) {
            // Remove from firstRemovableIdx to the start of the next user message
            val nextTurnStart = turnStarts.getOrElse(1) {
                // Fallback: just remove one message
                history.removeAt(firstRemovableIdx)
                history.size
            }
            val removeCount = nextTurnStart - firstRemovableIdx
            repeat(removeCount) { history.removeAt(firstRemovableIdx) }

            // Update turnStarts after removal
            turnStarts.removeAt(0)
            for (i in turnStarts.indices) {
                turnStarts[i] -= removeCount
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Token estimation (role-aware)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Estimates total tokens across all messages in [history].
     * Uses character/3 as a baseline with role-aware adjustments.
     * When actual usage data is available from the API, it serves as
     * a calibration point.
     */
    private fun estimateTokens(messages: List<LLMMessage>): Long {
        return messages.fold(0L) { acc, msg -> acc + estimateMessageTokens(msg) }
    }

    /**
     * Estimates tokens for a single message using role-aware ratios.
     */
    private fun estimateMessageTokens(msg: LLMMessage): Long {
        // Character count for the main content
        var tokens = msg.content.length / charsPerTokenForRole(msg.role)

        // Tool call definitions (function name + arguments JSON)
        msg.toolCalls?.forEach { tc ->
            tokens += (tc.function.name.length + tc.function.arguments.length) / 3L
        }

        // Reasoning content (if preserved)
        if (!msg.reasoningContent.isNullOrBlank()) {
            tokens += msg.reasoningContent.length / 3L
        }

        // Tool call ID overhead
        if (msg.toolCallId != null) {
            tokens += msg.toolCallId.length / 3L
        }

        // Message framing overhead (role, structure) ~4 tokens per message
        tokens += 4

        return tokens
    }

    /**
     * Returns the characters-per-token divisor for a given role.
     * System messages tend to be denser (more structure/formatting).
     */
    private fun charsPerTokenForRole(role: String): Double {
        return when (role) {
            "system" -> 2.5   // denser: markdown formatting, structure
            "user" -> 3.0     // natural language
            "assistant" -> 3.0
            "tool" -> 3.0
            else -> 3.0
        }
    }

    /**
     * Estimates the token cost of the system prompt string.
     */
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
