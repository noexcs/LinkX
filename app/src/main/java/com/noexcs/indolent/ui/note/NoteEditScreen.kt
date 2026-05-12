package com.noexcs.indolent.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.note.NoteItem
import com.noexcs.indolent.ui.theme.MarkdownContent
import java.util.UUID

private val presetColors = listOf(
    0xFF2D2D2D, // dark gray (default)
    0xFFF28B82, // red
    0xFFFBBC04, // yellow
    0xFFFFF475, // light yellow
    0xFFCCFF90, // light green
    0xFFA7FFEB, // teal
    0xFFCBF0F8, // light blue
    0xFFAECBFA, // blue
    0xFFD7AEFB, // purple
    0xFFFDCEE8, // pink
    0xFFE6C9A8, // brown
    0xFFFFFFFF, // white
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    note: NoteItem?,
    onBack: () -> Unit,
    onSave: (NoteItem) -> Unit,
    onDelete: (String) -> Unit,
) {
    val isNew = note == null
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var color by remember { mutableStateOf(note?.color ?: 0xFF2D2D2D) }
    var isPinned by remember { mutableStateOf(note?.isPinned ?: false) }
    var isArchived by remember { mutableStateOf(note?.isArchived ?: false) }
    var labelsText by remember { mutableStateOf(note?.labels?.joinToString(", ") ?: "") }
    var isPreview by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var hasManualChanges by remember { mutableStateOf(false) }

    // Auto-save after 1.5s of inactivity
    LaunchedEffect(title, content, color, isPinned, isArchived, labelsText) {
        kotlinx.coroutines.delay(1500)
        if (title.isNotBlank() || content.isNotBlank()) {
            val labels = labelsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            onSave(
                NoteItem(
                    id = note?.id ?: UUID.randomUUID().toString(),
                    title = title.trim(),
                    content = content.trim(),
                    color = color,
                    isPinned = isPinned,
                    isArchived = isArchived,
                    labels = labels,
                    createdAt = note?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            hasManualChanges = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar: back + title + actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (hasManualChanges) showDiscardConfirm = true else onBack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (isNew) stringResource(R.string.add_note) else stringResource(R.string.edit_note),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { isPinned = !isPinned }) {
                Icon(
                    Icons.Default.PushPin,
                    contentDescription = stringResource(R.string.note_pin),
                    tint = if (isPinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { isArchived = !isArchived }) {
                Icon(
                    if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                    contentDescription = stringResource(R.string.note_archive),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isNew) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(onClick = { isPreview = !isPreview }) {
                Text(
                    if (isPreview) stringResource(R.string.edit_content) else stringResource(R.string.preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Content body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (isPreview) {
                // Markdown preview
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (title.isNotBlank()) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    MarkdownContent(
                        content = content,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                // Edit mode
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; hasManualChanges = true },
                        placeholder = { Text(stringResource(R.string.note_title_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        textStyle = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it; hasManualChanges = true },
                        placeholder = { Text(stringResource(R.string.note_content_hint)) },
                        minLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        // Bottom toolbar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    stringResource(R.string.note_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetColors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (color == c) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                )
                                .clickable { color = c }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = labelsText,
                    onValueChange = { labelsText = it; hasManualChanges = true },
                    label = { Text(stringResource(R.string.note_labels)) },
                    placeholder = { Text(stringResource(R.string.note_labels_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Delete confirmation
    if (showDeleteConfirm && note != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_note_title)) },
            text = { Text(stringResource(R.string.delete_note_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(note.id)
                        showDeleteConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Discard confirmation
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(R.string.note_discard_title)) },
            text = { Text(stringResource(R.string.note_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onBack()
                    }
                ) {
                    Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
