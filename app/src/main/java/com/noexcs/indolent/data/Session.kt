package com.noexcs.indolent.data

import androidx.compose.runtime.mutableStateOf
import com.noexcs.indolent.agent.ChatMessage
import com.noexcs.indolent.agent.MessageRole
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val sessionId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage>
)

class MessageViewModel(
    val role: MessageRole,
    content: String = "",
    val id: String = java.util.UUID.randomUUID().toString()
) {
    val content = mutableStateOf(content)
}