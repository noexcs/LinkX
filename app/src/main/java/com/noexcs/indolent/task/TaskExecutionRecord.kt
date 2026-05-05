package com.noexcs.indolent.task

import kotlinx.serialization.Serializable

@Serializable
data class TaskExecutionRecord(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val prompt: String,
    val status: ExecutionStatus,
    val result: String = "",
    val errorMessage: String = "",
    val executedAt: Long,
    val durationMs: Long = 0
)

@Serializable
enum class ExecutionStatus { SUCCESS, FAILURE }
