package com.noexcs.indolent.task.conditional.conditionProvider

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager

class BatteryConditionProvider(private val context: Context) {

    fun getState(): Map<String, String> {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter) ?: return emptyMap()

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (scale > 0) (level * 100f / scale).toInt() else -1

        val status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }

        val plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "none"
        }

        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "overvoltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "unspecified_failure"
            else -> "unknown"
        }

        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val currentNow = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE

        val powerSaveMode = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isPowerSaveMode ?: false

        return mutableMapOf(
            "level" to pct.toString(),
            "status" to status,
            "plugged" to plugged,
            "health" to health,
            "temperature" to (temp / 10f).toString(),
            "voltage" to voltage.toString(),
            "capacity" to capacity.toString(),
            "current_now" to (if (currentNow != Int.MIN_VALUE) currentNow.toString() else "unavailable"),
            "battery_saver" to powerSaveMode.toString(),
            "is_charging" to (status == "charging").toString(),
            "is_discharging" to (status == "discharging").toString(),
            "is_full" to (status == "full").toString()
        )
    }
}