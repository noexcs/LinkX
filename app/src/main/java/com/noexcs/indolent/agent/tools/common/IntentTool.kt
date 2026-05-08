package com.noexcs.indolent.agent.tools.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import org.json.JSONObject

class IntentTool(context: Context) : AgentTool {
    private val context = context.applicationContext
    override val name = "send_intent"
    override val description = """
        Send Android Intents to launch activities in other apps.

        Capabilities:
        - Launch activities in any app (explicit or implicit)
        - Share content (text, URLs) with chooser dialog
        - Open URLs, emails, phone numbers, maps
        - Pass extra data (strings, integers, booleans, etc.)
        - Control activity launch flags
        - Set MIME types for content

        Common use cases:
        1. Open URL: action="VIEW", data="https://example.com"
        2. Make call: action="DIAL", data="tel:123456789"
        3. Send email: action="SENDTO", data="mailto:test@example.com"
        4. Share text: action="SEND", type="text/plain", useChooser=true, extrasJson='{"android.intent.extra.TEXT": "Hello"}'
        5. View map: action="VIEW", data="geo:0,0?q=New+York"
        6. Launch specific app: action="MAIN", category="LAUNCHER", packageName="com.example.app"

        Security restrictions:
        - file:// URIs are blocked (use content:// with a FileProvider instead)
        - Broadcasts and services are not supported
        - Dangerous actions (install/uninstall apps, device admin, shutdown) are blocked
        - ACTION_CALL is blocked; use ACTION_DIAL instead

        IMPORTANT: For extras, use valid JSON format in extrasJson parameter.
        Example: extrasJson='{"key1": "value", "count": 5, "enabled": true}'
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "action",
            type = "string",
            description = "Intent action (e.g. VIEW, SEND, DIAL, MAIN). Use short names like VIEW, SEND, DIAL.",
            required = false
        ),
        ToolParameter(
            name = "data",
            type = "string",
            description = "URI data for the intent (URL, tel:, mailto:, geo:, etc.)",
            required = false
        ),
        ToolParameter(
            name = "packageName",
            type = "string",
            description = "Target app package name for explicit intent",
            required = false
        ),
        ToolParameter(
            name = "className",
            type = "string",
            description = "Target activity class name (requires packageName)",
            required = false
        ),
        ToolParameter(
            name = "type",
            type = "string",
            description = "MIME type for the intent data",
            required = false
        ),
        ToolParameter(
            name = "category",
            type = "string",
            description = "Intent category (e.g. LAUNCHER, BROWSABLE)",
            required = false
        ),
        ToolParameter(
            name = "extrasJson",
            type = "string",
            description = "JSON object with extra data (strings, numbers, booleans)",
            required = false
        ),
        ToolParameter(
            name = "flags",
            type = "string",
            description = "Comma-separated intent flags (e.g. FLAG_ACTIVITY_NEW_TASK)",
            required = false
        ),
        ToolParameter(
            name = "subject",
            type = "string",
            description = "Subject line for SEND action",
            required = false
        ),
        ToolParameter(
            name = "useChooser",
            type = "boolean",
            description = "Show app chooser dialog",
            required = false
        ),
        ToolParameter(
            name = "chooserTitle",
            type = "string",
            description = "Title for the chooser dialog",
            required = false
        )
    )


    private data class IntentArgs(
        val action: String,
        val data: String,
        val packageName: String,
        val className: String,
        val type: String,
        val category: String,
        val extrasJson: String,
        val flags: String,
        val subject: String,
        val useChooser: Boolean,
        val chooserTitle: String
    )

    @Suppress("DEPRECATION")
    private val blockedActions = setOf(
        Intent.ACTION_FACTORY_TEST,
        Intent.ACTION_CALL,
        "android.intent.action.MASTER_CLEAR",
        "android.intent.action.REBOOT",
        "android.intent.action.SHUTDOWN",
        "android.intent.action.REQUEST_SHUTDOWN",
        Intent.ACTION_INSTALL_PACKAGE,
        Intent.ACTION_UNINSTALL_PACKAGE,
        "android.app.action.ADD_DEVICE_ADMIN",
        "android.intent.action.MANAGE_PACKAGE_STORAGE",
        "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS",
        "android.settings.ACTION_MANAGE_OVERLAY_PERMISSION",
        "android.settings.ACTION_MANAGE_WRITE_SETTINGS",
        "android.settings.ACTION_ACCESSIBILITY_SETTINGS",
        "android.settings.USAGE_ACCESS_SETTINGS",
    )

    private val blockedContentAuthorities = setOf(
        "contacts", "com.android.contacts",
        "sms", "mms", "mms-sms",
        "call_log", "com.android.calllog",
        "telephony",
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        return try {
            val action = args["action"] as? String ?: "android.intent.action.VIEW"
            val data = args["data"] as? String ?: ""
            val packageName = args["packageName"] as? String ?: ""
            val className = args["className"] as? String ?: ""
            val type = args["type"] as? String ?: ""
            val category = args["category"] as? String ?: ""
            val extrasJson = args["extrasJson"] as? String ?: "{}"
            val flags = args["flags"] as? String ?: ""
            val subject = args["subject"] as? String ?: ""
            val useChooser = args["useChooser"] as? Boolean ?: false
            val chooserTitle = args["chooserTitle"] as? String ?: ""

            val normalizedAction = normalizeAction(action)

            if (normalizedAction in blockedActions) {
                return "Error: Action '$action' is blocked for security reasons."
            }

            if (data.isNotBlank()) {
                val uri = data.toUri()
                val scheme = uri.scheme?.lowercase()
                if (scheme == "content") {
                    val authority = uri.authority?.lowercase() ?: ""
                    if (blockedContentAuthorities.any { authority.contains(it) }) {
                        return "Error: Access to content provider '$authority' is blocked for privacy reasons."
                    }
                }
            }

            val intentArgs = IntentArgs(action, data, packageName, className, type, category, extrasJson, flags, subject, useChooser, chooserTitle)
            val intent = buildIntent(intentArgs, normalizedAction)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val launchIntent = if (useChooser) {
                val title = chooserTitle.ifBlank { null }
                Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                intent
            }

            val resolved = if (!useChooser) {
                launchIntent.resolveActivity(context.packageManager)
            } else null

            try {
                context.startActivity(launchIntent)
                buildString {
                    appendLine("success: true")
                    appendLine("action: $normalizedAction")
                    if (data.isNotEmpty()) appendLine("data: $data")
                    if (type.isNotEmpty()) appendLine("type: $type")
                    when {
                        resolved != null -> {
                            appendLine("packageName: ${resolved.packageName}")
                            appendLine("activityName: ${resolved.className}")
                        }
                        packageName.isNotEmpty() -> {
                            appendLine("packageName: $packageName")
                            if (className.isNotEmpty()) appendLine("activityName: $className")
                        }
                    }
                    if (useChooser) appendLine("chooser: shown (title=\"${chooserTitle.ifBlank { "Share" }}\")")
                    if (flags.isNotEmpty()) appendLine("flags: $flags")
                }
            } catch (e: ActivityNotFoundException) {
                buildString {
                    appendLine("success: false")
                    appendLine("error: ActivityNotFoundException")
                    appendLine("message: No application found to handle this intent.")
                    appendLine("action: $normalizedAction")
                    if (data.isNotEmpty()) appendLine("data: $data")
                    if (type.isNotEmpty()) appendLine("type: $type")
                    if (packageName.isNotEmpty()) appendLine("packageName: $packageName")
                    if (className.isNotEmpty()) appendLine("activityName: $className")
                    appendLine()
                    appendLine("Suggestions:")
                    if (packageName.isNotEmpty()) appendLine("- Try removing packageName to use implicit resolution")
                    if (className.isNotEmpty()) appendLine("- Verify className is correct for the target package")
                    if (!useChooser) appendLine("- Try useChooser=true to let the user pick an app")
                    appendLine("- Verify the action/data/type combination is correct")
                }
            } catch (e: SecurityException) {
                Lumberjack.e("IntentTool", "SecurityException executing intent", e)
                buildString {
                    appendLine("success: false")
                    appendLine("error: SecurityException")
                    appendLine("message: ${e.message}")
                    appendLine("action: $normalizedAction")
                    if (packageName.isNotEmpty()) appendLine("packageName: $packageName")
                    if (className.isNotEmpty()) appendLine("activityName: $className")
                }
            }
        } catch (e: SecurityException) {
            Lumberjack.e("IntentTool", "SecurityException executing intent", e)
            buildString {
                appendLine("success: false")
                appendLine("error: SecurityException")
                appendLine("message: ${e.message}")
            }
        } catch (e: Exception) {
            Lumberjack.e("IntentTool", "Error executing intent", e)
            buildString {
                appendLine("success: false")
                appendLine("error: ${e.javaClass.simpleName}")
                appendLine("message: ${e.message}")
            }
        }
    }

    private fun buildIntent(args: IntentArgs, action: String): Intent {
        val intent = Intent()
        intent.action = action

        when {
            args.data.isNotBlank() && args.type.isNotBlank() -> intent.setDataAndType(args.data.toUri(), args.type)
            args.data.isNotBlank() -> intent.data = args.data.toUri()
            args.type.isNotBlank() -> intent.type = args.type
        }

        if (args.packageName.isNotBlank()) {
            if (args.className.isNotBlank()) {
                intent.setClassName(args.packageName, args.className)
            } else {
                intent.setPackage(args.packageName)
            }
        }

        if (args.category.isNotBlank()) {
            intent.addCategory(normalizeCategory(args.category))
        }

        if (args.subject.isNotBlank() && action == Intent.ACTION_SEND) {
            intent.putExtra(Intent.EXTRA_SUBJECT, args.subject)
        }

        if (args.extrasJson.isNotBlank() && args.extrasJson != "{}") {
            val extras = parseExtras(args.extrasJson)
            extras.forEach { (key, value) ->
                when (value) {
                    is String -> intent.putExtra(key, value)
                    is Int -> intent.putExtra(key, value)
                    is Boolean -> intent.putExtra(key, value)
                    is Long -> intent.putExtra(key, value)
                    is Double -> intent.putExtra(key, value)
                    is Float -> intent.putExtra(key, value)
                }
            }
        }

        if (args.flags.isNotBlank()) {
            args.flags.split(",").map { it.trim() }.forEach { flag ->
                parseFlag(flag)?.let { intent.addFlags(it) }
            }
        }

        return intent
    }

    private fun parseExtras(jsonString: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        try {
            val obj = JSONObject(jsonString)
            obj.keys().forEach { key ->
                val v = obj.get(key)
                when {
                    v is String -> result[key] = v
                    v is Int -> result[key] = v
                    v is Double -> result[key] = v
                    v is Boolean -> result[key] = v
                    v is Long -> result[key] = v
                    obj.opt(key) is Float -> result[key] = obj.getDouble(key).toFloat()
                }
            }
        } catch (e: Exception) {
            Lumberjack.e("IntentTool", "Error parsing extras JSON", e)
        }
        return result
    }

    private fun normalizeAction(action: String): String = when (action.uppercase()) {
        "VIEW", "ACTION_VIEW" -> Intent.ACTION_VIEW
        "SEND", "ACTION_SEND" -> Intent.ACTION_SEND
        "SEND_MULTIPLE", "ACTION_SEND_MULTIPLE" -> Intent.ACTION_SEND_MULTIPLE
        "SENDTO", "ACTION_SENDTO" -> Intent.ACTION_SENDTO
        "DIAL", "ACTION_DIAL" -> Intent.ACTION_DIAL
        "CALL", "ACTION_CALL" -> Intent.ACTION_CALL
        "EDIT", "ACTION_EDIT" -> Intent.ACTION_EDIT
        "PICK", "ACTION_PICK" -> Intent.ACTION_PICK
        "GET_CONTENT", "ACTION_GET_CONTENT" -> Intent.ACTION_GET_CONTENT
        "OPEN_DOCUMENT", "ACTION_OPEN_DOCUMENT" -> Intent.ACTION_OPEN_DOCUMENT
        "CREATE_DOCUMENT", "ACTION_CREATE_DOCUMENT" -> Intent.ACTION_CREATE_DOCUMENT
        "WEB_SEARCH", "ACTION_WEB_SEARCH" -> Intent.ACTION_WEB_SEARCH
        "SEARCH", "ACTION_SEARCH" -> Intent.ACTION_SEARCH
        "INSERT", "ACTION_INSERT" -> Intent.ACTION_INSERT
        "DELETE", "ACTION_DELETE" -> Intent.ACTION_DELETE
        "MAIN", "ACTION_MAIN" -> Intent.ACTION_MAIN
        "CHOOSER", "ACTION_CHOOSER" -> Intent.ACTION_CHOOSER
        else -> action
    }

    private fun normalizeCategory(category: String): String = when (category.uppercase()) {
        "LAUNCHER", "CATEGORY_LAUNCHER" -> Intent.CATEGORY_LAUNCHER
        "BROWSABLE", "CATEGORY_BROWSABLE" -> Intent.CATEGORY_BROWSABLE
        "DEFAULT", "CATEGORY_DEFAULT" -> Intent.CATEGORY_DEFAULT
        "INFO", "CATEGORY_INFO" -> Intent.CATEGORY_INFO
        "HOME", "CATEGORY_HOME" -> Intent.CATEGORY_HOME
        "OPENABLE", "CATEGORY_OPENABLE" -> Intent.CATEGORY_OPENABLE
        "APP_BROWSER", "CATEGORY_APP_BROWSER" -> Intent.CATEGORY_APP_BROWSER
        "APP_EMAIL", "CATEGORY_APP_EMAIL" -> Intent.CATEGORY_APP_EMAIL
        "APP_MUSIC", "CATEGORY_APP_MUSIC" -> Intent.CATEGORY_APP_MUSIC
        else -> category
    }

    private fun parseFlag(flag: String): Int? = when (flag.uppercase()) {
        "FLAG_ACTIVITY_NEW_TASK" -> Intent.FLAG_ACTIVITY_NEW_TASK
        "FLAG_ACTIVITY_CLEAR_TOP" -> Intent.FLAG_ACTIVITY_CLEAR_TOP
        "FLAG_ACTIVITY_SINGLE_TOP" -> Intent.FLAG_ACTIVITY_SINGLE_TOP
        "FLAG_ACTIVITY_NO_HISTORY" -> Intent.FLAG_ACTIVITY_NO_HISTORY
        "FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS" -> Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        "FLAG_ACTIVITY_BROUGHT_TO_FRONT" -> Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        "FLAG_ACTIVITY_RESET_TASK_IF_NEEDED" -> Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        "FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY" -> Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
        "FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET", "FLAG_ACTIVITY_NEW_DOCUMENT" -> Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        "FLAG_ACTIVITY_FORWARD_RESULT" -> Intent.FLAG_ACTIVITY_FORWARD_RESULT
        "FLAG_ACTIVITY_PREVIOUS_IS_TOP" -> Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
        "FLAG_DEBUG_LOG_RESOLUTION" -> Intent.FLAG_DEBUG_LOG_RESOLUTION
        "FLAG_FROM_BACKGROUND" -> Intent.FLAG_FROM_BACKGROUND
        "FLAG_GRANT_READ_URI_PERMISSION" -> Intent.FLAG_GRANT_READ_URI_PERMISSION
        "FLAG_GRANT_WRITE_URI_PERMISSION" -> Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        "FLAG_RECEIVER_REGISTERED_ONLY" -> Intent.FLAG_RECEIVER_REGISTERED_ONLY
        else -> null
    }
}