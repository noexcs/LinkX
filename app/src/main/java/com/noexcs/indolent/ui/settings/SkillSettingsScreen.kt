package com.noexcs.indolent.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noexcs.indolent.R
import com.noexcs.indolent.agent.skills.Skill
import com.noexcs.indolent.agent.skills.SkillRepository
import com.noexcs.indolent.agent.skills.SkillSource
import com.noexcs.indolent.data.SettingsManager
import kotlinx.coroutines.launch
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillSettingsScreen(
    skillRepository: SkillRepository,
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var skillsEnabled by remember { mutableStateOf(settingsManager.skillsEnabled) }
    var activeSkillName by remember { mutableStateOf(settingsManager.activeSkillName) }
    var allSkills by remember { mutableStateOf(skillRepository.getAllSkills()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Skill?>(null) }

    fun refreshSkills() {
        allSkills = skillRepository.getAllSkills()
    }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    skillRepository.importSkill(uri)
                    refreshSkills()
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.settings_saved)
                    )
                } catch (e: IOException) {
                    snackbarHostState.showSnackbar(
                        e.message ?: context.getString(R.string.tool_call_failed)
                    )
                }
            }
        }
    }

    // Export launcher
    var exportSkillName by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        val name = exportSkillName ?: return@rememberLauncherForActivityResult
        exportSkillName = null
        if (uri != null) {
            scope.launch {
                try {
                    skillRepository.exportSkill(name, uri)
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.settings_saved)
                    )
                } catch (e: IOException) {
                    snackbarHostState.showSnackbar(
                        e.message ?: context.getString(R.string.tool_call_failed)
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(R.string.title_skill_settings)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Master toggle
            SectionCard(
                title = stringResource(R.string.section_skills_master),
                subtitle = stringResource(R.string.section_skills_master_subtitle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (skillsEnabled) stringResource(R.string.enabled) else stringResource(
                            R.string.disabled
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = skillsEnabled,
                        onCheckedChange = { enabled ->
                            skillsEnabled = enabled
                            settingsManager.skillsEnabled = enabled
                            if (!enabled) {
                                activeSkillName = ""
                                settingsManager.activeSkillName = ""
                            }
                        }
                    )
                }
            }

            if (!skillsEnabled) {
                Text(
                    text = stringResource(R.string.skills_disabled_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Active skill selector
            if (skillsEnabled) {
                SectionCard(
                    title = stringResource(R.string.section_active_skill),
                    subtitle = stringResource(R.string.section_active_skill_subtitle)
                ) {
                    val enabledSkills = allSkills.filter { settingsManager.isSkillEnabled(it.name) }
                    SkillRadioRow(
                        name = stringResource(R.string.skill_none),
                        description = "",
                        isSelected = activeSkillName.isBlank(),
                        onClick = {
                            activeSkillName = ""
                            settingsManager.activeSkillName = ""
                        }
                    )
                    enabledSkills.forEach { skill ->
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SkillRadioRow(
                            name = skill.name,
                            description = skill.description,
                            isSelected = activeSkillName == skill.name,
                            onClick = {
                                activeSkillName = skill.name
                                settingsManager.activeSkillName = skill.name
                            }
                        )
                    }
                }

                // Built-in Skills
                val builtinSkills = allSkills.filter { it.source == SkillSource.BUILT_IN }
                if (builtinSkills.isNotEmpty()) {
                    SectionCard(
                        title = stringResource(R.string.section_builtin_skills),
                        subtitle = ""
                    ) {
                        builtinSkills.forEachIndexed { index, skill ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SkillToggleRow(
                                skill = skill,
                                isEnabled = settingsManager.isSkillEnabled(skill.name),
                                isActive = activeSkillName == skill.name,
                                showDelete = false,
                                onToggle = { enabled ->
                                    settingsManager.setSkillEnabled(skill.name, enabled)
                                    if (!enabled && activeSkillName == skill.name) {
                                        activeSkillName = ""
                                        settingsManager.activeSkillName = ""
                                    }
                                },
                                onActiveClick = {
                                    if (settingsManager.isSkillEnabled(skill.name)) {
                                        activeSkillName = skill.name
                                        settingsManager.activeSkillName = skill.name
                                    }
                                },
                                onDelete = {},
                                onExport = {}
                            )
                        }
                    }
                }

                // User Skills
                val userSkills = allSkills.filter { it.source == SkillSource.USER }
                SectionCard(
                    title = stringResource(R.string.section_user_skills),
                    subtitle = ""
                ) {
                    if (userSkills.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.skill_no_user_skills),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.skill_no_user_skills_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        userSkills.forEachIndexed { index, skill ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SkillToggleRow(
                                skill = skill,
                                isEnabled = settingsManager.isSkillEnabled(skill.name),
                                isActive = activeSkillName == skill.name,
                                showDelete = true,
                                onToggle = { enabled ->
                                    settingsManager.setSkillEnabled(skill.name, enabled)
                                    if (!enabled && activeSkillName == skill.name) {
                                        activeSkillName = ""
                                        settingsManager.activeSkillName = ""
                                    }
                                },
                                onActiveClick = {
                                    if (settingsManager.isSkillEnabled(skill.name)) {
                                        activeSkillName = skill.name
                                        settingsManager.activeSkillName = skill.name
                                    }
                                },
                                onDelete = { deleteTarget = skill },
                                onExport = {
                                    exportSkillName = skill.name
                                    exportLauncher.launch("${skill.name}.md")
                                }
                            )
                        }
                    }

                    // Create / Import buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.skill_create))
                        }
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(
                                    arrayOf(
                                        "text/markdown",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.skill_import))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Create dialog
    if (showCreateDialog) {
        CreateSkillDialog(
            onConfirm = { name, description ->
                skillRepository.createUserSkill(name, description)
                refreshSkills()
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    // Delete confirmation dialog
    deleteTarget?.let { skill ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.skill_delete_title)) },
            text = { Text(stringResource(R.string.skill_delete_message, skill.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        skillRepository.deleteUserSkill(skill.name)
                        refreshSkills()
                        if (activeSkillName == skill.name) {
                            activeSkillName = ""
                        }
                        deleteTarget = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SkillRadioRow(
    name: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SkillToggleRow(
    skill: Skill,
    isEnabled: Boolean,
    isActive: Boolean,
    showDelete: Boolean,
    onToggle: (Boolean) -> Unit,
    onActiveClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onActiveClick)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Text(
                        text = "● ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (skill.description.isNotBlank()) {
                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = isEnabled, onCheckedChange = onToggle)
        if (showDelete) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.skill_delete_title, skill.name),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CreateSkillDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.skill_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.skill_name_label)) },
                    placeholder = { Text(stringResource(R.string.skill_name_hint)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.skill_name_hint)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.skill_description_label)) },
                    placeholder = { Text(stringResource(R.string.skill_description_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isBlank()) {
                        nameError = true
                    } else {
                        onConfirm(trimmed, description.trim())
                    }
                }
            ) {
                Text(stringResource(R.string.skill_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
