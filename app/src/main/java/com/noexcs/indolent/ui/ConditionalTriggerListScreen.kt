package com.noexcs.indolent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.task.ExecutionStatus
import com.noexcs.indolent.task.TaskExecutionRecord
import com.noexcs.indolent.task.TaskExecutionRepository
import com.noexcs.indolent.task.conditional.ConditionalTrigger
import com.noexcs.indolent.task.conditional.ConditionalTriggerRepository
import com.noexcs.indolent.task.conditional.ConditionOperator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionalTriggerListScreen(
    onBack: () -> Unit,
    onViewInChat: (TaskExecutionRecord) -> Unit = {}
) {
    val context = LocalContext.current
    val triggerRepo = remember { ConditionalTriggerRepository(context.applicationContext) }
    val executionRepo = remember { TaskExecutionRepository(context.applicationContext) }
    val triggers = remember { triggerRepo.listAll() }
    var historyTriggerId by remember { mutableStateOf<String?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.conditional_triggers)) },
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
        }
    ) { padding ->
        if (triggers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.no_conditional_triggers),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(triggers, key = { it.id }) { trigger ->
                    ConditionalTriggerCard(
                        trigger = trigger,
                        onHistory = { historyTriggerId = trigger.id }
                    )
                }
            }
        }
    }

    if (historyTriggerId != null) {
        ConditionalExecutionHistorySheet(
            triggerId = historyTriggerId!!,
            executionRepo = executionRepo,
            onViewInChat = onViewInChat,
            onDismiss = { historyTriggerId = null }
        )
    }
}

@Composable
private fun ConditionalTriggerCard(
    trigger: ConditionalTrigger,
    onHistory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    trigger.title,
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

            // Conditions summary
            trigger.conditions.forEach { condition ->
                val opSymbol = when (condition.operator) {
                    ConditionOperator.EQUAL -> "="
                    ConditionOperator.NOT_EQUAL -> "≠"
                    ConditionOperator.GREATER_THAN -> ">"
                    ConditionOperator.LESS_THAN -> "<"
                    ConditionOperator.GREATER_OR_EQUAL -> "≥"
                    ConditionOperator.LESS_OR_EQUAL -> "≤"
                    ConditionOperator.CHANGED -> "changed"
                    ConditionOperator.BECOMES_TRUE -> "→ true"
                    ConditionOperator.BECOMES_FALSE -> "→ false"
                }
                val target = condition.targetValue?.let { " $it" } ?: ""
                Text(
                    "${condition.source.name}.${condition.field} $opSymbol$target",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Meta info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if (trigger.enabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (trigger.enabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "cooldown: ${trigger.cooldownMs / 1000}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "max ${trigger.maxFiresPerDay}/day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionalExecutionHistorySheet(
    triggerId: String,
    executionRepo: TaskExecutionRepository,
    onViewInChat: (TaskExecutionRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val records = remember { executionRepo.listByTaskId(triggerId) }
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
                    ConditionalHistoryItem(record, dateFormat, onClick = { onViewInChat(record) })
                }
            }
        }
    }
}

@Composable
private fun ConditionalHistoryItem(
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
