package com.noexcs.indolent

import com.noexcs.indolent.agent.LLMMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PendingExecutionData {

    data class ExecutionData(
        val taskId: String?,
        val title: String,
        val prompt: String,
        val messages: List<LLMMessage>
    )

    private var pending: ExecutionData? = null

    private val _hasPending = MutableStateFlow(false)
    val hasPending: StateFlow<Boolean> = _hasPending

    @Synchronized
    fun set(data: ExecutionData) {
        pending = data
        _hasPending.value = true
    }

    @Synchronized
    fun consume(): ExecutionData? {
        val data = pending
        pending = null
        _hasPending.value = false
        return data
    }
}
