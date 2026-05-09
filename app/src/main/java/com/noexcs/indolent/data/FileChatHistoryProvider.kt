package com.noexcs.indolent.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context
import com.noexcs.indolent.agent.ChatMessage
import com.noexcs.indolent.agent.LLMMessage
import com.noexcs.indolent.agent.MessageRoleMapper
import com.noexcs.indolent.agent.PersistedSession
import com.noexcs.indolent.agent.SessionMetadata
import com.noexcs.indolent.agent.SessionPersistence
import com.noexcs.indolent.agent.SessionType
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class FileChatHistoryProvider(context: Context) : SessionPersistence {

    private val dir = File(context.filesDir, "sessions").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutex = Mutex()

    override suspend fun save(
        sessionId: String,
        messages: List<LLMMessage>,
        title: String,
        type: SessionType
    ) {
        mutex.withLock {
            val sessionData = PersistedSession(
                sessionId = sessionId,
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                messages = messages,
                type = type
            )
            File(dir, "${sessionId}.json").writeText(json.encodeToString(PersistedSession.serializer(), sessionData))
        }
    }

    override suspend fun load(sessionId: String): List<LLMMessage>? {
        return mutex.withLock {
            val file = File(dir, "$sessionId.json")
            if (!file.exists()) return null
            val text = file.readText()

            // Try new format first
            try {
                val sessionData = json.decodeFromString(PersistedSession.serializer(), text)
                return sessionData.messages
            } catch (_: Exception) { }

            // Fall back to old format (ChatMessage-based Session)
            try {
                val oldSession = json.decodeFromString(OldSessionFormat.serializer(), text)
                val converted = oldSession.messages.map { msg ->
                    LLMMessage(
                        role = MessageRoleMapper.toRoleString(msg.role),
                        content = msg.content,
                        displayContentJson = msg.displayContentJson
                    )
                }
                // Re-save in new format
                save(sessionId, converted, oldSession.title, SessionType.CONVERSATION)
                return converted
            } catch (_: Exception) { }

            null
        }
    }

    override fun listSessions(): List<SessionMetadata> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val sessionData = json.decodeFromString(PersistedSession.serializer(), file.readText())
                    SessionMetadata(
                        sessionId = sessionData.sessionId,
                        title = sessionData.title,
                        createdAt = sessionData.createdAt,
                        updatedAt = sessionData.updatedAt,
                        type = sessionData.type
                    )
                } catch (_: Exception) {
                    // Try old format
                    try {
                        val old = json.decodeFromString(OldSessionFormat.serializer(), file.readText())
                        SessionMetadata(
                            sessionId = old.sessionId,
                            title = old.title,
                            createdAt = old.createdAt,
                            updatedAt = old.updatedAt,
                            type = SessionType.CONVERSATION
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    override fun delete(sessionId: String) {
        File(dir, "$sessionId.json").delete()
    }

    override fun rename(sessionId: String, newTitle: String) {
        val file = File(dir, "$sessionId.json")
        if (!file.exists()) return
        try {
            val sessionData = json.decodeFromString(PersistedSession.serializer(), file.readText())
            val updated = sessionData.copy(title = newTitle, updatedAt = System.currentTimeMillis())
            file.writeText(json.encodeToString(PersistedSession.serializer(), updated))
        } catch (_: Exception) {
            // Try old format fallback
            try {
                val old = json.decodeFromString(OldSessionFormat.serializer(), file.readText())
                val updated = old.copy(title = newTitle, updatedAt = System.currentTimeMillis())
                file.writeText(json.encodeToString(OldSessionFormat.serializer(), updated))
            } catch (e: Exception) {
                Lumberjack.e("FileChatHistoryProvider", "Failed to rename session", e)
            }
        }
    }

    // ── Old format for backward compatibility ──

    @Serializable
    private data class OldSessionFormat(
        val sessionId: String,
        val title: String,
        val createdAt: Long,
        val updatedAt: Long,
        val messages: List<ChatMessage>
    )
}
