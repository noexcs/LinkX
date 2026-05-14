package com.noexcs.indolent.task

import com.noexcs.indolent.agent.LLMMessage
import kotlinx.serialization.Serializable

@Serializable
data class TaskExecutionRecord(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val prompt: String,
    val status: ExecutionStatus,
    val result: List<LLMMessage> = emptyList(),
    val errorMessage: String = "",
    val executedAt: Long,
    val durationMs: Long = 0
)

@Serializable
enum class ExecutionStatus { SUCCESS, FAILURE }

val TaskExecutionRecord.resultPreview: String
    get() = result.lastOrNull { it.role == "assistant" }?.content
        ?: result.firstOrNull()?.content
        ?: ""
