package com.noexcs.indolent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.data.SettingsManager

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

    fun applyLanguage(tag: String) {
        selectedLanguage = tag
        settingsManager.language = tag
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
            MenuItemCard(
                title = stringResource(R.string.title_api_settings),
                subtitle = stringResource(R.string.section_api_settings_subtitle),
                onClick = onNavigateToApiSettings
            )

            MenuItemCard(
                title = stringResource(R.string.title_system_prompt_settings),
                subtitle = stringResource(R.string.section_system_prompt_subtitle),
                onClick = onNavigateToSystemPromptSettings
            )

            MenuItemCard(
                title = stringResource(R.string.title_memory_settings),
                subtitle = stringResource(R.string.section_memory_subtitle),
                onClick = onNavigateToMemorySettings
            )

            MenuItemCard(
                title = stringResource(R.string.title_tool_settings),
                subtitle = stringResource(R.string.section_tool_settings_subtitle),
                onClick = onNavigateToToolSettings
            )

            MenuItemCard(
                title = stringResource(R.string.title_heartbeat_settings),
                subtitle = stringResource(R.string.section_heartbeat_settings_subtitle),
                onClick = onNavigateToHeartbeatSettings
            )

            MenuItemCard(
                title = stringResource(R.string.title_usage_stats),
                subtitle = stringResource(R.string.section_usage_stats_subtitle),
                onClick = onNavigateToUsageStats
            )

            MenuItemCard(
                title = stringResource(R.string.title_appearance),
                subtitle = stringResource(R.string.section_appearance_subtitle),
                onClick = onNavigateToAppearance
            )

            // Language setting stays inline on the main page
            SectionCard(
                title = stringResource(R.string.section_language),
                subtitle = stringResource(R.string.section_language_subtitle)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    languageOptions.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = selectedLanguage == option.tag,
                            onClick = { applyLanguage(option.tag) },
                            shape = SegmentedButtonDefaults.itemShape(index, languageOptions.size),
                        ) {
                            Text(
                                stringResource(option.labelRes),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            MenuItemCard(
                title = stringResource(R.string.title_skill_settings),
                subtitle = stringResource(R.string.section_skill_settings_subtitle),
                onClick = onNavigateToSkillSettings
            )

            MenuItemCard(
                title = stringResource(R.string.title_about),
                subtitle = stringResource(R.string.section_about_subtitle),
                onClick = onNavigateToAbout
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
