package com.coderabyss.mobile.platformui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coderabyss.mobile.*
import com.coderabyss.mobile.models.Service
import com.coderabyss.mobile.tasks.PersistentTaskStore
import org.json.JSONObject

fun serviceTitle(service: Service) = when (service) {
    Service.COMPANION -> "AI Companion"; Service.APP -> "Build an App"; Service.RESEARCH -> "Research Paper"
    Service.VISUAL -> "Create Visuals"; Service.VIDEO -> "Create Video"; Service.VOICE -> "Speech"
}

@Composable
fun PlatformApp(vm: WorkspaceViewModel = viewModel()) {
    val context = LocalContext.current
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("ui_permissions", 0)
        if(!prefs.getBoolean("notifications_asked", false)) {
            prefs.edit().putBoolean("notifications_asked", true).apply()
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var page by rememberSaveable { mutableStateOf("Home") }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var showTasks by remember { mutableStateOf(false) }
    val flow = remember(vm) { vm.tasks.observe() }; val tasks by flow.collectAsStateWithLifecycle(emptyList())
    val open: (String) -> Unit = { projectId = it; page = "Workspace" }
    Scaffold(topBar = { TextButton(onClick = { showTasks = true }) { Text("Tasks • ${tasks.count { it.optString("status") !in PersistentTaskStore.terminal }}") } },
        bottomBar = { NavigationBar { listOf("Home", "Projects", "AI Models", "Settings").forEach { name ->
            NavigationBarItem(selected = page == name, onClick = { page = name }, icon = { Text(when(name) { "Home" -> "⌂"; "Projects" -> "▣"; "AI Models" -> "AI"; else -> "⚙" }) }, label = { Text(name) })
        } } }) { padding -> Box(Modifier.padding(padding)) {
        when (page) {
            "Home" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("CODER ABYSS", style = MaterialTheme.typography.headlineLarge)
                Text("v0.7 · Local AI + Cloud GPU")
                listOf(Service.COMPANION, Service.APP, Service.RESEARCH, Service.VISUAL, Service.VIDEO).forEach { service ->
                    FilledTonalButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        val last = vm.projects.all().firstOrNull { it.optString("type") == service.name }
                        open(last?.getString("projectId") ?: vm.projects.create(service))
                    }) { Text(serviceTitle(service)) }
                }
                Text("Prompts, tasks and results stay with your projects. Navigation never cancels generation.")
            }
            "Projects" -> ProjectsScreen(vm, open)
            "AI Models" -> PlatformModelsScreen({ page = "Home" }, { page = "Settings" })
            "Settings" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) { Text("Settings", style = MaterialTheme.typography.headlineMedium); VideoBackendSettingsPanel() }
            "Workspace" -> projectId?.let { WorkspaceScreen(it, vm, { page = "Projects" }, { page = "AI Models" }, { page = "Settings" }) }
        }
    } }
    if (showTasks) AlertDialog(onDismissRequest = { showTasks = false }, title = { Text("Tasks and history") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (tasks.isEmpty()) Text("No operations yet")
            tasks.forEach { task -> TaskCard(task, vm, { showTasks = false; if(task.optString("operation") == "MODEL_DOWNLOAD") page = "AI Models" else if(runCatching { vm.projects.read(task.getString("projectId")) }.isSuccess) open(task.getString("projectId")) }) }
        }
    }, confirmButton = { TextButton(onClick = { showTasks = false }) { Text("Close") } })
}

@Composable
fun ProjectsScreen(vm: WorkspaceViewModel, onOpen: (String) -> Unit) {
    val flow = remember(vm) { vm.observeProjects() }; val projects by flow.collectAsStateWithLifecycle(emptyList())
    var newProject by remember { mutableStateOf(false) }; var rename by remember { mutableStateOf<JSONObject?>(null) }
    var deletion by remember { mutableStateOf<JSONObject?>(null) }; var name by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Projects", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { newProject = true }) { Text("New Project") }; Text(message)
        projects.forEach { project -> val id = project.getString("projectId")
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                TextButton(onClick = { onOpen(id) }) { Text(project.optString("title")) }
                Text(project.optString("type")); Row {
                    TextButton(onClick = { rename = project; name = project.optString("title") }) { Text("Rename") }
                    TextButton(enabled = !vm.tasks.activeForProject(id), onClick = { runCatching { vm.projects.duplicate(id) }.onSuccess(onOpen).onFailure { message = "Copy failed. Check available storage." } }) { Text("Duplicate") }
                    TextButton(enabled = !vm.tasks.activeForProject(id), onClick = { deletion = project }) { Text("Delete") }
                }
            } }
        }
    }
    if (newProject) AlertDialog(onDismissRequest = { newProject = false }, title = { Text("New Project") }, text = { Column {
        listOf(Service.COMPANION, Service.APP, Service.RESEARCH, Service.VISUAL, Service.VIDEO).forEach { service -> TextButton(onClick = { newProject = false; onOpen(vm.projects.create(service)) }) { Text(serviceTitle(service)) } }
    } }, confirmButton = { TextButton(onClick = { newProject = false }) { Text("Close") } })
    rename?.let { project -> AlertDialog(onDismissRequest = { rename = null }, title = { Text("Rename project") }, text = { OutlinedTextField(name, { name = it.take(120) }) }, confirmButton = { TextButton(onClick = { vm.projects.update(project.getString("projectId")) { it.put("title", name.ifBlank { "Untitled" }) }; rename = null }) { Text("Save") } }) }
    deletion?.let { project -> AlertDialog(onDismissRequest = { deletion = null }, title = { Text("Delete project and its local files?") }, text = { Text("This permanently removes this project's prompts, sources and outputs. Managed copies in other projects remain safe.") }, confirmButton = { TextButton(onClick = {
        if (!vm.tasks.activeForProject(project.getString("projectId"))) runCatching { vm.projects.delete(project.getString("projectId")) }.onFailure { message = "Project deletion failed" }
        deletion = null
    }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deletion = null }) { Text("Keep") } }) }
}

