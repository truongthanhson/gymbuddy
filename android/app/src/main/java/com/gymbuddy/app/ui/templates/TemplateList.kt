package com.gymbuddy.app.ui.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gymbuddy.app.R
import com.gymbuddy.app.data.template.FolderEntity
import com.gymbuddy.app.data.template.TemplateWithExercises
import com.gymbuddy.app.ui.common.GymCard
import com.gymbuddy.app.ui.common.displayName
import com.gymbuddy.app.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

/** Callbacks for the template list on the Workout tab. */
class TemplateActions(
    val onStart: (TemplateWithExercises) -> Unit,
    val onEdit: (templateId: String) -> Unit,
    val onNew: () -> Unit,
    val onDuplicate: (templateId: String, copySuffix: String) -> Unit,
    val onDelete: (templateId: String) -> Unit,
    val onMove: (templateId: String, folderId: String?) -> Unit,
    val onCreateFolder: (name: String) -> Unit,
    val onRenameFolder: (folderId: String, name: String) -> Unit,
    val onDeleteFolder: (folderId: String) -> Unit,
)

/** Templates grouped by folder, as items of the Workout tab's list. */
fun LazyListScope.templateItems(
    state: TemplatesUiState,
    vietnamese: Boolean,
    actions: TemplateActions,
) {
    item {
        Row(Modifier.padding(top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.templates), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            NameDialogButton(
                title = stringResource(R.string.new_folder),
                label = stringResource(R.string.folder_name),
                onConfirm = actions.onCreateFolder,
            ) { open -> IconButton(onClick = open) { Icon(Icons.Filled.CreateNewFolder, stringResource(R.string.new_folder)) } }
            TextButton(onClick = actions.onNew) { Text("+ " + stringResource(R.string.new_template)) }
        }
    }
    val byFolder = state.templates.groupBy { it.template.folderId }
    val knownFolders = state.folders.map { it.id }.toSet()
    val loose = state.templates.filter { it.template.folderId == null || it.template.folderId !in knownFolders }
    if (state.templates.isEmpty()) {
        item {
            GymCard {
                Text(
                    stringResource(R.string.no_templates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (loose.isNotEmpty()) {
        item { SectionLabel(stringResource(R.string.my_templates)) }
        items(loose, key = { it.template.id }) { TemplateCard(it, state.folders, vietnamese, actions) }
    }
    state.folders.forEach { folder ->
        item(key = "folder-${folder.id}") { FolderHeader(folder, actions) }
        items(byFolder[folder.id].orEmpty(), key = { it.template.id }) { TemplateCard(it, state.folders, vietnamese, actions) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FolderHeader(folder: FolderEntity, actions: TemplateActions) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SectionLabel(folder.name) }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename_folder)) }, onClick = {
                    menuOpen = false
                    renaming = true
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete_folder)) }, onClick = {
                    menuOpen = false
                    actions.onDeleteFolder(folder.id)
                })
            }
        }
    }
    if (renaming) {
        NameDialog(
            title = stringResource(R.string.rename_folder),
            label = stringResource(R.string.folder_name),
            initial = folder.name,
            onDismiss = { renaming = false },
            onConfirm = {
                renaming = false
                actions.onRenameFolder(folder.id, it)
            },
        )
    }
}

@Composable
private fun TemplateCard(
    item: TemplateWithExercises,
    folders: List<FolderEntity>,
    vietnamese: Boolean,
    actions: TemplateActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    val copySuffix = stringResource(R.string.copy_suffix)

    GymCard(onClick = { preview = true }, contentPadding = 0.dp) {
        Row(Modifier.padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md), verticalAlignment = Alignment.Top) {
            Column(
                Modifier.weight(1f).padding(top = Spacing.xxs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(item.template.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.exercises.joinToString { it.exercise.displayName(vietnamese) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.template.lastUsedAt?.let {
                        stringResource(R.string.last_used, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)))
                    } ?: stringResource(R.string.never_used),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = {
                        menuOpen = false
                        actions.onEdit(item.template.id)
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = {
                        menuOpen = false
                        actions.onDuplicate(item.template.id, copySuffix)
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.move_to_folder)) }, onClick = {
                        menuOpen = false
                        moving = true
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
                        menuOpen = false
                        confirmDelete = true
                    })
                }
            }
        }
    }

    if (preview) {
        AlertDialog(
            onDismissRequest = { preview = false },
            title = { Text(item.template.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.exercises.forEach { e ->
                        Text("${e.sets.size} × ${e.exercise.displayName(vietnamese)}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preview = false
                    actions.onStart(item)
                }) { Text(stringResource(R.string.start_workout)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    preview = false
                    actions.onEdit(item.template.id)
                }) { Text(stringResource(R.string.edit)) }
            },
        )
    }

    if (moving) {
        AlertDialog(
            onDismissRequest = { moving = false },
            title = { Text(stringResource(R.string.move_to_folder)) },
            text = {
                Column {
                    (listOf<FolderEntity?>(null) + folders).forEach { folder ->
                        Text(
                            folder?.name ?: stringResource(R.string.no_folder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    moving = false
                                    actions.onMove(item.template.id, folder?.id)
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moving = false }) { Text(stringResource(R.string.close)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_template_title)) },
            text = { Text(item.template.name) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    actions.onDelete(item.template.id)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun NameDialogButton(
    title: String,
    label: String,
    onConfirm: (String) -> Unit,
    content: @Composable (open: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    content { open = true }
    if (open) {
        NameDialog(title = title, label = label, onDismiss = { open = false }, onConfirm = {
            open = false
            onConfirm(it)
        })
    }
}
