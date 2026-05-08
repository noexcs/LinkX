package com.noexcs.indolent.logging

import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class LogcatCapture(
    private val onEntry: (LogEntry) -> Unit
) {
    private val running = AtomicBoolean(false)
    private var process: Process? = null
    private var thread: Thread? = null

    fun start() {
        if (running.getAndSet(true)) return

        thread = Thread({
            try {
                val pid = android.os.Process.myPid()
                val cmd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    arrayOf("logcat", "-v", "threadtime", "--pid=${pid}")
                } else {
                    arrayOf("logcat", "-v", "threadtime")
                }

                process = Runtime.getRuntime().exec(cmd)

                process?.inputStream?.bufferedReader()?.use { reader ->
                    var line = reader.readLine()
                    while (line != null && running.get()) {
                        val entry = parseLine(line, pid)
                        if (entry != null) onEntry(entry)
                        line = reader.readLine()
                    }
                }
            } catch (e: Exception) {
                Log.e("LogcatCapture", "Error reading logcat", e)
            }
        }, "logcat-capture").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        process?.destroy()
        thread?.interrupt()
    }

    // ── Parse ──────────────────────────────────────────

    // Format: MM-DD HH:MM:SS.mmm  PID  TID LEVEL TAG: MESSAGE
    private val lineRegex =
        Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.+?):(.*)$""")

    private var sdf: SimpleDateFormat? = null
    private var sdfYear = 0

    private fun parseLine(line: String, pid: Int): LogEntry? {
        val match = lineRegex.find(line) ?: return null
        val g = match.groupValues
        val level = Level.fromLabel(g[4]) ?: return null

        return LogEntry(
            timestamp = parseTimestamp(g[1]),
            level = level,
            tag = g[5].trim(),
            message = g[6].trimStart(),
            throwable = null,
            thread = "tid:${g[3]}",
            pid = pid
        )
    }

    private fun parseTimestamp(ts: String): Long {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        if (sdf == null || year != sdfYear) {
            sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            sdfYear = year
        }
        return try {
            sdf?.parse("$year-$ts")?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
