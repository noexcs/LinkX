package com.noexcs.indolent.agent.tools.interact

import kotlinx.serialization.Serializable

@Serializable
enum class ContentType { IMAGE, TEXT, PDF, WEB }

@Serializable
data class DisplayContent(
    val id: String,
    val type: ContentType,
    val title: String? = null,
    val path: String? = null,
    val textContent: String? = null,
    val url: String? = null,
)
