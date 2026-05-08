package com.noexcs.indolent.agent.tools.finance

import android.annotation.SuppressLint
import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

object PythonInit {
    private var started = false
    private var tested = false
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    @Synchronized
    fun ensureStarted() {
        if (!started) {
            try {
                System.loadLibrary("gfortran")
                Python.start(AndroidPlatform(context))
            } catch (e: Exception) {
                Lumberjack.w("PythonInit", "Python already started by another caller")
            }
            started = true
            // Init portfolio storage
            try {
                val mod = Python.getInstance().getModule("portfolio")
                mod.callAttr("portfolio_init", context.filesDir.absolutePath)
            } catch (e: Exception) {
                Lumberjack.e("PythonInit", "Portfolio init error: ${e.message}", e)
            }
            runTests()
        }
    }

    private fun runTests() {
        try {
            val mod = Python.getInstance().getModule("test_deps")
            val result = mod.callAttr("test").toString()
            Lumberjack.i("PythonInit", "\n=== Dependency Tests ===\n$result")
        } catch (e: Exception) {
            Lumberjack.e("PythonInit", "Test harness error: ${e.message}", e)
        }
    }
}
