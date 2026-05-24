package com.noexcs.indolent.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.prompt.SystemPromptItem
import com.noexcs.indolent.prompt.SystemPromptRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptSettingsScreen(
    repository: SystemPromptRepository,
    settingsManager: SettingsManager,
    resumeTrigger: Int,
    onBack: () -> Unit,
    onEditPrompt: (String?) -> Unit,
) {
    var prompts by remember { mutableStateOf<List<SystemPromptItem>>(emptyList()) }
    var activeId by remember { mutableStateOf(settingsManager.activeSystemPromptId) }
    var localRefreshCounter by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf<SystemPromptItem?>(null) }
    var promptSnapshot by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var hasLaunchedEditor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMsg = stringResource(R.string.settings_saved)

    val effectiveTrigger = resumeTrigger + localRefreshCounter

    LaunchedEffect(effectiveTrigger) {
        migrateLegacyPrompt(repository, settingsManager)
        val prevSnapshot = promptSnapshot
        prompts = repository.listAll()
        activeId = settingsManager.activeSystemPromptId
        if (hasLaunchedEditor) {
            hasLaunchedEditor = false
            val currentSnapshot = prompts.associate { it.id to it.updatedAt }
            if (currentSnapshot != prevSnapshot) {
                snackbarHostState.showSnackbar(
                    message = savedMsg,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    fun navigateToEditor(promptId: String?) {
        promptSnapshot = prompts.associate { it.id to it.updatedAt }
        hasLaunchedEditor = true
        onEditPrompt(promptId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(R.string.title_system_prompt_settings)) },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navigateToEditor(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_note))
            }
        }
    ) { padding ->
        if (prompts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.no_prompts),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(prompts, key = { it.id }) { prompt ->
                    val isActive = prompt.id == activeId
                    PromptCard(
                        prompt = prompt,
                        isActive = isActive,
                        onActivate = {
                            activeId = prompt.id
                            settingsManager.activeSystemPromptId = prompt.id
                            localRefreshCounter++
                        },
                        onEdit = { navigateToEditor(prompt.id) },
                        onDelete = { showDeleteConfirm = prompt }
                    )
                }
            }
        }
    }

    showDeleteConfirm?.let { prompt ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.delete_prompt_title)) },
            text = { Text(stringResource(R.string.delete_prompt_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (prompt.id == activeId) {
                                settingsManager.activeSystemPromptId = null
                                activeId = null
                            }
                            repository.delete(prompt.id)
                            showDeleteConfirm = null
                            localRefreshCounter++
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PromptCard(
    prompt: SystemPromptItem,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onEdit),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: name + active badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = prompt.name.ifBlank { stringResource(R.string.prompt_untitled) },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.prompt_active),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Content preview
            if (prompt.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = prompt.content.replace("\n", " ").take(160),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer: date + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(prompt.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Activate capsule
                    Surface(
                        onClick = onActivate,
                        shape = RoundedCornerShape(50),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                if (isActive) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                stringResource(R.string.prompt_active),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                    // Delete capsule
                    Surface(
                        onClick = onDelete,
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                stringResource(R.string.delete),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun migrateLegacyPrompt(
    repository: SystemPromptRepository,
    settingsManager: SettingsManager,
) {
    if (settingsManager.activeSystemPromptId != null) return
    val existing = repository.listAll()
    if (existing.isNotEmpty()) return

    val legacy = settingsManager.userSystemPrompt
    if (legacy.isBlank()) return

    val item = SystemPromptItem(
        id = UUID.randomUUID().toString(),
        name = "Default",
        content = legacy,
    )
    repository.save(item)
    settingsManager.activeSystemPromptId = item.id
}
