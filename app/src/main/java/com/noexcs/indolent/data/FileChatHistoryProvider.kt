package com.noexcs.indolent.data

import android.content.Context
import com.noexcs.indolent.agent.LLMMessage
import com.noexcs.indolent.agent.PersistedSession
import com.noexcs.indolent.agent.SessionMetadata
import com.noexcs.indolent.agent.SessionPersistence
import com.noexcs.indolent.agent.SessionType
import kotlinx.serialization.json.Json
import java.io.File

class FileChatHistoryProvider(context: Context) : SessionPersistence {

    private val dir = File(context.filesDir, "sessions").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val syncLock = Any()

    override suspend fun save(
        sessionId: String,
        messages: List<LLMMessage>,
        title: String,
        type: SessionType
    ) {
        synchronized(syncLock) {
            val file = File(dir, "${sessionId}.json")
            val existingCreatedAt = if (file.exists()) {
                try {
                    json.decodeFromString(PersistedSession.serializer(), file.readText()).createdAt
                } catch (_: Exception) { null }
            } else null

            val sessionData = PersistedSession(
                sessionId = sessionId,
                title = title,
                createdAt = existingCreatedAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                messages = messages,
                type = type
            )
            file.writeText(json.encodeToString(PersistedSession.serializer(), sessionData))
        }
    }

    override suspend fun load(sessionId: String): List<LLMMessage>? {
        return synchronized(syncLock) {
            val file = File(dir, "$sessionId.json")
            if (!file.exists()) null
            else try {
                json.decodeFromString(PersistedSession.serializer(), file.readText()).messages
            } catch (_: Exception) { null }
        }
    }

    override fun listSessions(): List<SessionMetadata> {
        synchronized(syncLock) {
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
                    } catch (_: Exception) { null }
                }
                ?.sortedByDescending { it.updatedAt }
                ?: emptyList()
        }
    }

    override fun delete(sessionId: String) {
        synchronized(syncLock) {
            File(dir, "$sessionId.json").delete()
        }
    }

    override fun rename(sessionId: String, newTitle: String) {
        synchronized(syncLock) {
            val file = File(dir, "$sessionId.json")
            if (!file.exists()) return
            try {
                val sessionData = json.decodeFromString(PersistedSession.serializer(), file.readText())
                val updated = sessionData.copy(title = newTitle, updatedAt = System.currentTimeMillis())
                file.writeText(json.encodeToString(PersistedSession.serializer(), updated))
            } catch (_: Exception) { }
        }
    }
}
