package com.noexcs.indolent

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
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
import com.noexcs.indolent.ui.BackgroundTasksScreen
import com.noexcs.indolent.ui.chat.ChatScreen
import com.noexcs.indolent.ui.ConditionalTriggerListScreen
import com.noexcs.indolent.ui.HeartbeatHistoryScreen
import com.noexcs.indolent.ui.note.NotesScreen
import com.noexcs.indolent.ui.ScheduledTaskListScreen
import com.noexcs.indolent.ui.SettingsScreen
import com.noexcs.indolent.ui.todo.TodoListsScreen
import com.noexcs.indolent.ui.settings.AboutActivity
import com.noexcs.indolent.ui.settings.ApiSettingsActivity
import com.noexcs.indolent.ui.settings.AppearanceSettingsActivity
import com.noexcs.indolent.ui.settings.HeartbeatSettingsActivity
import com.noexcs.indolent.ui.settings.MemorySettingsActivity
import com.noexcs.indolent.ui.settings.SkillSettingsActivity
import com.noexcs.indolent.ui.settings.SystemPromptSettingsActivity
import com.noexcs.indolent.ui.settings.ToolSettingsActivity
import com.noexcs.indolent.ui.settings.UsageStatsActivity
import com.noexcs.indolent.ui.theme.IndolentTheme

private sealed class Screen {
    data object Chat : Screen()
    data object Settings : Screen()
    data object ScheduledTasks : Screen()
    data object HeartbeatHistory : Screen()
    data object ConditionalTriggers : Screen()
    data object TodoLists : Screen()
    data object Notes : Screen()
    data object BackgroundTasks : Screen()
}

private data class NavItem(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val screen: Screen,
)

private val navItems = listOf(
    NavItem(R.string.chat_tab, Icons.Filled.Chat, Icons.Outlined.Chat, Screen.Chat),
    NavItem(R.string.todo_lists, Icons.Filled.Checklist, Icons.Outlined.Checklist, Screen.TodoLists),
    NavItem(R.string.notes, Icons.Filled.EditNote, Icons.Outlined.EditNote, Screen.Notes),
    NavItem(R.string.background_tasks, Icons.Filled.Schedule, Icons.Outlined.Schedule, Screen.BackgroundTasks),
    NavItem(R.string.settings, Icons.Filled.Settings, Icons.Outlined.Settings, Screen.Settings),
)

