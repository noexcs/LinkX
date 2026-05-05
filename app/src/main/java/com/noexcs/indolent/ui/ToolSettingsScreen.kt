package com.noexcs.indolent.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.noexcs.indolent.R
import com.noexcs.indolent.data.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var termuxToolsEnabled by remember { mutableStateOf(settingsManager.termuxToolsEnabled) }
    var fundToolsEnabled by remember { mutableStateOf(settingsManager.fundToolsEnabled) }
    var commonToolsEnabled by remember { mutableStateOf(settingsManager.commonToolsEnabled) }
    var conditionalToolsEnabled by remember { mutableStateOf(settingsManager.conditionalToolsEnabled) }
    var filesystemToolsEnabled by remember { mutableStateOf(settingsManager.filesystemToolsEnabled) }
    var interactToolsEnabled by remember { mutableStateOf(settingsManager.interactToolsEnabled) }
    var notificationToolsEnabled by remember { mutableStateOf(settingsManager.notificationToolsEnabled) }
    var scheduledTaskToolsEnabled by remember { mutableStateOf(settingsManager.scheduledTaskToolsEnabled) }
    var selfToolsEnabled by remember { mutableStateOf(settingsManager.selfToolsEnabled) }
    var sensorToolsEnabled by remember { mutableStateOf(settingsManager.sensorToolsEnabled) }
    var settingToolsEnabled by remember { mutableStateOf(settingsManager.settingToolsEnabled) }
    var systemInfoToolsEnabled by remember { mutableStateOf(settingsManager.systemInfoToolsEnabled) }
    var safRoots by remember { mutableStateOf(settingsManager.safRoots) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    val termuxPermission = "com.termux.permission.RUN_COMMAND"
    var hasTermuxPermission by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.settings_saved)

    LaunchedEffect(Unit) {
        hasTermuxPermission = ContextCompat.checkSelfPermission(
            context, termuxPermission
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasTermuxPermission = granted
        if (!granted) {
            Toast.makeText(context, "RUN_COMMAND permission is required for Termux tools.", Toast.LENGTH_LONG).show()
        }
    }

    val safTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            safRoots = safRoots + uri.toString()
            hasUnsavedChanges = true
        }
    }

    BackHandler(enabled = hasUnsavedChanges) {
        showExitDialog = true
    }

    fun save() {
        settingsManager.termuxToolsEnabled = termuxToolsEnabled
        settingsManager.fundToolsEnabled = fundToolsEnabled
        settingsManager.commonToolsEnabled = commonToolsEnabled
        settingsManager.conditionalToolsEnabled = conditionalToolsEnabled
        settingsManager.filesystemToolsEnabled = filesystemToolsEnabled
        settingsManager.interactToolsEnabled = interactToolsEnabled
        settingsManager.notificationToolsEnabled = notificationToolsEnabled
        settingsManager.scheduledTaskToolsEnabled = scheduledTaskToolsEnabled
        settingsManager.selfToolsEnabled = selfToolsEnabled
        settingsManager.sensorToolsEnabled = sensorToolsEnabled
        settingsManager.settingToolsEnabled = settingToolsEnabled
        settingsManager.systemInfoToolsEnabled = systemInfoToolsEnabled
        settingsManager.safRoots = safRoots
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.title_tool_settings)) },
                scrollBehavior = scrollBehavior,
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
                title = stringResource(R.string.section_termux_tools),
                subtitle = stringResource(R.string.section_termux_tools_subtitle)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (termuxToolsEnabled) stringResource(R.string.tool_switch_enabled)
                                   else stringResource(R.string.tool_switch_disabled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (termuxToolsEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (hasTermuxPermission) "RUN_COMMAND permission granted"
                                   else "RUN_COMMAND permission not granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasTermuxPermission) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                        )
                    }
                    Switch(checked = termuxToolsEnabled, onCheckedChange = { termuxToolsEnabled = it; markChanged() })
                }
                if (!hasTermuxPermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { permissionLauncher.launch(termuxPermission) }) {
                        Text(stringResource(R.string.grant_permission))
                    }
                }
            }

            SectionCard(
                title = stringResource(R.string.section_fund_tools),
                subtitle = stringResource(R.string.section_fund_tools_subtitle)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (fundToolsEnabled) stringResource(R.string.tool_switch_enabled)
                                   else stringResource(R.string.tool_switch_disabled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (fundToolsEnabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.section_fund_tools_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = fundToolsEnabled, onCheckedChange = { fundToolsEnabled = it; markChanged() })
                }
            }

            ToolToggleCard(
                title = stringResource(R.string.section_common_tools),
                subtitle = stringResource(R.string.section_common_tools_subtitle),
                enabled = commonToolsEnabled,
                onToggle = { commonToolsEnabled = it; markChanged() }
            )

            ToolToggleCard(
                title = stringResource(R.string.section_conditional_tools),
                subtitle = stringResource(R.string.section_conditional_tools_subtitle),
                enabled = conditionalToolsEnabled,
                onToggle = { conditionalToolsEnabled = it; markChanged() }
            )

            ToolToggleCard(
                title = stringResource(R.string.section_filesystem_tools),
                subtitle = stringResource(R.string.section_filesystem_tools_subtitle),
                enabled = filesystemToolsEnabled,
                onToggle = { filesystemToolsEnabled = it; markChanged() }
            )

            ToolToggleCard(
                title = stringResource(R.string.section_interact_tools),
                subtitle = stringResource(R.string.section_interact_tools_subtitle),
                enabled = interactToolsEnabled,
                onToggle = { interactToolsEnabled = it; markChanged() }
            )

            ToolToggleCard(
                title = stringResource(R.string.section_notification_tools),
                subtitle = stringResource(R.string.section_notification_tools_subtitle),
                enabled = notificationToolsEnabled,
                onToggle = { notificationToolsEnabled = it; markChanged() }
            )

            ToolToggleCard(
                title = stringResource(R.string.section_scheduled_task_tools),
                subtitle = stringResource(R.string.section_scheduled_task_tools_subtitle),
                enabled = scheduledTaskToolsEnabled,
                onToggle = { scheduledTaskToolsEnabled = it; markChanged() }
            )

            ToolToggleCard(
                title = stringResource(R.string.section_self_tools),
                subtitle = stringResource(R.string.section_self_tools_subtitle),
                enabled = selfToolsEnabled,
                onToggle = { selfToolsEnabled = it; markChanged() }
            )

            ToolToggleCard(
                title = stringResource(R.string.section_sensor_tools),
                subtitle = stringResource(R.string.section_sensor_tools_subtitle),
                enabled = sensorToolsEnabled,
                onToggle = { sensorToolsEnabled = it; markChanged() }
            )

            ToolToggleCard(
                title = stringResource(R.string.section_setting_tools),
                subtitle = stringResource(R.string.section_setting_tools_subtitle),
                enabled = settingToolsEnabled,
                onToggle = { settingToolsEnabled = it; markChanged() }
            )

            ToolToggleCard(
                title = stringResource(R.string.section_system_info_tools),
                subtitle = stringResource(R.string.section_system_info_tools_subtitle),
                enabled = systemInfoToolsEnabled,
                onToggle = { systemInfoToolsEnabled = it; markChanged() }
            )

            SectionCard(
                title = stringResource(R.string.section_storage_access),
                subtitle = stringResource(R.string.section_storage_access_subtitle)
            ) {
                if (safRoots.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_authorized_directories),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    safRoots.forEach { uriString ->
                        val uri = Uri.parse(uriString)
                        val name = try {
                            DocumentFile.fromTreeUri(context, uri)?.name ?: uriString
                        } catch (_: Exception) {
                            uriString
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                safRoots = safRoots - uriString
                                markChanged()
                            }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.remove_directory),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { safTreeLauncher.launch(null) }) {
                    Text(stringResource(R.string.add_directory))
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
