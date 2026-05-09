package com.noexcs.indolent.agent

import android.content.Context
import com.noexcs.indolent.agent.skills.SkillRepository
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolProvider
import com.noexcs.indolent.data.MemoryManager
import com.noexcs.indolent.data.SettingsManager

object BackgroundSessionRunner {

    fun create(
        context: Context,
        sessionId: String,
        type: SessionType,
    ): Session {
        val settings = SettingsManager(context)
        val baseUrl = settings.baseUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Base URL not configured")
        val apiKey = settings.apiKey?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("API key not configured")
        val model = settings.model?.ifBlank { "deepseek-chat" } ?: "deepseek-chat"

        val agent = Agent(baseUrl, apiKey, model, settings.thinkingEnabled, settings.reasoningEffort)
        return Session(sessionId = sessionId, agent = agent, type = type)
    }

    suspend fun buildTools(context: Context): List<AgentTool> {
        val appContext = context.applicationContext
        return ToolProvider.build(appContext, SettingsManager(appContext), MemoryManager(appContext))
    }

    fun buildSystemPrompt(context: Context, baseInstruction: String): String {
        val appContext = context.applicationContext
        val settings = SettingsManager(appContext)
        val skillRepo = SkillRepository(appContext, settings)
        return SystemPromptBuilder.build(
            baseInstruction = baseInstruction,
            userSystemPrompt = settings.userSystemPrompt,
            memory = MemoryManager(appContext).read(),
            activeSkillContent = skillRepo.getActiveSkillContent()
        )
    }
}
