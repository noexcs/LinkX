package com.noexcs.indolent.agent.tools.interact

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationCompat
import com.noexcs.indolent.R
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.ui.interact.InteractDialogActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume

class AskUserTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "ask_user"
    override val description = """
        Ask the user a question and wait for their response. A dialog pops up centered on screen.

        Widget types:
        - "text" — Free-text input. Returns user text, "(empty)", or "cancelled".
        - "confirm" — Yes/No dialog. Returns "yes", "no", or "cancelled".
        - "checkbox" — Multi-select with checkboxes. Returns selected items joined by ", ", or "(none)".
        - "radio" — Single choice from a list. Returns the chosen option, or "cancelled".
        - "counter" — Number picker. Use range e.g. "1,100". Returns the chosen number as string.
        - "date" — Date picker dialog. Returns formatted date (default "yyyy-MM-dd").
        - "time" — Time picker dialog. Returns "HH:mm" format.
        - "speech" — Speech-to-text via system voice input. Returns recognized text.

        Common parameters:
        - values: comma-separated list for checkbox/radio
        - hint: placeholder or subtitle text
        - range: min,max for counter (e.g. "1,100")
        - date_format: date format string for date widget
        - numeric / password / multiline: flags for text widget

        Works from any context (chat, scheduled tasks, heartbeat). Uses full-screen intent
        notification as fallback for background launches. If the user dismisses the dialog
        without answering, returns "cancelled". If no response within timeout_seconds, returns "timeout".
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(name = "title", type = "string",
            description = "The question or prompt to show the user (dialog title)"),
        ToolParameter(name = "type", type = "string", required = false, defaultValue = "confirm",
            description = "Widget type: text, confirm, checkbox, radio, counter, date, time, speech"),
        ToolParameter(name = "values", type = "string", required = false,
            description = "Comma-separated options for checkbox/radio, e.g. \"Red,Green,Blue\""),
        ToolParameter(name = "hint", type = "string", required = false, defaultValue = "",
            description = "Placeholder text for text input, or message subtitle for confirm"),
        ToolParameter(name = "range", type = "string", required = false,
            description = "Range for counter widget, e.g. \"1,100\""),
        ToolParameter(name = "date_format", type = "string", required = false, defaultValue = "yyyy-MM-dd",
            description = "Date format for date widget, e.g. \"yyyy-MM-dd\" or \"MM/dd/yyyy\""),
        ToolParameter(name = "numeric", type = "boolean", required = false, defaultValue = false,
            description = "Use numeric keyboard (text type)"),
        ToolParameter(name = "password", type = "boolean", required = false, defaultValue = false,
            description = "Mask input (text type)"),
        ToolParameter(name = "multiline", type = "boolean", required = false, defaultValue = false,
            description = "Multi-line input (text type)"),
        ToolParameter(name = "timeout_seconds", type = "integer", required = false, defaultValue = 60,
            description = "Max wait time. Range 10–300.")
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val title = args["title"] as? String ?: return "Error: title is required"
        val type = (args["type"] as? String)?.lowercase() ?: "confirm"
        val values = args["values"] as? String ?: ""
        val hint = args["hint"] as? String ?: ""
        val range = args["range"] as? String ?: ""
        val dateFormat = args["date_format"] as? String ?: "yyyy-MM-dd"
        val numeric = args["numeric"] as? Boolean ?: false
        val password = args["password"] as? Boolean ?: false
        val multiline = args["multiline"] as? Boolean ?: false
        val timeoutSec = ((args["timeout_seconds"] as? Number)?.toInt() ?: 60).coerceIn(10, 300)

        val requestId = UUID.randomUUID().toString()
        val notifId = requestId.hashCode()

        ensureChannel()

        Lumberjack.i("AskUserTool", "Launching $type dialog: $title (request=$requestId)")

        return try {
            withTimeout((timeoutSec * 1000).toLong()) {
                suspendCancellableCoroutine { cont ->
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val receivedId = intent.getStringExtra(InteractDialogActivity.EXTRA_REQUEST_ID)
                            if (receivedId != requestId) return
                            val answer = intent.getStringExtra(InteractDialogActivity.EXTRA_ANSWER) ?: "no_response"
                            Lumberjack.i("AskUserTool", "Response: $answer (request=$requestId)")
                            try { appContext.unregisterReceiver(this) } catch (_: Exception) {}
                            cancelNotification(notifId)
                            cont.resume(answer) {}
                        }
                    }

                    cont.invokeOnCancellation {
                        try { appContext.unregisterReceiver(receiver) } catch (_: Exception) {}
                        cancelNotification(notifId)
                    }

                    val filter = IntentFilter(InteractDialogActivity.RESPONSE_ACTION)
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Context.RECEIVER_NOT_EXPORTED
                    } else { 0 }
                    appContext.registerReceiver(receiver, filter, flags)

                    val activityIntent = Intent(appContext, InteractDialogActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(InteractDialogActivity.EXTRA_TITLE, title)
                        putExtra(InteractDialogActivity.EXTRA_TYPE, type)
                        putExtra(InteractDialogActivity.EXTRA_VALUES, values)
                        putExtra(InteractDialogActivity.EXTRA_HINT, hint)
                        putExtra(InteractDialogActivity.EXTRA_RANGE, range)
                        putExtra(InteractDialogActivity.EXTRA_DATE_FORMAT, dateFormat)
                        putExtra(InteractDialogActivity.EXTRA_NUMERIC, numeric)
                        putExtra(InteractDialogActivity.EXTRA_PASSWORD, password)
                        putExtra(InteractDialogActivity.EXTRA_MULTILINE, multiline)
                        putExtra(InteractDialogActivity.EXTRA_REQUEST_ID, requestId)
                    }

                    // Primary: launch Activity directly (works when app is in foreground)
                    try {
                        appContext.startActivity(activityIntent)
                    } catch (e: Exception) {
                        Lumberjack.w("AskUserTool", "Direct Activity launch failed, relying on notification: ${e.message}")
                    }

                    // Fallback: full-screen intent notification (works from background)
                    val fullScreenPending = PendingIntent.getActivity(
                        appContext, notifId, activityIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(title)
                        .setContentText("Tap to respond")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_CALL)
                        .setFullScreenIntent(fullScreenPending, true)
                        .setAutoCancel(true)
                        .setOngoing(true)
                        .build()

                    val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(notifId, notification)
                }
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            cancelNotification(notifId)
            Lumberjack.w("AskUserTool", "Timeout waiting for response (${timeoutSec}s)")
            "timeout"
        }
    }

    private fun ensureChannel() {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            "AI Questions",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Dialog prompts from the AI assistant"
        }
        nm.createNotificationChannel(channel)
    }

    private fun cancelNotification(notifId: Int) {
        try {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId)
        } catch (_: Exception) {}
    }

    companion object {
        private const val CHANNEL_ID = "ai_interaction"
    }
}
