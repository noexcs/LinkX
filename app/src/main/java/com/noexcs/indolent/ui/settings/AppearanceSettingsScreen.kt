package com.noexcs.indolent.ui.settings

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.ui.theme.ContrastLevel
import com.noexcs.indolent.ui.theme.ThemeRegistry
import com.noexcs.indolent.ui.theme.ThemeState
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
    onContrastLevelChanged: (ContrastLevel) -> Unit = {},
) {
    var selectedThemeKey by remember { mutableStateOf(settingsManager.themeKey) }
    var dynamicColor by remember { mutableStateOf(settingsManager.dynamicColor) }
    var seedColor by remember { mutableStateOf(Color(settingsManager.seedColor)) }
    var hexInput by remember(seedColor) {
        mutableStateOf(String.format("%06X", seedColor.toArgb() and 0xFFFFFF))
    }
    var contrastLevel by remember {
        mutableStateOf(
            when (settingsManager.contrastLevel) {
                "medium" -> ContrastLevel.Medium
                "high" -> ContrastLevel.High
                else -> ContrastLevel.Standard
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(R.string.title_appearance)) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Current theme hero ───────────────────────────────
            val currentDescriptor = remember(ThemeState.themeKey, ThemeState.dynamicThemesVersion) {
                ThemeRegistry.findByKey(ThemeState.themeKey)
            }
            ThemeHeroCard(
                descriptor = currentDescriptor,
                onClick = {}, // already on the appearance page
            )

            // ── Theme Gallery ──────────────────────────────────
            Text(
                text = stringResource(R.string.section_theme_mode),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.section_theme_mode_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            val themes = remember { ThemeRegistry.themes }
            val currentScheme = MaterialTheme.colorScheme
            val columns = 3
            val rows = (themes.size + columns - 1) / columns

            // Grid as a series of rows (avoids LazyVerticalGrid nested scroll issues)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (col in 0 until columns) {
                            val index = row * columns + col
                            if (index < themes.size) {
                                val theme = themes[index]
                                val label = if (theme.label.isNotEmpty()) theme.label
                                            else stringResource(theme.labelRes)
                                val isSelected = selectedThemeKey == theme.key
                                val previewScheme = remember(theme.key, seedColor) {
                                    if (theme.usesSeedColor && seedColor != Color.Unspecified)
                                        seedColorScheme(seedColor, darkTheme = false)
                                    else theme.colorScheme ?: currentScheme
                                }

                                ThemeGalleryCard(
                                    themeKey = theme.key,
                                    label = label,
                                    mood = theme.mood,
                                    previewScheme = previewScheme,
                                    isSelected = isSelected,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        selectedThemeKey = theme.key
                                        settingsManager.themeKey = theme.key
                                        onThemeKeyChanged(theme.key)
                                    }
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ── Seed Color (shown when "seed" selected) ────────
            if (selectedThemeKey == "seed") {
                SectionCard(
                    title = stringResource(R.string.section_seed_color),
                    subtitle = stringResource(R.string.section_seed_color_subtitle)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Color preview
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

                        // Live preview
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.seed_color_custom) + " preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val liveScheme = remember(seedColor) {
                            seedColorScheme(seedColor, darkTheme = false)
                        }
                        ThemePreviewMini(scheme = liveScheme, width = 300.dp, height = 72.dp)
                    }
                }
            }

            // ── Dynamic Color ──────────────────────────────────
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

            // ── Contrast ──────────────────────────────────────
            SectionCard(
                title = stringResource(R.string.section_contrast),
                subtitle = stringResource(R.string.section_contrast_subtitle)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val contrastOptions = listOf(
                        ContrastLevel.Standard to stringResource(R.string.contrast_standard),
                        ContrastLevel.Medium to stringResource(R.string.contrast_medium),
                        ContrastLevel.High to stringResource(R.string.contrast_high),
                    )
                    contrastOptions.forEachIndexed { index, (level, label) ->
                        SegmentedButton(
                            selected = contrastLevel == level,
                            onClick = {
                                contrastLevel = level
                                settingsManager.contrastLevel = when (level) {
                                    ContrastLevel.Medium -> "medium"
                                    ContrastLevel.High -> "high"
                                    else -> "standard"
                                }
                                onContrastLevelChanged(level)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, contrastOptions.size),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Theme Gallery Card ────────────────────────────────────────

@Composable
private fun ThemeGalleryCard(
    themeKey: String,
    label: String,
    mood: String,
    previewScheme: androidx.compose.material3.ColorScheme,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
    val borderWidth = if (isSelected) 2.dp else 1.dp

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isSelected) 3.dp else 0.dp,
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .border(borderWidth, borderColor, MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Mini preview
            ThemePreviewMini(
                scheme = previewScheme,
                width = 130.dp,
                height = 72.dp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (mood.isNotEmpty()) {
                Text(
                    text = mood,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
