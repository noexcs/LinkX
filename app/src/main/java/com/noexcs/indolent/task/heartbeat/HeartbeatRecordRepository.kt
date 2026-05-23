package com.noexcs.indolent.task.heartbeat

import android.content.Context
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.serialization.json.Json
import java.io.File

class HeartbeatRecordRepository(context: Context) {
    private val dir = File(context.filesDir, "heartbeat/records").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Synchronized fun save(record: HeartbeatRecord) {
        File(dir, "${record.id}.json").writeText(json.encodeToString(record))
    }

    @Synchronized fun listAll(): List<HeartbeatRecord> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<HeartbeatRecord>(file.readText())
                } catch (_: Exception) { null }
            }
            ?.sortedByDescending { it.executedAt }
            ?: emptyList()
    }

    @Synchronized fun lastRecord(): HeartbeatRecord? = listAll().firstOrNull()

    @Synchronized fun pruneOldRecords(keep: Int = 50) {
        val records = listAll()
        if (records.size > keep) {
            records.drop(keep).forEach { record ->
                if (!File(dir, "${record.id}.json").delete()) {
                    Lumberjack.w("HeartbeatRecordRepository", "Failed to delete record: ${record.id}")
                }
            }
        }
    }
}
