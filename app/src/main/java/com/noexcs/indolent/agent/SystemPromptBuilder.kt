package com.noexcs.indolent.agent

object SystemPromptBuilder {
    fun build(baseInstruction: String, userSystemPrompt: String, memory: String): String {
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
        }
    }
}
