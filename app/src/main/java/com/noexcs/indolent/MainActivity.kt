package com.noexcs.indolent

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
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.noexcs.indolent.agent.skills.SkillRepository
import com.noexcs.indolent.data.FileChatHistoryProvider
import com.noexcs.indolent.data.UsageStatisticsAggregator
import com.noexcs.indolent.data.MemoryManager
import com.noexcs.indolent.data.SettingsManager

import com.noexcs.indolent.note.NoteRepository
import com.noexcs.indolent.task.heartbeat.HeartbeatScheduler
import com.noexcs.indolent.task.scheduler.TaskScheduler
import com.noexcs.indolent.todo.TodoItemRepository
import com.noexcs.indolent.todo.TodoListRepository
import com.noexcs.indolent.ui.BackgroundTasksScreen
import com.noexcs.indolent.ui.note.NotesScreen
import com.noexcs.indolent.ui.settings.AboutScreen
import com.noexcs.indolent.ui.settings.AppearanceSettingsScreen
import com.noexcs.indolent.ui.chat.ChatScreen
import com.noexcs.indolent.ui.ConditionalTriggerListScreen
import com.noexcs.indolent.ui.HeartbeatHistoryScreen
import com.noexcs.indolent.ui.settings.HeartbeatSettingsScreen
import com.noexcs.indolent.ui.settings.McpSettingsScreen
import com.noexcs.indolent.ui.settings.MemorySettingsScreen
import com.noexcs.indolent.ui.ScheduledTaskListScreen
import com.noexcs.indolent.ui.SettingsScreen
import com.noexcs.indolent.ui.todo.TodoListsScreen
import com.noexcs.indolent.ui.settings.SkillSettingsScreen
import com.noexcs.indolent.ui.settings.SystemPromptSettingsScreen
import com.noexcs.indolent.ui.settings.ToolSettingsScreen
import com.noexcs.indolent.ui.UsageStatsScreen
import com.noexcs.indolent.ui.settings.ApiSettingsScreen
import com.noexcs.indolent.ui.theme.IndolentTheme

private sealed class Screen {
    data object Chat : Screen()
    data object Settings : Screen()
    data object ScheduledTasks : Screen()
    data object HeartbeatHistory : Screen()
    data object ConditionalTriggers : Screen()
    data object ApiSettings : Screen()
    data object SystemPromptSettings : Screen()
    data object MemorySettings : Screen()
    data object ToolSettings : Screen()
    data object HeartbeatSettings : Screen()
    data object UsageStats : Screen()
    data object Appearance : Screen()
    data object About : Screen()
    data object McpSettings : Screen()
    data object SkillSettings : Screen()
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
private val settingsSubScreens = setOf(
    Screen.ApiSettings, Screen.SystemPromptSettings, Screen.MemorySettings,
    Screen.ToolSettings, Screen.HeartbeatSettings, Screen.UsageStats,
    Screen.Appearance, Screen.About, Screen.McpSettings, Screen.SkillSettings,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TaskScheduler(applicationContext).rescheduleAll()
        val settings = SettingsManager(applicationContext)
        if (settings.heartbeatEnabled) {
            HeartbeatScheduler(applicationContext).schedule()
        }
        enableEdgeToEdge()
        setContent {
            MainContent()
        }
    }
}

