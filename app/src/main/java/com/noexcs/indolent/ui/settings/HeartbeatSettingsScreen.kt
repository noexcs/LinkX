package com.noexcs.indolent.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.task.heartbeat.HeartbeatScheduler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartbeatSettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    onOpenHeartbeatHistory: () -> Unit = {},
    onOpenConditionalTriggers: () -> Unit = {}
) {
    val context = LocalContext.current
    var heartbeatEnabled by remember { mutableStateOf(settingsManager.heartbeatEnabled) }
    var heartbeatIntervalMinutes by remember { mutableStateOf(settingsManager.heartbeatIntervalMinutes) }
    var heartbeatCustomPrompt by remember { mutableStateOf(settingsManager.heartbeatCustomPrompt) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.settings_saved)

    BackHandler(enabled = hasUnsavedChanges) {
        showExitDialog = true
    }

    fun save() {
        settingsManager.heartbeatEnabled = heartbeatEnabled
        settingsManager.heartbeatIntervalMinutes = heartbeatIntervalMinutes
        settingsManager.heartbeatCustomPrompt = heartbeatCustomPrompt
        val heartbeatScheduler = HeartbeatScheduler(context)
        if (heartbeatEnabled) {
            heartbeatScheduler.schedule()
        } else {
            heartbeatScheduler.cancel()
        }
        hasUnsavedChanges = false
        scope.launch {
            snackbarHostState.showSnackbar(
                message = savedMsg,
                duration = SnackbarDuration.Short
            )
        }
    }

    fun markChanged() {
        hasUnsavedChanges = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(R.string.section_heartbeat)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) showExitDialog = true else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { save() },
                        enabled = hasUnsavedChanges,
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge)
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
            SectionCard(
                title = stringResource(R.string.section_heartbeat),
                subtitle = stringResource(R.string.section_heartbeat_subtitle)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (heartbeatEnabled) stringResource(R.string.tool_switch_enabled)
                            else stringResource(R.string.tool_switch_disabled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (heartbeatEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.heartbeat_interval),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = heartbeatEnabled,
                        onCheckedChange = { heartbeatEnabled = it; markChanged() })
                }

                if (heartbeatEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = heartbeatIntervalMinutes.toString(),
                        onValueChange = { text ->
                            val parsed = text.filter { it.isDigit() }.toIntOrNull()
                            if (parsed != null && parsed in 1..1440) {
                                heartbeatIntervalMinutes = parsed
                                markChanged()
                            } else if (text.isEmpty()) {
                                heartbeatIntervalMinutes = 1
                                markChanged()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.heartbeat_interval)) },
                        supportingText = { Text(stringResource(R.string.heartbeat_interval_hint)) },
                        suffix = { Text(stringResource(R.string.heartbeat_interval_suffix)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = settingsFieldColors()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = heartbeatCustomPrompt,
                        onValueChange = { heartbeatCustomPrompt = it; markChanged() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.heartbeat_custom_prompt_label)) },
                        placeholder = { Text(stringResource(R.string.heartbeat_custom_prompt_placeholder)) },
                        minLines = 1,
                        maxLines = 3,
                        colors = settingsFieldColors()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = onOpenHeartbeatHistory) {
                        Text(stringResource(R.string.heartbeat_history_button))
                    }
                }
            }

            SectionCard(
                title = stringResource(R.string.section_conditional_trigger),
                subtitle = stringResource(R.string.section_conditional_trigger_subtitle)
            ) {
                TextButton(onClick = onOpenConditionalTriggers) {
                    Text(stringResource(R.string.conditional_triggers_view))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_message)) },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; onBack() }) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            }
        )
    }
}
