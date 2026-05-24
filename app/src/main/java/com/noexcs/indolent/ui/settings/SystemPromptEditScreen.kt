package com.noexcs.indolent.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.prompt.SystemPromptItem
import com.noexcs.indolent.prompt.SystemPromptRepository
import com.noexcs.indolent.ui.theme.MarkdownContent
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptEditScreen(
    promptId: String?,
    repository: SystemPromptRepository,
    onBack: () -> Unit,
) {
    val isNew = promptId == null
    val id = remember { promptId ?: UUID.randomUUID().toString() }
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var createdAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var isPreview by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var hasManualChanges by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(isNew) }

    val scope = rememberCoroutineScope()
    var isActive by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { isActive = false } }

    val doSaveAndBack: () -> Unit = {
        if (hasManualChanges && (name.isNotBlank() || content.isNotBlank())) {
            scope.launch {
                repository.save(
                    SystemPromptItem(
                        id = id,
                        name = name.trim(),
                        content = content.trim(),
                        createdAt = createdAt,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                hasManualChanges = false
                onBack()
            }
        } else {
            onBack()
        }
    }

    BackHandler(onBack = doSaveAndBack)

    // Load existing prompt
    LaunchedEffect(promptId) {
        if (promptId != null) {
            repository.load(promptId)?.let { prompt ->
                name = prompt.name
                content = prompt.content
                createdAt = prompt.createdAt
            }
            loaded = true
        }
    }

    // Auto-save after 1.5s of inactivity
    LaunchedEffect(name, content) {
        if (!loaded) return@LaunchedEffect
        kotlinx.coroutines.delay(1500)
        if (isActive && (name.isNotBlank() || content.isNotBlank())) {
            repository.save(
                SystemPromptItem(
                    id = id,
                    name = name.trim(),
                    content = content.trim(),
                    createdAt = createdAt,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            hasManualChanges = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = doSaveAndBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (isNew) stringResource(R.string.new_prompt) else stringResource(R.string.edit_prompt),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (!isNew) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(onClick = { isPreview = !isPreview }) {
                Text(
                    if (isPreview) stringResource(R.string.edit_content) else stringResource(R.string.preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Content body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (isPreview) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (name.isNotBlank()) {
                        Text(
                            name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    MarkdownContent(
                        content = content,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; hasManualChanges = true },
                        placeholder = { Text(stringResource(R.string.prompt_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        textStyle = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it; hasManualChanges = true },
                        placeholder = { Text(stringResource(R.string.prompt_content_hint)) },
                        minLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_prompt_title)) },
            text = { Text(stringResource(R.string.delete_prompt_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.delete(id)
                            showDeleteConfirm = false
                            onBack()
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

}
