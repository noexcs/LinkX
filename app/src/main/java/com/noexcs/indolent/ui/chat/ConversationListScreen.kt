package com.noexcs.indolent.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.data.FileChatHistoryProvider

import com.noexcs.indolent.agent.SessionMetadata
import com.noexcs.indolent.agent.SessionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationDrawerContent(
    repository: FileChatHistoryProvider,
    onLoad: (String) -> Unit,
    onNewChat: () -> Unit,
    onNavigateToAutomations: () -> Unit,
    onNavigateToSettings: () -> Unit,
    refreshTrigger: Int = 0
) {
    var conversations by remember { mutableStateOf(emptyList<SessionMetadata>()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<SessionMetadata?>(null) }
    var conversationToRename by remember { mutableStateOf<SessionMetadata?>(null) }
    var renameText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val loaded = repository.listSessions().filter { it.type == SessionType.CONVERSATION }
            withContext(Dispatchers.Main) {
                conversations = loaded
            }
        }
    }

    LaunchedEffect(refreshTrigger) {
        refresh()
    }

    val filtered = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    // Group by time period
    val grouped = remember(filtered) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStart = cal.timeInMillis

        cal.timeInMillis = now
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val thisWeekStart = cal.timeInMillis

        filtered.groupBy { meta ->
            when {
                meta.updatedAt >= todayStart -> "Today"
                meta.updatedAt >= yesterdayStart -> "Yesterday"
                meta.updatedAt >= thisWeekStart -> "This Week"
                else -> "Earlier"
            }
        }.toList().sortedBy { (period, _) ->
            when (period) {
                "Today" -> 0; "Yesterday" -> 1; "This Week" -> 2; else -> 3
            }
        }
    }

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // Automations entry
        ListItem(
            headlineContent = { Text(stringResource(R.string.background_tasks)) },
            leadingContent = {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = stringResource(R.string.background_tasks),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToAutomations() },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        // Search
        DockedSearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { searchActive = false },
                    expanded = searchActive,
                    onExpandedChange = { searchActive = it },
                    placeholder = { Text(stringResource(R.string.search_conversations)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = ""; searchActive = false }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else null,
                )
            },
            expanded = searchActive,
            onExpandedChange = { searchActive = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
        ) {
            filtered.take(5).forEach { meta ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = meta.title.ifBlank { stringResource(R.string.untitled) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onLoad(meta.sessionId)
                            searchActive = false
                            searchQuery = ""
                        },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.no_conversations),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                grouped.forEach { (period, sessions) ->
                    item(key = "header_$period") {
                        Text(
                            text = period,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    items(sessions, key = { it.sessionId }) { meta ->
                        DrawerConversationItem(
                            meta = meta,
                            onClick = { onLoad(meta.sessionId) },
                            onDelete = { conversationToDelete = meta },
                            onRename = {
                                renameText = meta.title
                                conversationToRename = meta
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        // Settings entry (pinned to bottom)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings)) },
            leadingContent = {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToSettings() },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
        )
    }

    // Delete dialog
    conversationToDelete?.let { meta ->
        val title = meta.title.ifBlank { stringResource(R.string.untitled) }
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.delete_conversation_title)) },
            text = { Text(stringResource(R.string.delete_conversation_message, title)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        repository.delete(meta.sessionId)
                        withContext(Dispatchers.Main) {
                            refresh()
                            conversationToDelete = null
                        }
                    }
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Rename dialog
    conversationToRename?.let { meta ->
        AlertDialog(
            onDismissRequest = { conversationToRename = null },
            title = { Text(stringResource(R.string.rename_conversation)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                repository.rename(meta.sessionId, renameText.trim())
                                withContext(Dispatchers.Main) {
                                    refresh()
                                    conversationToRename = null
                                }
                            }
                        } else {
                            conversationToRename = null
                        }
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToRename = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DrawerConversationItem(
    meta: SessionMetadata,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault()) }
    val dateLabel = remember(meta.updatedAt) { dateFormat.format(java.util.Date(meta.updatedAt)) }

    ListItem(
        headlineContent = {
            Text(
                text = meta.title.ifBlank { stringResource(R.string.untitled) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true },
            ),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
    )

    if (showContextMenu) {
        ModalBottomSheet(
            onDismissRequest = { showContextMenu = false },
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.rename)) },
                leadingContent = {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                modifier = Modifier.clickable {
                    showContextMenu = false
                    onRename()
                },
            )
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickable {
                    showContextMenu = false
                    onDelete()
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