@Composable
fun TaskCard(task: JSONObject, vm: WorkspaceViewModel, onOpen: () -> Unit = {}) {
    val context = LocalContext.current; val id = task.getString("taskId"); val status = task.optString("status")
    var log by remember { mutableStateOf(false) }; var actionError by remember { mutableStateOf("") }
    val elapsed = ((task.optLong("completedAt", if(status in PersistentTaskStore.terminal) task.optLong("updatedAt", System.currentTimeMillis()) else System.currentTimeMillis()) - task.optLong("createdAt")) / 1000).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("${task.optString("service")} · ${task.optString("model")}", style = MaterialTheme.typography.titleSmall)
        Text("$status · ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')} elapsed")
        Text(task.optString("stage"))
        if(actionError.isNotBlank()) Text(actionError, color = MaterialTheme.colorScheme.error)
        task.optJSONObject("remoteStatus")?.let { remote ->
            if (!remote.isNull("progress")) Text("${(remote.optDouble("progress") * 100).toInt()}% · backend progress")
            remote.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if(task.optString("operation") == "VIDEO") {
            val stages = task.optJSONArray("log") ?: task.optJSONArray("stages") ?: org.json.JSONArray()
            for(n in (stages.length() - 5).coerceAtLeast(0) until stages.length()) Text("${if(n == stages.length()-1) "→" else "·"} ${stages.getJSONObject(n).optString("stage")}", style = MaterialTheme.typography.bodySmall)
        }
        if (task.has("downloadedBytes")) Text("${task.optLong("downloadedBytes")} / ${task.optLong("totalBytes")} bytes")
        task.optJSONObject("metrics")?.let { m -> if (m.has("outputTokens")) Text("${m.optInt("promptTokens")} prompt / ${m.optInt("outputTokens")} output / ${m.optInt("totalTokens")} total tokens · ${m.optDouble("tokensPerSecond")} tokens/s") }
        Row {
            TextButton(onClick = onOpen) { Text("Open") }
            if (status !in PersistentTaskStore.terminal) TextButton(onClick = { vm.tasks.cancel(id) }) { Text("Cancel") }
            if (status in setOf("FAILED", "INTERRUPTED", "DOWNLOAD_FAILED", "SUBMISSION_UNCERTAIN", "UNKNOWN", "WAITING_FOR_CONNECTION")) TextButton(onClick = { runCatching { vm.tasks.retry(id) }.onFailure { actionError = "Retry could not start. Check model, storage and Local Only settings." } }) { Text(if (status == "DOWNLOAD_FAILED") "Retry Download" else if (task.optBoolean("remote") && status != "FAILED") "Check same job" else "Retry") }
            TextButton(onClick = { log = true }) { Text("Log") }
        }
        if (status == "INTERRUPTED" && task.optString("partial").isNotBlank() && task.optString("operation") == "TEXT") Row {
            TextButton(onClick = { runCatching { vm.tasks.retry(id, true) }.onFailure { actionError = "Continue could not start. Check the selected model." } }) { Text("Continue draft") }
            TextButton(onClick = { vm.tasks.store.update(id) { it.remove("partial") } }) { Text("Discard partial") }
        }
    }
    if (log) {
        val diagnostic = "Task: $id\nJob: ${task.optString("jobId")}\nProvider: ${task.optString("space")}\n$status\n${task.optString("stage")}\n${task.optJSONArray("log") ?: task.optJSONArray("stages") ?: ""}"
        AlertDialog(onDismissRequest = { log = false }, title = { Text("Diagnostic") }, text = { Text(diagnostic, Modifier.verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = {
            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Diagnostic", diagnostic))
        }) { Text("Copy Diagnostic") } }, dismissButton = { TextButton(onClick = { log = false }) { Text("Close") } })
    }
}
