package com.noexcs.indolent.logging

data class LogFilter(
    val count: Int = 50,
    val level: Level? = null,
    val tag: String? = null,
    val query: String? = null,
    val since: Long? = null,
    val before: Long? = null,
    val offset: Int = 0
) {
    companion object {
        const val MAX_COUNT = 500
        const val DEFAULT_COUNT = 50

        fun parseSince(raw: String): Long? {
            val trimmed = raw.trim()
            trimmed.toLongOrNull()?.let { longVal ->
                // 10-digit numbers or values < ~Sept 2001 in millis → treat as epoch seconds
                return if (trimmed.length <= 10 || longVal < 1_000_000_000_000L) longVal * 1000 else longVal
            }
            // Try relative: "5m", "1h", "30s", "2d", "10min"
            val re = Regex("""^(\d+)\s*(s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days)?$""", RegexOption.IGNORE_CASE)
            val match = re.find(trimmed) ?: return null
            val num = match.groupValues[1].toLongOrNull() ?: return null
            val unit = (match.groupValues[2].takeIf { it.isNotEmpty() } ?: "s").lowercase()
            val ms = when {
                unit.startsWith("d") -> num * 86_400_000L
                unit.startsWith("h") -> num * 3_600_000L
                unit.startsWith("m") -> num * 60_000L
                else -> num * 1000L // seconds
            }
            return System.currentTimeMillis() - ms
        }
    }
}
