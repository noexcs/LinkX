package com.noexcs.indolent.task.heartbeat

import com.noexcs.indolent.task.ExecutionStatus
import kotlinx.serialization.Serializable

@Serializable
data class HeartbeatRecord(
    val id: String,
    val status: ExecutionStatus,
    val result: String = "",
    val errorMessage: String = "",
    val executedAt: Long,
    val durationMs: Long = 0
)
