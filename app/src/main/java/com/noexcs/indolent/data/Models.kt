package com.noexcs.indolent.data

import com.noexcs.indolent.agent.LLMProvider

fun getProviders(): List<LLMProvider> {
    return listOf(LLMProvider.DeepSeek)
}
