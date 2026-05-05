package com.noexcs.indolent.logging

import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class LogFileWriter(
    private val logDir: File,
    private val maxFileSize: Long = 1_000_000L,  // 1MB per file
    private val maxTotalSize: Long = 5_000_000L   // 5MB total
) {
    private val queue: BlockingQueue<LogEntry> = LinkedBlockingQueue(5000)
    private val running = AtomicBoolean(true)
    private var currentWriter: BufferedWriter? = null
    private var currentFile: File? = null
    private var currentFileSize: Long = 0
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val writerThread = Thread({
        while (running.get()) {
            try {
                val entry = queue.take()
                writeEntry(entry)
            } catch (_: InterruptedException) {
                // shutting down
            } catch (e: Exception) {
                android.util.Log.e("LogFileWriter", "Write error", e)
            }
        }
        // Drain remaining
        val remaining = mutableListOf<LogEntry>()
        queue.drainTo(remaining)
        remaining.forEach { try { writeEntry(it) } catch (_: Exception) {} }
        closeCurrent()
    }, "lumberjack-file-writer").apply {
        isDaemon = true
        start()
    }

    fun enqueue(entry: LogEntry) {
        if (running.get()) {
            queue.offer(entry) // drop if queue full (don't block caller)
        }
    }

    fun shutdown() {
        running.set(false)
        writerThread.interrupt()
    }

    private fun writeEntry(entry: LogEntry) {
        val writer = ensureWriter()
        val line = entry.format()
        writer.write(line)
        writer.newLine()
        currentFileSize += line.length + 1

        if (currentFileSize >= maxFileSize) {
            rotateFile()
        }
    }

    private fun ensureWriter(): BufferedWriter {
        val existing = currentWriter
        if (existing != null && currentFile != null && currentFileSize < maxFileSize) return existing

        rotateFile()
        return currentWriter!!
    }

    private fun rotateFile() {
        closeCurrent()

        val date = dateFormat.format(Date())
        val index = nextIndex(date)
        val file = File(logDir, "lumberjack_${date}_$index.log")
        logDir.mkdirs()

        currentFile = file
        currentWriter = BufferedWriter(FileWriter(file, true))
        currentFileSize = file.length()

        // Cleanup old files
        enforceTotalSize()
    }

    private fun nextIndex(date: String): Int {
        val prefix = "lumberjack_${date}_"
        val existing = logDir.listFiles()?.filter { it.name.startsWith(prefix) }?.mapNotNull {
            Regex("""_(\d+)\.log$""").find(it.name)?.groupValues?.get(1)?.toIntOrNull()
        }?.maxOrNull()
        return (existing ?: 0) + 1
    }

    private fun enforceTotalSize() {
        val files = logDir.listFiles()?.filter { it.name.startsWith("lumberjack_") }?.sortedBy { it.lastModified() }
        if (files == null) return

        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= maxTotalSize) break
            total -= f.length()
            f.delete()
        }
    }

    private fun closeCurrent() {
        currentWriter?.flush()
        currentWriter?.close()
        currentWriter = null
        currentFile = null
        currentFileSize = 0
    }
}
