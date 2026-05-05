package com.noexcs.indolent.agent.tools.setting

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack

class AudioControlTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext
    private val audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override val name = "audio_control"
    override val description = """
        Read or control audio volume and ringer mode.

        Actions:
        - "get" — read current volume levels for all streams (or a specific stream)
        - "set" — set volume for a specific stream (e.g. stream="media", value="7")
        - "adjust" — step volume up/down/mute (like hardware buttons)
        - "mode" — set ringer mode: "normal", "vibrate", or "silent"

        Streams: media, ring, alarm, notification, voice_call, system, dtmf
        Volume values: 0 to stream_max
        Shortcut values for adjust: "up", "down", "mute"

        No special permissions needed for volume operations.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "action",
            type = "string",
            description = "\"get\", \"set\", \"adjust\", or \"mode\""
        ),
        ToolParameter(
            name = "stream",
            type = "string",
            required = false,
            defaultValue = "media",
            description = "Audio stream: \"media\", \"ring\", \"alarm\", \"notification\", \"voice_call\", \"system\", \"dtmf\". Default \"media\"."
        ),
        ToolParameter(
            name = "value",
            type = "string",
            required = false,
            description = "Volume value (0 to max), or \"up\"/\"down\"/\"mute\" for adjust. Ringer mode for mode action: \"normal\"/\"vibrate\"/\"silent\"."
        )
    )

    private val streamMap = mapOf(
        "media" to AudioManager.STREAM_MUSIC,
        "music" to AudioManager.STREAM_MUSIC,
        "ring" to AudioManager.STREAM_RING,
        "alarm" to AudioManager.STREAM_ALARM,
        "notification" to AudioManager.STREAM_NOTIFICATION,
        "voice_call" to AudioManager.STREAM_VOICE_CALL,
        "call" to AudioManager.STREAM_VOICE_CALL,
        "system" to AudioManager.STREAM_SYSTEM,
        "dtmf" to AudioManager.STREAM_DTMF,
    )

    private val streamNames = mapOf(
        AudioManager.STREAM_MUSIC to "media",
        AudioManager.STREAM_RING to "ring",
        AudioManager.STREAM_ALARM to "alarm",
        AudioManager.STREAM_NOTIFICATION to "notification",
        AudioManager.STREAM_VOICE_CALL to "voice_call",
        AudioManager.STREAM_SYSTEM to "system",
        AudioManager.STREAM_DTMF to "dtmf",
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val action = args["action"] as? String ?: return "Error: action is required (\"get\", \"set\", \"adjust\", or \"mode\")"
        val streamName = args["stream"] as? String ?: "media"
        val value = args["value"] as? String

        Lumberjack.i("AudioControlTool", "Action=$action stream=$streamName value=$value")

        return try {
            when (action.lowercase()) {
                "get" -> getVolume(streamName)
                "set" -> setVolume(streamName, value)
                "adjust" -> adjustVolume(streamName, value)
                "mode" -> setRingerMode(value)
                else -> "Error: Unknown action '$action'. Use \"get\", \"set\", \"adjust\", or \"mode\"."
            }
        } catch (e: SecurityException) {
            Lumberjack.e("AudioControlTool", "Permission denied", e)
            "Error: Permission denied. Some audio operations may require additional permissions."
        } catch (e: Exception) {
            Lumberjack.e("AudioControlTool", "Audio operation failed", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    private fun getVolume(streamName: String): String {
        return if (streamName.lowercase() == "all" || streamName.isBlank()) {
            buildString {
                appendLine("Audio Status")
                appendLine("─".repeat(40))
                streamNames.forEach { (streamType, name) ->
                    val vol = audioManager.getStreamVolume(streamType)
                    val max = audioManager.getStreamMaxVolume(streamType)
                    val pct = if (max > 0) (vol * 100 / max) else 0
                    val bar = "█".repeat(pct / 10) + "░".repeat(10 - pct / 10)
                    appendLine("  $name: $vol/$max $bar ${pct}%")
                }
                appendLine()
                appendLine("Ringer mode: ${getRingerModeLabel()}")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    appendLine("Do Not Disturb: ${if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) "active (silent)" else "inactive"}")
                }
            }
        } else {
            val streamType = resolveStream(streamName) ?: return unknownStreamError()
            val vol = audioManager.getStreamVolume(streamType)
            val max = audioManager.getStreamMaxVolume(streamType)
            val pct = if (max > 0) (vol * 100 / max) else 0
            "Volume ($streamName): $vol / $max (${pct}%)"
        }
    }

    private fun setVolume(streamName: String, value: String?): String {
        if (value == null) return "Error: value is required for set action"

        val streamType = resolveStream(streamName) ?: return unknownStreamError()
        val max = audioManager.getStreamMaxVolume(streamType)
        val level = value.toIntOrNull()
            ?: return "Error: value must be an integer (0 to $max), got '$value'"

        if (level < 0 || level > max) {
            return "Error: Volume level $level out of range (0 to $max for $streamName)"
        }

        audioManager.setStreamVolume(streamType, level, 0)
        Lumberjack.i("AudioControlTool", "Set $streamName volume to $level/$max")
        return "OK: $streamName volume set to $level / $max"
    }

    private fun adjustVolume(streamName: String, direction: String?): String {
        if (direction == null) return "Error: value is required for adjust action (\"up\", \"down\", \"mute\")"

        val streamType = resolveStream(streamName) ?: return unknownStreamError()

        when (direction.lowercase()) {
            "up" -> audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_RAISE, 0)
            "down" -> audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_LOWER, 0)
            "mute" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_MUTE, 0)
                } else {
                    // Fallback: set to 0
                    audioManager.setStreamVolume(streamType, 0, 0)
                }
            }
            else -> return "Error: Unknown adjust direction '$direction'. Use \"up\", \"down\", or \"mute\"."
        }

        val newVol = audioManager.getStreamVolume(streamType)
        val max = audioManager.getStreamMaxVolume(streamType)
        Lumberjack.i("AudioControlTool", "Adjusted $streamName $direction, now $newVol/$max")
        return "OK: $streamName volume adjusted $direction → $newVol / $max"
    }

    private fun setRingerMode(mode: String?): String {
        if (mode == null) return "Error: value is required for mode action (\"normal\", \"vibrate\", \"silent\")"

        val ringerMode = when (mode.lowercase()) {
            "normal" -> AudioManager.RINGER_MODE_NORMAL
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "silent" -> AudioManager.RINGER_MODE_SILENT
            else -> return "Error: Unknown ringer mode '$mode'. Use \"normal\", \"vibrate\", or \"silent\"."
        }

        // Check if we're allowed (DND can block this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ringerMode == AudioManager.RINGER_MODE_SILENT && !audioManager.isVolumeFixed) {
                // OK
            }
        }

        audioManager.ringerMode = ringerMode
        Lumberjack.i("AudioControlTool", "Set ringer mode to $mode")
        return "OK: Ringer mode set to $mode"
    }

    private fun getRingerModeLabel(): String = when (audioManager.ringerMode) {
        AudioManager.RINGER_MODE_NORMAL -> "normal"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        AudioManager.RINGER_MODE_SILENT -> "silent"
        else -> "unknown"
    }

    private fun resolveStream(name: String): Int? {
        return streamMap[name.lowercase().trim()]
    }

    private fun unknownStreamError(): String {
        return "Error: Unknown stream. Supported streams: ${streamMap.keys.toSet().sorted().joinToString(", ")}"
    }
}
