package com.noexcs.indolent.agent.tools.systeminfo

import android.app.usage.UsageStatsManager
import android.content.Context
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CurrentScreenInfoTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "get_current_screen"
    override val description = """
        Get information about what is currently shown on screen — specifically which app
        is in the foreground.

        Returns:
        - Foreground app package name and human-readable label
        - Recent app usage (last 5 apps with timestamps)
        - Whether usage-stats permission is granted

        Requires: "Usage access" permission (PACKAGE_USAGE_STATS). If not granted,
        the tool reports the permission is missing and returns recent usage info
        via an accessibility-adjacent fallback when possible.

        Use this to understand what the user is likely looking at, to provide
        context-aware assistance.
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm == null) {
                return@withContext "Error: UsageStats service not available on this device."
            }

            // Check if permission is granted. On API 23+, check via AppOpsManager proxy.
            // Simple check: try to query and see if we get results.
            val now = System.currentTimeMillis()
            val startTime = now - 60_000 // last minute
            val usageList = try {
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, now)
            } catch (e: SecurityException) {
                return@withContext errorMissingPermission()
            }

            if (usageList.isNullOrEmpty()) {
                return@withContext errorMissingPermission()
            }

            // Sort by last time used descending
            val sorted = usageList.sortedByDescending { it.lastTimeUsed }
            val foreground = sorted.firstOrNull { it.lastTimeStamp > 0 }

            buildString {
                if (foreground != null) {
                    appendLine("foregroundPackage: ${foreground.packageName}")
                    val label = appLabel(foreground.packageName)
                    if (label != null) appendLine("foregroundApp: $label")
                    appendLine("lastUsed: ${formatTime(foreground.lastTimeUsed)}")
                    appendLine("totalTimeForeground: ${foreground.totalTimeInForeground / 1000}s")
                } else {
                    appendLine("foreground: unknown (no recent usage data)")
                }

                // Recent apps
                appendLine()
                appendLine("recentApps:")
                val recent = sorted.filter { it.lastTimeUsed > 0 }.take(5)
                recent.forEach { u ->
                    val label = appLabel(u.packageName) ?: u.packageName
                    appendLine("  - $label (${u.packageName}) last used: ${formatTime(u.lastTimeUsed)}")
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("CurrentScreenInfoTool", "Error reading screen info", e)
            "Error reading screen info: ${e.message}"
        }
    }

    private fun appLabel(packageName: String): String? {
        return try {
            val info = appContext.packageManager.getApplicationInfo(packageName, 0)
            appContext.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            Lumberjack.e("CurrentScreenInfoTool", "Error getting app label for $packageName", e)
            null
        }
    }

    private fun formatTime(epochMs: Long): String {
        if (epochMs <= 0) return "never"
        val diff = System.currentTimeMillis() - epochMs
        return when {
            diff < 5_000 -> "now"
            diff < 60_000 -> "${diff / 1000}s ago"
            diff < 3_600_000 -> "${diff / 60_000}min ago"
            else -> "${diff / 3_600_000}h ago"
        }
    }

    private fun errorMissingPermission(): String {
        return buildString {
            appendLine("Error: Usage access permission not granted.")
            appendLine()
            appendLine("To enable, guide the user to:")
            appendLine("  Settings → Security & Privacy → More privacy settings → Usage access")
            appendLine("  Or search \"Usage access\" in system Settings.")
            appendLine("Then grant access to this app (Indolent).")
        }
    }
}