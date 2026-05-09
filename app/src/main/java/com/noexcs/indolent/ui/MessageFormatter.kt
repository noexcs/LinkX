package com.noexcs.indolent.ui

object MessageFormatter {

    fun formatTokens(n: Int): String = when {
        n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000.0)}M"
        n >= 1_000 -> "${"%.1f".format(n / 1_000.0)}k"
        else -> n.toString()
    }

    fun formatArgsJson(args: Map<String, Any?>): String {
        if (args.isEmpty()) return "{}"
        return args.entries.joinToString(",\n") { (k, v) ->
            "  \"$k\": ${v.toJsonLiteral()}"
        }.let { "{\n$it\n}" }
    }

    private fun Any?.toJsonLiteral(): String = when (this) {
        null -> "null"
        is String -> "\"$this\""
        is Number -> toString()
        is Boolean -> toString()
        else -> "\"$this\""
    }
}
