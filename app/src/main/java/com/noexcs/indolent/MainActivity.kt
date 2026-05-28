package com.noexcs.indolent

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.noexcs.indolent.data.FileChatHistoryProvider
import com.noexcs.indolent.data.MemoryManager
import com.noexcs.indolent.data.SettingsManager
import com.noexcs.indolent.note.NoteRepository
import com.noexcs.indolent.task.heartbeat.HeartbeatScheduler
import com.noexcs.indolent.task.scheduler.TaskScheduler
import com.noexcs.indolent.todo.TodoItemRepository
import com.noexcs.indolent.todo.TodoListRepository
import com.noexcs.indolent.ui.BackgroundTasksActivity
import com.noexcs.indolent.ui.chat.ChatScreen
import com.noexcs.indolent.ui.note.NotesScreen
import com.noexcs.indolent.ui.settings.SettingsActivity
import com.noexcs.indolent.ui.todo.TodoListsScreen
import com.noexcs.indolent.ui.theme.IndolentTheme

private sealed class Screen {
    data object Chat : Screen()
    data object TodoLists : Screen()
    data object Notes : Screen()
}

private data class NavItem(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val screen: Screen,
)

private val navItems = listOf(
    NavItem(R.string.chat_tab, Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat, Screen.Chat),
    NavItem(R.string.todo_lists, Icons.Filled.Checklist, Icons.Outlined.Checklist, Screen.TodoLists),
    NavItem(R.string.notes, Icons.Filled.EditNote, Icons.Outlined.EditNote, Screen.Notes),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            TaskScheduler(applicationContext).rescheduleAll()
            val settings = SettingsManager(applicationContext)
            if (settings.heartbeatEnabled) {
                HeartbeatScheduler(applicationContext).schedule()
            }
        }
        enableEdgeToEdge()

        // Load theme settings synchronously before first composition to prevent flash
        val preloadSettings = SettingsManager(applicationContext)
        com.noexcs.indolent.ui.theme.ThemeState.apply {
            themeKey = preloadSettings.themeKey
            dynamicColor = preloadSettings.dynamicColor
            seedColor = Color(preloadSettings.seedColor)
            contrastLevel = when (preloadSettings.contrastLevel) {
                "medium" -> com.noexcs.indolent.ui.theme.ContrastLevel.Medium
                "high" -> com.noexcs.indolent.ui.theme.ContrastLevel.High
                else -> com.noexcs.indolent.ui.theme.ContrastLevel.Standard
            }
        }
        com.noexcs.indolent.ui.theme.ThemeRegistry.loadDynamic(preloadSettings.dynamicThemesJson)

        setContent {
            MainContent()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        com.noexcs.indolent.data.LocaleNotifier.notifyChanged()
    }
}

@Composable
private fun MainContent() {
    val appContext = LocalContext.current.applicationContext
    val memoryManager = remember { MemoryManager(appContext).also { it.warmUp() } }
    val settingsManager = remember { SettingsManager(appContext) }
    val conversationRepository = remember { FileChatHistoryProvider(appContext) }
    val viewModel = remember {
        AgentViewModel(appContext, memoryManager, settingsManager, conversationRepository)
    }
    val todoItemRepository = remember { TodoItemRepository(appContext) }
    val todoListRepository = remember { TodoListRepository(appContext, todoItemRepository) }
    val noteRepository = remember { NoteRepository(appContext) }

    val themeKey = com.noexcs.indolent.ui.theme.ThemeState.themeKey
    val dynamicColor = com.noexcs.indolent.ui.theme.ThemeState.dynamicColor
    val seedColor = com.noexcs.indolent.ui.theme.ThemeState.seedColor

    IndolentTheme(
        themeKey = themeKey,
        dynamicColor = dynamicColor,
        seedColor = seedColor,
    ) {
    com.noexcs.indolent.data.LocaleNotifier.Observe()

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { navItems.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Observe PendingExecutionData from BackgroundTasksActivity
    val hasPendingExecution by PendingExecutionData.hasPending.collectAsState()
    LaunchedEffect(hasPendingExecution) {
        if (hasPendingExecution) {
            PendingExecutionData.consume()?.let { data ->
                viewModel.loadExecutionAsConversation(data.taskId, data.title, data.prompt, data.messages)
                coroutineScope.launch { pagerState.animateScrollToPage(0) }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                navItems.forEachIndexed { index, item ->
                    val selected = pagerState.currentPage == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (index != pagerState.currentPage) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        },
                        icon = {
                            Icon(
                                if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = stringResource(item.labelRes),
                            )
                        },
                        label = { Text(stringResource(item.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        }
    ) { scaffoldPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .statusBarsPadding()
                .padding(bottom = scaffoldPadding.calculateBottomPadding())
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            beyondViewportPageCount = navItems.size,
            userScrollEnabled = true,
        ) { page ->
            when (navItems[page].screen) {
                Screen.Chat -> ChatScreen(
                    viewModel = viewModel,
                    conversationRepository = conversationRepository,
                    onNavigateToAutomations = {
                        context.startActivity(Intent(context, BackgroundTasksActivity::class.java))
                    },
                    onNavigateToSettings = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    },
                )
                Screen.TodoLists -> TodoListsScreen(
                    onBack = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    todoListRepository = todoListRepository,
                    todoItemRepository = todoItemRepository,
                )
                Screen.Notes -> NotesScreen(
                    onBack = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    noteRepository = noteRepository,
                )
            }
        }
    }
    }
}