@Composable
private fun MainContent() {
    val appContext = LocalContext.current.applicationContext
    val memoryManager = remember { MemoryManager(appContext) }
    val settingsManager = remember { SettingsManager(appContext) }
    val conversationRepository = remember { FileChatHistoryProvider(appContext) }
    val skillRepository = remember { SkillRepository(appContext, settingsManager) }
    val viewModel = remember {
        AgentViewModel(appContext, memoryManager, settingsManager, conversationRepository)
    }
    val usageStatsAggregator = remember { UsageStatisticsAggregator(appContext) }
    val todoListRepository = remember { TodoListRepository(appContext) }
    val todoItemRepository = remember { TodoItemRepository(appContext) }
    val noteRepository = remember { NoteRepository(appContext) }

    var themeKey by remember { mutableStateOf(settingsManager.themeKey) }
    var dynamicColor by remember { mutableStateOf(settingsManager.dynamicColor) }
    var seedColor by remember { mutableStateOf(Color(settingsManager.seedColor)) }

    IndolentTheme(
        themeKey = themeKey,
        dynamicColor = dynamicColor,
        seedColor = seedColor,
    ) {
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
            in settingsSubScreens -> Screen.Settings
            is Screen.HeartbeatHistory -> Screen.BackgroundTasks
            is Screen.ConditionalTriggers -> Screen.BackgroundTasks
            is Screen.ScheduledTasks -> Screen.BackgroundTasks
            else -> Screen.Chat
        }
    }

    val onViewExecutionInChat: (String, String, String) -> Unit = { title, prompt, result ->
        viewModel.loadExecutionAsConversation(title, prompt, result)
        currentScreen = Screen.Chat
    }

    Scaffold(
        topBar = {
            if (isRoot) {
                Surface(
                    modifier = Modifier.statusBarsPadding(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        navItems.forEachIndexed { index, item ->
                            val selected = pagerState.currentPage == index
                            Tab(
                                selected = selected,
                                onClick = {
                                    if (index != pagerState.currentPage) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                },
//                                text = { Text(stringResource(item.labelRes)) },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes),
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        if (isRoot) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .padding(top = scaffoldPadding.calculateTopPadding())
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
                                else record.errorMessage)
                        }
                    )
                    Screen.Settings -> SettingsScreen(
                        settingsManager = settingsManager,
                        onBack = { currentScreen = Screen.Chat },
                        onNavigateToApiSettings = { currentScreen = Screen.ApiSettings },
                        onNavigateToSystemPromptSettings = { currentScreen = Screen.SystemPromptSettings },
                        onNavigateToMemorySettings = { currentScreen = Screen.MemorySettings },
                        onNavigateToToolSettings = { currentScreen = Screen.ToolSettings },
                        onNavigateToHeartbeatSettings = { currentScreen = Screen.HeartbeatSettings },
                        onNavigateToUsageStats = { currentScreen = Screen.UsageStats },
                        onNavigateToAppearance = { currentScreen = Screen.Appearance },
                        onNavigateToAbout = { currentScreen = Screen.About },
                        onNavigateToSkillSettings = { currentScreen = Screen.SkillSettings },
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
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = scaffoldPadding.calculateTopPadding()),
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
                    Screen.ApiSettings -> ApiSettingsScreen(
                        settingsManager = settingsManager,
                        onBack = { currentScreen = Screen.Settings },
                    )
                    Screen.SystemPromptSettings -> SystemPromptSettingsScreen(
                        settingsManager = settingsManager,
                        onBack = { currentScreen = Screen.Settings },
                    )
                    Screen.MemorySettings -> MemorySettingsScreen(
                        memoryManager = memoryManager,
                        onBack = { currentScreen = Screen.Settings },
                    )
                    Screen.ToolSettings -> ToolSettingsScreen(
                        settingsManager = settingsManager,
                        onBack = { currentScreen = Screen.Settings },
                        onNavigateToMcpSettings = { currentScreen = Screen.McpSettings },
                    )
                    Screen.HeartbeatSettings -> HeartbeatSettingsScreen(
                        settingsManager = settingsManager,
                        onBack = { currentScreen = Screen.Settings },
                        onOpenHeartbeatHistory = { currentScreen = Screen.HeartbeatHistory },
                        onOpenConditionalTriggers = { currentScreen = Screen.ConditionalTriggers },
                    )
                    Screen.UsageStats -> UsageStatsScreen(
                        settingsManager = settingsManager,
                        aggregator = usageStatsAggregator,
                        onBack = { currentScreen = Screen.Settings },
                    )
                    Screen.Appearance -> AppearanceSettingsScreen(
                        settingsManager = settingsManager,
                        onThemeKeyChanged = { themeKey = it },
                        onDynamicColorChanged = { dynamicColor = it },
                        onSeedColorChanged = { seedColor = it },
                        onBack = { currentScreen = Screen.Settings },
                    )
                    Screen.About -> AboutScreen(
                        onBack = { currentScreen = Screen.Settings },
                    )
                    Screen.McpSettings -> McpSettingsScreen(
                        settingsManager = settingsManager,
                        onBack = { currentScreen = Screen.Settings },
                    )
                    Screen.SkillSettings -> SkillSettingsScreen(
                        skillRepository = skillRepository,
                        settingsManager = settingsManager,
                        onBack = { currentScreen = Screen.Settings },
                    )
                    Screen.ScheduledTasks -> ScheduledTaskListScreen(
                        onBack = { currentScreen = Screen.BackgroundTasks },
                        onViewInChat = { record ->
                            onViewExecutionInChat(record.taskTitle, record.prompt,
                                if (record.status == com.noexcs.indolent.task.ExecutionStatus.SUCCESS) record.result
                                else record.errorMessage)
                        }
                    )
                    Screen.HeartbeatHistory -> HeartbeatHistoryScreen(
                        onBack = { currentScreen = Screen.BackgroundTasks },
                        onViewInChat = { record ->
                            onViewExecutionInChat(
                                "Heartbeat",
                                "Heartbeat check at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(record.executedAt))}",
                                if (record.status == com.noexcs.indolent.task.ExecutionStatus.SUCCESS) record.result
                                else record.errorMessage
                            )
                        }
                    )
                    Screen.ConditionalTriggers -> ConditionalTriggerListScreen(
                        onBack = { currentScreen = Screen.BackgroundTasks },
                        onViewInChat = { record ->
                            onViewExecutionInChat(record.taskTitle, record.prompt,
                                if (record.status == com.noexcs.indolent.task.ExecutionStatus.SUCCESS) record.result
                                else record.errorMessage)
                        }
                    )
                    else -> {}
                }
            }
        }
    }
    }
}
