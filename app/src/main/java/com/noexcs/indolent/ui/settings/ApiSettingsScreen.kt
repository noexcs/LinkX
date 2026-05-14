package com.noexcs.indolent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.noexcs.indolent.R
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.agent.LLMProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    var providerType by remember { mutableStateOf(settingsManager.providerType) }
    var baseUrl by remember { mutableStateOf(settingsManager.baseUrl) }
    var apiKey by remember { mutableStateOf(settingsManager.apiKey) }
    var model by remember { mutableStateOf(settingsManager.model) }
    var thinkingEnabled by remember { mutableStateOf(settingsManager.thinkingEnabled) }
    var reasoningEffort by remember { mutableStateOf(settingsManager.reasoningEffort) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }
    var reasoningEffortExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.settings_saved)

    BackHandler(enabled = hasUnsavedChanges) {
        showExitDialog = true
    }

    fun save() {
        settingsManager.providerType = providerType
        settingsManager.baseUrl = baseUrl
        settingsManager.apiKey = apiKey
        settingsManager.model = model
        settingsManager.thinkingEnabled = thinkingEnabled
        settingsManager.reasoningEffort = reasoningEffort
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
                title = { Text(stringResource(R.string.title_api_settings)) },
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
                title = stringResource(R.string.section_provider),
                subtitle = stringResource(R.string.section_provider_subtitle)
            ) {
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = providerType?.displayName ?: "Select Provider",
                        onValueChange = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true),
                        label = { Text(stringResource(R.string.provider_label)) },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    )
                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }) {
                        LLMProvider.all().forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(text = provider.displayName) },
                                onClick = {
                                    providerType = provider
                                    providerExpanded = false
                                    markChanged()
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = apiKey ?: "",
                    onValueChange = { apiKey = it; markChanged() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.api_key_label)) },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(if (apiKeyVisible) R.string.hide else R.string.show)
                            )
                        }
                    },
                    colors = settingsFieldColors()
                )

                OutlinedTextField(
                    value = baseUrl ?: "",
                    onValueChange = { baseUrl = it; markChanged() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.base_url_label)) },
                    placeholder = { Text("https://api.deepseek.com") },
                    singleLine = true,
                    colors = settingsFieldColors(),
                    supportingText = { Text("e.g., https://api.deepseek.com") }
                )

                OutlinedTextField(
                    value = model ?: "",
                    onValueChange = { model = it; markChanged() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.model_label)) },
                    placeholder = { Text("e.g., deepseek-chat, deepseek-reasoner") },
                    singleLine = true,
                    colors = settingsFieldColors(),
                    supportingText = { Text("Enter the model name / ID") }
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.thinking_label),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(R.string.thinking_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = thinkingEnabled,
                        onCheckedChange = { thinkingEnabled = it; markChanged() })
                }

                if (thinkingEnabled) {
                    ExposedDropdownMenuBox(
                        expanded = reasoningEffortExpanded,
                        onExpandedChange = { reasoningEffortExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = when (reasoningEffort) {
                                "high" -> stringResource(R.string.reasoning_effort_high)
                                "max" -> stringResource(R.string.reasoning_effort_max)
                                else -> reasoningEffort
                            },
                            onValueChange = { },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true),
                            label = { Text(stringResource(R.string.reasoning_effort_label)) },
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasoningEffortExpanded) },
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        )
                        ExposedDropdownMenu(
                            expanded = reasoningEffortExpanded,
                            onDismissRequest = { reasoningEffortExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reasoning_effort_high)) },
                                onClick = {
                                    reasoningEffort = "high"
                                    reasoningEffortExpanded = false
                                    markChanged()
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reasoning_effort_max)) },
                                onClick = {
                                    reasoningEffort = "max"
                                    reasoningEffortExpanded = false
                                    markChanged()
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }
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
