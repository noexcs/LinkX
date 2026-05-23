package com.noexcs.indolent.logging

import android.os.Process
import android.util.Log
import java.io.File

object Lumberjack {

    private var buffer: LogBuffer = LogBuffer(10000)
    private var fileWriter: LogFileWriter? = null
    private var minFileLevel: Level = Level.I
    private var initialized = false
    private var logcatCapture: LogcatCapture? = null
    @Volatile
    private var capturingLogcat = false

    // ── Init ──────────────────────────────────────────────

    fun init(logDir: File, fileLevel: Level = Level.I) {
        if (initialized) return
        initialized = true
        minFileLevel = fileLevel

        try {
            fileWriter = LogFileWriter(logDir)
        } catch (e: Exception) {
            Log.e("Lumberjack", "Failed to init file writer", e)
        }

        // Crash capture
        val original = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            f("CRASH", "Uncaught exception in thread ${thread.name}", throwable)
            fileWriter?.shutdown()
            original?.uncaughtException(thread, throwable)
        }

        i("Lumberjack", "Logger initialized. PID=${Process.myPid()}")
    }

    // ── Logcat capture ───────────────────────────────────

    fun startLogcatCapture() {
        if (capturingLogcat) return
        capturingLogcat = true

        val capture = LogcatCapture { entry ->
            buffer.append(entry)
            if (entry.level.priority >= minFileLevel.priority) {
                fileWriter?.enqueue(entry)
            }
        }
        logcatCapture = capture
        capture.start()
        i("Lumberjack", "Logcat capture started")
    }

    fun stopLogcatCapture() {
        if (!capturingLogcat) return
        capturingLogcat = false
        logcatCapture?.stop()
        logcatCapture = null
        i("Lumberjack", "Logcat capture stopped")
    }

    fun isLogcatCaptureActive(): Boolean = capturingLogcat

    // ── Log API ──────────────────────────────────────────

    fun v(tag: String, message: String) = log(Level.V, tag, message, null)
    fun d(tag: String, message: String) = log(Level.D, tag, message, null)
    fun i(tag: String, message: String) = log(Level.I, tag, message, null)
    fun w(tag: String, message: String) = log(Level.W, tag, message, null)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(Level.E, tag, message, throwable)
    fun f(tag: String, message: String, throwable: Throwable? = null) = log(Level.F, tag, message, throwable)

    // ── Query ────────────────────────────────────────────

    fun query(filter: LogFilter): LogBuffer.QueryResult {
        return buffer.query(filter)
    }

    fun query(
        count: Int = 50,
        level: Level? = null,
        tag: String? = null,
        query: String? = null,
        since: Long? = null,
        before: Long? = null,
        offset: Int = 0
    ): LogBuffer.QueryResult {
        return buffer.query(LogFilter(count, level, tag, query, since, before, offset))
    }

    fun bufferSize(): Int = buffer.size()

    fun clear() = buffer.clear()

    // ── Internal ─────────────────────────────────────────

    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        val entry = LogEntry.create(level, tag, message, throwable)

        if (!capturingLogcat) {
            // Write directly to buffer + file
            buffer.append(entry)
            if (level.priority >= minFileLevel.priority) {
                fileWriter?.enqueue(entry)
            }
        }
        // When logcat capture is active, it reads from logcat and feeds buffer+file,
        // so we skip direct writes to avoid duplicates.

        // Bridge to logcat
        val logMsg = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        when (level) {
            Level.V -> Log.v(tag, logMsg)
            Level.D -> Log.d(tag, logMsg)
            Level.I -> Log.i(tag, logMsg)
            Level.W -> Log.w(tag, logMsg)
            Level.E -> if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            Level.F -> Log.wtf(tag, logMsg)
        }
    }
}
