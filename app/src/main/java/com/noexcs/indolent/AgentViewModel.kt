package com.noexcs.indolent

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noexcs.indolent.agent.Agent
import com.noexcs.indolent.agent.AgentEvent
import com.noexcs.indolent.agent.LLMMessage
import com.noexcs.indolent.agent.MessageRole
import com.noexcs.indolent.agent.MessageRoleMapper
import com.noexcs.indolent.agent.Session
import com.noexcs.indolent.agent.SessionType
import com.noexcs.indolent.agent.SystemPromptBuilder
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolProvider
import com.noexcs.indolent.agent.tools.interact.ContentDisplayManager
import com.noexcs.indolent.agent.tools.interact.DisplayContent
import com.noexcs.indolent.data.FileChatHistoryProvider
import com.noexcs.indolent.data.MemoryManager
import com.noexcs.indolent.data.MessageViewModel
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.ui.MessageFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.util.UUID

class AgentViewModel(
    private val appContext: Context,
    private val memoryManager: MemoryManager,
    private val settingsManager: SettingsManager,
    private val fileChatHistoryProvider: FileChatHistoryProvider
) : ViewModel() {

    val contentDisplayManager = ContentDisplayManager()
    val messages = mutableStateListOf<MessageViewModel>()
    val isLoading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val tokenUsage = mutableStateOf("")

    var onConversationUpdated: (() -> Unit)? = null
    private var hasNotifiedFirstResponse = false
    private var sessionId: String = UUID.randomUUID().toString()

    // Persistent session — carries conversation history across messages.
    // Recreated only when API settings change.
    private var session: Session? = null
    private var agentApiKey: String? = null
    private var agentBaseUrl: String? = null
    private var agentModel: String? = null

    private fun resolveSession(): Session {
        val apiKey = settingsManager.apiKey ?: ""
        val baseUrl = settingsManager.baseUrl ?: ""
        val model = settingsManager.model?.ifBlank { "deepseek-chat" } ?: "deepseek-chat"

        if (session == null || agentApiKey != apiKey || agentBaseUrl != baseUrl || agentModel != model) {
            val existingHistory = session?.history?.toList()
            val agent = Agent(baseUrl, apiKey, model, settingsManager.thinkingEnabled, settingsManager.reasoningEffort)
            session = Session(
                sessionId = sessionId,
                agent = agent,
                persistence = fileChatHistoryProvider,
                type = SessionType.CONVERSATION
            )
            if (existingHistory != null) {
                session!!.setHistory(existingHistory)
            }
            agentApiKey = apiKey
            agentBaseUrl = baseUrl
            agentModel = model
        }
        return session!!
    }

    fun checkSettings(): Boolean {
        return settingsManager.apiKey.isNullOrBlank() || settingsManager.baseUrl.isNullOrBlank()
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || isLoading.value) return

        val apiKey = settingsManager.apiKey ?: ""
        val baseUrl = settingsManager.baseUrl ?: ""

        if (baseUrl.isBlank()) {
            error.value = "Base URL is not configured. Please set it in Settings."
            return
        }
        if (apiKey.isBlank()) {
            error.value = "API key is not configured. Please set it in Settings."
            return
        }

        error.value = null
        isLoading.value = true
        messages.add(MessageViewModel(role = MessageRole.User, content = userText))

        viewModelScope.launch {
            try {

                val systemPrompt = buildSystemPrompt()
                val tools = buildTools()
                val session = resolveSession()

                // Streaming assistant message placeholder
                var assistantMsg: MessageViewModel? = null
                var reasoningMsg: MessageViewModel? = null
                val toolMsgs = mutableMapOf<String, MessageViewModel>()
                val toolArgsBuf = mutableMapOf<String, StringBuilder>()

                withTimeout(600_000) {
                    session.run(userText, systemPrompt, tools).collect { event ->
                    Lumberjack.v("Agent", "Event: $event")
                    when (event) {
                        is AgentEvent.Reasoning -> {
                            val msg = reasoningMsg ?: MessageViewModel(
                                role = MessageRole.Thinking,
                                content = ""
                            ).also {
                                reasoningMsg = it
                                messages.add(it)
                            }
                            msg.content.value += event.content
                        }
                        is AgentEvent.Text -> {
                            val msg = assistantMsg ?: MessageViewModel(
                                role = MessageRole.Assistant,
                                content = ""
                            ).also {
                                assistantMsg = it
                                messages.add(it)
                            }
                            msg.content.value += event.content
                        }
                        is AgentEvent.ToolCallStart -> {
                            assistantMsg = null
                            reasoningMsg = null
                            val msg = MessageViewModel(role = MessageRole.ToolInfo, content = "")
                            msg.content.value = "🔧 Calling ${event.name}..."
                            messages.add(msg)
                            toolMsgs[event.callId] = msg
                            toolArgsBuf[event.callId] = StringBuilder()
                        }
                        is AgentEvent.ToolCallDelta -> {
                            toolArgsBuf[event.callId]?.append(event.argumentsDelta)
                            val fullArgs = toolArgsBuf[event.callId]?.toString() ?: ""
                            toolMsgs[event.callId]?.let {
                                it.content.value = "🔧 ${event.name}\n$fullArgs"
                            }
                        }
                        is AgentEvent.ToolCallBegin -> {
                            val argsStr = MessageFormatter.formatArgsJson(event.arguments)
                            toolMsgs[event.callId]?.let {
                                it.content.value = "🔧 ${event.name}\n$argsStr\n⏳ Executing..."
                            }
                        }
                        is AgentEvent.ToolResult -> {
                            val argsStr = MessageFormatter.formatArgsJson(event.args)
                            val resultPreview = event.result.lines()
                                .take(20).joinToString("\n")
                                .let { if (event.result.lines().size > 20) "$it\n…" else it }
                            toolMsgs[event.callId]?.let {
                                it.content.value = "🔧 ${event.name}\n$argsStr\n$resultPreview"
                                if (event.name == "display_content") {
                                    val idMatch = Regex("Content ID: (\\S+)").find(event.result)
                                    idMatch?.let { match -> it.displayContentId = match.groupValues[1] }
                                }
                            }
                            toolMsgs.remove(event.callId)
                            toolArgsBuf.remove(event.callId)
                        }
                        is AgentEvent.Error -> {
                            Lumberjack.e("AgentViewModel", "Error: ${event.message}")
                            error.value = event.message
                        }
                        is AgentEvent.Usage -> {
                            settingsManager.cumulativePromptTokens += event.promptTokens
                            settingsManager.cumulativeCompletionTokens += event.completionTokens
                            val total = event.promptTokens + event.completionTokens
                            tokenUsage.value = "Prompt: ${MessageFormatter.formatTokens(event.promptTokens)} | Completion: ${MessageFormatter.formatTokens(event.completionTokens)} | Total: ${MessageFormatter.formatTokens(total)}"
                            Lumberjack.i("AgentViewModel", "Token usage: $tokenUsage")
                        }
                        is AgentEvent.Truncated -> {
                            assistantMsg?.let {
                                it.content.value += "\n\n*[Response truncated due to length limit]*"
                            }
                        }
                    }
                }
                } // withTimeout

                // Sync displayContentJson from UI messages back to session history before saving
                val json = Json { ignoreUnknownKeys = true }
                val displayContentIds = messages.mapNotNull { it.displayContentId }.toSet()
                if (displayContentIds.isNotEmpty()) {
                    for (msg in messages) {
                        msg.displayContentId?.let { id ->
                            contentDisplayManager.getStoredContent(id)?.let { content ->
                                val displayJson = json.encodeToString(DisplayContent.serializer(), content)
                                // Update corresponding tool LLMMessage in session history
                                val idx = session.history.indexOfLast {
                                    it.role == "tool" && it.content.contains(id)
                                }
                                if (idx >= 0) {
                                    session.history[idx] = session.history[idx].copy(displayContentJson = displayJson)
                                }
                            }
                        }
                    }
                }

                // Save conversation to history
                session.save()

                if (!hasNotifiedFirstResponse) {
                    onConversationUpdated?.invoke()
                    hasNotifiedFirstResponse = true
                }

            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Lumberjack.e("AgentViewModel", "Agent run timed out", e)
                error.value = "Agent run timed out after 10 minutes"
            } catch (e: Exception) {
                Lumberjack.e("AgentViewModel", "Agent run failed", e)
                error.value = e.message ?: "Unknown error"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun buildSystemPrompt(): String {
        return SystemPromptBuilder.build(
            baseInstruction = "You are a helpful Android assistant.",
            userSystemPrompt = settingsManager.userSystemPrompt,
            memory = memoryManager.read()
        )
    }

    fun buildTools(): List<AgentTool> {
        return ToolProvider.build(appContext, settingsManager, memoryManager, contentDisplayManager)
    }

    fun clearMessages() {
        messages.clear()
        session?.clear()
        sessionId = UUID.randomUUID().toString()
        hasNotifiedFirstResponse = false
        tokenUsage.value = ""
    }

    fun newConversation() = clearMessages()

    fun loadExecutionAsConversation(title: String, prompt: String, result: String) {
        clearMessages()
        messages.add(MessageViewModel(role = MessageRole.System, content = title))
        messages.add(MessageViewModel(role = MessageRole.User, content = prompt))
        messages.add(MessageViewModel(role = MessageRole.Assistant, content = result))
        // Restore session context so follow-up messages have history
        resolveSession().setHistory(
            listOf(
                LLMMessage(role = "user", content = prompt),
                LLMMessage(role = "assistant", content = result)
            )
        )
        hasNotifiedFirstResponse = true
        // Save to file so it appears in the conversation drawer
        viewModelScope.launch {
            session?.let { s ->
                s.title = title
                s.save()
            }
            onConversationUpdated?.invoke()
        }
    }

    fun loadConversation(id: String) {
        viewModelScope.launch {
            val session = resolveSession()
            val loaded = session.load(id)
            if (loaded && session.history.isNotEmpty()) {
                val json = Json { ignoreUnknownKeys = true }
                val viewModels = session.history.map { msg ->
                    val vm = MessageViewModel(MessageRoleMapper.toMessageRole(msg.role), msg.content)
                    msg.displayContentJson?.let { jsonStr ->
                        try {
                            val content = json.decodeFromString(DisplayContent.serializer(), jsonStr)
                            contentDisplayManager.store(content)
                            vm.displayContentId = content.id
                        } catch (_: Exception) { }
                    }
                    vm
                }
                messages.clear()
                messages.addAll(viewModels)
                hasNotifiedFirstResponse = session.history.any { it.role == "assistant" }
            } else {
                clearMessages()
            }
            sessionId = id
        }
    }
}
