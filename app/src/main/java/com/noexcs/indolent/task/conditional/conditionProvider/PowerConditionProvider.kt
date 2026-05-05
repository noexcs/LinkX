package com.noexcs.indolent.task.conditional.conditionProvider

import android.content.Context
import android.os.Build
import android.os.PowerManager

class PowerConditionProvider(private val context: Context) {

    fun getState(): Map<String, String> {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = pm?.isPowerSaveMode ?: false
        val isInteractive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            pm?.isInteractive ?: true
        } else true

        return mapOf(
            "is_power_save" to isPowerSave.toString(),
            "is_interactive" to isInteractive.toString(),
            "screen_on" to isInteractive.toString()
        )
    }
}
