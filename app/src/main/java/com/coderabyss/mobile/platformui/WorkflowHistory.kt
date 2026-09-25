package com.coderabyss.mobile.platformui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun WorkflowHistory(projectId: String, vm: WorkspaceViewModel, busy: Boolean) {
    var open by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    TextButton(onClick = { open = true }) { Text("Version history") }
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
