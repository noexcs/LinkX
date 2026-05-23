package com.noexcs.indolent.agent.tools.conditional

import com.noexcs.indolent.task.conditional.ConditionOperator
import com.noexcs.indolent.task.conditional.ConditionSource
import com.noexcs.indolent.task.conditional.TriggerCondition

internal object ConditionParser {

    fun parseConditions(json: String): List<TriggerCondition> {
        val conditions = mutableListOf<TriggerCondition>()
        val cleaned = json.trim()
        if (!cleaned.startsWith("[")) return conditions

        val objectPattern = Regex("""\{[^}]+\}""")
        objectPattern.findAll(cleaned).forEach { match ->
            val obj = match.value
            val source = extractJsonString(obj, "source")?.uppercase()
            val field = extractJsonString(obj, "field")
            val operator = extractJsonString(obj, "operator")?.uppercase()
            val targetValue = extractJsonString(obj, "targetValue")

            if (source != null && field != null && operator != null) {
                val src = try { ConditionSource.valueOf(source) } catch (_: Exception) { null }
                val op = try { ConditionOperator.valueOf(operator) } catch (_: Exception) { null }
                if (src != null && op != null) {
                    conditions.add(TriggerCondition(src, field, op, targetValue))
                }
            }
        }
        return conditions
    }

    fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"]*)"""")
        return pattern.find(json)?.groupValues?.getOrNull(1)
    }
}
