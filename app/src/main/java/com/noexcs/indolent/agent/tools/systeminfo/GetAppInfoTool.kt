package com.noexcs.indolent.agent.tools.systeminfo

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GetAppInfoTool(context: Context) : AgentTool {
    private val context = context.applicationContext
    override val name = "get_app_info"
    override val description = "Get list of all installed apps or detailed information about a specific app"

    override val parameters = listOf(
        ToolParameter(
            name = "packageName",
            type = "string",
            description = "Package name of a specific app (leave empty to list all installed apps)",
            required = false
        )
    )


    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val packageName = args["packageName"] as? String ?: ""
            if (packageName.isBlank()) {
                getAllInstalledApps()
            } else {
                getAppDetails(packageName)
            }
        } catch (e: Exception) {
            Lumberjack.e("GetAppInfoTool", "Error executing get_app_info", e)
            "Error: ${e.message}"
        }
    }

    private fun getAllInstalledApps(): String {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        if (packages.isEmpty()) {
            return "No applications found."
        }

        val appList = packages.map { appInfo ->
            val label = appInfo.loadLabel(pm).toString()
            val packageName = appInfo.packageName
            val version = getAppVersion(packageName)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val systemTag = if (isSystem) " [System]" else ""
            "- $label$systemTag ($packageName) v$version"
        }.sorted()

        return buildString {
            appendLine("Installed Applications (${appList.size}):")
            appendLine("=".repeat(50))
            appList.forEach { appendLine(it) }
        }
    }

    private fun getAppDetails(packageName: String): String {
        val pm = context.packageManager

        val packageInfo = try {
            pm.getPackageInfo(
                packageName,
                PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS
            )
        } catch (e: PackageManager.NameNotFoundException) {
            return "Error: App '$packageName' is not installed."
        }

        val appInfo = packageInfo.applicationInfo ?: return "Error: Cannot retrieve app info."
        val label = appInfo.loadLabel(pm).toString()
        val versionName = packageInfo.versionName ?: "Unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        val installTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(packageInfo.firstInstallTime))

        val updateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(packageInfo.lastUpdateTime))

        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isEnabled = appInfo.enabled
        val targetSdkVersion = appInfo.targetSdkVersion
        val minSdkVersion = appInfo.minSdkVersion

        val sourceDir = appInfo.sourceDir
        val dataDir = appInfo.dataDir

        val activities = packageInfo.activities?.size ?: 0
        val services = packageInfo.services?.size ?: 0
        val receivers = packageInfo.receivers?.size ?: 0
        val providers = packageInfo.providers?.size ?: 0

        val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()

        return buildString {
            appendLine("App Information:")
            appendLine("=".repeat(50))
            appendLine("Name: $label")
            appendLine("Package: $packageName")
            appendLine("Version: $versionName ($versionCode)")
            appendLine("Target SDK: $targetSdkVersion")
            appendLine("Min SDK: $minSdkVersion")
            appendLine("System App: $isSystemApp")
            appendLine("Enabled: $isEnabled")
            appendLine("Installed: $installTime")
            appendLine("Last Updated: $updateTime")
            appendLine("")
            appendLine("Components:")
            appendLine("  Activities: $activities")
            appendLine("  Services: $services")
            appendLine("  Receivers: $receivers")
            appendLine("  Providers: $providers")
            appendLine("")
            appendLine("Paths:")
            appendLine("  Source: $sourceDir")
            appendLine("  Data: $dataDir")

            if (requestedPermissions.isNotEmpty()) {
                appendLine("")
                appendLine("Permissions (${requestedPermissions.size}):")
                requestedPermissions.take(10).forEach { perm ->
                    appendLine("  - $perm")
                }
                if (requestedPermissions.size > 10) {
                    appendLine("  ... and ${requestedPermissions.size - 10} more")
                }
            }
        }
    }

    private fun getAppVersion(packageName: String): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            Lumberjack.e("GetAppInfoTool", "Error getting app version for $packageName", e)
            "Unknown"
        }
    }
}