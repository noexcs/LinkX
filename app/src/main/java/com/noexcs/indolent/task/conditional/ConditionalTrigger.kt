package com.noexcs.indolent.task.conditional

import kotlinx.serialization.Serializable

@Serializable
data class ConditionalTrigger(
    val id: String,
    val title: String,
    val conditions: List<TriggerCondition>,
    val prompt: String,
    val cooldownMs: Long = 300_000,
    val maxFiresPerDay: Int = 10,
    val notifyEnabled: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long = 0,
    val fireCount: Int = 0,
    val fireCountDate: String = ""  // "yyyy-MM-dd" for daily reset
)

@Serializable
data class TriggerCondition(
    val source: ConditionSource,
    val field: String,
    val operator: ConditionOperator,
    val targetValue: String? = null
)

@Serializable
enum class ConditionSource {
    BATTERY,
    SYSTEM_SETTING,
    SENSOR,
    POWER
}

@Serializable
enum class ConditionOperator {
    EQUAL,
    NOT_EQUAL,
    GREATER_THAN,
    LESS_THAN,
    GREATER_OR_EQUAL,
    LESS_OR_EQUAL,
    CHANGED,
    BECOMES_TRUE,
    BECOMES_FALSE
}
