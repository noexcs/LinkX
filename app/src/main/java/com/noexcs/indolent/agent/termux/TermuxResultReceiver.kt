package com.noexcs.indolent.agent.termux

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.agent.termux.CommandResult
import com.termux.shared.termux.TermuxConstants
import java.util.concurrent.ConcurrentHashMap

class TermuxResultReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_EXECUTION_ID = "execution_id"
        const val EXTRA_CALLBACK_KEY = "callback_key"

        private const val LOG_TAG = "TermuxResultReceiver"

        @JvmStatic
        val resultCallbacks = ConcurrentHashMap<String, (CommandResult) -> Unit>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        Lumberjack.d(LOG_TAG, "Received execution result")
        Lumberjack.d(LOG_TAG, "Intent extras: ${intent.extras?.keySet()}")

        val resultBundle = intent.getBundleExtra(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE)
        if (resultBundle == null) {
            Lumberjack.e(LOG_TAG, "No result bundle at key \"${TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE}\"")
            return
        }

        Lumberjack.d(LOG_TAG, "Result bundle keys: ${resultBundle.keySet()}")

        val executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, 0)
        val callbackKey = intent.getStringExtra(EXTRA_CALLBACK_KEY)

        val stdout = resultBundle.getString(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT, "") ?: ""
        val stderr = resultBundle.getString(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR, "") ?: ""
        val exitCode = resultBundle.getInt(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE, -1)
        val errCode = resultBundle.getInt(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERR, -1)
        val errmsg = resultBundle.getString(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG, "") ?: ""

        Lumberjack.d(LOG_TAG, "Execution id $executionId result:\n" +
                "stdout: `$stdout`\n" +
                "stdout_original_length: `${resultBundle.getString(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT_ORIGINAL_LENGTH)}`\n" +
                "stderr: `$stderr`\n" +
                "stderr_original_length: `${resultBundle.getString(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR_ORIGINAL_LENGTH)}`\n" +
                "exitCode: $exitCode\n" +
                "errCode: $errCode\n" +
                "errmsg: `$errmsg`")

        Lumberjack.d(LOG_TAG, "Looking for callback: $callbackKey, available: ${resultCallbacks.keys}")

        callbackKey?.let { key ->
            val callback = resultCallbacks.remove(key)
            if (callback != null) {
                Lumberjack.d(LOG_TAG, "Invoking callback for $key")
                callback(
                    CommandResult(
                        stdout = stdout,
                        stderr = stderr,
                        exitCode = exitCode,
                        errorMessage = if (errCode != Activity.RESULT_OK && errmsg.isNotEmpty()) errmsg else null
                    )
                )
            } else {
                Lumberjack.e(LOG_TAG, "No callback found for key: $key")
            }
        }
    }
}