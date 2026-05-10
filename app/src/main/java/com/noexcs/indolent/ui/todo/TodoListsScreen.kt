package com.noexcs.indolent.ui.todo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.todo.TodoItem
import com.noexcs.indolent.todo.TodoList
import com.noexcs.indolent.todo.TodoItemRepository
import com.noexcs.indolent.todo.TodoListRepository
import com.noexcs.indolent.todo.Priority
import java.util.UUID

private sealed class TodoView {
    data object Lists : TodoView()
    data class ListDetail(val listId: String, val listName: String) : TodoView()
    data class SmartList(val type: SmartListType) : TodoView()
}

private enum class SmartListType(val titleRes: Int, val icon: ImageVector) {
    MY_DAY(R.string.my_day, Icons.Default.WbSunny),
    IMPORTANT(R.string.important, Icons.Default.Star),
    PLANNED(R.string.planned, Icons.Default.CalendarToday)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListsScreen(
    onBack: () -> Unit,
    todoListRepository: TodoListRepository,
    todoItemRepository: TodoItemRepository,
) {
    var currentView by remember { mutableStateOf<TodoView>(TodoView.Lists) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var showTaskEditSheet by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<TodoItem?>(null) }

    var refreshTrigger by remember { mutableStateOf(0) }
    val lists = remember(refreshTrigger) { todoListRepository.listAll() }
    val myDayCount = remember(refreshTrigger) { todoItemRepository.listMyDayItems().size }
    val importantCount = remember(refreshTrigger) { todoItemRepository.listImportantItems().size }
    val plannedCount = remember(refreshTrigger) { todoItemRepository.listPlannedItems().size }
    val refresh = { refreshTrigger++ }

    AnimatedContent(
        targetState = currentView,
        transitionSpec = {
            fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
                fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
        },
        label = "todoView"
    ) { view ->
        when (view) {
            is TodoView.Lists -> TodoListsContent(
                lists = lists,
                myDayCount = myDayCount,
                importantCount = importantCount,
                plannedCount = plannedCount,
                onBack = onBack,
                onSmartListClick = { type -> currentView = TodoView.SmartList(type) },
                onListClick = { list -> currentView = TodoView.ListDetail(list.id, list.name) },
                onCreateList = { showCreateListDialog = true },
            )
            is TodoView.SmartList -> TodoSmartListContent(
                type = view.type,
                items = when (view.type) {
                    SmartListType.MY_DAY -> todoItemRepository.listMyDayItems()
                    SmartListType.IMPORTANT -> todoItemRepository.listImportantItems()
                    SmartListType.PLANNED -> todoItemRepository.listPlannedItems()
                },
                onBack = { currentView = TodoView.Lists },
                onAddTask = {
                    editingItem = null
                    showTaskEditSheet = true
                },
                onToggleComplete = { item ->
                    todoItemRepository.save(item.copy(isCompleted = !item.isCompleted))
                    refresh()
                },
                onItemClick = { item ->
                    editingItem = item
                    showTaskEditSheet = true
                },
            )
            is TodoView.ListDetail -> TodoListDetailContent(
                listName = view.listName,
                items = todoItemRepository.listByListId(view.listId),
                onBack = { currentView = TodoView.Lists },
                onAddTask = {
                    editingItem = null
                    showTaskEditSheet = true
                },
                onToggleComplete = { item ->
                    todoItemRepository.save(item.copy(isCompleted = !item.isCompleted))
                    refresh()
                },
                onItemClick = { item ->
                    editingItem = item
                    showTaskEditSheet = true
                },
            )
        }
    }

    // Create List Dialog
    if (showCreateListDialog) {
        var listName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateListDialog = false },
            title = { Text(stringResource(R.string.add_list)) },
            text = {
                OutlinedTextField(
                    value = listName,
                    onValueChange = { listName = it },
                    placeholder = { Text(stringResource(R.string.task_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (listName.isNotBlank()) {
                            val newList = TodoList(
                                id = UUID.randomUUID().toString(),
                                name = listName.trim(),
                                sortOrder = lists.size
                            )
                            todoListRepository.save(newList)
                            showCreateListDialog = false
                            refresh()
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateListDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Task Edit Sheet
    if (showTaskEditSheet) {
        TaskEditSheet(
            item = editingItem,
            currentListId = (currentView as? TodoView.ListDetail)?.listId,
            onDismiss = {
                showTaskEditSheet = false
                editingItem = null
            },
            onSave = { item ->
                todoItemRepository.save(item)
                showTaskEditSheet = false
                editingItem = null
                refresh()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoListsContent(
    lists: List<TodoList>,
    myDayCount: Int,
    importantCount: Int,
    plannedCount: Int,
    onBack: () -> Unit,
    onSmartListClick: (SmartListType) -> Unit,
    onListClick: (TodoList) -> Unit,
    onCreateList: () -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateList,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_list))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Smart Lists section
            item {
                SmartListCard(
                    icon = Icons.Default.WbSunny,
                    title = stringResource(R.string.my_day),
                    count = myDayCount,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onSmartListClick(SmartListType.MY_DAY) }
                )
            }
            item {
                SmartListCard(
                    icon = Icons.Default.Star,
                    title = stringResource(R.string.important),
                    count = importantCount,
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = { onSmartListClick(SmartListType.IMPORTANT) }
                )
            }
            item {
                SmartListCard(
                    icon = Icons.Default.CalendarToday,
                    title = stringResource(R.string.planned),
                    count = plannedCount,
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = { onSmartListClick(SmartListType.PLANNED) }
                )
            }

            // Divider
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            // "My Lists" label
            item {
                Text(
                    stringResource(R.string.my_lists),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (lists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.no_lists),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(lists, key = { it.id }) { list ->
                    UserListCard(
                        list = list,
                        onClick = { onListClick(list) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartListCard(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (count > 0) "$count ${stringResource(R.string.tasks_count)}" else stringResource(
                        R.string.no_tasks
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun UserListCard(
    list: TodoList,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                list.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoListDetailContent(
    listName: String,
    items: List<TodoItem>,
    onBack: () -> Unit,
    onAddTask: () -> Unit,
    onToggleComplete: (TodoItem) -> Unit,
    onItemClick: (TodoItem) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    listName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.no_tasks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        TodoTaskRow(
                            item = item,
                            onToggleComplete = { onToggleComplete(item) },
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddTask,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_task))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoSmartListContent(
    type: SmartListType,
    items: List<TodoItem>,
    onBack: () -> Unit,
    onAddTask: () -> Unit,
    onToggleComplete: (TodoItem) -> Unit,
    onItemClick: (TodoItem) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(type.titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.no_tasks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        TodoTaskRow(
                            item = item,
                            onToggleComplete = { onToggleComplete(item) },
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddTask,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_task))
        }
    }
}

@Composable
private fun TodoTaskRow(
    item: TodoItem,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isCompleted,
                onCheckedChange = { onToggleComplete() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.isCompleted)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.dueDate != null) {
                        Text(
                            java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
                                .format(java.util.Date(item.dueDate)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.priority != Priority.NONE) {
                        PriorityBadge(item.priority)
                    }
                    if (item.subtasks.isNotEmpty()) {
                        val completed = item.subtasks.count { it.isCompleted }
                        Text(
                            "$completed/${item.subtasks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (item.isImportant) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority: Priority) {
    val (color, labelRes) = when (priority) {
        Priority.HIGH -> MaterialTheme.colorScheme.error to R.string.priority_high
        Priority.MEDIUM -> MaterialTheme.colorScheme.tertiary to R.string.priority_medium
        Priority.LOW -> MaterialTheme.colorScheme.secondary to R.string.priority_low
        Priority.NONE -> Color.Transparent to R.string.priority_none
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}


