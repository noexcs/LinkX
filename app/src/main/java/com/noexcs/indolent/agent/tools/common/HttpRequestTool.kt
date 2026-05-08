package com.noexcs.indolent.agent.tools.common

import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpRequestTool : AgentTool {

    override val name = "http_request"
    override val description = """
        Make an HTTP request to a given URL. Supports GET, POST, PUT, DELETE, PATCH, HEAD.

        Use this to call external REST APIs, fetch web content, or interact with any HTTP service.
        Response body is truncated at 2 MB.

        Example headers (JSON object string):
        {"Authorization": "Bearer xxx", "Content-Type": "application/json"}
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "url",
            type = "string",
            description = "Target URL (must start with http:// or https://)"
        ),
        ToolParameter(
            name = "method",
            type = "string",
            required = false,
            defaultValue = "GET",
            description = "HTTP method: GET, POST, PUT, DELETE, PATCH, HEAD"
        ),
        ToolParameter(
            name = "headers",
            type = "string",
            required = false,
            defaultValue = "{}",
            description = "JSON object of request headers, e.g. {\"Content-Type\": \"application/json\"}"
        ),
        ToolParameter(
            name = "body",
            type = "string",
            required = false,
            defaultValue = "",
            description = "Request body string (used for POST, PUT, PATCH)"
        ),
    )

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val url = (args["url"] as? String)?.trim() ?: return@withContext "Error: url is required"

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext "Error: url must start with http:// or https://"
        }

        val method = ((args["method"] as? String)?.uppercase()?.trim() ?: "GET")
            .takeIf { it.isNotBlank() } ?: "GET"

        if (method !in ALLOWED_METHODS) {
            return@withContext "Error: unsupported method '$method'. Allowed: ${ALLOWED_METHODS.joinToString(", ")}"
        }

        val headersStr = (args["headers"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: "{}"
        val headers = try {
            val json = JSONObject(headersStr)
            json.keys().asSequence().associate { it to json.getString(it) }
        } catch (_: Exception) {
            return@withContext "Error: failed to parse headers as JSON. Example: {\"Content-Type\": \"application/json\"}"
        }

        val bodyStr = (args["body"] as? String)?.trim()?.takeIf { it.isNotBlank() }

        Lumberjack.i("HttpRequestTool", "$method $url")

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            val request = Request.Builder().url(url).apply {
                headers.forEach { (key, value) -> addHeader(key, value) }
                when (method) {
                    "POST", "PUT", "PATCH", "DELETE" -> {
                        val contentType = headers["Content-Type"] ?: "text/plain"
                        val body = bodyStr.orEmpty().toRequestBody(contentType.toMediaTypeOrNull())
                        method(method, body)
                    }
                    "HEAD" -> method("HEAD", null)
                    else -> get()
                }
            }.build()

            val response = client.newCall(request).execute()

            val responseHeaders = buildString {
                response.headers.forEach { (name, value) ->
                    appendLine("  $name: $value")
                }
            }

            val rawBody = response.body?.string() ?: ""
            val MAX_BODY = 2 * 1024 * 1024 // 2 MB
            val truncated = rawBody.length > MAX_BODY
            val body = if (truncated) rawBody.take(MAX_BODY) else rawBody

            buildString {
                appendLine("HTTP ${response.code}")
                appendLine("URL: $url")
                appendLine()
                if (responseHeaders.isNotBlank()) {
                    appendLine("Response Headers:")
                    append(responseHeaders)
                    appendLine()
                }
                appendLine("Response Body:")
                appendLine(body)
                if (truncated) {
                    appendLine()
                    appendLine("[Response truncated at $MAX_BODY bytes]")
                }
            }
        } catch (e: IOException) {
            Lumberjack.e("HttpRequestTool", "Request failed: $url", e)
            "Error: ${e.message}"
        } catch (e: Exception) {
            Lumberjack.e("HttpRequestTool", "Unexpected error: $url", e)
            "Error: ${e.message}"
        }
    }

    private companion object {
        val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD")
    }
}
