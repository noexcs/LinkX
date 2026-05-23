package com.noexcs.indolent

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noexcs.indolent.agent.Agent
import com.noexcs.indolent.agent.AgentEvent
import com.noexcs.indolent.agent.ContextConfig
import com.noexcs.indolent.agent.LLMMessage
import com.noexcs.indolent.agent.MessageRole
import com.noexcs.indolent.agent.MessageRoleMapper
import com.noexcs.indolent.agent.Session
import com.noexcs.indolent.agent.SessionType
import com.noexcs.indolent.agent.skills.SkillRepository
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolProvider
import com.noexcs.indolent.agent.tools.common.AgentClipboardStore
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
    private val clipboardStore = AgentClipboardStore()
    val messages = mutableStateListOf<MessageViewModel>()
    val isLoading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val tokenUsage = mutableStateOf("")

    var onConversationUpdated: (() -> Unit)? = null
    private var hasNotifiedFirstResponse = false
    private var sessionId: String = UUID.randomUUID().toString()
    private val skillRepository by lazy { SkillRepository(appContext, settingsManager) }

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
            val agent = Agent(baseUrl, apiKey, model, settingsManager.thinkingEnabled, settingsManager.reasoningEffort, clipboardStore = clipboardStore)
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

                val tools = buildTools()
                val session = resolveSession()

                // Build recent conversation context for memory retrieval (last 3 turns before current)
                val recentMessages = messages.dropLast(1).takeLast(3).mapNotNull { vm ->
                    val roleName = when (vm.role) {
                        MessageRole.User -> "user"
                        MessageRole.Assistant -> "assistant"
                        else -> null
                    }
                    roleName?.let { "$it: ${vm.content.value.take(500)}" }
                }
                session.context = buildContextConfig(userText, recentMessages)

                // Streaming assistant message placeholder
                var assistantMsg: MessageViewModel? = null
                var reasoningMsg: MessageViewModel? = null
                val toolMsgs = mutableMapOf<String, MessageViewModel>()
                val toolArgsBuf = mutableMapOf<String, StringBuilder>()

                withTimeout(600_000) {
                    session.run(userText, tools).collect { event ->
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
                        is AgentEvent.StreamRetry -> {
                            Lumberjack.w("AgentViewModel", "Stream retry attempt ${event.attempt}: ${event.reason}")
                            assistantMsg?.let {
                                it.content.value = ""
                                assistantMsg = null
                            }
                            reasoningMsg?.let {
                                it.content.value = ""
                                reasoningMsg = null
                            }
                            toolMsgs.clear()
                            toolArgsBuf.clear()
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
                        is AgentEvent.PasteContent -> {
                            messages.add(MessageViewModel(role = MessageRole.Assistant, content = event.content))
                        }
                        is AgentEvent.Truncated -> {
                            assistantMsg?.let {
                                it.content.value += "\n\n*[Response truncated due to length limit]*"
                            }
                        }
                        is AgentEvent.ContextWarning -> {
                            Lumberjack.w("AgentViewModel", "Context warning: ${event.message} (${event.estimatedTokens}/${event.budgetTokens})")
                        }
                        is AgentEvent.ContextSummarized -> {
                            Lumberjack.i("AgentViewModel", "Context summarized: ${event.messagesBefore} → ${event.messagesAfter} messages, summary ${event.summaryLength} chars")
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

    suspend fun buildContextConfig(currentMessage: String = "", recentMessages: List<String> = emptyList()): ContextConfig {
        val clipboardInstruction = if (
            settingsManager.commonToolsEnabled &&
            settingsManager.isToolEnabled("agent_clipboard")
        ) {
            """
                You have an agent-internal clipboard with named slots (separate from the system clipboard).

                ## Slots
                Content is organized into named slots via the `ns` parameter. The default slot is "default" when `ns` is omitted.

                ## Operations
                - action="copy" with `text`: Store text into a slot.
                - action="copy" with `prefix`+`suffix`: Extract content between two text anchors from a single history message. Must match exactly one message.
                - action="copy" with `source`: Read content from a file path into a slot.
                - action="paste": Display a slot's content to the user in the conversation.
                - action="clear": Clear a specific slot (with `ns`), or all slots (without `ns`).
                - action="info": Show status of a slot, or list all slots.

                ## Interpolation
                Use {{agent_clipboard}} in any tool parameter to inject the default slot's content.
                Use {{agent_clipboard:slotname}} to inject a named slot's content.
                Example: agent_clipboard(action="copy", text="Hello World") then fs_write(path="/sdcard/hello.txt", content="{{agent_clipboard}}")

                Shared with subagents.
            """.trimIndent()
        } else ""

        val screenInstruction = if (
            settingsManager.screenToolsEnabled &&
            settingsManager.isToolEnabled("screen_read")
        ) {
            """
                You can read and interact with the device screen via accessibility service tools.
                The accessibility service must be enabled in Settings → Accessibility → Indolent.

                Workflow:
                1. screen_read(mode="summary") — get an overview of what's on screen
                2. screen_click(index=N) or screen_click(text="Button") — click an element
                3. screen_screenshot — capture a screenshot for visual reference
                4. screen_scroll(direction="down") — scroll the screen
                5. screen_input(text="hello") — type text into a focused input field

                Use screen_read indexes to precisely target elements for clicks.
                Screen tools are unavailable if the accessibility service is not running.
            """.trimIndent()
        } else ""

        // Build retrieval query from current message + recent conversation context
        val queryParts = mutableListOf<String>()
        if (recentMessages.isNotEmpty()) {
            queryParts.addAll(recentMessages)
        }
        if (currentMessage.isNotBlank()) {
            queryParts.add(currentMessage)
        }
        val query = queryParts.joinToString("\n")

        // Attempt semantic retrieval; fall back to full dump on failure or empty results
        val retrieved = if (query.isNotBlank()) {
            try {
                memoryManager.search(query, k = 5)
            } catch (e: Exception) {
                Lumberjack.e("AgentViewModel", "Memory search failed", e)
                emptyList()
            }
        } else {
            emptyList()
        }

        val retrievedMemory = if (retrieved.isNotEmpty()) {
            retrieved.joinToString("\n\n---\n\n")
        } else {
            ""
        }

        return ContextConfig(
            baseInstruction = "You are a helpful Android assistant.",
            userSystemPrompt = settingsManager.userSystemPrompt,
            memory = retrievedMemory.ifEmpty { memoryManager.read() },
            retrievedMemory = retrievedMemory,
            activeSkillContent = skillRepository.getActiveSkillContent(),
            clipboardInstruction = clipboardInstruction,
            screenInstruction = screenInstruction
        )
    }

    suspend fun buildTools(): List<AgentTool> {
        return ToolProvider.build(appContext, settingsManager, memoryManager, contentDisplayManager, clipboardStore, historyProvider = { session?.history })
    }

    fun clearMessages() {
        messages.clear()
        session?.clear()
        sessionId = UUID.randomUUID().toString()
        hasNotifiedFirstResponse = false
        tokenUsage.value = ""
    }

    fun newConversation() = clearMessages()

    fun loadExecutionAsConversation(taskId: String?, title: String, prompt: String, messages: List<LLMMessage>) {
        viewModelScope.launch {
            // Try loading the full session history first (with tool calls preserved)
            if (taskId != null) {
                val session = resolveSession()
                if (session.load(taskId) && session.history.isNotEmpty()) {
                    loadConversation(taskId)
                    return@launch
                }
            }
            // Fallback: build view from the provided message list
            loadExecutionAsConversationFallback(title, prompt, messages)
        }
    }

    private fun loadExecutionAsConversationFallback(title: String, prompt: String, historyMessages: List<LLMMessage>) {
        clearMessages()
        messages.add(MessageViewModel(role = MessageRole.System, content = title))
        messages.add(MessageViewModel(role = MessageRole.User, content = prompt))
        // Render the messages list, skipping the system prompt message (first one)
        val json = Json { ignoreUnknownKeys = true }
        val assistantToolCalls = historyMessages
            .filter { it.role == "assistant" }
            .flatMap { it.toolCalls.orEmpty() }
            .associateBy { it.id }
        historyMessages.drop(1).forEach { msg ->  // skip system prompt
            // Emit Thinking bubble for reasoning content
            if (msg.role == "assistant" && !msg.reasoningContent.isNullOrBlank()) {
                this.messages.add(MessageViewModel(MessageRole.Thinking, msg.reasoningContent))
            }
            val displayContent = if (msg.role == "tool" && msg.toolCallId != null) {
                val tc = assistantToolCalls[msg.toolCallId]
                if (tc != null) {
                    val args = try { json.decodeFromString<Map<String, Any?>>(tc.function.arguments) } catch (_: Exception) { emptyMap() }
                    val argsStr = MessageFormatter.formatArgsJson(args)
                    val resultPreview = msg.content.lines().take(20).joinToString("\n")
                        .let { if (msg.content.lines().size > 20) "$it\n…" else it }
                    "🔧 ${tc.function.name}\n$argsStr\n$resultPreview"
                } else msg.content
            } else msg.content
            this.messages.add(MessageViewModel(MessageRoleMapper.toMessageRole(msg.role), displayContent))
        }
        resolveSession().setHistory(historyMessages)
        hasNotifiedFirstResponse = true
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
                // Build toolCallId -> ToolCall map from all assistant messages for history reload
                val assistantToolCalls = session.history
                    .filter { it.role == "assistant" }
                    .flatMap { it.toolCalls.orEmpty() }
                    .associateBy { it.id }

                val viewModels = session.history.flatMap { msg ->
                    val result = mutableListOf<MessageViewModel>()

                    // Emit a Thinking bubble for assistant reasoning content persisted in history
                    if (msg.role == "assistant" && !msg.reasoningContent.isNullOrBlank()) {
                        result.add(MessageViewModel(MessageRole.Thinking, msg.reasoningContent))
                    }

                    val displayContent = if (msg.role == "tool" && msg.toolCallId != null) {
                        val tc = assistantToolCalls[msg.toolCallId]
                        if (tc != null) {
                            val args = try {
                                json.decodeFromString<Map<String, Any?>>(tc.function.arguments)
                            } catch (_: Exception) { emptyMap() }
                            val argsStr = MessageFormatter.formatArgsJson(args)
                            val resultPreview = msg.content.lines()
                                .take(20).joinToString("\n")
                                .let { if (msg.content.lines().size > 20) "$it\n…" else it }
                            "🔧 ${tc.function.name}\n$argsStr\n$resultPreview"
                        } else msg.content
                    } else msg.content

                    val vm = MessageViewModel(MessageRoleMapper.toMessageRole(msg.role), displayContent)
                    msg.displayContentJson?.let { jsonStr ->
                        try {
                            val content = json.decodeFromString(DisplayContent.serializer(), jsonStr)
                            contentDisplayManager.store(content)
                            vm.displayContentId = content.id
                        } catch (_: Exception) { }
                    }
                    result.add(vm)
                    result
                }
                messages.clear()
                messages.addAll(viewModels)
                hasNotifiedFirstResponse = session.history.any { it.role == "assistant" }
            } else {
                clearMessages()
            }
            sessionId = id
            session.sessionId = id
        }
    }
}
