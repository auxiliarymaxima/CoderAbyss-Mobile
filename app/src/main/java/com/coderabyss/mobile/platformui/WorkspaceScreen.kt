package com.coderabyss.mobile.platformui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coderabyss.mobile.*
import com.coderabyss.mobile.models.*
import com.coderabyss.mobile.remote.SpaceRegistry
import com.coderabyss.mobile.tasks.*
import com.coderabyss.mobile.voice.VoiceCaptureService
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Composable
fun SavedTextField(value: String, label: String, onChange: (String) -> Unit, minLines: Int = 1) {
    var text by remember { mutableStateOf(value) }; var saved by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != saved) { text = value; saved = value } }
    OutlinedTextField(text, { text = it; saved = it; onChange(it) }, label = { Text(label) }, minLines = minLines, modifier = Modifier.fillMaxWidth())
}

@Composable
fun WorkspaceScreen(id: String, vm: WorkspaceViewModel, onBack: () -> Unit, onModels: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current; val projectFlow = remember(id) { vm.observeProject(id) }
    val project by projectFlow.collectAsStateWithLifecycle(vm.projects.read(id))
    val taskFlow = remember(vm) { vm.tasks.observe() }; val tasks by taskFlow.collectAsStateWithLifecycle(emptyList())
    val service = Service.valueOf(project.getString("type"))
    val prefs = remember { context.getSharedPreferences("service_defaults", 0) }
    val default = when(service) { Service.COMPANION -> "lfm-2.5-1.2b-q4"; Service.APP, Service.RESEARCH -> "qwen-coder-1.5b-q4"; Service.VISUAL -> "sdxl"; Service.VIDEO -> "wan"; else -> "whisper-tiny-en" }
    val selected = project.optString("preferredModel").ifBlank { prefs.getString(service.name, default) ?: default }
    val descriptor = runCatching { ModelRegistry.get(selected) }.getOrNull()
    var message by remember(id) { mutableStateOf("") }
    val current = tasks.filter { it.optString("projectId") == id }
    val active = current.any { it.optString("status") !in PersistentTaskStore.terminal }
    val input = project.optJSONObject("generationSettings") ?: JSONObject()
    fun setting(key: String, value: Any) { vm.projects.update(id) { it.put("generationSettings", (it.optJSONObject("generationSettings") ?: JSONObject()).put(key, value)) } }
    fun submit(parameters: JSONObject = JSONObject()) {
        runCatching {
            vm.projects.checkpoint(id)
            vm.tasks.submit(id, when(service) { Service.VISUAL -> Operation.IMAGE; Service.VIDEO -> Operation.VIDEO; else -> Operation.TEXT }, selected, parameters)
        }.onFailure { message = it.message?.takeIf { text -> text.length < 240 } ?: "Could not start. Check the selected model and provider." }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row { TextButton(onClick = onBack) { Text("Projects") }; Spacer(Modifier.weight(1f)); ModelPicker(service, selected, {
            vm.projects.update(id) { project -> project.put("preferredModel", it) }; prefs.edit().putString(service.name, it).apply()
        }, onModels) }
        Text(serviceTitle(service), style = MaterialTheme.typography.headlineMedium)
        SavedTextField(project.optString("title"), "Project name", { vm.projects.update(id) { project -> project.put("title", it) } })
        Text(descriptor?.name ?: "Select a model")
        if (descriptor?.executionType == ExecutionType.HUGGING_FACE_SPACE) {
            Text(if (VideoBackendSettings(context).localOnly) "Unavailable in Local Only mode." else if (SpaceRegistry.capability(context, selected)?.optBoolean("available") == true) "Cloud GPU · ${SpaceRegistry.health(context).name.replace('_', ' ')}" else "Cloud GPU · Configure / test connection")
            TextButton(onClick = onSettings) { Text("Provider settings") }
        }
        SavedTextField(project.optString("prompt"), if (service == Service.RESEARCH) "Topic and instructions" else "Prompt", { vm.projects.update(id) { project -> project.put("prompt", it) } }, 4)
        PersistentVoiceControl(id, vm, tasks, onModels)
        Text("Dictation is added to this prompt for editing. It never submits generation.", style = MaterialTheme.typography.bodySmall)
        when (service) {
            Service.RESEARCH -> ResearchWorkspace(project, vm, selected, active)
            Service.VIDEO -> {
                Row { listOf("wan" to "QUICK · Wan", "ltx" to "LONG · LTX").forEach { (model, label) -> FilterChip(selected == model, {
                    vm.projects.update(id) { it.put("preferredModel", model) }; prefs.edit().putString(service.name, model).apply()
                }, label = { Text(label) }) } }
                val capability = SpaceRegistry.capability(context, selected)
                val durations = capability?.optJSONArray("durations")?.let { a -> (0 until a.length()).map(a::getInt) } ?: emptyList()
                val resolutions = capability?.optJSONArray("resolutions")?.let { a -> (0 until a.length()).map(a::getString) } ?: emptyList()
                val duration = input.optInt("duration").takeIf { it in durations } ?: durations.firstOrNull()
                val resolution = input.optString("resolution").takeIf { it in resolutions } ?: resolutions.firstOrNull()
                ChoiceMenu("Duration", duration?.let { "$it seconds" } ?: "Unavailable", durations.map { "$it seconds" }) { setting("duration", it.substringBefore(' ').toInt()) }
                ChoiceMenu("Resolution", resolution ?: "Unavailable", resolutions) { setting("resolution", it) }
                Text(capability?.optString("description") ?: "Test the provider to retrieve supported settings.")
                val count = VideoPromptRules.words(project.optString("prompt"))
                if (selected == "wan") {
                    Text("$count / 100 words", color = if(count > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Text("Wan video prompts are limited to 100 words for better generation quality.")
                    if (count > 100) Text("Please shorten your prompt before generating.", color = MaterialTheme.colorScheme.error)
                }
                Button(enabled = !active && !VideoBackendSettings(context).localOnly && capability?.optBoolean("available") == true && duration != null && resolution != null && count > 0 && (selected != "wan" || count <= 100), onClick = {
                    val parts = resolution!!.split('x'); val w = parts[0].toInt(); val h = parts[1].toInt()
                    submit(JSONObject(input.toString()).put("duration", duration).put("resolution", resolution).put("aspectRatio", if (selected == "ltx") "5:3" else if (w == h) "1:1" else if(w > h) "30:17" else "17:30"))
                }) { Text("Generate video") }
            }
            Service.VISUAL -> {
                SavedTextField(input.optString("negativePrompt"), "Negative prompt (optional)", { setting("negativePrompt", it) })
                val sizes = SpaceRegistry.capability(context, selected)?.optJSONArray("resolutions")?.let { a -> (0 until a.length()).map(a::getString) } ?: emptyList()
                ChoiceMenu("Resolution", input.optString("resolution", sizes.firstOrNull() ?: "Unavailable"), sizes) { setting("resolution", it) }
                SavedTextField(input.optLong("seed", -1).toString(), "Seed (-1 for random)", { it.toLongOrNull()?.let { value -> setting("seed", value) } })
                Text("SDXL uses two 77-token prompt encoders. The backend rejects overlong prompts without truncating them.")
                Button(enabled = !active && project.optString("prompt").isNotBlank() && !VideoBackendSettings(context).localOnly && SpaceRegistry.capability(context, selected)?.optBoolean("available") == true, onClick = { submit(JSONObject(input.toString()).put("resolution", input.optString("resolution", sizes.firstOrNull() ?: "512x512"))) }) { Text("Generate image") }
            }
            else -> {
                Button(enabled = !active && project.optString("prompt").isNotBlank(), onClick = { submit() }) { Text(if(service == Service.APP) "Generate source" else "Send") }
                if (service == Service.APP) {
                    Text("Generated source is saved in this project. Review it before running or publishing.")
                    Text("APK/AAB compilation requires a configured isolated Android build worker. No build worker is connected.")
                    TextButton(onClick = { runCatching { vm.tasks.submit(id, Operation.SOURCE_PACKAGE) }.onFailure { message = "Could not queue source export" } }) { Text("Export source ZIP") }
                    SourceEditor(project, vm)
                }
            }
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
        current.take(6).forEach { task -> TaskCard(task, vm)
            if (task.optString("partial").isNotBlank()) Text(task.optString("partial"))
        }
        OutputGallery(project, vm)
        ManagedAssets(project, vm)
        Text("Saved versions", style = MaterialTheme.typography.titleMedium)
        ChoiceMenu("Restore draft", "Choose a saved version", vm.projects.versions(id).map { it.name }) { version ->
            if(!active) runCatching { vm.projects.restoreDraft(id, version) }.onFailure { message = "Could not restore saved draft" }
            else message = "Wait for active work before restoring a draft."
        }
        Text("Restoring changes editable content and saved source files; it never restarts a GPU job or deletes completed outputs.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ChoiceMenu(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }, enabled = options.isNotEmpty()) { Text("$label: $selected ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { options.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { onSelect(value); open = false }) } }
    }
}

@Composable
fun PersistentVoiceControl(projectId: String, vm: WorkspaceViewModel, tasks: List<JSONObject>, onModels: () -> Unit) {
    val context = LocalContext.current; var error by remember { mutableStateOf("") }
    val voiceTasks = tasks.filter { it.optString("operation") == "TRANSCRIBE" && it.optString("status") !in PersistentTaskStore.terminal }
    val recording = voiceTasks.firstOrNull { it.optString("status") == "RECORDING" }
    fun start() {
        runCatching {
            check(vm.tasks.store.all().none { it.optString("operation") == "TRANSCRIBE" && it.optString("status") !in PersistentTaskStore.terminal }) { "Speech is already recording or transcribing" }
            val manager = OfflineModelManager(context)
            val model = ModelCatalog.models.firstOrNull { it.kind == ModelKind.SPEECH && manager.isInstalled(it) } ?: error("Download Whisper in AI Models first")
            check(DeviceCompatibility.evaluate(ModelRegistry.get(model.id), DeviceCompatibility.snapshot(context)).canExecute) { "Not enough available memory for Whisper" }
            val id = UUID.randomUUID().toString(); val now = System.currentTimeMillis()
            vm.tasks.store.write(JSONObject().put("taskId", id).put("projectId", projectId).put("service", "VOICE").put("operation", "TRANSCRIBE").put("model", model.id).put("remote", false)
                .put("createdAt", now).put("updatedAt", now).put("status", "RECORDING").put("stage", "Starting microphone").put("cancelRequested", false).put("log", JSONArray())
                .put("parameters", JSONObject().put("audio", "assets/$id.pcm")))
            ContextCompat.startForegroundService(context, Intent(context, VoiceCaptureService::class.java).putExtra("taskId", id))
        }.onFailure { error = it.message ?: "Could not start speech capture" }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if(granted) start() else error = "Microphone permission is required for dictation" }
    Row {
        OutlinedButton(enabled = recording != null || voiceTasks.isEmpty(), onClick = {
            if(recording != null) context.startService(Intent(context, VoiceCaptureService::class.java).setAction("STOP"))
            else if(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }) { Text(if (recording != null) "Stop and transcribe" else if(voiceTasks.isNotEmpty()) "Transcribing…" else "Tap to Speak") }
        if (error.isNotBlank()) TextButton(onClick = onModels) { Text("AI Models") }
    }
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
}

@Composable
fun SourceEditor(project: JSONObject, vm: WorkspaceViewModel) {
    val id = project.getString("projectId"); val root = vm.projects.file(id, "source")
    val paths = root.walkTopDown().filter { it.isFile && !it.name.endsWith(".partial") }.map { it.relativeTo(root).invariantSeparatorsPath }.toList()
    var selected by remember(id) { mutableStateOf<String?>(null) }; var text by remember { mutableStateOf("") }
    ChoiceMenu("Source file", selected ?: "Select", paths) { selected = it; text = vm.projects.file(id, "source/$it").readText() }
    if(selected != null) {
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 8, label = { Text(selected!!) })
        TextButton(onClick = { vm.projects.checkpoint(id); val file = vm.projects.file(id, "source/$selected"); val partial = java.io.File(file.path + ".partial"); partial.writeText(text); check(partial.renameTo(file)); vm.projects.update(id) {} }) { Text("Save source") }
    }
}