private val rootScreens = navItems.map { it.screen }.toSet()

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

    // Read from global ThemeState — already initialized before setContent — AI tools write here for instant theme switching
    val themeKey = com.noexcs.indolent.ui.theme.ThemeState.themeKey
    val dynamicColor = com.noexcs.indolent.ui.theme.ThemeState.dynamicColor
    val seedColor = com.noexcs.indolent.ui.theme.ThemeState.seedColor

    IndolentTheme(
        themeKey = themeKey,
        dynamicColor = dynamicColor,
        seedColor = seedColor,
    ) {
    // Observe locale changes to trigger recomposition without Activity restart
    com.noexcs.indolent.data.LocaleNotifier.Observe()

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    val isRoot = currentScreen in rootScreens
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { navItems.size }
    )
    val coroutineScope = rememberCoroutineScope()

    // Sync pager -> currentScreen when user swipes
    LaunchedEffect(pagerState.currentPage) {
        val screen = navItems[pagerState.currentPage].screen
        if (currentScreen != screen && isRoot) {
            currentScreen = screen
        }
    }

    // When currentScreen changes externally (sub-screen nav, onViewInChat), sync pager
    LaunchedEffect(currentScreen) {
        if (isRoot) {
            val index = navItems.indexOfFirst { it.screen == currentScreen }
            if (index >= 0 && index != pagerState.currentPage) {
                pagerState.animateScrollToPage(index)
            }
        }
    }

    // Back press: sub-screens return to their parent root
    BackHandler(enabled = !isRoot) {
        currentScreen = when (currentScreen) {
            is Screen.HeartbeatHistory -> Screen.BackgroundTasks
            is Screen.ConditionalTriggers -> Screen.BackgroundTasks
            is Screen.ScheduledTasks -> Screen.BackgroundTasks
            else -> Screen.Chat
        }
    }

    val settingsScrollState = rememberScrollState()
    val context = LocalContext.current

    val onViewExecutionInChat: (String, String, List<com.noexcs.indolent.agent.LLMMessage>, String?) -> Unit = { title, prompt, messages, taskId ->
        viewModel.loadExecutionAsConversation(taskId, title, prompt, messages)
        currentScreen = Screen.Chat
    }

    Scaffold(
        bottomBar = {
            if (isRoot) {
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
                            alwaysShowLabel = index < 3,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        if (isRoot) {
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
                    )
                    Screen.TodoLists -> TodoListsScreen(
                        onBack = { currentScreen = Screen.Chat },
                        todoListRepository = todoListRepository,
                        todoItemRepository = todoItemRepository,
                    )
                    Screen.Notes -> NotesScreen(
                        onBack = { currentScreen = Screen.Chat },
                        noteRepository = noteRepository,
                    )
                    Screen.BackgroundTasks -> BackgroundTasksScreen(
                        onViewInChat = { record ->
                            onViewExecutionInChat(record.taskTitle, record.prompt,
                                if (record.status == com.noexcs.indolent.task.ExecutionStatus.SUCCESS) record.result
                                else listOf(com.noexcs.indolent.agent.LLMMessage(role = "assistant", content = record.errorMessage)),
                                record.taskId)
                        }
                    )
                    Screen.Settings -> SettingsScreen(
                        settingsManager = settingsManager,
                        scrollState = settingsScrollState,
                        onBack = { currentScreen = Screen.Chat },
                        onNavigateToApiSettings = { context.startActivity(Intent(context, ApiSettingsActivity::class.java)) },
                        onNavigateToSystemPromptSettings = { context.startActivity(Intent(context, SystemPromptSettingsActivity::class.java)) },
                        onNavigateToMemorySettings = { context.startActivity(Intent(context, MemorySettingsActivity::class.java)) },
                        onNavigateToToolSettings = { context.startActivity(Intent(context, ToolSettingsActivity::class.java)) },
                        onNavigateToHeartbeatSettings = { context.startActivity(Intent(context, HeartbeatSettingsActivity::class.java)) },
                        onNavigateToUsageStats = { context.startActivity(Intent(context, UsageStatsActivity::class.java)) },
                        onNavigateToAppearance = { context.startActivity(Intent(context, AppearanceSettingsActivity::class.java)) },
                        onNavigateToAbout = { context.startActivity(Intent(context, AboutActivity::class.java)) },
                        onNavigateToSkillSettings = { context.startActivity(Intent(context, SkillSettingsActivity::class.java)) },
                    )
                    else -> {}
                }
            }
        } else {
            // Sub-screens use AnimatedContent with slide transitions
            val enterDuration = 400
            val exitDuration = 200
            val enterEasing = FastOutSlowInEasing
            val exitEasing = FastOutSlowInEasing

            AnimatedContent(
                targetState = currentScreen,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                transitionSpec = {
                    (slideInHorizontally(tween(enterDuration, easing = enterEasing)) { it / 4 } +
                        fadeIn(tween(enterDuration, easing = enterEasing)))
                        .togetherWith(
                            slideOutHorizontally(tween(exitDuration, easing = exitEasing)) { -it / 4 } +
                                fadeOut(tween(exitDuration, easing = exitEasing))
                        )
                },
                label = "subScreenTransition"
            ) { screen ->
                when (screen) {
                    Screen.ScheduledTasks -> ScheduledTaskListScreen(
                        onBack = { currentScreen = Screen.BackgroundTasks },
                        onViewInChat = { record ->
                            onViewExecutionInChat(record.taskTitle, record.prompt,
                                if (record.status == com.noexcs.indolent.task.ExecutionStatus.SUCCESS) record.result
                                else listOf(com.noexcs.indolent.agent.LLMMessage(role = "assistant", content = record.errorMessage)),
                                record.taskId)
                        }
                    )
                    Screen.HeartbeatHistory -> HeartbeatHistoryScreen(
                        onBack = { currentScreen = Screen.BackgroundTasks },
                        onViewInChat = { record ->
                            onViewExecutionInChat(
                                "Heartbeat",
                                "Heartbeat check at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(record.executedAt))}",
                                if (record.status == com.noexcs.indolent.task.ExecutionStatus.SUCCESS) record.result
                                else listOf(com.noexcs.indolent.agent.LLMMessage(role = "assistant", content = record.errorMessage)),
                                null
                            )
                        }
                    )
                    Screen.ConditionalTriggers -> ConditionalTriggerListScreen(
                        onBack = { currentScreen = Screen.BackgroundTasks },
                        onViewInChat = { record ->
                            onViewExecutionInChat(record.taskTitle, record.prompt,
                                if (record.status == com.noexcs.indolent.task.ExecutionStatus.SUCCESS) record.result
                                else listOf(com.noexcs.indolent.agent.LLMMessage(role = "assistant", content = record.errorMessage)),
                                record.taskId)
                        }
                    )
                    else -> {}
                }
            }
        }
    }
    }
}
