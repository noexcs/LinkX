package com.noexcs.indolent.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context
import com.noexcs.indolent.agent.ChatMessage
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-based chat history provider that persists messages to disk.
 */
public class FileChatHistoryProvider(context: Context) {

    private val dir = File(context.filesDir, "sessions").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val mutex = Mutex()

    suspend fun store(conversationId: String, messages: List<ChatMessage>) {
        mutex.withLock {
            val title = messages.firstOrNull()?.content ?: conversationId
            val session = Session(
                sessionId = conversationId,
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                messages = messages
            )
            File(dir, "${conversationId}.json").writeText(json.encodeToString(session))
        }
    }

    suspend fun load(conversationId: String): List<ChatMessage> {
        return mutex.withLock {
            val file = File(dir, "$conversationId.json")
            if (!file.exists()) {
                emptyList()
            } else {
                try {
                    val session = json.decodeFromString<Session>(file.readText())
                    session.messages
                } catch (e: Exception) {
                    Lumberjack.e("FileChatHistoryProvider", "Error decoding session in load", e)
                    emptyList()
                }
            }
        }
    }

    fun listAll(): List<Session> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val conv = json.decodeFromString<Session>(file.readText())
                    Session(conv.sessionId, conv.title, conv.createdAt, conv.updatedAt, conv.messages)
                } catch (e: Exception) {
                    Lumberjack.e("FileChatHistoryProvider", "Error decoding session in listAll", e)
                    null
                }
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    fun delete(sessionId: String) {
        File(dir, "$sessionId.json").delete()
    }

    fun _load(sessionId: String): Session? {
        val file = File(dir, "$sessionId.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<Session>(file.readText())
        } catch (e: Exception) {
            Lumberjack.e("FileChatHistoryProvider", "Error decoding session in _load", e)
            null
        }
    }

    fun save(session: Session) {
        File(dir, "${session.sessionId}.json").writeText(json.encodeToString(session))
    }

    fun rename(id: String, newTitle: String) {
        val conv = _load(id) ?: return
        save(conv.copy(title = newTitle))
    }
}
