package com.noexcs.indolent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.ui.settings.GroupCard
import com.noexcs.indolent.ui.settings.GroupCardItem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment

private data class LanguageOption(val tag: String, val labelRes: Int)

private val languageOptions = listOf(
    LanguageOption("", R.string.language_system),
    LanguageOption("en", R.string.language_en),
    LanguageOption("zh-Hans", R.string.language_zh_hans),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    scrollState: ScrollState = rememberScrollState(),
    onBack: () -> Unit,
    onNavigateToApiSettings: () -> Unit,
    onNavigateToSystemPromptSettings: () -> Unit,
    onNavigateToMemorySettings: () -> Unit,
    onNavigateToToolSettings: () -> Unit,
    onNavigateToHeartbeatSettings: () -> Unit,
    onNavigateToUsageStats: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSkillSettings: () -> Unit = {}
) {
    var selectedLanguage by remember { mutableStateOf(settingsManager.language) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    val languageLabel = languageOptions.find { it.tag == selectedLanguage }
        ?.let { stringResource(it.labelRes) } ?: stringResource(R.string.language_system)

    // Language bottom sheet
    if (showLanguageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.section_language),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                languageOptions.forEach { option ->
                    val selected = selectedLanguage == option.tag
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedLanguage = option.tag
                                settingsManager.language = option.tag
                                showLanguageSheet = false
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── AI Configuration ───────────────────────────────────
        GroupCard(title = stringResource(R.string.section_group_ai_config)) {
            GroupCardItem(
                title = stringResource(R.string.title_api_settings),
                subtitle = stringResource(R.string.section_api_settings_subtitle),
                onClick = onNavigateToApiSettings,
            )
            GroupCardItem(
                title = stringResource(R.string.title_system_prompt_settings),
                subtitle = stringResource(R.string.section_system_prompt_subtitle),
                onClick = onNavigateToSystemPromptSettings,
            )
            GroupCardItem(
                title = stringResource(R.string.title_memory_settings),
                subtitle = stringResource(R.string.section_memory_subtitle),
                onClick = onNavigateToMemorySettings,
            )
            GroupCardItem(
                title = stringResource(R.string.title_tool_settings),
                subtitle = stringResource(R.string.section_tool_settings_subtitle),
                onClick = onNavigateToToolSettings,
            )
            GroupCardItem(
                title = stringResource(R.string.title_heartbeat_settings),
                subtitle = stringResource(R.string.section_heartbeat_settings_subtitle),
                onClick = onNavigateToHeartbeatSettings,
            )
            GroupCardItem(
                title = stringResource(R.string.title_skill_settings),
                subtitle = stringResource(R.string.section_skill_settings_subtitle),
                onClick = onNavigateToSkillSettings,
            )
        }

        // ── Personalization ────────────────────────────────────
        GroupCard(title = stringResource(R.string.section_group_personalization)) {
            GroupCardItem(
                title = stringResource(R.string.title_appearance),
                subtitle = stringResource(R.string.section_appearance_subtitle),
                onClick = onNavigateToAppearance,
            )
            GroupCardItem(
                title = stringResource(R.string.section_language),
                subtitle = languageLabel,
                onClick = { showLanguageSheet = true },
            )
        }

        // ── System & Info ──────────────────────────────────────
        GroupCard(title = stringResource(R.string.section_group_system)) {
            GroupCardItem(
                title = stringResource(R.string.title_usage_stats),
                subtitle = stringResource(R.string.section_usage_stats_subtitle),
                onClick = onNavigateToUsageStats,
            )
            GroupCardItem(
                title = stringResource(R.string.title_about),
                subtitle = stringResource(R.string.section_about_subtitle),
                onClick = onNavigateToAbout,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
