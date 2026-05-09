package com.noexcs.indolent.agent.skills

enum class SkillSource { BUILT_IN, USER }

data class Skill(
    val name: String,
    val description: String,
    val content: String,
    val source: SkillSource,
    val fileName: String = ""
)
