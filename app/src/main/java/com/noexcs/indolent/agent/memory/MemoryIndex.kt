package com.noexcs.indolent.agent.memory

import android.util.Base64
import com.noexcs.indolent.logging.Lumberjack
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MemoryIndex {

    fun save(chunks: List<MemoryChunk>, file: File) {
        val jsonArray = JSONArray()
        for (chunk in chunks) {
            val obj = JSONObject()
            obj.put("id", chunk.id)
            obj.put("headerKey", chunk.headerKey)
            obj.put("text", chunk.text)
            obj.put("embedding", encodeEmbedding(chunk.embedding))
            obj.put("createdAt", chunk.createdAt)
            jsonArray.put(obj)
        }
        // Atomic write via temp file
        val tmpFile = File(file.parent, "memory_index.tmp")
        tmpFile.writeText(jsonArray.toString(2))
        tmpFile.renameTo(file)
    }

    fun load(file: File): List<MemoryChunk>? {
        if (!file.exists()) return null
        return try {
            val jsonArray = JSONArray(file.readText())
            val chunks = mutableListOf<MemoryChunk>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                chunks.add(
                    MemoryChunk(
                        id = obj.getString("id"),
                        headerKey = obj.getString("headerKey"),
                        text = obj.getString("text"),
                        embedding = decodeEmbedding(obj.getString("embedding")),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            chunks
        } catch (e: Exception) {
            Lumberjack.e("MemoryIndex", "Failed to load index", e)
            null
        }
    }

    private fun encodeEmbedding(vec: FloatArray): String {
        val buf = ByteBuffer.allocate(vec.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        val floatBuf = buf.asFloatBuffer()
        floatBuf.put(vec)
        return Base64.encodeToString(buf.array(), Base64.NO_WRAP)
    }

    private fun decodeEmbedding(base64: String): FloatArray {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floatBuf = buf.asFloatBuffer()
        val vec = FloatArray(floatBuf.remaining())
        floatBuf.get(vec)
        return vec
    }
}
