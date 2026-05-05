package com.noexcs.indolent.task.scheduler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object DeviceOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    fun openAutoStartSettings(context: Context): Boolean {
        return tryAutoStartIntent(context)
    }

    private fun tryAutoStartIntent(context: Context): Boolean {
        val manufacturers = listOf(
            "xiaomi" to listOf(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            "redmi" to listOf(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            "huawei" to listOf(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            "honor" to listOf(
                "com.hihonor.systemmanager",
                "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            "oppo" to listOf(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            "vivo" to listOf(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            ),
            "samsung" to listOf(
                "com.samsung.android.sm_cn",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            ),
            "oneplus" to listOf(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            ),
            "realme" to listOf(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            "letv" to listOf(
                "com.letv.android.letvsafe",
                "com.letv.android.letvsafe.AutobootManageActivity"
            ),
            "meizu" to listOf(
                "com.meizu.safe",
                "com.meizu.safe.security.AppPermissionActivity"
            ),
        )

        for ((_, pair) in manufacturers) {
            val intent = Intent().apply {
                setClassName(pair[0], pair[1])
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        }

        return false
    }
}
