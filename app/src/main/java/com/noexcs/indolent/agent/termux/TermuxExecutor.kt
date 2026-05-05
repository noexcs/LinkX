package com.noexcs.indolent.agent.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.noexcs.indolent.logging.Lumberjack
import com.termux.shared.termux.TermuxConstants
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class TermuxExecutor(private val context: Context) {

    companion object {
        private const val LOG_TAG = "TermuxExecutor"
        private val executionIdCounter = AtomicInteger(2000)
    }

    suspend fun execute(
        command: String,
        workdir: String = "/data/data/com.termux/files/home",
        timeoutMs: Long = 60_000
    ): CommandResult = withTimeout(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            try {
                val executionId = executionIdCounter.getAndIncrement()
                val callbackKey = "tool_exec_$executionId"

                TermuxResultReceiver.Companion.resultCallbacks[callbackKey] = { result ->
                    if (cont.isActive) cont.resume(result)
                }

                cont.invokeOnCancellation {
                    TermuxResultReceiver.Companion.resultCallbacks.remove(callbackKey)
                }

                val intent = Intent().apply {
                    setClassName(
                        TermuxConstants.TERMUX_PACKAGE_NAME,
                        TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE_NAME
                    )
                    action = TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.ACTION_RUN_COMMAND
                    putExtra(
                        TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_COMMAND_PATH,
                        "/data/data/com.termux/files/usr/bin/bash"
                    )
                    putExtra(
                        TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_ARGUMENTS,
                        arrayOf("-c", command)
                    )
                    putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_WORKDIR, workdir)
                    putExtra(TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_BACKGROUND, true)
                    putExtra(
                        TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_COMMAND_LABEL,
                        "LocalAgent Tool"
                    )
                }

                val pluginResultsIntent = Intent(context, TermuxResultReceiver::class.java).apply {
                    putExtra(TermuxResultReceiver.Companion.EXTRA_EXECUTION_ID, executionId)
                    putExtra(TermuxResultReceiver.Companion.EXTRA_CALLBACK_KEY, callbackKey)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    executionId,
                    pluginResultsIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
                )

                intent.putExtra(
                    TermuxConstants.TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_PENDING_INTENT,
                    pendingIntent
                )

                Lumberjack.d(LOG_TAG, "Executing [$executionId]: $command")
                context.startService(intent)
            } catch (e: Exception) {
                Lumberjack.e(LOG_TAG, "Failed to execute: ${e.message}", e)
                if (cont.isActive) {
                    cont.resume(CommandResult(errorMessage = "Execution error: ${e.message}"))
                }
            }
        }
    }
}