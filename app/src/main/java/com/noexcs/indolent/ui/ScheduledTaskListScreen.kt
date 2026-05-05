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
                colors = TopAppBarDefaults.mediumTopAppBarColors(
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
        TaskEditSheet(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCard(
    task: ScheduledTask,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onHistory: () -> Unit
) {
    val freqLabel = when (task.frequency) {
        TaskFrequency.DAILY -> stringResource(R.string.freq_daily)
        TaskFrequency.WEEKDAYS -> stringResource(R.string.freq_weekdays)
        TaskFrequency.WEEKLY -> stringResource(R.string.freq_weekly)
        TaskFrequency.ONCE -> stringResource(R.string.freq_once)
    }
    val timeStr = "%02d:%02d".format(task.hour, task.minute)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        enableDismissFromStartToEnd = false,
    ) {
        ElevatedCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        ) {
            ListItem(
                headlineContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            task.title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onHistory) {
                            Text(
                                stringResource(R.string.history),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                supportingContent = {
                    Text(
                        "$freqLabel · $timeStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = {
                    Switch(checked = task.enabled, onCheckedChange = onToggle)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditSheet(
    task: ScheduledTask?,
    onDismiss: () -> Unit,
    onSave: (ScheduledTask) -> Unit
) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var frequency by remember { mutableStateOf(task?.frequency ?: TaskFrequency.DAILY) }
    var hour by remember { mutableIntStateOf(task?.hour ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(task?.minute ?: Calendar.getInstance().get(Calendar.MINUTE)) }
    var prompt by remember { mutableStateOf(task?.prompt ?: "") }
    var notifyEnabled by remember { mutableStateOf(task?.notifyEnabled ?: true) }
    var showTimePicker by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(if (task == null) R.string.add_task else R.string.edit_task),
                style = MaterialTheme.typography.titleMedium
            )

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.task_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Frequency
            Text(stringResource(R.string.task_frequency), style = MaterialTheme.typography.labelMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = TaskFrequency.entries
                options.forEachIndexed { index, freq ->
                    SegmentedButton(
                        selected = frequency == freq,
                        onClick = { frequency = freq },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size)
                    ) {
                        Text(freqDisplayName(freq), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Time
            OutlinedButton(onClick = { showTimePicker = true }) {
                Text("%02d:%02d".format(hour, minute))
            }

            // Prompt
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text(stringResource(R.string.task_prompt)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            // Notification toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.task_notify), modifier = Modifier.weight(1f))
                Switch(checked = notifyEnabled, onCheckedChange = { notifyEnabled = it })
            }

            // Save
            Button(
                onClick = {
                    if (title.isBlank() || prompt.isBlank()) return@Button
                    onSave(
                        ScheduledTask(
                            id = task?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            frequency = frequency,
                            hour = hour,
                            minute = minute,
                            prompt = prompt.trim(),
                            notifyEnabled = notifyEnabled,
                            enabled = true,
                            createdAt = task?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && prompt.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onConfirm = { h, m -> hour = h; minute = m; showTimePicker = false },
            onDismiss = { showTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExecutionHistorySheet(
    taskId: String,
    executionRepo: TaskExecutionRepository,
    onViewInChat: (TaskExecutionRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val records = remember { executionRepo.listByTaskId(taskId) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.execution_history),
                style = MaterialTheme.typography.titleMedium
            )

            if (records.isEmpty()) {
                Text(
                    stringResource(R.string.no_execution_records),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                records.forEach { record ->
                    HistoryItem(record, dateFormat, onClick = { onViewInChat(record) })
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    record: TaskExecutionRecord,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    dateFormat.format(Date(record.executedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${record.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (record.status == ExecutionStatus.SUCCESS)
                            Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (record.status == ExecutionStatus.SUCCESS)
                            Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (record.status == ExecutionStatus.SUCCESS) {
                Text(
                    record.result,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    record.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF44336),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                record.prompt,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun freqDisplayName(freq: TaskFrequency): String = when (freq) {
    TaskFrequency.DAILY -> stringResource(R.string.freq_daily)
    TaskFrequency.WEEKDAYS -> stringResource(R.string.freq_weekdays)
    TaskFrequency.WEEKLY -> stringResource(R.string.freq_weekly)
    TaskFrequency.ONCE -> stringResource(R.string.freq_once)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = { TimePicker(state = state) }
    )
}
