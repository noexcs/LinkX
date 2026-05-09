package com.noexcs.indolent.data

import androidx.compose.runtime.mutableStateOf
import com.noexcs.indolent.agent.MessageRole

class MessageViewModel(
    val role: MessageRole,
    content: String = "",
    val id: String = java.util.UUID.randomUUID().toString()
) {
    val content = mutableStateOf(content)
    var displayContentId: String? = null
}
