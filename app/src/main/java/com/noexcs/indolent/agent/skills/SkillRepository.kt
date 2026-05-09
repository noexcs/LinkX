package com.noexcs.indolent.agent.skills

import android.content.Context
import android.net.Uri
import com.noexcs.indolent.data.SettingsManager
import java.io.File
import java.io.IOException

class SkillRepository(
    private val context: Context,
    private val settings: SettingsManager
) {
    companion object {
        private val BUILT_IN_SKILL_FILES = listOf(
            "coding-assistant.md",
            "writing-assistant.md"
        )
    }

    private val appContext = context.applicationContext
    private val userSkillsDir: File
        get() = File(appContext.filesDir, "skills").also { it.mkdirs() }

    @Volatile
    private var builtInCache: List<Skill>? = null

    fun getAllSkills(): List<Skill> {
        return getBuiltinSkills() + getUserSkills()
    }

    fun getBuiltinSkills(): List<Skill> {
        builtInCache?.let { return it }
        val skills = BUILT_IN_SKILL_FILES.mapNotNull { fileName ->
            try {
                val content = appContext.assets.open("skills/$fileName").bufferedReader().use { it.readText() }
                val parsed = SkillFileParser.parse(fileName, content)
                Skill(
                    name = parsed.name,
                    description = parsed.description,
                    content = parsed.content,
                    source = SkillSource.BUILT_IN,
                    fileName = fileName
                )
            } catch (e: IOException) {
                null
            }
        }
        builtInCache = skills
        return skills
    }

    fun getUserSkills(): List<Skill> {
        val files = userSkillsDir.listFiles() ?: emptyArray()
        return files
            .filter { it.isFile && it.name.endsWith(".md") }
            .mapNotNull { file ->
                try {
                    val content = file.readText()
                    val parsed = SkillFileParser.parse(file.name, content)
                    Skill(
                        name = parsed.name,
                        description = parsed.description,
                        content = parsed.content,
                        source = SkillSource.USER,
                        fileName = file.name
                    )
                } catch (e: IOException) {
                    null
                }
            }
            .sortedBy { it.name }
    }

    fun getActiveSkill(): Skill? {
        if (!settings.skillsEnabled) return null
        val activeName = settings.activeSkillName
        if (activeName.isBlank()) return null
        return getAllSkills().firstOrNull { it.name == activeName && settings.isSkillEnabled(it.name) }
    }

    fun getActiveSkillContent(): String {
        return getActiveSkill()?.content ?: ""
    }

    fun createUserSkill(name: String, description: String): Skill {
        val fileName = "$name.md"
        val frontmatter = buildString {
            appendLine("---")
            appendLine("name: $name")
            if (description.isNotBlank()) {
                appendLine("description: $description")
            }
            appendLine("---")
            appendLine()
        }

        var file = File(userSkillsDir, fileName)
        if (file.exists()) {
            var suffix = 1
            while (file.exists()) {
                file = File(userSkillsDir, "${name}-${suffix}.md")
                suffix++
            }
        }

        file.writeText(frontmatter)

        val actualName = file.nameWithoutExtension
        return Skill(
            name = actualName,
            description = description,
            content = "",
            source = SkillSource.USER,
            fileName = file.name
        )
    }

    fun deleteUserSkill(name: String) {
        val skill = getUserSkills().firstOrNull { it.name == name } ?: return
        val file = File(userSkillsDir, skill.fileName)
        if (file.exists()) {
            file.delete()
        }
        if (settings.activeSkillName == name) {
            settings.activeSkillName = ""
        }
    }

    fun importSkill(uri: Uri): Skill {
        val content = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IOException("Cannot read file")

        val parsed = SkillFileParser.parse("imported.md", content)
        var fileName = "${parsed.name}.md"

        // Avoid overwriting existing files
        var file = File(userSkillsDir, fileName)
        if (file.exists()) {
            var suffix = 1
            while (file.exists()) {
                file = File(userSkillsDir, "${parsed.name}-${suffix}.md")
                suffix++
            }
            fileName = file.name
        }

        file.writeText(content)

        val actualName = file.nameWithoutExtension
        return Skill(
            name = actualName,
            description = parsed.description,
            content = parsed.content,
            source = SkillSource.USER,
            fileName = fileName
        )
    }

    fun exportSkill(name: String, uri: Uri) {
        val skill = getUserSkills().firstOrNull { it.name == name }
            ?: throw IOException("Skill not found: $name")
        val file = File(userSkillsDir, skill.fileName)
        if (!file.exists()) {
            throw IOException("Skill file not found: ${skill.fileName}")
        }
        appContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(file.readText())
        } ?: throw IOException("Cannot write to destination")
    }
}
