package com.noexcs.indolent.ui.todo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.todo.Priority
import com.noexcs.indolent.todo.Subtask
import com.noexcs.indolent.todo.TodoItem
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditSheet(
    item: TodoItem?,
    currentListId: String?,
    onDismiss: () -> Unit,
    onSave: (TodoItem) -> Unit,
) {
    val isNew = item == null
    var title by remember { mutableStateOf(item?.title ?: "") }
    var note by remember { mutableStateOf(item?.note ?: "") }
    var priority by remember { mutableStateOf(item?.priority ?: Priority.NONE) }
    var dueDate by remember { mutableStateOf(item?.dueDate) }
    var isMyDay by remember { mutableStateOf(item?.isMyDay ?: false) }
    var isImportant by remember { mutableStateOf(item?.isImportant ?: false) }
    var subtasks by remember { mutableStateOf(item?.subtasks ?: emptyList()) }
    var newSubtaskTitle by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                if (isNew) stringResource(R.string.add_task) else stringResource(R.string.edit_task),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.task_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Priority selector
            Text(
                stringResource(R.string.priority_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Priority.entries.forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = {
                            Text(
                                when (p) {
                                    Priority.NONE -> stringResource(R.string.priority_none)
                                    Priority.LOW -> stringResource(R.string.priority_low)
                                    Priority.MEDIUM -> stringResource(R.string.priority_medium)
                                    Priority.HIGH -> stringResource(R.string.priority_high)
                                }
                            )
                        }
                    )
                }
            }

            // Note
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.task_note)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // Due date (placeholder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (dueDate != null) java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date(dueDate!!)) else stringResource(R.string.set_due_date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (dueDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    // TODO: Date picker
                    dueDate = System.currentTimeMillis() + 86400000 // tomorrow
                }) {
                    Text(stringResource(R.string.set_due_date))
                }
            }

            // Subtasks
            Text(
                stringResource(R.string.subtasks_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            subtasks.forEach { subtask ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = subtask.isCompleted,
                        onCheckedChange = {
                            subtasks = subtasks.map {
                                if (it.id == subtask.id) it.copy(isCompleted = !it.isCompleted) else it
                            }
                        }
                    )
                    Text(
                        subtask.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        subtasks = subtasks.filter { it.id != subtask.id }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
            // Add subtask
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newSubtaskTitle,
                    onValueChange = { newSubtaskTitle = it },
                    placeholder = { Text(stringResource(R.string.subtask_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (newSubtaskTitle.isNotBlank()) {
                            subtasks = subtasks + Subtask(
                                id = UUID.randomUUID().toString(),
                                title = newSubtaskTitle.trim()
                            )
                            newSubtaskTitle = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_subtask))
                }
            }

            // My Day toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.add_to_my_day), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isMyDay,
                    onCheckedChange = { isMyDay = it }
                )
            }

            // Important toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.mark_important), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isImportant,
                    onCheckedChange = { isImportant = it }
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val updatedItem = TodoItem(
                                id = item?.id ?: UUID.randomUUID().toString(),
                                listId = item?.listId ?: currentListId ?: "default",
                                title = title.trim(),
                                note = note.trim(),
                                dueDate = dueDate,
                                priority = priority,
                                isCompleted = item?.isCompleted ?: false,
                                isImportant = isImportant,
                                isMyDay = isMyDay,
                                myDayDate = if (isMyDay) System.currentTimeMillis() else item?.myDayDate,
                                subtasks = subtasks,
                                reminder = item?.reminder,
                                createdAt = item?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                                sortOrder = item?.sortOrder ?: 0
                            )
                            onSave(updatedItem)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
