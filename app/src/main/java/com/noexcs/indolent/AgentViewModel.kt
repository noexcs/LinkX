package com.noexcs.indolent

import android.content.Context
import android.content.pm.PackageManager
import com.noexcs.indolent.logging.Lumberjack
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noexcs.indolent.agent.Agent
import com.noexcs.indolent.agent.AgentEvent
import com.noexcs.indolent.agent.ChatMessage
import com.noexcs.indolent.agent.LLMMessage
import com.noexcs.indolent.agent.MessageRole
import com.noexcs.indolent.agent.termux.TermuxExecutor
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolProvider
import com.noexcs.indolent.agent.tools.finance.FundETFFundInfoEmTool
import com.noexcs.indolent.agent.tools.finance.FundInfoIndexEmTool
import com.noexcs.indolent.agent.tools.finance.FundIndividualAchievementXqTool
import com.noexcs.indolent.agent.tools.finance.FundIndividualAnalysisXqTool
import com.noexcs.indolent.agent.tools.finance.FundIndividualBasicInfoXqTool
import com.noexcs.indolent.agent.tools.finance.FundIndividualDetailHoldXqTool
import com.noexcs.indolent.agent.tools.finance.FundIndividualDetailInfoXqTool
import com.noexcs.indolent.agent.tools.finance.FundIndividualProfitProbabilityXqTool
import com.noexcs.indolent.agent.tools.finance.FundManagerEmTool
import com.noexcs.indolent.agent.tools.finance.FundOpenFundInfoEmTool
import com.noexcs.indolent.agent.tools.finance.FundOpenFundRankEmTool
import com.noexcs.indolent.agent.tools.finance.FundOverviewEmTool
import com.noexcs.indolent.agent.tools.finance.FundPortfolioBondHoldEmTool
import com.noexcs.indolent.agent.tools.finance.FundPortfolioChangeEmTool
import com.noexcs.indolent.agent.tools.finance.FundPortfolioHoldEmTool
import com.noexcs.indolent.agent.tools.finance.FundPortfolioIndustryAllocationEmTool
import com.noexcs.indolent.agent.tools.finance.FundValueEstimationEmRankTool
import com.noexcs.indolent.agent.tools.finance.FundValueEstimationEmTool
import com.noexcs.indolent.agent.tools.finance.PythonInit
import com.noexcs.indolent.agent.tools.finance.StockBoardConceptNameEmTool
import com.noexcs.indolent.agent.tools.finance.StockBoardConceptConsEmTool
import com.noexcs.indolent.agent.tools.finance.StockBoardConceptHistEmTool
import com.noexcs.indolent.agent.tools.finance.StockBoardConceptSpotEmTool
import com.noexcs.indolent.agent.tools.finance.StockBoardIndustryNameEmTool
import com.noexcs.indolent.agent.tools.finance.StockBoardIndustrySpotEmTool
import com.noexcs.indolent.agent.tools.finance.StockConceptFundFlowHistTool
import com.noexcs.indolent.agent.tools.finance.StockIndividualFundFlowTool
import com.noexcs.indolent.agent.tools.finance.StockIndividualInfoEmTool
import com.noexcs.indolent.agent.tools.finance.StockIndividualSpotXqTool
import com.noexcs.indolent.agent.tools.finance.StockIntradayEmTool
import com.noexcs.indolent.agent.tools.finance.StockIntradaySinaTool
import com.noexcs.indolent.agent.tools.finance.StockMarketFundFlowTool
import com.noexcs.indolent.agent.tools.finance.StockSectorFundFlowHistTool
import com.noexcs.indolent.agent.tools.finance.StockSectorFundFlowRankTool
import com.noexcs.indolent.agent.tools.finance.StockSectorFundFlowSummaryTool
import com.noexcs.indolent.agent.tools.finance.StockSectorDetailTool
import com.noexcs.indolent.agent.tools.finance.StockSectorSpotTool
import com.noexcs.indolent.agent.tools.finance.StockZhAHistTool
import com.noexcs.indolent.agent.tools.finance.TrendIndicatorTool
import com.noexcs.indolent.agent.tools.finance.OscillatorIndicatorTool
import com.noexcs.indolent.agent.tools.finance.VolumeIndicatorTool
import com.noexcs.indolent.agent.tools.finance.MomentumIndicatorTool
import com.noexcs.indolent.agent.tools.finance.DirectionalIndicatorTool
import com.noexcs.indolent.agent.tools.finance.EnergyIndicatorTool
import com.noexcs.indolent.agent.tools.finance.FundPerformanceTool
import com.noexcs.indolent.agent.tools.finance.FundVsBenchmarkTool
import com.noexcs.indolent.agent.tools.finance.PortfolioAddTool
import com.noexcs.indolent.agent.tools.finance.PortfolioAnalyzeAllTool
import com.noexcs.indolent.agent.tools.finance.PortfolioListTool
import com.noexcs.indolent.agent.tools.finance.PortfolioRemoveTool
import com.noexcs.indolent.agent.tools.finance.PortfolioSummaryTool
import com.noexcs.indolent.agent.tools.finance.PortfolioUpdateTool
import com.noexcs.indolent.agent.tools.systeminfo.BatteryInfoTool
import com.noexcs.indolent.agent.tools.common.CalendarTool
import com.noexcs.indolent.agent.tools.common.ClipboardTool
import com.noexcs.indolent.agent.tools.notification.CreateNotificationTool
import com.noexcs.indolent.agent.tools.notification.DismissNotificationTool
import com.noexcs.indolent.agent.tools.notification.ListActiveNotificationsTool
import com.noexcs.indolent.agent.tools.notification.ManageNotificationChannelTool
import com.noexcs.indolent.agent.tools.notification.OpenNotificationAccessSettingsTool
import com.noexcs.indolent.agent.tools.notification.QueryNotificationTool
import com.noexcs.indolent.agent.tools.notification.UpdateNotificationTool
import com.noexcs.indolent.agent.tools.systeminfo.CurrentScreenInfoTool
import com.noexcs.indolent.agent.tools.filesystem.ReadFileTool
import com.noexcs.indolent.agent.tools.filesystem.WriteFileTool
import com.noexcs.indolent.agent.tools.filesystem.ListFilesTool
import com.noexcs.indolent.agent.tools.filesystem.DeleteFileTool
import com.noexcs.indolent.agent.tools.filesystem.GetStorageInfoTool
import com.noexcs.indolent.agent.tools.sensor.GetSensorDataTool
import com.noexcs.indolent.agent.tools.setting.SystemSettingTool
import com.noexcs.indolent.agent.tools.setting.AudioControlTool
import com.noexcs.indolent.agent.tools.systeminfo.GetAppInfoTool
import com.noexcs.indolent.agent.tools.interact.AskUserTool
import com.noexcs.indolent.agent.tools.interact.ContentDisplayManager
import com.noexcs.indolent.agent.tools.interact.DisplayContent
import com.noexcs.indolent.agent.tools.common.IntentTool
import com.noexcs.indolent.agent.tools.self.LogQueryTool
import com.noexcs.indolent.agent.tools.common.GetCurrentTimeTool
import com.noexcs.indolent.agent.tools.systeminfo.NetworkStatusTool
import com.noexcs.indolent.agent.tools.scheduledTask.CreateScheduledTaskTool
import com.noexcs.indolent.agent.tools.scheduledTask.ListScheduledTasksTool
import com.noexcs.indolent.agent.tools.scheduledTask.EditScheduledTaskTool
import com.noexcs.indolent.agent.tools.scheduledTask.DeleteScheduledTaskTool
import com.noexcs.indolent.agent.tools.termux.TermuxExecuteCommandTool
import com.noexcs.indolent.agent.tools.common.SubagentTool
import com.noexcs.indolent.agent.tools.conditional.CreateConditionalTriggerTool
import com.noexcs.indolent.agent.tools.conditional.ListConditionalTriggersTool
import com.noexcs.indolent.agent.tools.conditional.EditConditionalTriggerTool
import com.noexcs.indolent.agent.tools.conditional.DeleteConditionalTriggerTool
import com.noexcs.indolent.agent.tools.conditional.ListConditionalTriggerHistoryTool
import com.noexcs.indolent.agent.tools.common.UpdateMemoryTool
import com.noexcs.indolent.data.FileChatHistoryProvider
import com.noexcs.indolent.data.MemoryManager
import com.noexcs.indolent.data.MessageViewModel
import com.noexcs.indolent.data.SettingsManager
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

    private val executor = TermuxExecutor(appContext.applicationContext)
    val contentDisplayManager = ContentDisplayManager()
    val messages = mutableStateListOf<MessageViewModel>()
    val isLoading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val tokenUsage = mutableStateOf("")

    var onConversationUpdated: (() -> Unit)? = null
    private var hasNotifiedFirstResponse = false
    private var sessionId: String = UUID.randomUUID().toString()

    // Persistent agent — carries conversation history across messages.
    // Recreated only when API settings change.
    private var agent: Agent? = null
    private var agentApiKey: String? = null
    private var agentBaseUrl: String? = null
    private var agentModel: String? = null

    private fun resolveAgent(): Agent {
        val apiKey = settingsManager.apiKey ?: ""
        val baseUrl = settingsManager.baseUrl ?: ""
        val model = settingsManager.model?.ifBlank { "deepseek-chat" } ?: "deepseek-chat"

        if (agent == null || agentApiKey != apiKey || agentBaseUrl != baseUrl || agentModel != model) {
            val existingHistory = agent?.getHistory()
            agent = Agent(baseUrl, apiKey, model, settingsManager.thinkingEnabled, settingsManager.reasoningEffort)
            if (existingHistory != null) {
                agent!!.setHistory(existingHistory)
            }
            agentApiKey = apiKey
            agentBaseUrl = baseUrl
            agentModel = model
        }
        return agent!!
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
                val agent = resolveAgent()

                // Streaming assistant message placeholder
                var assistantMsg: MessageViewModel? = null
                var reasoningMsg: MessageViewModel? = null
                val toolMsgs = mutableMapOf<String, MessageViewModel>()
                val toolArgsBuf = mutableMapOf<String, StringBuilder>()

                withTimeout(600_000) {
                    agent.run(userText, systemPrompt, tools).collect { event ->
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
                            reasoningMsg = null // reset so next reasoning creates a new bubble
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
                            val argsStr = formatArgsJson(event.arguments)
                            toolMsgs[event.callId]?.let {
                                it.content.value = "🔧 ${event.name}\n$argsStr\n⏳ Executing..."
                            }
                        }
                        is AgentEvent.ToolResult -> {
                            val argsStr = formatArgsJson(event.args)
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
                            tokenUsage.value = "Prompt: ${formatTokens(event.promptTokens)} | Completion: ${formatTokens(event.completionTokens)} | Total: ${formatTokens(total)}"
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

                // Save conversation to history
                val json = Json { ignoreUnknownKeys = true }
                val chatMessages = messages.map { msg ->
                    val displayJson = msg.displayContentId?.let { id ->
                        contentDisplayManager.getStoredContent(id)?.let {
                            json.encodeToString(DisplayContent.serializer(), it)
                        }
                    }
                    ChatMessage(role = msg.role, content = msg.content.value, displayContentJson = displayJson)
                }
                fileChatHistoryProvider.store(sessionId, chatMessages)

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
        val memory = memoryManager.read()
        return buildString {
            appendLine("You are a helpful Android assistant.")
            if (settingsManager.userSystemPrompt.isNotBlank()) {
                appendLine()
                appendLine("# User Custom Instruct")
                appendLine(settingsManager.userSystemPrompt)
            }
            if (memory.isNotBlank()) {
                appendLine()
                appendLine("# Memory")
                appendLine("<memory>")
                appendLine(memory)
                appendLine("</memory>")
            }
        }
    }

    fun buildTools(): List<AgentTool> {
        return ToolProvider.build(appContext, settingsManager, memoryManager, executor, contentDisplayManager)
    }

    fun clearMessages() {
        messages.clear()
        agent?.clearHistory()
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
        // Restore agent context so follow-up messages have history
        resolveAgent().setHistory(
            listOf(
                LLMMessage(role = "user", content = prompt),
                LLMMessage(role = "assistant", content = result)
            )
        )
        hasNotifiedFirstResponse = true
        // Save to file so it appears in the conversation drawer
        viewModelScope.launch {
            val chatMessages = messages.map { msg ->
                ChatMessage(role = msg.role, content = msg.content.value)
            }
            fileChatHistoryProvider.store(sessionId, chatMessages)
            onConversationUpdated?.invoke()
        }
    }

    private fun formatTokens(n: Int): String = when {
        n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000.0)}M"
        n >= 1_000 -> "${"%.1f".format(n / 1_000.0)}k"
        else -> n.toString()
    }

    private fun formatArgsJson(args: Map<String, Any?>): String {
        if (args.isEmpty()) return "{}"
        return args.entries.joinToString(",\n") { (k, v) ->
            "  \"$k\": ${v.toJsonLiteral()}"
        }.let { "{\n$it\n}" }
    }

    private fun Any?.toJsonLiteral(): String = when (this) {
        null -> "null"
        is String -> "\"$this\""
        is Number -> toString()
        is Boolean -> toString()
        else -> "\"$this\""
    }

    fun loadConversation(id: String) {
        val session = fileChatHistoryProvider._load(id)
        if (session != null) {
            val json = Json { ignoreUnknownKeys = true }
            val viewModels = session.messages.map { msg ->
                val vm = MessageViewModel(msg.role, msg.content)
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

            // Restore conversation context into the agent so the LLM remembers
            // prior turns when the user sends the next message.
            val agentHistory = session.messages.mapNotNull { msg ->
                when (msg.role) {
                    MessageRole.User -> LLMMessage(role = "user", content = msg.content)
                    MessageRole.Assistant -> LLMMessage(role = "assistant", content = msg.content)
                    else -> null
                }
            }
            resolveAgent().setHistory(agentHistory)

            hasNotifiedFirstResponse = session.messages.any { it.role == MessageRole.Assistant }
        } else {
            clearMessages()
        }
        sessionId = id
    }
}
