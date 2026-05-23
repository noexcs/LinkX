package com.noexcs.indolent.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import com.noexcs.indolent.R
import com.noexcs.indolent.task.ExecutionStatus
import com.noexcs.indolent.task.ScheduledTask
import com.noexcs.indolent.task.ScheduledTaskRepository
import com.noexcs.indolent.task.TaskExecutionRecord
import com.noexcs.indolent.task.TaskExecutionRepository
import com.noexcs.indolent.task.resultPreview
import com.noexcs.indolent.task.TaskFrequency
import com.noexcs.indolent.task.scheduler.TaskScheduler
import com.noexcs.indolent.task.scheduler.DeviceOptimizationHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTaskListScreen(
    onBack: () -> Unit,
    onViewInChat: (TaskExecutionRecord) -> Unit = {}
) {
    val context = LocalContext.current
    val repo = remember { ScheduledTaskRepository(context.applicationContext) }
    val executionRepo = remember { TaskExecutionRepository(context.applicationContext) }
    val scheduler = remember { TaskScheduler(context.applicationContext) }
    var tasks by remember { mutableStateOf(repo.listAll()) }
    var showSheet by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<ScheduledTask?>(null) }
    var historyTaskId by remember { mutableStateOf<String?>(null) }
    var batteryOptimizationIgnored by remember { mutableStateOf(false) }

    // Request POST_NOTIFICATIONS runtime permission (required on API 33+)
    var notificationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationPermissionGranted = granted }

    LaunchedEffect(Unit) {
        if (!notificationPermissionGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        batteryOptimizationIgnored = DeviceOptimizationHelper.isIgnoringBatteryOptimizations(context)
    }

    /** Try to schedule; if permission missing, open system settings and show a toast. */
    fun trySchedule(task: ScheduledTask) {
        if (!scheduler.schedule(task)) {
            Toast.makeText(context, context.getString(R.string.exact_alarm_permission_needed), Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.scheduled_tasks)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editingTask = null; showSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_task))
            }
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Battery optimization warning
                if (!batteryOptimizationIgnored) {
                    item(key = "battery_warning") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Battery optimization may prevent tasks from running on time.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        DeviceOptimizationHelper.openBatteryOptimizationSettings(context)
                                    }) {
                                        Text("Disable optimization")
                                    }
                                    TextButton(onClick = {
                                        if (!DeviceOptimizationHelper.openAutoStartSettings(context)) {
                                            Toast.makeText(context, "Auto-start settings not found for this device.", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Text("Auto-start")
                                    }
                                }
                            }
                        }
                    }
                }
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onClick = { editingTask = task; showSheet = true },
                        onToggle = { enabled ->
                            val updated = task.copy(enabled = enabled)
                            repo.save(updated)
                            if (enabled) trySchedule(updated) else scheduler.cancel(task.id)
                            tasks = repo.listAll()
                        },
                        onDelete = {
                            scheduler.cancel(task.id)
                            executionRepo.deleteByTaskId(task.id)
                            repo.delete(task.id)
                            tasks = repo.listAll()
                        },
                        onHistory = { historyTaskId = task.id }
                    )
                }
            }
        }
    }

    if (showSheet) {
        ScheduledTaskEditSheet(
            task = editingTask,
            onDismiss = { showSheet = false },
            onSave = { task ->
                repo.save(task)
                if (task.enabled) trySchedule(task) else scheduler.cancel(task.id)
                tasks = repo.listAll()
                showSheet = false
            }
        )
    }

    if (historyTaskId != null) {
        ExecutionHistorySheet(
            taskId = historyTaskId!!,
            executionRepo = executionRepo,
            onViewInChat = onViewInChat,
            onDismiss = { historyTaskId = null }
        )
    }
}

