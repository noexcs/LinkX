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
    val clipboardStore: AgentClipboardStore? = null
) {
    private val client = LLMClient(baseUrl, apiKey)

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
            val textBuf = StringBuilder()
            val reasoningBuf = StringBuilder()
            val toolAcc = mutableMapOf<Int, ToolCallBuilder>()
            var finishReason: String? = null

            // ── stream from LLM ──
            val request = LLMRequest(
                model = model,
                messages = buildMessages(systemPrompt, history),
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
                        emit(AgentEvent.Usage(
                            promptTokens = u.optInt("prompt_tokens", 0),
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
                    trimHistory(history)
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
        maxIterations: Int = 100,
        completeProcess: Boolean = false
    ): String {
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
                return if (completeProcess)
                    history2String(history)
                else
                    "Error: ${e.message}"
            }

            val content = response.content
            val toolCalls = response.toolCalls

            history += LLMMessage(
                role = "assistant",
                content = content,
                toolCalls = toolCalls,
                reasoningContent = response.reasoningContent
            )

            if (toolCalls.isNullOrEmpty()) {
                return if (completeProcess)
                    history2String(history)
                else
                    content
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
            trimHistory(history)
        }
        history.add(LLMMessage(role = "system", content = "(max iterations reached)"))

        return if (completeProcess) history2String(history) else history.last().content
    }

    // ── helpers ──

    private fun history2String(history: List<LLMMessage>): String {
        return history.joinToString("\n") {
            if (it.role == "tool")
                "\n```\n${it.content}\n```\n"
            else
                it.content
        }
    }

    private fun buildMessages(systemPrompt: String, history: List<LLMMessage>): List<LLMMessage> {
        return ArrayList<LLMMessage>(history.size + 1).apply {
            add(LLMMessage(role = "system", content = systemPrompt))
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

    private fun trimHistory(history: MutableList<LLMMessage>, maxTokens: Int = 100_000) {
        while (estimateTokens(history) > maxTokens && history.size > 4) {
            history.removeAt(0)
        }
    }

    private fun estimateTokens(messages: List<LLMMessage>): Long {
        return messages.fold(0L) { acc, msg ->
            var n = acc + msg.content.length / 3L
            msg.toolCalls?.forEach { tc ->
                n += (tc.function.name.length + tc.function.arguments.length).toLong() / 3L
            }
            n
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
