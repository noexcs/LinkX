package com.noexcs.indolent.logging

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long,
    val level: Level,
    val tag: String,
    val message: String,
    val throwable: String?,
    val thread: String,
    val pid: Int
) {
    fun format(): String {
        val time = sdf.format(Date(timestamp))
        val tb = if (throwable != null) "\n$throwable" else ""
        return "$time [$pid/${thread.take(12)}] [${level.label}] $tag: $message$tb"
    }

    fun toShortString(): String {
        val time = sdf.format(Date(timestamp))
        return "$time [${level.label}] $tag: $message"
    }

    companion object {
        private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

        fun create(level: Level, tag: String, message: String, throwable: Throwable? = null): LogEntry {
            return LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message,
                throwable = throwable?.let { stackTrace(it) },
                thread = Thread.currentThread().name,
                pid = android.os.Process.myPid()
            )
        }

        private fun stackTrace(t: Throwable): String {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            return sw.toString()
        }
    }
}
