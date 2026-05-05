package com.noexcs.indolent.logging

enum class Level(val priority: Int, val label: String) {
    V(0, "V"),
    D(1, "D"),
    I(2, "I"),
    W(3, "W"),
    E(4, "E"),
    F(5, "F");

    val atLeast: (Level) -> Boolean = { other -> this.priority >= other.priority }

    companion object {
        fun fromLabel(label: String): Level? = when (label.uppercase()) {
            "V", "VERBOSE" -> V
            "D", "DEBUG" -> D
            "I", "INFO" -> I
            "W", "WARN", "WARNING" -> W
            "E", "ERROR", "ERR" -> E
            "F", "FATAL" -> F
            else -> null
        }
    }
}
