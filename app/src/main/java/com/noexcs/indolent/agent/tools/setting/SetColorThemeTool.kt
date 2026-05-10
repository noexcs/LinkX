package com.noexcs.indolent.agent.tools.setting

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.logging.Lumberjack
import com.noexcs.indolent.ui.theme.ThemeRegistry
import com.noexcs.indolent.ui.theme.ThemeState
import com.noexcs.indolent.ui.theme.ThemeDescriptor
import com.noexcs.indolent.ui.theme.seedColorScheme

class SetColorThemeTool(private val settings: SettingsManager) : AgentTool {

    override val name = "set_color_theme"
    override val description = """
        Create, apply, list, or delete color themes for the app.

        Actions:
        - "create" — create a new theme from a seed color. Provide a name and hex seed_color. Optionally set dark_theme=true for a dark theme.
        - "apply" — switch to an existing theme by its key (e.g. "aurora", "system", "dynamic_0").
        - "list" — list all available themes including built-in and user-created ones.
        - "delete" — remove a user-created dynamic theme by its key.

        The seed color is used to generate a complete Material 3 color scheme (primary, secondary, tertiary, background, surface, etc.) using the HCT color system.

        Built-in theme keys: system, light, dark, aurora, matcha, cyber, neutral, crimson, sunset_orange, golden_yellow, emerald_green, cyan_blue, seed.

        Examples:
        - Create: action=create name="Ocean Blue" seed_color="#2E7D5A"
        - Create dark: action=create name="Night Rose" seed_color="#B3261E" dark_theme=true
        - Apply: action=apply key="aurora"
        - List: action=list
        - Delete: action=delete key="dynamic_0"
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "action",
            type = "string",
            description = "\"create\", \"apply\", \"list\", or \"delete\""
        ),
        ToolParameter(
            name = "name",
            type = "string",
            required = false,
            description = "Display name for the theme. Required for create."
        ),
        ToolParameter(
            name = "seed_color",
            type = "string",
            required = false,
            description = "Hex color like \"#6750A4\" used as seed to generate the full scheme. Required for create."
        ),
        ToolParameter(
            name = "dark_theme",
            type = "boolean",
            required = false,
            description = "Set to true to create a dark theme variant. Default false."
        ),
        ToolParameter(
            name = "key",
            type = "string",
            required = false,
            description = "Theme key to apply or delete. Required for apply/delete."
        ),
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val action = args["action"] as? String
            ?: return "Error: action is required (\"create\", \"apply\", \"list\", or \"delete\")"

        Lumberjack.i("SetColorThemeTool", "Action=$action")

        return try {
            when (action.lowercase()) {
                "create" -> createTheme(args)
                "apply" -> applyTheme(args)
                "list" -> listThemes()
                "delete" -> deleteTheme(args)
                else -> "Error: Unknown action '$action'. Use \"create\", \"apply\", \"list\", or \"delete\"."
            }
        } catch (e: Exception) {
            Lumberjack.e("SetColorThemeTool", "Operation failed", e)
            "Error: ${e.message ?: "Unknown error"}"
        }
    }

    private fun createTheme(args: Map<String, Any?>): String {
        val name = (args["name"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: return "Error: name is required for create action"
        val seedColorStr = (args["seed_color"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: return "Error: seed_color is required for create action (e.g. \"#6750A4\")"
        val isDark = (args["dark_theme"] as? Boolean) ?: false

        val seedColor = parseHexColor(seedColorStr)
            ?: return "Error: Invalid seed_color '$seedColorStr'. Use hex format like \"#6750A4\" or \"#FF6750A4\"."

        val scheme = seedColorScheme(seedColor, isDark)
        val key = ThemeRegistry.nextDynamicKey()

        val descriptor = ThemeDescriptor(
            key = key,
            label = name,
            isDark = isDark,
            colorScheme = scheme,
        )

        ThemeState.addDynamicTheme(descriptor)
        settings.dynamicThemesJson = ThemeRegistry.toDynamicJson()

        // Switch to the new theme — updates both persistence and live UI
        settings.themeKey = key
        ThemeState.applyTheme(key)

        val themeType = if (isDark) "dark" else "light"
        return buildString {
            appendLine("Theme \"$name\" created and applied.")
            appendLine("Key: $key")
            appendLine("Type: $themeType")
            appendLine("Seed: ${seedColorStr.uppercase()}")
            appendLine("Primary: #${Integer.toHexString(scheme.primary.toArgb() and 0xFFFFFF).uppercase()}")
            appendLine()
            appendLine("Use action=apply key=\"$key\" to re-apply later.")
        }
    }

    private fun applyTheme(args: Map<String, Any?>): String {
        val key = (args["key"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: return "Error: key is required for apply action"

        val theme = ThemeRegistry.findByKey(key)
        if (theme.key != key) {
            return "Error: No theme found with key '$key'. Use action=list to see available themes."
        }

        settings.themeKey = key
        ThemeState.applyTheme(key)
        val name = theme.label.ifEmpty { "(built-in)" }
        return "Theme \"$name\" ($key) applied."
    }

    private fun listThemes(): String = buildString {
        appendLine("Available Themes")
        appendLine("═".repeat(50))
        val currentKey = settings.themeKey
        ThemeRegistry.themes.forEach { theme ->
            val marker = if (theme.key == currentKey) "★" else " "
            val name = theme.label.ifEmpty {
                // Built-in themes use labelRes — show key as fallback
                theme.key.replace('_', ' ').replaceFirstChar { it.uppercase() }
            }
            val type = when {
                theme.supportsDynamicColor -> "system dynamic"
                theme.usesSeedColor -> "custom seed"
                theme.isDark -> "dark"
                else -> "light"
            }
            val dyn = if (ThemeRegistry.isDynamic(theme.key)) " [dynamic]" else ""
            appendLine(" $marker ${theme.key.padEnd(20)} $name  ($type)$dyn")
        }
        appendLine()
        appendLine("★ = currently active")
        appendLine("Use action=apply key=\"<key>\" to switch themes.")
    }

    private fun deleteTheme(args: Map<String, Any?>): String {
        val key = (args["key"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: return "Error: key is required for delete action"

        if (!ThemeRegistry.isDynamic(key)) {
            return "Error: '$key' is a built-in theme and cannot be deleted. Only user-created dynamic themes can be deleted."
        }

        ThemeState.removeDynamicTheme(key)
        settings.dynamicThemesJson = ThemeRegistry.toDynamicJson()

        // If we were using this theme, fall back to seed
        if (settings.themeKey == key) {
            settings.themeKey = "seed"
            ThemeState.applyTheme("seed")
        }

        return "Theme '$key' deleted."
    }

    private fun parseHexColor(hex: String): Color? {
        val cleaned = hex.removePrefix("#").trim()
        return try {
            when (cleaned.length) {
                6 -> Color(("FF$cleaned").toLong(16).toInt())
                8 -> Color(cleaned.toLong(16).toInt())
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
