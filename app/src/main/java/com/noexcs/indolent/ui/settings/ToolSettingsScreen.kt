package com.noexcs.indolent.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.content.ContextCompat
import com.noexcs.indolent.R
import com.noexcs.indolent.agent.tools.filesystem.FsUtils
import com.noexcs.indolent.agent.tools.ToolGroup
import com.noexcs.indolent.agent.tools.ToolRegistry
import com.noexcs.indolent.agent.tools.screen.AccessibilityHelper
import com.noexcs.indolent.data.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
    onNavigateToMcpSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    var termuxToolsEnabled by remember { mutableStateOf(settingsManager.termuxToolsEnabled) }
    var pythonToolsEnabled by remember { mutableStateOf(settingsManager.pythonToolsEnabled) }
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
    var screenToolsEnabled by remember { mutableStateOf(settingsManager.screenToolsEnabled) }
    var mcpToolsEnabled by remember { mutableStateOf(settingsManager.mcpToolsEnabled) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(AccessibilityHelper.isEnabled(context)) }
    var showExitDialog by remember { mutableStateOf(false) }
    val termuxPermission = "com.termux.permission.RUN_COMMAND"
    var hasTermuxPermission by remember { mutableStateOf(false) }
    var hasAllFilesAccess by remember { mutableStateOf(FsUtils.hasAllFilesAccess(context)) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.settings_saved)

    // Per-tool enabled states, initialized from persistent storage
    var toolEnabledStates by remember {
        mutableStateOf(
            ToolRegistry.allTools.associate { it.name to settingsManager.isToolEnabled(it.name) }
        )
    }

    // Group tools for the UI
    val toolsByGroup = remember {
        ToolRegistry.allTools.groupBy { it.group }
    }

    LaunchedEffect(Unit) {
        hasTermuxPermission = ContextCompat.checkSelfPermission(
            context, termuxPermission
        ) == PackageManager.PERMISSION_GRANTED
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAllFilesAccess = FsUtils.hasAllFilesAccess(context)
                isAccessibilityEnabled = AccessibilityHelper.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasTermuxPermission = granted
        if (!granted) {
            Toast.makeText(context, "RUN_COMMAND permission is required for Termux tools.", Toast.LENGTH_LONG).show()
        }
    }

    BackHandler(enabled = hasUnsavedChanges) {
        showExitDialog = true
    }

    fun save() {
        settingsManager.termuxToolsEnabled = termuxToolsEnabled
        settingsManager.pythonToolsEnabled = pythonToolsEnabled
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
        settingsManager.screenToolsEnabled = screenToolsEnabled
        settingsManager.mcpToolsEnabled = mcpToolsEnabled
        toolEnabledStates.forEach { (name, enabled) ->
            settingsManager.setToolEnabled(name, enabled)
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
                title = { Text(stringResource(R.string.title_tool_settings)) },
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
            // Termux — special: has permission UI
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_termux_tools),
                subtitle = stringResource(R.string.section_termux_tools_subtitle),
                groupEnabled = termuxToolsEnabled,
                onGroupToggle = { termuxToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.TERMUX] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
                extraContent = {
                    if (!hasTermuxPermission) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "RUN_COMMAND permission not granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(onClick = { permissionLauncher.launch(termuxPermission) }) {
                            Text(stringResource(R.string.grant_permission))
                        }
                    }
                }
            )

            // Fund
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_fund_tools),
                subtitle = stringResource(R.string.section_fund_tools_subtitle),
                groupEnabled = fundToolsEnabled,
                onGroupToggle = { fundToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.FUND] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
            )

            // Python
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_python_tools),
                subtitle = stringResource(R.string.section_python_tools_subtitle),
                groupEnabled = pythonToolsEnabled,
                onGroupToggle = { pythonToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.PYTHON] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
            )

            // Common
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_common_tools),
                subtitle = stringResource(R.string.section_common_tools_subtitle),
                groupEnabled = commonToolsEnabled,
                onGroupToggle = { commonToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.COMMON] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
            )

            // Conditional
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_conditional_tools),
                subtitle = stringResource(R.string.section_conditional_tools_subtitle),
                groupEnabled = conditionalToolsEnabled,
                onGroupToggle = { conditionalToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.CONDITIONAL] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
            )

            // Filesystem
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_filesystem_tools),
                subtitle = stringResource(R.string.section_filesystem_tools_subtitle),
                groupEnabled = filesystemToolsEnabled,
                onGroupToggle = { filesystemToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.FILESYSTEM] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
                extraContent = {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.all_files_access_status),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (hasAllFilesAccess)
                                    stringResource(R.string.all_files_access_granted)
                                else
                                    stringResource(R.string.all_files_access_not_granted),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasAllFilesAccess)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                        if (!hasAllFilesAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Button(onClick = {
                                try {
                                    val intent =
                                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                    intent.data = Uri.parse("package:${context.packageName}")
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "This device does not support the All Files Access settings page.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }) {
                                Text(stringResource(R.string.grant_all_files_access))
                            }
                        }
                    }
                }
            )

            // Interact
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_interact_tools),
                subtitle = stringResource(R.string.section_interact_tools_subtitle),
                groupEnabled = interactToolsEnabled,
                onGroupToggle = { interactToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.INTERACT] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
            )

            // Notification
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_notification_tools),
                subtitle = stringResource(R.string.section_notification_tools_subtitle),
                groupEnabled = notificationToolsEnabled,
                onGroupToggle = { notificationToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.NOTIFICATION] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
            )

            // Scheduled Task
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_scheduled_task_tools),
                subtitle = stringResource(R.string.section_scheduled_task_tools_subtitle),
                groupEnabled = scheduledTaskToolsEnabled,
                onGroupToggle = { scheduledTaskToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.SCHEDULED_TASK] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
            )

            // Self
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_self_tools),
                subtitle = stringResource(R.string.section_self_tools_subtitle),
                groupEnabled = selfToolsEnabled,
                onGroupToggle = { selfToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.SELF] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
            )

            // Sensor
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_sensor_tools),
                subtitle = stringResource(R.string.section_sensor_tools_subtitle),
                groupEnabled = sensorToolsEnabled,
                onGroupToggle = { sensorToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.SENSOR] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
            )

            // Setting
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_setting_tools),
                subtitle = stringResource(R.string.section_setting_tools_subtitle),
                groupEnabled = settingToolsEnabled,
                onGroupToggle = { settingToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.SETTING] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
            )

            // System Info
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_system_info_tools),
                subtitle = stringResource(R.string.section_system_info_tools_subtitle),
                groupEnabled = systemInfoToolsEnabled,
                onGroupToggle = { systemInfoToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.SYSTEM_INFO] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
            )

            // Screen
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_screen_tools),
                subtitle = stringResource(R.string.section_screen_tools_subtitle),
                groupEnabled = screenToolsEnabled,
                onGroupToggle = { screenToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.SCREEN] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
                extraContent = {
                    if (!isAccessibilityEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Accessibility service is not enabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(onClick = { AccessibilityHelper.openSettings(context) }) {
                            Text("Open Accessibility Settings")
                        }
                    }
                }
            )

            // MCP
            ExpandableToolGroupCard(
                title = stringResource(R.string.section_mcp_tools),
                subtitle = stringResource(R.string.section_mcp_tools_subtitle),
                groupEnabled = mcpToolsEnabled,
                onGroupToggle = { mcpToolsEnabled = it; markChanged() },
                tools = toolsByGroup[ToolGroup.MCP] ?: emptyList(),
                toolStates = toolEnabledStates,
                onToolToggle = { name, enabled ->
                    toolEnabledStates = toolEnabledStates + (name to enabled)
                    markChanged()
                },
                extraContent = {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(onClick = { onNavigateToMcpSettings() }) {
                        Text(stringResource(R.string.mcp_configure_servers))
                    }
                }
            )

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
