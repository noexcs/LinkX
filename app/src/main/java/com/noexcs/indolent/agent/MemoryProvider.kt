package com.noexcs.indolent.agent

interface MemoryProvider {
    fun read(): String
    fun save(key: String, value: String)
}
