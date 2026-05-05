package com.noexcs.indolent.agent

import com.noexcs.indolent.agent.tools.ToolParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LLMClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Non-streaming completion. */
    suspend fun chat(request: LLMRequest): LLMResponse = withContext(Dispatchers.IO) {
        val body = buildBody(request.copy(stream = false))
        val httpRequest = buildRequest(body)
        val response = http.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: "no body"
            throw IOException("HTTP ${response.code}: ${response.message} | body: $errBody")
        }
        parseResponse(response.body?.string() ?: "")
    }

    /**
     * Streaming SSE completion.
     * Each emitted [String] is the raw JSON payload from a `data:` line
     * (the "[DONE]" sentinel is filtered out).
     */
    fun stream(request: LLMRequest): Flow<String> = flow {
        val body = buildBody(request.copy(stream = true))
        val httpRequest = buildRequest(body)
        val response = http.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: "no body"
            throw IOException("HTTP ${response.code}: ${response.message} | body: $errBody")
        }
        val source = response.body?.source() ?: throw IOException("Empty response body")
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data: ")) continue
            val data = line.removePrefix("data: ")
            if (data == "[DONE]") break
            emit(data)
        }
    }.flowOn(Dispatchers.IO)

    // ── request body construction ──

    private fun buildBody(request: LLMRequest): String {
        val toolsJson = buildToolDefinitions(request)
        return JSONObject().apply {
            put("model", request.model)
            put("messages", JSONArray().apply {
                request.messages.forEach { msg -> put(buildMessageJson(msg)) }
            })
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            put("stream", request.stream)
            if (request.stream) {
                put("stream_options", JSONObject().put("include_usage", true))
            }
            if (toolsJson != null) put("tools", toolsJson)
            request.toolChoice?.let { put("tool_choice", buildToolChoice(it)) }
            request.thinkingEnabled?.let { enabled ->
                put("thinking", JSONObject().put("type", if (enabled) "enabled" else "disabled"))
            }
            if (!request.reasoningEffort.isNullOrEmpty()) put("reasoning_effort", request.reasoningEffort)
            if (!request.responseFormat.isNullOrEmpty()) {
                put("response_format", JSONObject().put("type", request.responseFormat))
            }
            request.topP?.let { put("top_p", it) }
            request.frequencyPenalty?.let { put("frequency_penalty", it) }
            request.presencePenalty?.let { put("presence_penalty", it) }
        }.toString()
    }

    private fun buildToolChoice(choice: ToolChoice): Any {
        return when (choice) {
            is ToolChoice.Auto -> "auto"
            is ToolChoice.None -> "none"
            is ToolChoice.Required -> "required"
            is ToolChoice.Named -> JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().put("name", choice.name))
            }
        }
    }

    private fun buildMessageJson(msg: LLMMessage) = JSONObject().apply {
        put("role", msg.role)
        put("content", msg.content)
        if (!msg.toolCalls.isNullOrEmpty()) {
            put("tool_calls", JSONArray().apply {
                msg.toolCalls.forEach { tc ->
                    put(JSONObject().apply {
                        put("id", tc.id)
                        put("type", tc.type)
                        put("function", JSONObject().apply {
                            put("name", tc.function.name)
                            put("arguments", tc.function.arguments)
                        })
                    })
                }
            })
        }
        if (msg.toolCallId != null) {
            put("tool_call_id", msg.toolCallId)
        }
        if (!msg.reasoningContent.isNullOrEmpty()) {
            put("reasoning_content", msg.reasoningContent)
        }
    }

    private fun buildToolDefinitions(request: LLMRequest): JSONArray? {
        val defs = request.toolDefinitions ?: return null
        return JSONArray().apply {
            defs.forEach { def ->
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", def.name)
                        put("description", def.description)
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject().apply {
                                def.parameters.forEach { p ->
                                    put(p.name, JSONObject().apply {
                                        val itemType = extractArrayItemType(p.type)
                                        if (itemType != null) {
                                            put("type", "array")
                                            put("items", JSONObject().apply {
                                                put("type", itemType)
                                            })
                                        } else {
                                            put("type", p.type)
                                        }
                                        put("description", p.description)
                                    })
                                }
                            })
                            put("required", JSONArray().apply {
                                def.parameters.filter { it.required }.forEach { put(it.name) }
                            })
                        })
                    })
                })
            }
        }
    }

    /**
     * If [type] is "array of <something>", returns the item type.
     * Otherwise returns null. Supports: "array of string", "array of integer",
     * "array of object", "array of number", "array of boolean".
     */
    private fun extractArrayItemType(type: String): String? {
        val lower = type.lowercase().trim()
        val prefix = "array of "
        if (!lower.startsWith(prefix)) return null
        val itemType = lower.removePrefix(prefix).trim()
        return when (itemType) {
            "string", "integer", "object", "number", "boolean" -> itemType
            else -> null
        }
    }

    // ── response parsing ──

    private fun parseResponse(body: String): LLMResponse {
        val root = JSONObject(body)
        val choice = root.getJSONArray("choices")
            .optJSONObject(0) ?: throw IOException("No choices in response")

        val message = choice.optJSONObject("message")
        val content = message?.optString("content", "") ?: ""
        val model = root.optString("model", "")
        val reasoningContent = message?.optString("reasoning_content", null)

        val toolCalls = message?.optJSONArray("tool_calls")?.let { tcs ->
            (0 until tcs.length()).map { i ->
                val tc = tcs.getJSONObject(i)
                val func = tc.getJSONObject("function")
                ToolCall(
                    id = tc.getString("id"),
                    type = tc.optString("type", "function"),
                    function = ToolFunction(
                        name = func.getString("name"),
                        arguments = func.getString("arguments")
                    )
                )
            }
        }

        val usage = root.optJSONObject("usage")?.let { u ->
            TokenUsage(
                promptTokens = u.optInt("prompt_tokens", 0),
                completionTokens = u.optInt("completion_tokens", 0),
                totalTokens = u.optInt("total_tokens", 0)
            )
        }

        return LLMResponse(
            content = content,
            model = model,
            usage = usage,
            toolCalls = toolCalls,
            reasoningContent = reasoningContent
        )
    }

    private fun buildRequest(jsonBody: String): Request {
        return Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
    }
}
