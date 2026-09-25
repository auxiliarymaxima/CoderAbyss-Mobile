package com.coderabyss.mobile.platformui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.Description
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun WorkflowHistory(projectId: String, vm: WorkspaceViewModel, busy: Boolean, inline: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    if(inline) {
        Text("Version History", style = MaterialTheme.typography.titleLarge)
        val versions = vm.projects.versions(projectId)
        if(versions.isEmpty()) WorkflowPanel { Text("No saved versions yet"); Text("Versions are saved when you generate or revise.", color = WorkflowMuted) }
        versions.forEachIndexed { index, file ->
            val snapshot = remember(file.name) { runCatching { JSONObject(file.readText()) }.getOrNull() }
            Card(onClick = { chosen = file.name; open = true }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WorkflowNavy), border = androidx.compose.foundation.BorderStroke(1.dp, if(chosen == file.name) WorkflowCyan else WorkflowBorder)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Description, null, tint = WorkflowCyan)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Version ${versions.size - index}", style = MaterialTheme.typography.titleMedium)
                        Text(snapshot?.optString("prompt")?.takeIf { it.isNotBlank() } ?: "Saved draft", maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = WorkflowMuted)
                        Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(file.lastModified())), style = MaterialTheme.typography.labelSmall, color = WorkflowMuted)
                    }
                }
            }
        }
    } else TextButton(onClick = { open = true }) { Text("Version history") }
    if(open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Saved versions") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val versions = vm.projects.versions(projectId)
            if(versions.isEmpty()) Text("No saved versions yet")
            versions.forEachIndexed { index, file ->
                TextButton(onClick = { chosen = file.name }) { Text("Version ${versions.size - index} · ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(file.lastModified()))}") }
            }
            chosen?.let { name ->
                val draft = remember(name) { runCatching { JSONObject(vm.projects.file(projectId, "versions/$name").readText()) }.getOrNull() }
                if(draft == null) Text("This version could not be read") else {
                    Text(draft.optString("title"), style = MaterialTheme.typography.titleMedium)
                    Text(draft.optString("prompt"))
                    val sections = draft.optJSONArray("sections")
                    if(sections != null) for(n in 0 until sections.length()) { val section = sections.getJSONObject(n); Text(section.optString("title"), color = MaterialTheme.colorScheme.primary); Text(section.optString("text")) }
                }
            }
            if(error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = chosen != null && !busy, onClick = {
        runCatching { check(!vm.tasks.activeForProject(projectId)); vm.projects.restoreDraft(projectId, chosen!!); open = false }
            .onFailure { error = "Cannot restore while work is active, or this version is unreadable." }
    }) { Text("Restore this version") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("Close") } })
}
