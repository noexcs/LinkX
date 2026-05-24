package com.noexcs.indolent.agent

import android.content.Context
import com.noexcs.indolent.agent.LLMMessage
import com.noexcs.indolent.agent.skills.SkillRepository
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolProvider
import com.noexcs.indolent.agent.tools.common.AgentClipboardStore
import com.noexcs.indolent.data.FileChatHistoryProvider
import com.noexcs.indolent.data.MemoryManager
import com.noexcs.indolent.data.SettingsManager

object BackgroundSessionRunner {

    @Volatile private var cachedMemoryManager: MemoryManager? = null

    private fun getMemoryManager(context: Context): MemoryManager {
        cachedMemoryManager?.let { return it }
        synchronized(this) {
            cachedMemoryManager?.let { return it }
            val mm = MemoryManager(context.applicationContext).also { it.warmUp() }
            cachedMemoryManager = mm
            return mm
        }
    }

    fun create(
        context: Context,
        sessionId: String,
        type: SessionType,
        clipboardStore: AgentClipboardStore? = null,
    ): Session {
        val settings = SettingsManager(context)
        if (settings.baseUrl.isBlank()) throw IllegalStateException("Base URL not configured")
        if (settings.apiKey.isBlank()) throw IllegalStateException("API key not configured")
        val baseUrl = settings.baseUrl
        val apiKey = settings.apiKey
        val model = settings.model.ifBlank { "deepseek-chat" }

        val agent = Agent(baseUrl, apiKey, model, settings.thinkingEnabled, settings.reasoningEffort, clipboardStore = clipboardStore)
        val persistence = FileChatHistoryProvider(context)
        return Session(sessionId = sessionId, agent = agent, persistence = persistence, type = type)
    }

    suspend fun buildTools(
        context: Context,
        clipboardStore: AgentClipboardStore? = null,
        historyProvider: () -> List<LLMMessage>? = { null },
    ): List<AgentTool> {
        val appContext = context.applicationContext
        return ToolProvider.build(appContext, SettingsManager(appContext), getMemoryManager(appContext),
            clipboardStore = clipboardStore, historyProvider = historyProvider)
    }

    /**
     * Builds a [ContextConfig] for background sessions.
     * Callers should set this on the session via `session.context = config` before
     * calling [Session.execute].
     */
    fun buildContextConfig(
        context: Context,
        baseInstruction: String,
        clipboardStore: AgentClipboardStore? = null,
    ): ContextConfig {
        val appContext = context.applicationContext
        val settings = SettingsManager(appContext)
        val skillRepo = SkillRepository(appContext, settings)
        val clipboardInstruction = buildClipboardInstruction(settings, clipboardStore, detailed = false)
        val screenInstruction = buildScreenInstruction(settings, detailed = false)

        return ContextConfig(
            baseInstruction = baseInstruction,
            userSystemPrompt = loadActiveSystemPrompt(appContext, settings),
            memory = getMemoryManager(appContext).read(),
            activeSkillContent = skillRepo.getActiveSkillContent(),
            clipboardInstruction = clipboardInstruction,
            screenInstruction = screenInstruction
        )
    }

    // ── Shared instruction builders ──

    fun buildClipboardInstruction(
        settings: SettingsManager,
        clipboardStore: AgentClipboardStore? = null,
        detailed: Boolean = true,
    ): String {
        if (!settings.commonToolsEnabled || !settings.isToolEnabled("agent_clipboard") || clipboardStore == null) return ""
        return if (detailed) {
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
        } else {
            "You have an agent-internal clipboard with named slots. Use `agent_clipboard` tool with `ns` for slots. {{agent_clipboard}} or {{agent_clipboard:slotname}} injects stored content into tool parameters."
        }
    }

    fun buildScreenInstruction(
        settings: SettingsManager,
        detailed: Boolean = true,
    ): String {
        if (!settings.screenToolsEnabled || !settings.isToolEnabled("screen_read")) return ""
        return if (detailed) {
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
        } else {
            "Use screen_read, screen_click, screen_screenshot, screen_scroll, screen_input tools to interact with the device screen. The accessibility service must be enabled."
        }
    }

    private fun loadActiveSystemPrompt(context: Context, settings: SettingsManager): String {
        val activeId = settings.activeSystemPromptId ?: return settings.userSystemPrompt
        return try {
            val repo = com.noexcs.indolent.prompt.SystemPromptRepository(context)
            kotlinx.coroutines.runBlocking { repo.load(activeId)?.content ?: "" }
        } catch (e: Exception) {
            ""
        }
    }
}
