package com.noexcs.indolent.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoAwesome
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
    val listState = rememberLazyListState()
    var fabExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.isScrollInProgress) {
        fabExpanded = listState.firstVisibleItemIndex == 0 && !listState.isScrollInProgress
    }

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

    fun duplicatePrompt(prompt: SystemPromptItem) {
        scope.launch {
            val copy = prompt.copy(
                id = UUID.randomUUID().toString(),
                name = "${prompt.name} (copy)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            repository.save(copy)
            localRefreshCounter++
        }
    }

    val activePrompt = prompts.find { it.id == activeId }
    val otherPrompts = prompts.filter { it.id != activeId }

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
            ExtendedFloatingActionButton(
                onClick = { navigateToEditor(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    AnimatedVisibility(visible = fabExpanded, enter = fadeIn(), exit = fadeOut()) {
                        Text(stringResource(R.string.new_prompt_label))
                    }
                },
                expanded = fabExpanded,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    ) { padding ->
        if (prompts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.no_prompts),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.no_prompts_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Hero: Active prompt
                if (activePrompt != null) {
                    item(key = "hero") {
                        SectionHeader(stringResource(R.string.prompt_current_persona))
                        Spacer(modifier = Modifier.height(8.dp))
                        ActivePromptHeroCard(
                            prompt = activePrompt,
                            onEdit = { navigateToEditor(activePrompt.id) },
                            onMenuAction = { action ->
                                when (action) {
                                    "duplicate" -> duplicatePrompt(activePrompt)
                                    "delete" -> showDeleteConfirm = activePrompt
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Section: Your Prompts
                if (otherPrompts.isNotEmpty()) {
                    item(key = "section_header") {
                        SectionHeader(stringResource(R.string.prompt_your_prompts))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(otherPrompts, key = { it.id }) { prompt ->
                        PromptCard(
                            prompt = prompt,
                            isActive = false,
                            onEdit = { navigateToEditor(prompt.id) },
                            onMenuAction = { action ->
                                when (action) {
                                    "activate" -> {
                                        activeId = prompt.id
                                        settingsManager.activeSystemPromptId = prompt.id
                                        localRefreshCounter++
                                    }
                                    "duplicate" -> duplicatePrompt(prompt)
                                    "delete" -> showDeleteConfirm = prompt
                                }
                            }
                        )
                    }
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

// ── Active Hero Card ──

@Composable
private fun ActivePromptHeroCard(
    prompt: SystemPromptItem,
    onEdit: () -> Unit,
    onMenuAction: (String) -> Unit,
) {
    val bgColor = Color(prompt.color)
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onEdit),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (prompt.icon.isNotBlank()) {
                        Text(prompt.icon, style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        prompt.name.ifBlank { stringResource(R.string.prompt_untitled) },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (bgColor.red + bgColor.green + bgColor.blue > 1.8f) Color.Black else Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (prompt.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            prompt.content.replace("\n", " ").take(200),
                            style = MaterialTheme.typography.bodyMedium,
                            color = (if (bgColor.red + bgColor.green + bgColor.blue > 1.8f) Color.Black else Color.White).copy(alpha = 0.75f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = if (bgColor.red + bgColor.green + bgColor.blue > 1.8f) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.7f),
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.prompt_duplicate)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = { showMenu = false; onMenuAction("duplicate") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onMenuAction("delete") }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(prompt.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (bgColor.red + bgColor.green + bgColor.blue > 1.8f) Color.Black else Color.White).copy(alpha = 0.5f),
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = (if (bgColor.red + bgColor.green + bgColor.blue > 1.8f) Color.Black else Color.White).copy(alpha = 0.15f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (bgColor.red + bgColor.green + bgColor.blue > 1.8f) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            stringResource(R.string.prompt_active),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (bgColor.red + bgColor.green + bgColor.blue > 1.8f) Color.Black else Color.White,
                        )
                    }
                }
            }
        }
    }
}

// ── Regular Prompt Card ──

@Composable
private fun PromptCard(
    prompt: SystemPromptItem,
    isActive: Boolean,
    onEdit: () -> Unit,
    onMenuAction: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color dot
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = RoundedCornerShape(50),
                    color = Color(prompt.color),
                ) {}
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = prompt.name.ifBlank { stringResource(R.string.prompt_untitled) },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.prompt_set_active)) },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            onClick = { showMenu = false; onMenuAction("activate") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.prompt_duplicate)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = { showMenu = false; onMenuAction("duplicate") }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onMenuAction("delete") }
                        )
                    }
                }
            }

            if (prompt.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = prompt.content.replace("\n", " ").take(160),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(prompt.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

// ── Section Header ──

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

// ── Migration ──

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
