package com.noexcs.indolent.task.conditional.conditionProvider

import android.content.Context
import android.provider.Settings
import kotlin.collections.iterator

class SettingConditionProvider(private val context: Context) {

    data class SettingDef(val constant: String, val type: String, val isGlobal: Boolean = false)

    private val keyMap = mapOf(
        "brightness" to SettingDef(Settings.System.SCREEN_BRIGHTNESS, "int"),
        "screen_brightness" to SettingDef(Settings.System.SCREEN_BRIGHTNESS, "int"),
        "auto_brightness" to SettingDef(Settings.System.SCREEN_BRIGHTNESS_MODE, "int"),
        "brightness_mode" to SettingDef(Settings.System.SCREEN_BRIGHTNESS_MODE, "int"),
        "screen_timeout" to SettingDef(Settings.System.SCREEN_OFF_TIMEOUT, "int"),
        "timeout" to SettingDef(Settings.System.SCREEN_OFF_TIMEOUT, "int"),
        "font_scale" to SettingDef(Settings.System.FONT_SCALE, "float"),
        "animator_scale" to SettingDef(Settings.Global.ANIMATOR_DURATION_SCALE, "float", isGlobal = true),
        "transition_scale" to SettingDef(Settings.Global.TRANSITION_ANIMATION_SCALE, "float", isGlobal = true),
        "window_animation_scale" to SettingDef(Settings.Global.WINDOW_ANIMATION_SCALE, "float", isGlobal = true),
        "haptic_feedback" to SettingDef(Settings.System.HAPTIC_FEEDBACK_ENABLED, "int"),
        "sound_effects" to SettingDef(Settings.System.SOUND_EFFECTS_ENABLED, "int"),
        "accelerometer_rotation" to SettingDef(Settings.System.ACCELEROMETER_ROTATION, "int"),
        "date_format" to SettingDef(Settings.System.DATE_FORMAT, "string"),
        "time_12_24" to SettingDef(Settings.System.TIME_12_24, "string"),
        "screen_brightness_mode" to SettingDef(Settings.System.SCREEN_BRIGHTNESS_MODE, "int")
    )

    fun getState(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val cr = context.contentResolver

        for ((key, def) in keyMap) {
            val value = try {
                when (def.type) {
                    "int" -> if (def.isGlobal) Settings.Global.getInt(cr, def.constant).toString()
                             else Settings.System.getInt(cr, def.constant).toString()
                    "float" -> if (def.isGlobal) Settings.Global.getFloat(cr, def.constant).toString()
                              else Settings.System.getFloat(cr, def.constant).toString()
                    "string" -> if (def.isGlobal) Settings.Global.getString(cr, def.constant) ?: "(default)"
                               else Settings.System.getString(cr, def.constant) ?: "(default)"
                    else -> "(unknown type)"
                }
            } catch (e: Settings.SettingNotFoundException) {
                "(not set)"
            }
            result[key] = value
        }

        return result
    }

    fun resolveKey(name: String): SettingDef? = keyMap[name.lowercase().trim()]
}
