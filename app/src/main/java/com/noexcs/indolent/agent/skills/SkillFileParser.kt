package com.noexcs.indolent.agent.skills

object SkillFileParser {

    data class ParsedFrontmatter(
        val name: String,
        val description: String,
        val content: String
    )

    fun parse(fileName: String, fileContent: String): ParsedFrontmatter {
        val trimmed = fileContent.trimStart()
        if (!trimmed.startsWith("---\n")) {
            return fallback(fileName, fileContent)
        }

        val afterFirstDelim = trimmed.removePrefix("---\n")
        val closingIndex = afterFirstDelim.indexOf("\n---\n")
        if (closingIndex == -1) {
            // Check for "\n---" at end of file (no trailing newline)
            if (afterFirstDelim.endsWith("\n---")) {
                val frontmatterBlock = afterFirstDelim.removeSuffix("\n---")
                val fields = parseFrontmatterFields(frontmatterBlock)
                return ParsedFrontmatter(
                    name = fields["name"] ?: nameFromFileName(fileName),
                    description = fields["description"] ?: "",
                    content = ""
                )
            }
            return fallback(fileName, fileContent)
        }

        val frontmatterBlock = afterFirstDelim.substring(0, closingIndex)
        val body = afterFirstDelim.substring(closingIndex + 5).trimStart()
        val fields = parseFrontmatterFields(frontmatterBlock)

        return ParsedFrontmatter(
            name = fields["name"] ?: nameFromFileName(fileName),
            description = fields["description"] ?: "",
            content = body
        )
    }

    private fun parseFrontmatterFields(block: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        for (line in block.lines()) {
            val separatorIndex = line.indexOf(": ")
            if (separatorIndex == -1) continue
            val key = line.substring(0, separatorIndex).trim().lowercase()
            val value = line.substring(separatorIndex + 2).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                fields[key] = value
            }
        }
        return fields
    }

    private fun fallback(fileName: String, content: String): ParsedFrontmatter {
        return ParsedFrontmatter(
            name = nameFromFileName(fileName),
            description = "",
            content = content
        )
    }

    private fun nameFromFileName(fileName: String): String {
        return fileName.removeSuffix(".md")
            .replace(Regex("[_\\s]+"), "-")
            .lowercase()
    }
}
