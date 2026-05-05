package com.noexcs.indolent.data

import android.content.Context
import com.noexcs.indolent.agent.MemoryProvider
import java.io.File

class MemoryManager(context: Context) : MemoryProvider {
    private val file = File(context.filesDir, "memory.md")

    override fun read(): String = if (file.exists()) file.readText() else ""

    fun write(content: String) {
        file.writeText(content)
    }

    override fun save(key: String, value: String) {
        val current = read()
        val marker = "\n## $key\n"
        val existingIdx = current.indexOf(marker)
        val updated = if (existingIdx >= 0) {
            // Replace existing section
            val nextMarker = current.indexOf("\n## ", existingIdx + marker.length)
            val prefix = current.substring(0, existingIdx)
            val suffix = if (nextMarker >= 0) current.substring(nextMarker) else ""
            "$prefix$marker$value\n$suffix"
        } else {
            // Append new section
            "${current.trimEnd()}$marker$value\n"
        }
        write(updated)
    }

    fun saveMemory(key: String, value: String) = save(key, value)
}
