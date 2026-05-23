package com.noexcs.indolent.task

import kotlinx.serialization.Serializable

@Serializable
data class ScheduledTask(
    val id: String,
    val title: String,
    val frequency: TaskFrequency,
    val hour: Int,
    val minute: Int,
    val prompt: String,
    val notifyEnabled: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val dayOfWeek: Int? = null  // 1=Sunday..7=Saturday, for WEEKLY frequency
)

@Serializable
enum class TaskFrequency {
    DAILY, WEEKDAYS, WEEKLY, ONCE
}
