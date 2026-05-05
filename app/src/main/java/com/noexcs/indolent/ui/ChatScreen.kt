package com.noexcs.indolent.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.ui.theme.MarkdownContent
import com.noexcs.indolent.R
import com.noexcs.indolent.AgentViewModel
import com.noexcs.indolent.data.FileChatHistoryProvider
import com.noexcs.indolent.agent.MessageRole
import com.noexcs.indolent.data.MessageViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: AgentViewModel,
    conversationRepository: FileChatHistoryProvider,
    onOpenSettings: () -> Unit = {},
    onOpenScheduledTasks: () -> Unit = {},
    onOpenConditionalTriggers: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableStateOf(0) }

    // Set up callback to refresh conversation list when messages are updated
    LaunchedEffect(Unit) {
        viewModel.onConversationUpdated = {
            refreshTrigger++
        }
    }

    // Back press closes drawer instead of exiting
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawerContent(
                repository = conversationRepository,
                onLoad = { id ->
                    viewModel.loadConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.newConversation()
                    scope.launch { drawerState.close() }
                },
                refreshTrigger = refreshTrigger
            )
        }
    ) {
        ChatContent(
            viewModel = viewModel,
            conversationRepository = conversationRepository,
            refreshTrigger = refreshTrigger,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onOpenSettings = onOpenSettings,
            onOpenScheduledTasks = onOpenScheduledTasks,
            onOpenConditionalTriggers = onOpenConditionalTriggers,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContent(
    viewModel: AgentViewModel,
    conversationRepository: FileChatHistoryProvider,
    refreshTrigger: Int,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenScheduledTasks: () -> Unit,
    onOpenConditionalTriggers: () -> Unit = {},
) {
    val messages = viewModel.messages
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val tokenUsage by viewModel.tokenUsage
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val copiedMsg = stringResource(R.string.copied)

    // Refresh conversation list when trigger changes
    LaunchedEffect(refreshTrigger) {
        // This will cause the drawer content to recompose with fresh data
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    val lastAssistantIndex = if (!isLoading) {
        messages.indices.lastOrNull { messages[it].role == MessageRole.Assistant }
    } else null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.chat_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = stringResource(R.string.conversations),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.newConversation() }) {
                        Icon(
                            Icons.Default.AddComment,
                            contentDescription = stringResource(R.string.new_chat),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenScheduledTasks) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.scheduled_tasks),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenConditionalTriggers) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.conditional_triggers),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Messages
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // With reverseLayout, index 0 is at the bottom (most recent)
                if (isLoading) {
                    item { ThinkingIndicator() }
                }

                itemsIndexed(
                    messages.asReversed(),
                    key = { _, message -> message.id }) { index, message ->
                    val originalIndex = messages.size - 1 - index

                    // Show action buttons before the message (visually below in reversed layout)
//                    if (originalIndex == lastAssistantIndex) {
//                        AssistantActions(
//                            onCopy = {
//                                val clipboard =
//                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//                                clipboard.setPrimaryClip(
//                                    ClipData.newPlainText(
//                                        "message",
//                                        message.content.value
//                                    )
//                                )
//                                scope.launch {
//                                    snackbarHostState.showSnackbar(
//                                        copiedMsg,
//                                        duration = SnackbarDuration.Short
//                                    )
//                                }
//                            },
//                        )
//                    }

                    MessageBubble(message)
                }

                if (messages.isEmpty() && !isLoading) {
                    item { EmptyState() }
                }
            }

            // Error
            AnimatedVisibility(visible = error != null) {
                error?.let { errorMsg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Token usage
            if (tokenUsage.isNotEmpty()) {
                Text(
                    text = tokenUsage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // Input area
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                stringResource(R.string.message_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (input.isNotBlank() && !isLoading) {
                                    viewModel.sendMessage(input.trim())
                                    input = ""
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
                            }
                        )
                    )
                    FilledIconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.sendMessage(input.trim())
                                input = ""
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        },
                        enabled = input.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .size(48.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.4f
                            )
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.send),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantActions(
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistantActionButton(icon = {
            Icon(
                Icons.Default.CopyAll,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        }, onClick = onCopy)
    }
}

@Composable
private fun AssistantActionButton(
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(28.dp),
        shape = RoundedCornerShape(14.dp),
        border = ButtonDefaults.outlinedButtonBorder(enabled = false),
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "thinking")

    Row(
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(R.string.thinking),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun MessageBubble(message: MessageViewModel) {
    when (message.role) {
        MessageRole.User -> UserBubble(message.content.value)
        MessageRole.Assistant -> AssistantBubble(message.content.value)
        MessageRole.System -> AssistantBubble(message.content.value)
        MessageRole.Thinking -> ThinkingBubble(message.content.value)
        MessageRole.ToolInfo -> ToolCallBubble(message.content.value)
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AssistantBubble(content: String) {
    MarkdownContent(
        content = content,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun ThinkingBubble(content: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("💭", style = MaterialTheme.typography.labelSmall)
            Text(
                "Thinking" + if (!expanded) "…" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ToolCallBubble(content: String) {
    var expanded by remember { mutableStateOf(false) }
    val lines = content.lineSequence().toList()
    val toolName = lines.firstOrNull()?.removePrefix("🔧 ") ?: content
    val body = lines.drop(1).joinToString("\n")
    val isLong = content.length > 50

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable { expanded = !expanded }
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "🔧",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = toolName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isLong) {
                    Text(
                        text = if (expanded) "▲" else "▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            if (expanded && body.isNotBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

