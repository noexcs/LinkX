package com.noexcs.indolent.agent.tools.screen

import android.content.Context
import android.content.Intent
import android.provider.Settings

object AccessibilityHelper {

    fun isEnabled(context: Context): Boolean {
        val fullName = "${context.packageName}/${LinkXAccessibilityService::class.java.name}"
        val shortName = "${context.packageName}/.${LinkXAccessibilityService::class.java.simpleName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        // Format is "pkg/cls1:pkg/cls2" or "pkg/.Cls1:pkg/.Cls2"
        return enabledServices.split(':').any { entry ->
            entry.equals(fullName, ignoreCase = true) ||
                entry.equals(shortName, ignoreCase = true)
        }
    }

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
