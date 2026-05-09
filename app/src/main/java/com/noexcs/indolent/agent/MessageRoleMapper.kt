package com.noexcs.indolent.agent

object MessageRoleMapper {

    fun toRoleString(role: MessageRole): String = when (role) {
        MessageRole.User -> "user"
        MessageRole.Assistant -> "assistant"
        MessageRole.System -> "system"
        MessageRole.Thinking -> "assistant"
        MessageRole.ToolInfo -> "tool"
    }

    fun toMessageRole(roleString: String): MessageRole = when (roleString) {
        "user" -> MessageRole.User
        "assistant" -> MessageRole.Assistant
        "system" -> MessageRole.System
        "tool" -> MessageRole.ToolInfo
        else -> MessageRole.System
    }
}
