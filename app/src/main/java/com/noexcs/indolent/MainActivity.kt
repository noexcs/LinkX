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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.noexcs.indolent.agent.skills.SkillRepository
import com.noexcs.indolent.data.FileChatHistoryProvider
import com.noexcs.indolent.data.UsageStatisticsAggregator
import com.noexcs.indolent.data.MemoryManager
import com.noexcs.indolent.data.SettingsManager

import com.noexcs.indolent.task.heartbeat.HeartbeatScheduler
import com.noexcs.indolent.task.scheduler.TaskScheduler
import com.noexcs.indolent.ui.AboutScreen
import com.noexcs.indolent.ui.ApiSettingsScreen
import com.noexcs.indolent.ui.AppearanceSettingsScreen
import com.noexcs.indolent.ui.ChatScreen
import com.noexcs.indolent.ui.ConditionalTriggerListScreen
import com.noexcs.indolent.ui.HeartbeatHistoryScreen
import com.noexcs.indolent.ui.HeartbeatSettingsScreen
import com.noexcs.indolent.ui.McpSettingsScreen
import com.noexcs.indolent.ui.MemorySettingsScreen
import com.noexcs.indolent.ui.ScheduledTaskListScreen
import com.noexcs.indolent.ui.SettingsScreen
import com.noexcs.indolent.ui.SkillSettingsScreen
import com.noexcs.indolent.ui.SystemPromptSettingsScreen
import com.noexcs.indolent.ui.ToolSettingsScreen
import com.noexcs.indolent.ui.UsageStatsScreen
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
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recover alarms lost due to force-stop or system kill.
        // No-op if alarms are already scheduled (PendingIntent flags handle dedup).
        TaskScheduler(applicationContext).rescheduleAll()
        val settings = SettingsManager(applicationContext)
        if (settings.heartbeatEnabled) {
            HeartbeatScheduler(applicationContext).schedule()
        }
        enableEdgeToEdge()
        setContent {
            IndolentTheme {
                MainContent()
            }
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

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }

    // Back press on settings sub-screens returns to Settings; other screens return to Chat
    BackHandler(enabled = currentScreen !is Screen.Chat) {
        currentScreen = when (currentScreen) {
            is Screen.ApiSettings, is Screen.SystemPromptSettings, is Screen.MemorySettings,
            is Screen.ToolSettings, is Screen.HeartbeatSettings, is Screen.UsageStats,
            is Screen.Appearance, is Screen.About, is Screen.McpSettings,
            is Screen.SkillSettings -> Screen.Settings
            else -> Screen.Chat
        }
    }

    val isSettingsSubScreen = { screen: Screen ->
        screen is Screen.ApiSettings || screen is Screen.SystemPromptSettings ||
        screen is Screen.MemorySettings || screen is Screen.ToolSettings ||
        screen is Screen.HeartbeatSettings || screen is Screen.UsageStats ||
        screen is Screen.Appearance || screen is Screen.About ||
        screen is Screen.McpSettings || screen is Screen.SkillSettings
    }

    // MD3 Emphasized transition: 500ms with FastOutSlowInEasing (matches cubic-bezier(0.2, 0, 0, 1))
    val enterDuration = 400
    val exitDuration = 200
    val enterEasing = FastOutSlowInEasing
    val exitEasing = FastOutSlowInEasing

    AnimatedContent(
        targetState = currentScreen,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        transitionSpec = {
            when {
                targetState is Screen.Settings || targetState is Screen.ScheduledTasks ||
                targetState is Screen.HeartbeatHistory || targetState is Screen.ConditionalTriggers ||
                isSettingsSubScreen(targetState) -> {
                    (slideInHorizontally(tween(enterDuration, easing = enterEasing)) { it / 4 } +
                        fadeIn(tween(enterDuration, easing = enterEasing)))
                        .togetherWith(
                            slideOutHorizontally(tween(exitDuration, easing = exitEasing)) { -it / 4 } +
                                fadeOut(tween(exitDuration, easing = exitEasing))
                        )
                }
                targetState is Screen.Chat -> {
                    (slideInHorizontally(tween(enterDuration, easing = enterEasing)) { -it / 4 } +
                        fadeIn(tween(enterDuration, easing = enterEasing)))
                        .togetherWith(
                            slideOutHorizontally(tween(exitDuration, easing = exitEasing)) { it / 4 } +
                                fadeOut(tween(exitDuration, easing = exitEasing))
                        )
                }
                else -> fadeIn(tween(enterDuration, easing = enterEasing))
                    .togetherWith(fadeOut(tween(exitDuration, easing = exitEasing)))
            }
        },
        label = "screenTransition"
    ) { screen ->
        val onViewExecutionInChat: (String, String, String) -> Unit = { title, prompt, result ->
            viewModel.loadExecutionAsConversation(title, prompt, result)
            currentScreen = Screen.Chat
        }

        when (screen) {
            Screen.Chat -> ChatScreen(
                viewModel = viewModel,
                conversationRepository = conversationRepository,
                onOpenSettings = { currentScreen = Screen.Settings },
                onOpenScheduledTasks = { currentScreen = Screen.ScheduledTasks },
                onOpenConditionalTriggers = { currentScreen = Screen.ConditionalTriggers },
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
                onBack = { currentScreen = Screen.Chat },
                onViewInChat = { record ->
                    onViewExecutionInChat(
                        record.taskTitle,
                        record.prompt,
                        if (record.status == com.noexcs.indolent.task.ExecutionStatus.SUCCESS) record.result
                        else record.errorMessage
                    )
                }
            )
            Screen.HeartbeatHistory -> HeartbeatHistoryScreen(
                onBack = { currentScreen = Screen.Chat },
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
                onBack = { currentScreen = Screen.Chat },
                onViewInChat = { record ->
                    onViewExecutionInChat(
                        record.taskTitle,
                        record.prompt,
                        if (record.status == com.noexcs.indolent.task.ExecutionStatus.SUCCESS) record.result
                        else record.errorMessage
                    )
                }
            )
        }
    }
}
