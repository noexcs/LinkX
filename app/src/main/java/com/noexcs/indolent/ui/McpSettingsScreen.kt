package com.noexcs.indolent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import androidx.compose.ui.res.stringResource
import com.noexcs.indolent.agent.mcp.McpClientManager
import com.noexcs.indolent.agent.mcp.McpServerConfig
import com.noexcs.indolent.data.SettingsManager
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpSettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    var configs by remember {
        mutableStateOf(
            try {
                json.decodeFromString<List<McpServerConfig>>(settingsManager.mcpServerConfigsJson)
            } catch (_: Exception) { emptyList() }
        )
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<McpServerConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<McpServerConfig?>(null) }
    val scope = rememberCoroutineScope()

    fun persist(newConfigs: List<McpServerConfig>) {
        configs = newConfigs
        settingsManager.mcpServerConfigsJson = json.encodeToString(newConfigs)
        scope.launch { McpClientManager.disconnectAll() }
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.mcp_configure_servers)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.mcp_server_add))
            }
        }
    ) { padding ->
        if (configs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.mcp_no_servers),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { showAddDialog = true }) {
                    Text(stringResource(R.string.mcp_server_add))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(configs, key = { it.id }) { config ->
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = config.name,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = config.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = config.enabled,
                                    onCheckedChange = { enabled ->
                                        persist(configs.map { if (it.id == config.id) it.copy(enabled = enabled) else it })
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { editingConfig = config }) {
                                    Text(stringResource(R.string.mcp_server_save))
                                }
                                IconButton(onClick = { deleteTarget = config }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.mcp_server_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // Add/Edit Dialog
    if (showAddDialog || editingConfig != null) {
        val isEdit = editingConfig != null
        var name by remember(showAddDialog, editingConfig) {
            mutableStateOf(editingConfig?.name ?: "")
        }
        var url by remember(showAddDialog, editingConfig) {
            mutableStateOf(editingConfig?.url ?: "")
        }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingConfig = null
            },
            title = { Text(stringResource(if (isEdit) R.string.mcp_server_edit_title else R.string.mcp_server_add_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.mcp_server_name)) },
                        placeholder = { Text(stringResource(R.string.mcp_server_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.mcp_server_url)) },
                        placeholder = { Text(stringResource(R.string.mcp_server_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank() && url.isNotBlank()) {
                            if (isEdit) {
                                persist(configs.map { if (it.id == editingConfig!!.id) it.copy(name = name, url = url) else it })
                            } else {
                                val newConfig = McpServerConfig(
                                    id = UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    url = url.trim()
                                )
                                persist(configs + newConfig)
                            }
                            editingConfig = null
                            showAddDialog = false
                        }
                    },
                    enabled = name.isNotBlank() && url.isNotBlank()
                ) {
                    Text(stringResource(R.string.mcp_server_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    editingConfig = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete confirmation
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.mcp_server_delete_title)) },
            text = { Text(stringResource(R.string.mcp_server_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    persist(configs.filter { it.id != deleteTarget!!.id })
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.mcp_server_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
