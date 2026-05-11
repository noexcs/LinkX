package com.noexcs.indolent.agent

object SystemPromptBuilder {
    fun build(
        baseInstruction: String,
        userSystemPrompt: String,
        memory: String,
        activeSkillContent: String = "",
        clipboardInstruction: String = ""
    ): String {
        return buildString {
            appendLine(baseInstruction.trimEnd())
            if (userSystemPrompt.isNotBlank()) {
                appendLine()
                appendLine("# User Custom Instruct")
                appendLine(userSystemPrompt)
            }
            if (memory.isNotBlank()) {
                appendLine()
                appendLine("# Memory")
                appendLine("<memory>")
                appendLine(memory)
                appendLine("</memory>")
            }
            if (activeSkillContent.isNotBlank()) {
                appendLine()
                appendLine("# Active Skill")
                appendLine(activeSkillContent)
            }
            if (clipboardInstruction.isNotBlank()) {
                appendLine()
                appendLine("# Agent Clipboard")
                appendLine(clipboardInstruction)
            }
        }
    }
}
