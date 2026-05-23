package com.noexcs.indolent.task.conditional

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.task.conditional.conditionProvider.BatteryConditionProvider
import com.noexcs.indolent.task.conditional.conditionProvider.PowerConditionProvider
import com.noexcs.indolent.task.conditional.conditionProvider.SensorConditionProvider
import com.noexcs.indolent.task.conditional.conditionProvider.SettingConditionProvider

class ConditionEvaluator(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun evaluateAll(): List<ConditionalTrigger> {
        val triggers = ConditionalTriggerRepository(context).listEnabled()
        if (triggers.isEmpty()) return emptyList()

        var batteryState: Map<String, String>? = null
        var settingState: Map<String, String>? = null
        var sensorState: Map<String, String>? = null
        var powerState: Map<String, String>? = null

        // Determine which sources we actually need to query based on all conditions
        val neededSources = triggers.flatMap { it.conditions }.map { it.source }.toSet()

        if (ConditionSource.BATTERY in neededSources) {
            batteryState = BatteryConditionProvider(context).getState()
        }
        if (ConditionSource.SYSTEM_SETTING in neededSources) {
            settingState = SettingConditionProvider(context).getState()
        }
        if (ConditionSource.SENSOR in neededSources) {
            sensorState = SensorConditionProvider(context).sampleAll()
        }
        if (ConditionSource.POWER in neededSources) {
            powerState = PowerConditionProvider(context).getState()
        }

        return triggers.filter { trigger ->
            val met = trigger.conditions.all { condition ->
                val actualValue = resolveValue(
                    condition,
                    batteryState ?: emptyMap(),
                    settingState ?: emptyMap(),
                    sensorState ?: emptyMap(),
                    powerState ?: emptyMap()
                )
                val result = evaluate(condition, actualValue)
                if (!result) {
                    Lumberjack.d(TAG, "Condition not met: ${condition.source}.${condition.field} ${condition.operator} ${condition.targetValue} (actual=$actualValue)")
                }
                result
            }
            if (met) {
                Lumberjack.i(TAG, "All conditions met for trigger: '${trigger.title}' (${trigger.id})")
            }
            met
        }
    }

    private fun resolveValue(
        condition: TriggerCondition,
        battery: Map<String, String>,
        settings: Map<String, String>,
        sensor: Map<String, String>,
        power: Map<String, String>
    ): String? = when (condition.source) {
        ConditionSource.BATTERY -> battery[condition.field.lowercase()]
        ConditionSource.SYSTEM_SETTING -> settings[condition.field.lowercase()]
        ConditionSource.SENSOR -> sensor[condition.field.lowercase()]
        ConditionSource.POWER -> power[condition.field.lowercase()]
    }

    fun evaluate(condition: TriggerCondition, actualValue: String?): Boolean {
        val targetValue = condition.targetValue

        return when (condition.operator) {
            ConditionOperator.EQUAL -> actualValue != null && actualValue == targetValue
            ConditionOperator.NOT_EQUAL -> actualValue != null && actualValue != targetValue
            ConditionOperator.GREATER_THAN -> compare(actualValue, targetValue)?.let { it > 0 } ?: false
            ConditionOperator.LESS_THAN -> compare(actualValue, targetValue)?.let { it < 0 } ?: false
            ConditionOperator.GREATER_OR_EQUAL -> compare(actualValue, targetValue)?.let { it >= 0 } ?: false
            ConditionOperator.LESS_OR_EQUAL -> compare(actualValue, targetValue)?.let { it <= 0 } ?: false
            ConditionOperator.CHANGED -> hasChanged(condition, actualValue)
            ConditionOperator.BECOMES_TRUE -> becomesBoolean(condition, actualValue, true)
            ConditionOperator.BECOMES_FALSE -> becomesBoolean(condition, actualValue, false)
        }
    }

    private fun compare(actual: String?, target: String?): Int? {
        if (actual == null || target == null) return null
        val aNum = actual.toDoubleOrNull()
        val tNum = target.toDoubleOrNull()
        if (aNum != null && tNum != null) {
            return aNum.compareTo(tNum)
        }
        return actual.compareTo(target)
    }

    private fun hasChanged(condition: TriggerCondition, actualValue: String?): Boolean {
        val key = stateKey(condition)
        val previous = prefs.getString(key, null)
        val actual = actualValue ?: return false

        if (previous != actual) {
            prefs.edit().putString(key, actual).apply()
            Lumberjack.d(TAG, "State changed for $key: $previous -> $actual")
            return true
        }
        return false
    }

    private fun becomesBoolean(condition: TriggerCondition, actualValue: String?, targetState: Boolean): Boolean {
        val key = stateKey(condition)
        val previous = prefs.getString(key, null)
        val currentIsTrue = actualValue?.lowercase() == "true"
        val previousIsTrue = previous == "true"

        // Store current value
        if (actualValue != null) {
            prefs.edit().putString(key, actualValue).apply()
        }

        // "becomes_true": was NOT true, now IS true
        // "becomes_false": was true, now IS NOT true
        val triggered = when (targetState) {
            true -> !previousIsTrue && currentIsTrue
            false -> previousIsTrue && !currentIsTrue
        }

        if (triggered) {
            Lumberjack.d(TAG, "Boolean transition for $key: $previous -> $actualValue")
        }
        return triggered
    }

    private fun stateKey(condition: TriggerCondition): String =
        "cond_${condition.source.name}_${condition.field}"

    /**
     * Calculate adaptive polling interval based on how close conditions are to their thresholds.
     * When any condition is near its threshold, poll more frequently for faster response.
     *
     * @param baseIntervalMinutes the user-configured base interval
     * @return recommended interval in milliseconds
     */
    suspend fun getRecommendedIntervalMs(baseIntervalMinutes: Int): Long {
        val baseMs = baseIntervalMinutes * 60_000L
        val triggers = ConditionalTriggerRepository(context).listEnabled()
        if (triggers.isEmpty()) return baseMs

        val batteryState = BatteryConditionProvider(context).getState()
        val settingState = SettingConditionProvider(context).getState()
        val powerState = PowerConditionProvider(context).getState()
        // Skip sensor sampling for interval calculation to keep it lightweight

        var minProximity = 1.0  // 0 = at threshold, 1 = far

        for (trigger in triggers) {
            for (condition in trigger.conditions) {
                if (condition.targetValue == null) continue
                val actualValue = when (condition.source) {
                    ConditionSource.BATTERY -> batteryState[condition.field.lowercase()]
                    ConditionSource.SYSTEM_SETTING -> settingState[condition.field.lowercase()]
                    ConditionSource.POWER -> powerState[condition.field.lowercase()]
                    ConditionSource.SENSOR -> null  // skip sensors
                } ?: continue

                val aNum = actualValue.toDoubleOrNull() ?: continue
                val tNum = condition.targetValue.toDoubleOrNull() ?: continue

                when (condition.operator) {
                    ConditionOperator.GREATER_THAN,
                    ConditionOperator.LESS_THAN,
                    ConditionOperator.GREATER_OR_EQUAL,
                    ConditionOperator.LESS_OR_EQUAL -> {
                        if (tNum == 0.0) continue
                        val proximity = kotlin.math.abs(aNum - tNum) / kotlin.math.abs(tNum)
                        if (proximity < minProximity) minProximity = proximity
                    }
                    else -> {}
                }
            }
        }

        // Adaptive mapping: closer to threshold → shorter interval
        // proximity < 0.1 → 25% of base interval (e.g., 30s for 2min base)
        // proximity < 0.25 → 50% of base interval
        // proximity < 0.5 → 75% of base interval
        // otherwise → 100% (base interval)
        val factor = when {
            minProximity < 0.1 -> 0.25
            minProximity < 0.25 -> 0.5
            minProximity < 0.5 -> 0.75
            else -> 1.0
        }

        val adaptiveMs = (baseMs * factor).toLong().coerceIn(30_000, baseMs)
        if (adaptiveMs != baseMs) {
            Lumberjack.d(TAG, "Adaptive interval: ${adaptiveMs / 1000}s (proximity=${String.format("%.2f", minProximity)}, factor=$factor)")
        }
        return adaptiveMs
    }

    companion object {
        private const val TAG = "ConditionEvaluator"
        private const val PREFS_NAME = "condition_evaluator_state"
    }
}
