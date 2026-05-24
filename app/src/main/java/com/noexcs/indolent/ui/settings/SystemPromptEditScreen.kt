package com.noexcs.indolent.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.prompt.SystemPromptItem
import com.noexcs.indolent.prompt.SystemPromptRepository
import java.util.UUID

private val presetColors = listOf(
    0xFF6750A4, // deep purple (M3 primary)
    0xFFB8504A, // coral red
    0xFFE09D32, // golden amber
    0xFF3E6B4E, // forest green
    0xFF4A8CB4, // sky blue
    0xFFD0835A, // warm terracotta
    0xFF6B5B8A, // lavender
    0xFF3F9090, // teal
    0xFFC76B8A, // rose pink
    0xFF6B8A5B, // sage green
    0xFF8A7A5B, // warm taupe
    0xFFE0C872, // soft gold
)

private enum class SaveStatus { Hidden, Saving, Saved }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptEditScreen(
    promptId: String?,
    repository: SystemPromptRepository,
    onBack: () -> Unit,
) {
    val isNew = promptId == null
    val id = remember { promptId ?: UUID.randomUUID().toString() }
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(presetColors[0]) }
    var createdAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var hasManualChanges by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(isNew) }
    var saveStatus by remember { mutableStateOf(SaveStatus.Hidden) }

    val scope = rememberCoroutineScope()
    var isActive by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { isActive = false } }

    val currentItem = SystemPromptItem(
        id = id,
        name = name.trim(),
        content = content.trim(),
        color = color,
        createdAt = createdAt,
        updatedAt = System.currentTimeMillis(),
    )

    val doSave: suspend () -> Unit = {
        repository.save(currentItem)
        hasManualChanges = false
        saveStatus = SaveStatus.Saved
        delay(1500)
        if (saveStatus == SaveStatus.Saved) saveStatus = SaveStatus.Hidden
    }

    val doSaveAndBack: () -> Unit = {
        if (hasManualChanges && (name.isNotBlank() || content.isNotBlank())) {
            scope.launch { doSave(); onBack() }
        } else {
            onBack()
        }
    }

    BackHandler(onBack = doSaveAndBack)

    // Load existing prompt
    LaunchedEffect(promptId) {
        if (promptId != null) {
            repository.load(promptId)?.let { prompt ->
                name = prompt.name
                content = prompt.content
                color = prompt.color
                createdAt = prompt.createdAt
            }
            loaded = true
        }
    }

    // Auto-save after 1.5s of inactivity
    LaunchedEffect(name, content, color) {
        if (!loaded) return@LaunchedEffect
        delay(1500)
        if (isActive && hasManualChanges && (name.isNotBlank() || content.isNotBlank())) {
            saveStatus = SaveStatus.Saving
            doSave()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar — simple row, back + title only
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 16.dp)
                .statusBarsPadding()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = doSaveAndBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (isNew) stringResource(R.string.new_prompt) else stringResource(R.string.edit_prompt),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }

        // Save status below top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = saveStatus,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "saveStatus"
            ) { status ->
                when (status) {
                    SaveStatus.Hidden -> {}
                    SaveStatus.Saving -> Text(
                        stringResource(R.string.prompt_saving),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    SaveStatus.Saved -> Text(
                        stringResource(R.string.prompt_saved),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                }
            }
        }

        // Content body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Hero title
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; hasManualChanges = true; saveStatus = SaveStatus.Hidden },
                placeholder = { Text(stringResource(R.string.prompt_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Color picker
            Text(
                stringResource(R.string.prompt_color),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presetColors.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .then(
                                if (color == c) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape)
                            )
                            .clickable {
                                color = c
                                hasManualChanges = true
                                saveStatus = SaveStatus.Hidden
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Content
            OutlinedTextField(
                value = content,
                onValueChange = { content = it; hasManualChanges = true; saveStatus = SaveStatus.Hidden },
                placeholder = { Text(stringResource(R.string.prompt_content_hint)) },
                minLines = 12,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
