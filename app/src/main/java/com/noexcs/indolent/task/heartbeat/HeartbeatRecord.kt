package com.noexcs.indolent.task.heartbeat

import com.noexcs.indolent.agent.LLMMessage
import com.noexcs.indolent.task.ExecutionStatus
import kotlinx.serialization.Serializable

@Serializable
data class HeartbeatRecord(
    val id: String,
    val status: ExecutionStatus,
    val result: List<LLMMessage> = emptyList(),
    val errorMessage: String = "",
    val executedAt: Long,
    val durationMs: Long = 0
)

val HeartbeatRecord.resultPreview: String
    get() = result.lastOrNull { it.role == "assistant" }?.content
        ?: result.firstOrNull()?.content
        ?: ""
