package com.noexcs.indolent.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.core.content.edit
import com.noexcs.indolent.agent.LLMProvider


class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    var userSystemPrompt: String
        get() = prefs.getString("user_system_prompt", "") ?: ""
        set(value) = prefs.edit { putString("user_system_prompt", value) }

    var providerType: LLMProvider?
        get() {
            val id = prefs.getString("provider_type", "deepseek") ?: "deepseek"
            return if (id == "deepseek") LLMProvider.DeepSeek else null
        }
        set(value) {
            prefs.edit { putString("provider_type", value?.id ?: "deepseek") }
        }

    var baseUrl: String?
        get() = prefs.getString("base_url", "")
        set(value) = prefs.edit { putString("base_url", value) }

    var apiKey: String?
        get() = prefs.getString("api_key", "")
        set(value) = prefs.edit { putString("api_key", value) }

    var model: String?
        get() = prefs.getString("model", "")
        set(value) { prefs.edit { putString("model", value) } }

    var thinkingEnabled: Boolean
        get() = prefs.getBoolean("thinking_enabled", true)
        set(value) = prefs.edit { putBoolean("thinking_enabled", value) }

    var reasoningEffort: String
        get() = prefs.getString("reasoning_effort", "high") ?: "high"
        set(value) = prefs.edit { putString("reasoning_effort", value) }

    var language: String
        get() = prefs.getString("language", "") ?: ""
        set(value) {
            prefs.edit { putString("language", value) }
            applyLocale(value)
        }

    var fundToolsEnabled: Boolean
        get() = prefs.getBoolean("fund_tools_enabled", true)
        set(value) = prefs.edit { putBoolean("fund_tools_enabled", value) }

    var termuxToolsEnabled: Boolean
        get() = prefs.getBoolean("termux_tools_enabled", true)
        set(value) = prefs.edit { putBoolean("termux_tools_enabled", value) }

    var commonToolsEnabled: Boolean
        get() = prefs.getBoolean("common_tools_enabled", true)
        set(value) = prefs.edit { putBoolean("common_tools_enabled", value) }

    var conditionalToolsEnabled: Boolean
        get() = prefs.getBoolean("conditional_tools_enabled", true)
        set(value) = prefs.edit { putBoolean("conditional_tools_enabled", value) }

    var filesystemToolsEnabled: Boolean
        get() = prefs.getBoolean("filesystem_tools_enabled", true)
        set(value) = prefs.edit { putBoolean("filesystem_tools_enabled", value) }

    var interactToolsEnabled: Boolean
        get() = prefs.getBoolean("interact_tools_enabled", true)
        set(value) = prefs.edit { putBoolean("interact_tools_enabled", value) }

    var notificationToolsEnabled: Boolean
        get() = prefs.getBoolean("notification_tools_enabled", true)
        set(value) = prefs.edit { putBoolean("notification_tools_enabled", value) }

    var scheduledTaskToolsEnabled: Boolean
        get() = prefs.getBoolean("scheduled_task_tools_enabled", true)
        set(value) = prefs.edit { putBoolean("scheduled_task_tools_enabled", value) }

    var selfToolsEnabled: Boolean
        get() = prefs.getBoolean("self_tools_enabled", true)
        set(value) = prefs.edit { putBoolean("self_tools_enabled", value) }

    var sensorToolsEnabled: Boolean
        get() = prefs.getBoolean("sensor_tools_enabled", true)
        set(value) = prefs.edit { putBoolean("sensor_tools_enabled", value) }

    var settingToolsEnabled: Boolean
        get() = prefs.getBoolean("setting_tools_enabled", true)
        set(value) = prefs.edit { putBoolean("setting_tools_enabled", value) }

    var systemInfoToolsEnabled: Boolean
        get() = prefs.getBoolean("system_info_tools_enabled", true)
        set(value) = prefs.edit { putBoolean("system_info_tools_enabled", value) }

    var screenToolsEnabled: Boolean
        get() = prefs.getBoolean("screen_tools_enabled", false)
        set(value) = prefs.edit { putBoolean("screen_tools_enabled", value) }

    var heartbeatEnabled: Boolean
        get() = prefs.getBoolean("heartbeat_enabled", false)
        set(value) = prefs.edit { putBoolean("heartbeat_enabled", value) }

    var heartbeatIntervalMinutes: Int
        get() = prefs.getInt("heartbeat_interval_minutes", 30)
        set(value) = prefs.edit { putInt("heartbeat_interval_minutes", value) }

    var heartbeatCustomPrompt: String
        get() = prefs.getString("heartbeat_custom_prompt", "") ?: ""
        set(value) = prefs.edit { putString("heartbeat_custom_prompt", value) }

    var cumulativePromptTokens: Long
        get() = prefs.getLong("cumulative_prompt_tokens", 0L)
        set(value) = prefs.edit { putLong("cumulative_prompt_tokens", value) }

    var cumulativeCompletionTokens: Long
        get() = prefs.getLong("cumulative_completion_tokens", 0L)
        set(value) = prefs.edit { putLong("cumulative_completion_tokens", value) }

    var conditionMonitorEnabled: Boolean
        get() = prefs.getBoolean("condition_monitor_enabled", true)
        set(value) = prefs.edit { putBoolean("condition_monitor_enabled", value) }

    var conditionMonitorIntervalMinutes: Int
        get() = prefs.getInt("condition_monitor_interval_minutes", 2)
        set(value) = prefs.edit { putInt("condition_monitor_interval_minutes", value) }

    var mcpToolsEnabled: Boolean
        get() = prefs.getBoolean("mcp_tools_enabled", false)
        set(value) = prefs.edit { putBoolean("mcp_tools_enabled", value) }

    var mcpServerConfigsJson: String
        get() = prefs.getString("mcp_server_configs", "[]") ?: "[]"
        set(value) = prefs.edit { putString("mcp_server_configs", value) }

    var pythonToolsEnabled: Boolean
        get() = prefs.getBoolean("python_tools_enabled", true)
        set(value) = prefs.edit { putBoolean("python_tools_enabled", value) }

    var skillsEnabled: Boolean
        get() = prefs.getBoolean("skills_enabled", false)
        set(value) = prefs.edit { putBoolean("skills_enabled", value) }

    var activeSkillName: String
        get() = prefs.getString("active_skill_name", "") ?: ""
        set(value) = prefs.edit { putString("active_skill_name", value) }

    var themeKey: String
        get() = prefs.getString("theme_key", "system") ?: "system"
        set(value) = prefs.edit { putString("theme_key", value) }

    var seedColor: Int
        get() = prefs.getInt("seed_color", 0xFF6750A4.toInt())
        set(value) = prefs.edit { putInt("seed_color", value) }

    var dynamicThemesJson: String
        get() = prefs.getString("dynamic_themes_json", "[]") ?: "[]"
        set(value) = prefs.edit { putString("dynamic_themes_json", value) }

    var dynamicColor: Boolean
        get() = prefs.getBoolean("dynamic_color", true)
        set(value) = prefs.edit { putBoolean("dynamic_color", value) }

    fun isSkillEnabled(skillName: String): Boolean =
        prefs.getBoolean("skill_enabled_$skillName", true)

    fun setSkillEnabled(skillName: String, enabled: Boolean) {
        prefs.edit { putBoolean("skill_enabled_$skillName", enabled) }
    }

    fun isToolEnabled(toolName: String): Boolean =
        prefs.getBoolean("tool_enabled_$toolName", true)

    fun setToolEnabled(toolName: String, enabled: Boolean) {
        prefs.edit { putBoolean("tool_enabled_$toolName", enabled) }
    }

    fun getToolSettingKeys(): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith("tool_enabled_") }
            .map { it.removePrefix("tool_enabled_") }
            .toSet()
    }

    fun setRawString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    fun setRawBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    fun setRawInt(key: String, value: Int) {
        prefs.edit { putInt(key, value) }
    }

    fun setRawLong(key: String, value: Long) {
        prefs.edit { putLong(key, value) }
    }

    fun applyLocale(tag: String = language) {
        val localeManager = appContext.getSystemService(LocaleManager::class.java)
        localeManager.applicationLocales = if (tag.isEmpty()) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(tag)
        }
    }
}
