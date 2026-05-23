package com.noexcs.indolent.task.conditional

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Provides passive (event-driven) condition monitoring for battery and system settings.
 *
 * While the app process is alive, this catches battery changes and settings writes
 * immediately and re-evaluates conditions. Falls back to the AlarmManager-based
 * [ConditionMonitorWorker] polling when the process is dead.
 */
class PassiveConditionMonitor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            Lumberjack.d(TAG, "Battery change detected, evaluating conditions...")
            evaluateAndDispatch()
        }
    }

    private val settingsObserver = object : android.database.ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            Lumberjack.d(TAG, "System setting change detected, evaluating conditions...")
            evaluateAndDispatch()
        }
    }

    fun start() {
        if (registered) return
        try {
            context.registerReceiver(
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            try {
                context.contentResolver.registerContentObserver(
                    Settings.System.CONTENT_URI,
                    true,
                    settingsObserver
                )
            } catch (e: Exception) {
                context.unregisterReceiver(batteryReceiver)
                throw e
            }
            registered = true
            Lumberjack.i(TAG, "Passive monitoring started (battery + settings)")
        } catch (e: Exception) {
            Lumberjack.e(TAG, "Failed to start passive monitoring", e)
        }
    }

    fun stop() {
        if (!registered) return
        var receiverUnregistered = false
        var observerUnregistered = false
        try {
            context.unregisterReceiver(batteryReceiver)
            receiverUnregistered = true
        } catch (e: Exception) {
            Lumberjack.e(TAG, "Error unregistering battery receiver", e)
        }
        try {
            context.contentResolver.unregisterContentObserver(settingsObserver)
            observerUnregistered = true
        } catch (e: Exception) {
            Lumberjack.e(TAG, "Error unregistering settings observer", e)
        }
        if (receiverUnregistered && observerUnregistered) {
            registered = false
        }
        Lumberjack.d(TAG, "Passive monitoring stopped")
    }

    private fun evaluateAndDispatch() {
        scope.launch {
            try {
                val evaluator = ConditionEvaluator(context)
                val triggers = evaluator.evaluateAll()
                if (triggers.isNotEmpty()) {
                    val dispatcher = TriggerDispatcher(context)
                    for (trigger in triggers) {
                        dispatcher.dispatch(trigger)
                    }
                }
            } catch (e: Exception) {
                Lumberjack.e(TAG, "Error in passive evaluation", e)
            }
        }
    }

    companion object {
        private const val TAG = "PassiveConditionMonitor"
    }
}
