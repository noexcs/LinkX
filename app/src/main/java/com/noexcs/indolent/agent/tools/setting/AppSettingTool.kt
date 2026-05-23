package com.noexcs.indolent.agent.tools.setting

import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.data.fetchUserBalance
import com.noexcs.indolent.logging.Lumberjack

class AppSettingTool(private val settings: SettingsManager) : AgentTool {

    override val name = "app_setting"
    override val description = """
        Read or modify the app's own settings, and query API usage / balance.

        Actions:
        - "list" — show all app settings grouped by category
        - "get" — read a specific setting by key
        - "set" — change a setting value
        - "balance" — fetch API account balance (requires configured base_url + api_key)

        Settings are applied immediately after being changed.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "action",
            type = "string",
            description = "\"get\", \"set\", \"list\", or \"balance\""
        ),
        ToolParameter(
            name = "key",
            type = "string",
            required = false,
            description = "Setting key name. Required for get/set."
        ),
        ToolParameter(
            name = "value",
            type = "string",
            required = false,
            description = "New value for the setting. Required for set action."
        )
    )

    private data class SettingDef(
        val type: String,
        val description: String,
        val readonly: Boolean = false,
        val sensitive: Boolean = false
    )

    private val registry = mapOf(
        // API / provider
        "provider_type" to SettingDef("string", "LLM provider (e.g. \"deepseek\")"),
        "base_url" to SettingDef("string", "API base URL"),
        "api_key" to SettingDef("string", "API authentication key", sensitive = true),
        "model" to SettingDef("string", "Model name (e.g. \"deepseek-chat\")"),
        "thinking_enabled" to SettingDef("boolean", "Deep thinking / reasoning toggle"),
        "reasoning_effort" to SettingDef("string", "Reasoning effort level (\"high\" or \"max\")"),
        "language" to SettingDef("string", "UI language (empty=system, \"en\", \"zh-Hans\")"),
        "user_system_prompt" to SettingDef("string", "User-defined extra system prompt"),

        // Tool group toggles
        "fund_tools_enabled" to SettingDef("boolean", "Fund tools group"),
        "termux_tools_enabled" to SettingDef("boolean", "Termux tools group"),
        "common_tools_enabled" to SettingDef("boolean", "Common tools group"),
        "conditional_tools_enabled" to SettingDef("boolean", "Conditional trigger tools group"),
        "filesystem_tools_enabled" to SettingDef("boolean", "Filesystem tools group"),
        "interact_tools_enabled" to SettingDef("boolean", "Interaction tools group"),
        "notification_tools_enabled" to SettingDef("boolean", "Notification tools group"),
        "scheduled_task_tools_enabled" to SettingDef("boolean", "Scheduled task tools group"),
        "self_tools_enabled" to SettingDef("boolean", "Self tools group"),
        "sensor_tools_enabled" to SettingDef("boolean", "Sensor tools group"),
        "setting_tools_enabled" to SettingDef("boolean", "Setting tools group (this tool's own group)"),
        "system_info_tools_enabled" to SettingDef("boolean", "System info tools group"),

        // Heartbeat
        "heartbeat_enabled" to SettingDef("boolean", "Heartbeat feature"),
        "heartbeat_interval_minutes" to SettingDef("int", "Heartbeat interval in minutes (1-1440)"),
        "heartbeat_custom_prompt" to SettingDef("string", "Custom prompt for heartbeat tasks"),

        // Usage stats (read-only)
        "cumulative_prompt_tokens" to SettingDef("long", "Cumulative prompt tokens used", readonly = true),
        "cumulative_completion_tokens" to SettingDef("long", "Cumulative completion tokens used", readonly = true),

        // Condition monitor
        "condition_monitor_enabled" to SettingDef("boolean", "Condition monitor switch"),
        "condition_monitor_interval_minutes" to SettingDef("int", "Condition monitor interval in minutes"),
    )

    private val settingGroups = listOf(
        "API / Provider" to listOf(
            "provider_type", "base_url", "api_key", "model",
            "thinking_enabled", "reasoning_effort", "user_system_prompt", "language"
        ),
        "Tool Groups" to listOf(
            "fund_tools_enabled", "termux_tools_enabled", "common_tools_enabled",
            "conditional_tools_enabled", "filesystem_tools_enabled", "interact_tools_enabled",
            "notification_tools_enabled", "scheduled_task_tools_enabled", "self_tools_enabled",
            "sensor_tools_enabled", "setting_tools_enabled", "system_info_tools_enabled"
        ),
        "Heartbeat" to listOf(
            "heartbeat_enabled", "heartbeat_interval_minutes", "heartbeat_custom_prompt"
        ),
        "Condition Monitor" to listOf(
            "condition_monitor_enabled", "condition_monitor_interval_minutes"
        ),
        "Usage Stats" to listOf(
            "cumulative_prompt_tokens", "cumulative_completion_tokens"
        ),
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val action = args["action"] as? String
            ?: return "Error: action is required (\"get\", \"set\", \"list\", or \"balance\")"
        val key = args["key"] as? String
        val value = args["value"] as? String

        Lumberjack.i("AppSettingTool", "Action=$action key=$key value=$value")

        return try {
            when (action.lowercase()) {
                "list" -> listAll()
                "get" -> getSetting(key)
                "set" -> setSetting(key, value)
                "balance" -> fetchBalance()
                else -> "Error: Unknown action '$action'. Use \"get\", \"set\", \"list\", or \"balance\"."
            }
        } catch (e: Exception) {
            Lumberjack.e("AppSettingTool", "Operation failed", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    private fun listAll(): String = buildString {
        appendLine("App Settings")
        appendLine("═".repeat(60))

        settingGroups.forEach { (groupName, keys) ->
            appendLine()
            appendLine("▸ $groupName")
            appendLine("─".repeat(40))
            keys.forEach { key ->
                val def = registry[key] ?: return@forEach
                val raw = getRawValue(key)
                val display = if (def.sensitive) maskValue(raw) else raw
                val ro = if (def.readonly) " [read-only]" else ""
                appendLine("  $key = $display  [${def.type}]$ro")
            }
        }

        // Dynamic per-tool toggles
        val toolKeys = settings.getToolSettingKeys().sorted()
        if (toolKeys.isNotEmpty()) {
            appendLine()
            appendLine("▸ Per-Tool Toggles")
            appendLine("─".repeat(40))
            toolKeys.forEach { key ->
                val enabled = settings.isToolEnabled(key)
                appendLine("  tool_enabled_$key = $enabled  [boolean]")
            }
        }
    }

    private fun getSetting(key: String?): String {
        if (key == null) return "Error: key is required for get action"

        // Handle per-tool dynamic key
        if (key.startsWith("tool_enabled_")) {
            val toolName = key.removePrefix("tool_enabled_")
            val value = settings.isToolEnabled(toolName)
            return "$key = $value (type: boolean, per-tool toggle for \"$toolName\")"
        }

        val def = registry[key]
            ?: return buildString {
                appendLine("Error: Unknown setting key '$key'.")
                appendLine("Supported keys: ${registry.keys.sorted().joinToString(", ")}")
                appendLine("Per-tool keys: tool_enabled_<toolName> (e.g. tool_enabled_execute_command)")
            }

        val raw = getRawValue(key)
        val display = if (def.sensitive) maskValue(raw) else raw
        return "$key = $display (type: ${def.type}, ${def.description})"
    }

    private fun setSetting(key: String?, value: String?): String {
        if (key == null) return "Error: key is required for set action"
        if (value == null) return "Error: value is required for set action"

        // Handle per-tool dynamic key
        if (key.startsWith("tool_enabled_")) {
            val toolName = key.removePrefix("tool_enabled_")
            val boolVal = parseBool(value)
                ?: return "Error: '$value' is not a valid boolean (use true/false/0/1)"
            settings.setToolEnabled(toolName, boolVal)
            Lumberjack.i("AppSettingTool", "Set $key = $boolVal")
            return "OK: $key set to $boolVal"
        }

        val def = registry[key]
            ?: return buildString {
                appendLine("Error: Unknown setting key '$key'.")
                appendLine("Supported keys: ${registry.keys.sorted().joinToString(", ")}")
            }

        if (def.readonly) {
            return "Error: '$key' is read-only and cannot be modified."
        }

        return when (def.type) {
            "string" -> {
                settings.setRawString(key, value)
                "OK: $key set to \"$value\""
            }
            "boolean" -> {
                val boolVal = parseBool(value)
                    ?: return "Error: '$value' is not a valid boolean (use true/false/0/1)"
                settings.setRawBoolean(key, boolVal)
                "OK: $key set to $boolVal"
            }
            "int" -> {
                val intVal = value.toIntOrNull()
                    ?: return "Error: '$value' is not a valid integer"
                settings.setRawInt(key, intVal)
                "OK: $key set to $intVal"
            }
            "long" -> {
                val longVal = value.toLongOrNull()
                    ?: return "Error: '$value' is not a valid long integer"
                settings.setRawLong(key, longVal)
                "OK: $key set to $longVal"
            }
            else -> "Error: Unknown type '${def.type}' for setting '$key'."
        }
    }

    // ---- value accessors ----

    private fun getRawValue(key: String): String {
        return when (key) {
            "provider_type" -> settings.providerType?.id ?: "deepseek"
            "base_url" -> settings.baseUrl
            "api_key" -> settings.apiKey
            "model" -> settings.model
            "thinking_enabled" -> settings.thinkingEnabled.toString()
            "reasoning_effort" -> settings.reasoningEffort
            "language" -> settings.language.ifEmpty { "(system default)" }
            "user_system_prompt" -> settings.userSystemPrompt.ifEmpty { "(empty)" }
            "fund_tools_enabled" -> settings.fundToolsEnabled.toString()
            "termux_tools_enabled" -> settings.termuxToolsEnabled.toString()
            "common_tools_enabled" -> settings.commonToolsEnabled.toString()
            "conditional_tools_enabled" -> settings.conditionalToolsEnabled.toString()
            "filesystem_tools_enabled" -> settings.filesystemToolsEnabled.toString()
            "interact_tools_enabled" -> settings.interactToolsEnabled.toString()
            "notification_tools_enabled" -> settings.notificationToolsEnabled.toString()
            "scheduled_task_tools_enabled" -> settings.scheduledTaskToolsEnabled.toString()
            "self_tools_enabled" -> settings.selfToolsEnabled.toString()
            "sensor_tools_enabled" -> settings.sensorToolsEnabled.toString()
            "setting_tools_enabled" -> settings.settingToolsEnabled.toString()
            "system_info_tools_enabled" -> settings.systemInfoToolsEnabled.toString()
            "heartbeat_enabled" -> settings.heartbeatEnabled.toString()
            "heartbeat_interval_minutes" -> settings.heartbeatIntervalMinutes.toString()
            "heartbeat_custom_prompt" -> settings.heartbeatCustomPrompt.ifEmpty { "(empty)" }
            "cumulative_prompt_tokens" -> settings.cumulativePromptTokens.toString()
            "cumulative_completion_tokens" -> settings.cumulativeCompletionTokens.toString()
            "condition_monitor_enabled" -> settings.conditionMonitorEnabled.toString()
            "condition_monitor_interval_minutes" -> settings.conditionMonitorIntervalMinutes.toString()
            else -> "(unknown)"
        }
    }

    private fun maskValue(value: String): String {
        if (value.length <= 8) return "***"
        return value.take(4) + "***" + value.takeLast(4)
    }

    private fun parseBool(value: String): Boolean? {
        return when (value.lowercase().trim()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    }

    private suspend fun fetchBalance(): String {
        val baseUrl = settings.baseUrl
        val apiKey = settings.apiKey
        if (baseUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
            return "Error: base_url and api_key must be configured first."
        }

        val response = fetchUserBalance(baseUrl, apiKey)
        return buildString {
            appendLine("API Account Balance")
            appendLine("─".repeat(40))
            if (!response.isAvailable) {
                appendLine("Balance info is not available.")
                return@buildString
            }
            if (response.balanceInfos.isEmpty()) {
                appendLine("No balance entries found.")
                return@buildString
            }
            response.balanceInfos.forEach { info ->
                appendLine("  Currency:     ${info.currency}")
                appendLine("  Total:        ${info.totalBalance}")
                appendLine("  Granted:      ${info.grantedBalance}")
                appendLine("  Topped up:    ${info.toppedUpBalance}")
                appendLine()
            }
        }.trimEnd()
    }
}
