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

    var heartbeatEnabled: Boolean
        get() = prefs.getBoolean("heartbeat_enabled", false)
        set(value) = prefs.edit { putBoolean("heartbeat_enabled", value) }

    var heartbeatIntervalMinutes: Int
        get() = prefs.getInt("heartbeat_interval_minutes", 30)
        set(value) = prefs.edit { putInt("heartbeat_interval_minutes", value) }

    var heartbeatCustomPrompt: String
        get() = prefs.getString("heartbeat_custom_prompt", "") ?: ""
        set(value) = prefs.edit { putString("heartbeat_custom_prompt", value) }

    var conditionMonitorEnabled: Boolean
        get() = prefs.getBoolean("condition_monitor_enabled", true)
        set(value) = prefs.edit { putBoolean("condition_monitor_enabled", value) }

    var conditionMonitorIntervalMinutes: Int
        get() = prefs.getInt("condition_monitor_interval_minutes", 2)
        set(value) = prefs.edit { putInt("condition_monitor_interval_minutes", value) }

    var safRoots: Set<String>
        get() = prefs.getStringSet("saf_roots", emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet("saf_roots", value) }

    fun applyLocale(tag: String = language) {
        val localeManager = appContext.getSystemService(LocaleManager::class.java)
        localeManager.applicationLocales = if (tag.isEmpty()) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(tag)
        }
    }
}
