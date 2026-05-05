package com.noexcs.indolent.agent.tools.setting

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

class SystemSettingTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "system_setting"
    override val description = """
        Read or modify Android system settings (Settings.System).

        Actions:
        - "list" — show all supported settings with current values
        - "get" — read a specific setting by key (e.g. "brightness", "screen_timeout")
        - "set" — change a setting value (requires WRITE_SETTINGS permission)

        Supported keys: brightness, auto_brightness, screen_timeout, font_scale,
        animator_scale, transition_scale, window_animation_scale, haptic_feedback,
        sound_effects, accelerometer_rotation, date_format, time_12_24.

        WRITE_SETTINGS permission is declared; the user must grant it once in
        Settings > Apps > Special app access > Modify system settings.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "action",
            type = "string",
            description = "\"get\", \"set\", or \"list\""
        ),
        ToolParameter(
            name = "key",
            type = "string",
            required = false,
            description = "Setting key name (e.g. \"brightness\", \"screen_timeout\"). Required for get/set."
        ),
        ToolParameter(
            name = "value",
            type = "string",
            required = false,
            description = "New value for the setting. Required for set action."
        )
    )

    private data class SettingDef(
        val constant: String,
        val type: String,
        val description: String,
        val exampleValue: String? = null
    )

    private val keyMap = mapOf(
        "brightness" to SettingDef(Settings.System.SCREEN_BRIGHTNESS, "int", "Screen brightness (0–255, higher=brighter)", "128"),
        "screen_brightness" to SettingDef(Settings.System.SCREEN_BRIGHTNESS, "int", "Screen brightness (0–255, higher=brighter)", "128"),
        "auto_brightness" to SettingDef(Settings.System.SCREEN_BRIGHTNESS_MODE, "int", "Auto-brightness: 0=manual, 1=auto", "1"),
        "brightness_mode" to SettingDef(Settings.System.SCREEN_BRIGHTNESS_MODE, "int", "Auto-brightness: 0=manual, 1=auto", "1"),
        "screen_timeout" to SettingDef(Settings.System.SCREEN_OFF_TIMEOUT, "int", "Screen sleep timeout in milliseconds (e.g. 15000=15s, 30000=30s, 60000=1min)", "30000"),
        "timeout" to SettingDef(Settings.System.SCREEN_OFF_TIMEOUT, "int", "Screen sleep timeout in milliseconds", "30000"),
        "font_scale" to SettingDef(Settings.System.FONT_SCALE, "float", "System font scale (1.0=normal, 1.15=large)", "1.0"),
        "animator_scale" to SettingDef(Settings.System.ANIMATOR_DURATION_SCALE, "float", "Animator duration scale (0=off, 1.0=normal)", "1.0"),
        "transition_scale" to SettingDef(Settings.System.TRANSITION_ANIMATION_SCALE, "float", "Transition animation scale (0=off, 1.0=normal)", "1.0"),
        "window_animation_scale" to SettingDef(Settings.System.WINDOW_ANIMATION_SCALE, "float", "Window animation scale (0=off, 1.0=normal)", "1.0"),
        "haptic_feedback" to SettingDef(Settings.System.HAPTIC_FEEDBACK_ENABLED, "int", "Haptic feedback: 0=off, 1=on", "1"),
        "sound_effects" to SettingDef(Settings.System.SOUND_EFFECTS_ENABLED, "int", "UI sound effects: 0=off, 1=on", "1"),
        "accelerometer_rotation" to SettingDef(Settings.System.ACCELEROMETER_ROTATION, "int", "Auto-rotate screen: 0=off, 1=on", "1"),
        "date_format" to SettingDef(Settings.System.DATE_FORMAT, "string", "Date format string (e.g. \"yyyy-MM-dd\")", "yyyy-MM-dd"),
        "time_12_24" to SettingDef(Settings.System.TIME_12_24, "string", "Time format: \"12\" or \"24\"", "24"),
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val action = args["action"] as? String ?: return "Error: action is required (\"get\", \"set\", or \"list\")"
        val key = args["key"] as? String
        val rawValue = args["value"] as? String

        Lumberjack.i("SystemSettingTool", "Action=$action key=$key value=$rawValue")

        return try {
            when (action.lowercase()) {
                "list" -> listAll()
                "get" -> getSetting(key)
                "set" -> setSetting(key, rawValue)
                else -> "Error: Unknown action '$action'. Use \"get\", \"set\", or \"list\"."
            }
        } catch (e: SecurityException) {
            Lumberjack.e("SystemSettingTool", "Permission denied", e)
            buildPermissionError()
        } catch (e: Exception) {
            Lumberjack.e("SystemSettingTool", "Operation failed", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    private fun getSetting(key: String?): String {
        if (key == null) return "Error: key is required for get action"

        val def = resolveKey(key) ?: return buildString {
            appendLine("Error: Unknown setting key '$key'.")
            appendLine("Supported keys: ${keyMap.keys.toSet().sorted().joinToString(", ")}")
        }

        val value = Settings.System.getString(appContext.contentResolver, def.constant)
            ?: Settings.System.getInt(appContext.contentResolver, def.constant, -1)
                .takeIf { it != -1 }
                ?.toString()
            ?: Settings.System.getFloat(appContext.contentResolver, def.constant, -1f)
                .takeIf { it != -1f }
                ?.toString()
            ?: Settings.System.getLong(appContext.contentResolver, def.constant, -1L)
                .takeIf { it != -1L }
                ?.toString()
            ?: "(not set, using system default)"

        return "$key = $value (type: ${def.type}, ${def.description})"
    }

    private fun setSetting(key: String?, value: String?): String {
        if (key == null) return "Error: key is required for set action"
        if (value == null) return "Error: value is required for set action"

        val def = resolveKey(key) ?: return buildString {
            appendLine("Error: Unknown setting key '$key'.")
            appendLine("Supported keys: ${keyMap.keys.toSet().sorted().joinToString(", ")}")
        }

        // Check WRITE_SETTINGS permission
        if (!Settings.System.canWrite(appContext)) {
            return buildPermissionError()
        }

        val success = when (def.type) {
            "int" -> Settings.System.putInt(appContext.contentResolver, def.constant, value.toIntOrNull() ?: return "Error: '$value' is not a valid integer")
            "float" -> Settings.System.putFloat(appContext.contentResolver, def.constant, value.toFloatOrNull() ?: return "Error: '$value' is not a valid float")
            "string" -> Settings.System.putString(appContext.contentResolver, def.constant, value)
            else -> false
        }

        return if (success) {
            Lumberjack.i("SystemSettingTool", "Set $key = $value")
            "OK: $key set to $value"
        } else {
            "Error: Failed to write setting. The value may be outside the allowed range."
        }
    }

    private fun listAll(): String = buildString {
        appendLine("System Settings")
        appendLine("─".repeat(50))
        keyMap.entries.groupBy { it.value.constant }.forEach { (constant, entries) ->
            val alias = entries.first().key
            val def = entries.first().value
            val value = Settings.System.getString(appContext.contentResolver, constant)
                ?: Settings.System.getInt(appContext.contentResolver, constant, -1)
                    .takeIf { it != -1 }?.toString()
                ?: Settings.System.getFloat(appContext.contentResolver, constant, -1f)
                    .takeIf { it != -1f }?.toString()
                ?: Settings.System.getLong(appContext.contentResolver, constant, -1L)
                    .takeIf { it != -1L }?.toString()
                ?: "(default)"
            appendLine("  $alias = $value  [${def.type}] ${def.description}")
        }
        appendLine()
        appendLine("Can write: ${if (Settings.System.canWrite(appContext)) "yes" else "no (grant in Settings > Apps > Special app access > Modify system settings)"}")
    }

    private fun resolveKey(name: String): SettingDef? {
        return keyMap[name.lowercase().trim()]
    }

    private fun buildPermissionError(): String = buildString {
        appendLine("Error: WRITE_SETTINGS permission not granted.")
        appendLine()
        appendLine("This app needs permission to modify system settings.")
        appendLine("To grant it: Settings > Apps > Indolent > Modify system settings (allow)")
        appendLine()
        // Provide a direct intent to the grant page
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${appContext.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            appContext.startActivity(intent)
            appendLine("A settings page should have opened. Grant permission there and retry.")
        } catch (_: Exception) {
            appendLine("Could not open settings automatically. Please navigate manually.")
        }
    }
}
