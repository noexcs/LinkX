package com.noexcs.indolent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R

private data class ThemeOption(val key: String, val labelRes: Int)

private val themeOptions = listOf(
    ThemeOption("system", R.string.theme_system),
    ThemeOption("light", R.string.theme_light),
    ThemeOption("dark", R.string.theme_dark),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    var selectedTheme by remember { mutableStateOf("system") }
    var dynamicColor by remember { mutableStateOf(true) }

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
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    themeOptions.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = selectedTheme == option.key,
                            onClick = { selectedTheme = option.key },
                            shape = SegmentedButtonDefaults.itemShape(index, themeOptions.size),
                        ) {
                            Text(
                                stringResource(option.labelRes),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
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
                    Switch(checked = dynamicColor, onCheckedChange = { dynamicColor = it })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
