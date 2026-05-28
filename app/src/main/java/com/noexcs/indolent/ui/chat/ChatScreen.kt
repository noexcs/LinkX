package com.noexcs.indolent.ui.chat

import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.ui.theme.MarkdownContent
import com.noexcs.indolent.R
import com.noexcs.indolent.AgentViewModel
import com.noexcs.indolent.data.FileChatHistoryProvider
import com.noexcs.indolent.agent.MessageRole
import com.noexcs.indolent.agent.tools.interact.ContentDisplayManager
import com.noexcs.indolent.data.MessageViewModel
import kotlinx.coroutines.launch

// ─── ChatScreen ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: AgentViewModel,
    conversationRepository: FileChatHistoryProvider,
    onNavigateToAutomations: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.onConversationUpdated = { refreshTrigger++ }
    }

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
                onNavigateToAutomations = {
                    scope.launch { drawerState.close() }
                    onNavigateToAutomations()
                },
                onNavigateToSettings = {
                    scope.launch { drawerState.close() }
                    onNavigateToSettings()
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
        )
    }
}

// ─── ChatContent ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContent(
    viewModel: AgentViewModel,
    conversationRepository: FileChatHistoryProvider,
    refreshTrigger: Int,
    onOpenDrawer: () -> Unit,
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


    // Track whether user has manually scrolled away from bottom
    var userScrolledUp by remember { mutableStateOf(false) }

    // New message → auto-scroll to bottom if user hasn't scrolled up
    LaunchedEffect(messages.size) {
        if (!userScrolledUp && messages.isNotEmpty()) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    // Detect when user stops scrolling — check if they scrolled away from bottom
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            userScrolledUp = lastVisibleItem < totalItems - 1
        }
    }

    // Detect IME visibility change → auto-scroll to bottom
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(imeVisible) {
        if (imeVisible && !userScrolledUp && messages.isNotEmpty()) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Actions bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = stringResource(R.string.conversations),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.newConversation() }) {
                    Icon(
                        Icons.Default.AddComment,
                        contentDescription = stringResource(R.string.new_chat),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // ── Messages ──────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (messages.isEmpty() && !isLoading) {
                    item { EmptyState() }
                }

                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        contentDisplayManager = viewModel.contentDisplayManager,
                    )
                }

                if (isLoading) {
                    item { ThinkingIndicator() }
                }
            }

            // ── Error ─────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
            ) {
                error?.let { errorMsg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // ── Token usage ───────────────────────────────────────────────
            if (tokenUsage.isNotEmpty()) {
                Text(
                    text = tokenUsage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // ── Input area ────────────────────────────────────────────────
            MessageInputBox(
                input = input,
                onInputChange = { input = it },
                enabled = !isLoading,
                onSend = {
                    if (input.isNotBlank()) {
                        userScrolledUp = false
                        viewModel.sendMessage(input.trim())
                        input = ""
                        scope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) }
                    }
                }
            )
        }
    }

    // ── Display Content BottomSheet ───────────────────────────────────────
    val displayContent by viewModel.contentDisplayManager.currentContent
    if (displayContent != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.contentDisplayManager.dismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ContentDisplaySheet(
                content = displayContent!!,
            )
        }
    }
}

// ─── Message Input Box ─────────────────────────────────────────────────────────

@Composable
private fun MessageInputBox(
    input: String,
    onInputChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    val hasText = input.isNotBlank()

    Surface(
        shape = com.noexcs.indolent.ui.theme.PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
        shadowElevation = if (hasText) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        stringResource(R.string.message_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                maxLines = 6,
                minLines = 1,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (hasText && enabled) onSend()
                    }
                )
            )

            // ── Send button ───────────────────────────────────────────────
            FilledIconButton(
                onClick = onSend,
                enabled = hasText && enabled,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .size(48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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

// ─── Empty State ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AddComment,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(R.string.empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.empty_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Thinking Indicator ────────────────────────────────────────────────────────

@Composable
private fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "thinking")

    // Three dots with wave animation — each dot rises and falls in sequence
    Row(
        modifier = Modifier
            .padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val offsetY by transition.animateFloat(
                initialValue = 0f,
                targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(420, delayMillis = index * 140),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .offset { IntOffset(0, offsetY.roundToInt()) }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            stringResource(R.string.thinking),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Thinking Bubble (collapsed thinking stream) ───────────────────────────────

@Composable
private fun ThinkingBubble(content: String) {
    var expanded by remember { mutableStateOf(false) }
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)

    // Downward expansion — natural without reverseLayout
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = surfaceColor,
        onClick = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .animateContentSize(tween(300)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.thinking_process),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!expanded) {
                        Text(
                            stringResource(R.string.tap_to_view_reasoning),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded)
                        Icons.Filled.CheckCircle
                    else
                        Icons.Filled.Psychology,
                    contentDescription = if (expanded) "collapse" else "expand",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }

            if (expanded) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                MarkdownContent(
                    content = content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

// ─── Tool Call Bubble ──────────────────────────────────────────────────────────

@Composable
private fun ToolCallBubble(
    content: String,
    displayContentId: String?,
    contentDisplayManager: ContentDisplayManager,
) {
    var expanded by remember { mutableStateOf(false) }
    val lines = content.lineSequence().toList()
    val rawFirstLine = lines.firstOrNull() ?: content
    val toolName = rawFirstLine.removePrefix("🔧 ")
    val body = lines.drop(1).joinToString("\n")
    val hasDetail = body.isNotBlank()
    val canReopen = displayContentId != null

    val surfaceColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    // Downward expansion — natural without reverseLayout
    Surface(
        shape = MaterialTheme.shapes.large,
        color = surfaceColor,
        onClick = { if (hasDetail) expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .animateContentSize(tween(300)),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                ) {
                    Text(
                        text = toolName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (hasDetail) {
                    Text(
                        text = if (expanded) "▼" else "▶",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            if (canReopen) {
                Surface(
                    onClick = { contentDisplayManager.show(displayContentId!!) },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "View content",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            if (expanded && hasDetail) {
                HorizontalDivider(
                    color = borderColor,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    text = body.trimEnd(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

// ─── Message Bubbles ───────────────────────────────────────────────────────────

@Composable
private fun MessageBubble(message: MessageViewModel, contentDisplayManager: ContentDisplayManager) {
    when (message.role) {
        MessageRole.User -> UserBubble(message.content.value)
        MessageRole.Assistant -> AssistantBubble(message.content.value)
        MessageRole.System -> AssistantBubble(message.content.value)
        MessageRole.Thinking -> ThinkingBubble(message.content.value)
        MessageRole.ToolInfo -> ToolCallBubble(
            content = message.content.value,
            displayContentId = message.displayContentId,
            contentDisplayManager = contentDisplayManager,
        )
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
            shape = RoundedCornerShape(24.dp, 24.dp, 6.dp, 24.dp),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyLarge,
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
