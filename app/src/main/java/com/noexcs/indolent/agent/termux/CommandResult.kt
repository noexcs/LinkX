package com.noexcs.indolent.agent.termux

data class CommandResult(
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = 0,
    val errorMessage: String? = null
)