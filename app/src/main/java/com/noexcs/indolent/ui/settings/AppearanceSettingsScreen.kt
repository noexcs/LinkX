package com.noexcs.indolent.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.ui.theme.ThemeRegistry
import com.noexcs.indolent.ui.theme.seedColorScheme

private val seedColorPresets = listOf(
    Color(0xFF6750A4) to "M3 Default",
    Color(0xFFE86A17) to "Sunset",
    Color(0xFFB3261E) to "Crimson",
    Color(0xFF2E7D5A) to "Emerald",
    Color(0xFF006E90) to "Ocean",
    Color(0xFFB78103) to "Gold",
    Color(0xFF5B5FEF) to "Aurora",
    Color(0xFFFF4DCA) to "Cyber Pink",
    Color(0xFF3D5AFE) to "Blue",
    Color(0xFF4F6F52) to "Matcha",
    Color(0xFF00E5FF) to "Cyan",
    Color(0xFF9A4521) to "Terracotta",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettingsScreen(
    settingsManager: SettingsManager,
    onThemeKeyChanged: (String) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onSeedColorChanged: (Color) -> Unit,
    onBack: () -> Unit,
) {
    var selectedThemeKey by remember { mutableStateOf(settingsManager.themeKey) }
    var dynamicColor by remember { mutableStateOf(settingsManager.dynamicColor) }
    var seedColor by remember { mutableStateOf(Color(settingsManager.seedColor)) }
    var hexInput by remember(seedColor) {
        mutableStateOf(String.format("%06X", seedColor.toArgb() and 0xFFFFFF))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.title_appearance)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(
                title = stringResource(R.string.section_theme_mode),
                subtitle = stringResource(R.string.section_theme_mode_subtitle)
            ) {
                Column {
                    ThemeRegistry.themes.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedThemeKey = theme.key
                                    settingsManager.themeKey = theme.key
                                    onThemeKeyChanged(theme.key)
                                }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(theme.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            RadioButton(
                                selected = selectedThemeKey == theme.key,
                                onClick = null
                            )
                        }
                    }
                }
            }

            if (selectedThemeKey == "seed") {
                SectionCard(
                    title = stringResource(R.string.section_seed_color),
                    subtitle = stringResource(R.string.section_seed_color_subtitle)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Current color preview
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(seedColor)
                                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.seed_color_custom),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "#${hexInput.uppercase()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Hex input
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { input ->
                                val filtered = input.filter { it in "0123456789ABCDEFabcdef" }.take(6)
                                hexInput = filtered
                                if (filtered.length == 6) {
                                    val newColor = Color(("FF$filtered").toLong(16).toInt())
                                    seedColor = newColor
                                    settingsManager.seedColor = newColor.toArgb()
                                    onSeedColorChanged(newColor)
                                }
                            },
                            label = { Text("HEX") },
                            prefix = { Text("#") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    // Try to apply even partial input by padding
                                    val padded = hexInput.padEnd(6, '0')
                                    val newColor = Color(("FF$padded").toLong(16).toInt())
                                    seedColor = newColor
                                    settingsManager.seedColor = newColor.toArgb()
                                    onSeedColorChanged(newColor)
                                }
                            )
                        )

                        HorizontalDivider()

                        // Preset colors
                        Text(
                            text = stringResource(R.string.seed_color_presets),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            seedColorPresets.forEach { (color, _) ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (seedColor.toArgb() == color.toArgb()) 3.dp else 1.dp,
                                            color = if (seedColor.toArgb() == color.toArgb())
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            seedColor = color
                                            hexInput = String.format("%06X", color.toArgb() and 0xFFFFFF)
                                            settingsManager.seedColor = color.toArgb()
                                            onSeedColorChanged(color)
                                        }
                                )
                            }
                        }

                        // Preview cards
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val previewScheme = remember(seedColor) {
                            seedColorScheme(seedColor, darkTheme = false)
                        }
                        ThemePreviewRow(previewScheme)
                    }
                }
            }

            SectionCard(
                title = stringResource(R.string.section_dynamic_color),
                subtitle = stringResource(R.string.section_dynamic_color_subtitle)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (dynamicColor) stringResource(R.string.tool_switch_enabled)
                            else stringResource(R.string.tool_switch_disabled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (dynamicColor) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = dynamicColor, onCheckedChange = {
                        dynamicColor = it
                        settingsManager.dynamicColor = it
                        onDynamicColorChanged(it)
                    })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ThemePreviewRow(scheme: androidx.compose.material3.ColorScheme) {
    val swatches = listOf(
        "Primary" to scheme.primary,
        "2nd" to scheme.secondary,
        "3rd" to scheme.tertiary,
        "Bg" to scheme.background,
        "Sf" to scheme.surface,
        "Sc" to scheme.surfaceContainer,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        swatches.forEach { (label, color) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
