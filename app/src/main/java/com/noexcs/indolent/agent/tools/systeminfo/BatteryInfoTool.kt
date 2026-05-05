package com.noexcs.indolent.agent.tools.systeminfo

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.logging.Lumberjack
import kotlin.math.abs

class BatteryInfoTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "get_battery_info"
    override val description = """
        Read current battery and power state of this Android device.

        Returns:
        - Battery level (percentage) and raw scale/current values
        - Charging status: charging (AC/USB/wireless), discharging, full, unknown
        - Estimated time remaining to charge (when available)
        - Battery health: cold, dead, good, overheat, overvoltage, unknown
        - Temperature in Celsius, voltage in millivolts
        - Battery technology (e.g. Li-ion)
        - Design capacity vs remaining charge (when available)
        - Current draw in μA (instant and average)
        - Whether battery saver / power save mode is active

        Use this to check battery before starting power-intensive operations or to
        report device status to the user.
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryIntent = appContext.registerReceiver(null, filter)
            if (batteryIntent == null) return "Error: Cannot read battery state"

            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (scale > 0) (level * 100f / scale).toInt() else -1

            val statusStr = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not charging"
                else -> "unknown"
            }

            val pluggedStr = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                else -> "none"
            }

            val healthStr = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_COLD -> "cold"
                BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "overvoltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "unspecified failure"
                else -> "unknown"
            }

            val temp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val technology = batteryIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "unknown"
            val present = batteryIntent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)

            val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val chargeRemaining = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: -1
            val currentNow = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
            val avgCurrent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) ?: Int.MIN_VALUE

            val powerSaveMode = (appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isPowerSaveMode ?: false

            // Time to charge estimate (API 28+)
            val timeToChargeMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && bm != null) {
                bm.computeChargeTimeRemaining() / 1_000_000 // nanos → millis
            } else -1L

            buildString {
                appendLine("Battery: $pct%")
                appendLine("status: $statusStr")
                if (statusStr == "charging") appendLine("charger: $pluggedStr")
                appendLine("health: $healthStr")
                if (temp > 0) appendLine("temperature: ${temp / 10f}°C")
                if (voltage > 0) appendLine("voltage: ${voltage}mV")
                appendLine("technology: $technology")
                appendLine("present: $present")
                if (capacity > 0) appendLine("designCapacity: $capacity%")
                if (chargeRemaining > 0) appendLine("chargeRemaining: ${chargeRemaining}μAh")
                if (currentNow != Int.MIN_VALUE) appendLine("currentNow: ${currentNow}μA")
                if (avgCurrent != Int.MIN_VALUE) appendLine("averageCurrent: ${avgCurrent}μA")
                if (powerSaveMode) appendLine("batterySaver: on")

                if (statusStr == "charging" && timeToChargeMs > 0) {
                    val mins = timeToChargeMs / 60_000
                    appendLine("timeToFull: ${mins}min (${mins / 60}h ${mins % 60}m)")
                } else if (statusStr == "discharging" && pct > 0 && currentNow < 0) {
                    // Rough estimate: remaining capacity / discharge rate
                    // chargeRemaining is in μAh, currentNow is in μA (negative when discharging)
                    if (chargeRemaining > 0) {
                        val hours = chargeRemaining.toFloat() / abs(currentNow.toFloat())
                        val totalMin = (hours * 60).toLong()
                        appendLine("timeToEmpty (est): ${totalMin}min (${totalMin / 60}h ${totalMin % 60}m)")
                    }
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("BatteryInfoTool", "Error reading battery info", e)
            "Error reading battery info: ${e.message}"
        }
    }
}