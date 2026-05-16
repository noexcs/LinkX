package com.noexcs.indolent.agent

object SystemPromptBuilder {

    /**
     * Builds a complete system prompt from a [ContextConfig].
     */
    fun build(config: ContextConfig): String {
        return buildString {
            appendLine(config.baseInstruction.trimEnd())
            if (config.userSystemPrompt.isNotBlank()) {
                appendLine()
                appendLine("# User Custom Instruct")
                appendLine(config.userSystemPrompt)
            }
            val effectiveMemory = if (config.retrievedMemory.isNotBlank()) {
                config.retrievedMemory
            } else {
                config.memory
            }
            if (effectiveMemory.isNotBlank()) {
                appendLine()
                appendLine("# Memory")
                appendLine("<memory>")
                appendLine(effectiveMemory)
                appendLine("</memory>")
            }
            if (config.activeSkillContent.isNotBlank()) {
                appendLine()
                appendLine("# Active Skill")
                appendLine(config.activeSkillContent)
            }
            if (config.clipboardInstruction.isNotBlank()) {
                appendLine()
                appendLine("# Agent Clipboard")
                appendLine(config.clipboardInstruction)
            }
            if (config.screenInstruction.isNotBlank()) {
                appendLine()
                appendLine("# Screen Interaction")
                appendLine(config.screenInstruction)
            }
        }
    }

    // Keep the legacy overload for gradual migration — callers that haven't
    // been updated yet can still use individual parameters.
    @Deprecated("Use build(ContextConfig) instead", ReplaceWith("build(ContextConfig(...))"))
    fun build(
        baseInstruction: String,
        userSystemPrompt: String,
        memory: String,
        activeSkillContent: String = "",
        clipboardInstruction: String = "",
        screenInstruction: String = ""
    ): String {
        return build(
            ContextConfig(
                baseInstruction = baseInstruction,
                userSystemPrompt = userSystemPrompt,
                memory = memory,
                activeSkillContent = activeSkillContent,
                clipboardInstruction = clipboardInstruction,
                screenInstruction = screenInstruction
            )
        )
    }
}
