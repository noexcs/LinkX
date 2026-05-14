package com.noexcs.indolent.agent

object MessageRoleMapper {

    fun toMessageRole(roleString: String): MessageRole = when (roleString) {
        "user" -> MessageRole.User
        "assistant" -> MessageRole.Assistant
        "system" -> MessageRole.System
        "tool" -> MessageRole.ToolInfo
        else -> MessageRole.System
    }
}
