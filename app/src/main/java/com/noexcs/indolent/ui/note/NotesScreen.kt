package com.noexcs.indolent.ui.note

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.note.NoteItem
import com.noexcs.indolent.note.NoteRepository
import kotlinx.coroutines.launch
import java.util.UUID

private sealed class NoteView {
    data object Grid : NoteView()
    data class Edit(val noteId: String?) : NoteView()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onBack: () -> Unit,
    noteRepository: NoteRepository,
) {
    var currentView by remember { mutableStateOf<NoteView>(NoteView.Grid) }
    var showArchive by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var filterLabel by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var refreshTrigger by remember { mutableStateOf(0) }
    var activeNotes by remember { mutableStateOf(emptyList<NoteItem>()) }
    var archivedNotes by remember { mutableStateOf(emptyList<NoteItem>()) }

    LaunchedEffect(refreshTrigger) {
        activeNotes = noteRepository.listActive()
        archivedNotes = noteRepository.listArchived()
    }

    val baseNotes = if (showArchive) archivedNotes else activeNotes
    val notes = remember(baseNotes, searchQuery, filterLabel) {
        baseNotes.filter { note ->
            val matchesSearch = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.content.contains(searchQuery, ignoreCase = true)
            val matchesLabel = filterLabel == null || filterLabel in note.labels
            matchesSearch && matchesLabel
        }
    }

    AnimatedContent(
        targetState = currentView,
        transitionSpec = {
            fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
                fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
        },
        label = "noteView"
    ) { view ->
        when (view) {
            is NoteView.Grid -> NotesGridContent(
                notes = notes,
                showArchive = showArchive,
                isGridView = isGridView,
                searchQuery = searchQuery,
                filterLabel = filterLabel,
                onBack = onBack,
                onSearchChange = { searchQuery = it },
                onClearFilter = { filterLabel = null },
                onToggleArchive = { showArchive = !showArchive },
                onToggleView = { isGridView = !isGridView },
                onNoteClick = { note -> currentView = NoteView.Edit(note.id) },
                onCreateNote = { currentView = NoteView.Edit(null) },
                onLabelClick = { label -> filterLabel = if (filterLabel == label) null else label },
                onArchiveNote = { note ->
                    scope.launch {
                        noteRepository.save(note.copy(isArchived = !note.isArchived, updatedAt = System.currentTimeMillis()))
                        refreshTrigger++
                    }
                },
                onPinNote = { note ->
                    scope.launch {
                        noteRepository.save(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
                        refreshTrigger++
                    }
                },
            )
            is NoteView.Edit -> {
                var loadedNote by remember { mutableStateOf<NoteItem?>(null) }
                LaunchedEffect(view.noteId) {
                    loadedNote = view.noteId?.let { noteRepository.load(it) }
                }
                // Wait for existing notes to load before showing editor
                if (view.noteId == null || loadedNote != null) {
                    NoteEditScreen(
                        note = loadedNote,
                        onBack = { currentView = NoteView.Grid },
                        onSave = { updatedNote ->
                            scope.launch {
                                noteRepository.save(updatedNote)
                                refreshTrigger++
                            }
                        },
                        onDelete = { id ->
                            scope.launch {
                                noteRepository.delete(id)
                            }
                            currentView = NoteView.Grid
                            refreshTrigger++
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesGridContent(
    notes: List<NoteItem>,
    showArchive: Boolean,
    isGridView: Boolean,
    searchQuery: String,
    filterLabel: String?,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onClearFilter: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleView: () -> Unit,
    onNoteClick: (NoteItem) -> Unit,
    onCreateNote: () -> Unit,
    onLabelClick: (String) -> Unit,
    onArchiveNote: (NoteItem) -> Unit,
    onPinNote: (NoteItem) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.note_search_hint)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
            )

            // Label filter chip
            if (filterLabel != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    InputChip(
                        selected = true,
                        onClick = onClearFilter,
                        label = { Text(filterLabel) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleView) {
                    Icon(
                        if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        contentDescription = stringResource(R.string.toggle_view),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleArchive) {
                    Icon(
                        if (showArchive) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = stringResource(R.string.note_archive),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            searchQuery.isNotBlank() || filterLabel != null -> stringResource(R.string.note_no_search_results)
                            showArchive -> stringResource(R.string.no_archived_notes)
                            else -> stringResource(R.string.no_notes)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (isGridView) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteGridCard(
                        note = note,
                        onClick = { onNoteClick(note) },
                        onArchive = { onArchiveNote(note) },
                        onPin = { onPinNote(note) },
                        onLabelClick = onLabelClick,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteListItem(
                        note = note,
                        onClick = { onNoteClick(note) },
                        onArchive = { onArchiveNote(note) },
                        onPin = { onPinNote(note) },
                        onLabelClick = onLabelClick,
                    )
                }
            }
        }
        }

        FloatingActionButton(
            onClick = onCreateNote,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_note))
        }
    }
}

@Composable
private fun NoteGridCard(
    note: NoteItem,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onPin: () -> Unit,
    onLabelClick: (String) -> Unit,
) {
    val bgColor = Color(note.color)
    val onBgColor = if (bgColor.red + bgColor.green + bgColor.blue > 1.5f) Color.Black else Color.White

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            if (note.title.isNotBlank()) {
                Text(
                    note.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = onBgColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (note.content.isNotBlank()) {
                Text(
                    note.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = onBgColor.copy(alpha = 0.8f),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (note.labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    note.labels.take(3).forEach { label ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = onBgColor.copy(alpha = 0.15f),
                            modifier = Modifier.clickable { onLabelClick(label) }
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = onBgColor.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (note.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        tint = onBgColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault()).format(java.util.Date(note.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = onBgColor.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun NoteListItem(
    note: NoteItem,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onPin: () -> Unit,
    onLabelClick: (String) -> Unit,
) {
    val bgColor = Color(note.color)
    val onBgColor = if (bgColor.red + bgColor.green + bgColor.blue > 1.5f) Color.Black else Color.White

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (note.title.isNotBlank()) {
                    Text(
                        note.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = onBgColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (note.content.isNotBlank()) {
                    Text(
                        note.content.take(100),
                        style = MaterialTheme.typography.bodySmall,
                        color = onBgColor.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (note.labels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        note.labels.take(3).forEach { label ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = onBgColor.copy(alpha = 0.15f),
                                modifier = Modifier.clickable { onLabelClick(label) }
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onBgColor.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (note.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        tint = onBgColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date(note.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = onBgColor.copy(alpha = 0.5f),
                )
            }
        }
    }
}
