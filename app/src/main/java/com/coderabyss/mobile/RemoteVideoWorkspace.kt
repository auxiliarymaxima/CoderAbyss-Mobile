package com.coderabyss.mobile

import android.content.Intent
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun RemoteVideoWorkspace(initialProject: String?, experimental: Boolean = false, onBack: () -> Unit, onSettings: () -> Unit,
                         onExperimental: () -> Unit, voice: @Composable ((Boolean) -> Unit, (String) -> Unit) -> Unit) {
    val context = LocalContext.current
    val tasks = remember { VideoTasks(context) }
    val settings = remember { VideoBackendSettings(context) }
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(experimental) {
        if (experimental && androidx.core.content.ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("coder_abyss_video", android.content.Context.MODE_PRIVATE) }
    var models by remember { mutableStateOf(VideoModelCatalog.models(context)) }
    var connection by remember { mutableStateOf("Disconnected") }
    var projectId by rememberSaveable(initialProject, experimental) { mutableStateOf(initialProject) }
    var variationParent by rememberSaveable { mutableStateOf<String?>(null) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var engine by rememberSaveable { mutableStateOf(preferences.getString("remote_engine", "wan") ?: "wan") }
    var duration by rememberSaveable { mutableIntStateOf(1) }
    var resolution by rememberSaveable { mutableStateOf("256x256") }
    var task by remember { mutableStateOf<JSONObject?>(null) }
    var voiceBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var recommendations by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!experimental && settings.configured() && !settings.localOnly) {
            connection = "Authenticating"
            runCatching { HuggingFaceVideoClient(context).capabilities() }
                .onSuccess { models = VideoModelCatalog.models(context); connection = "Connected" }
                .onFailure { connection = "Unavailable" }
        }
    }
    val selectedModel = if (experimental) HostedVideoModel("wan", "Local Wan", true, listOf(1, 2, 3), listOf("256x256", "384x256", "512x288"), "Experimental — Very Slow on Mobile") else models.first { it.engine == engine }
    val preferencePrefix = if (experimental) "local_wan" else engine
    LaunchedEffect(engine, models, experimental, projectId) {
        if (experimental) engine = "wan"
        duration = preferences.getInt("${preferencePrefix}_duration", selectedModel.durations.firstOrNull() ?: 1)
        if (duration !in selectedModel.durations) duration = selectedModel.durations.firstOrNull() ?: 1
        resolution = preferences.getString("${preferencePrefix}_resolution", null)?.takeIf { it in selectedModel.resolutions } ?: selectedModel.resolutions.firstOrNull() ?: "256x256"
        projectId?.let { id ->
            val request = tasks.read(id).getJSONObject("request")
            if (request.getString("engine") == engine) {
                duration = request.getInt("duration")
                resolution = request.getString("resolution")
            }
        }
    }
    LaunchedEffect(projectId) {
        projectId?.let { id ->
            val saved = tasks.read(id)
            prompt = saved.getJSONObject("request").getString("prompt")
            engine = saved.getJSONObject("request").getString("engine")
        }
        while (true) {
            task = projectId?.let { tasks.read(it) }
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val count = VideoPromptRules.words(prompt)
    val localOnly = settings.localOnly
    val active = task?.optString("status")?.let { it !in VideoTasks.finished } == true
    val file = projectId?.let(tasks::output)?.takeIf { task?.optString("status") == "COMPLETED" && it.isFile }
    val player = remember { VideoView(context) }
    DisposableEffect(player) { onDispose { player.stopPlayback() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Create video", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = engine == "wan", onClick = { engine = "wan"; preferences.edit().putString("remote_engine", engine).apply() }, label = { Text("QUICK · Wan") })
            FilterChip(enabled = !experimental, selected = engine == "ltx", onClick = { engine = "ltx"; preferences.edit().putString("remote_engine", engine).apply() }, label = { Text("LONG · LTX") })
        }
        Text(if (experimental) "On-device generation · background task" else if (localOnly) "Unavailable while Local Only is enabled." else connection)
        TextButton(onClick = onSettings) { Text("Video Backend settings") }
        TextButton(onClick = { recommendations = true }) { Text("Recommended Models") }
        Text(selectedModel.description)
        if (selectedModel.available) {
            Text("${selectedModel.title} · ${if (experimental) "On device" else "Hosted GPU"}")
            if (engine == "ltx") Text("Long videos use multiple conditioned 5-second segments. Continuity is not guaranteed.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                selectedModel.durations.forEach { seconds -> FilterChip(selected = duration == seconds, onClick = {
                    duration = seconds; preferences.edit().putInt("${preferencePrefix}_duration", seconds).apply()
                }, label = { Text("${seconds}s") }) }
            }
            selectedModel.resolutions.forEach { size ->
                FilterChip(selected = resolution == size, onClick = {
                    resolution = size; preferences.edit().putString("${preferencePrefix}_resolution", size).apply()
                }, label = { Text(size) })
            }
        }
        voice({ voiceBusy = it }) { text -> prompt = listOf(prompt, text).filter { it.isNotBlank() }.joinToString("\n") }
        OutlinedTextField(prompt, { prompt = it }, label = { Text("Describe your video") },
            modifier = Modifier.fillMaxWidth().height(160.dp), isError = engine == "wan" && count > 100)
        if (engine == "wan") {
            Text("$count / 100 words", color = if (count > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text("Wan video prompts are limited to 100 words for better generation quality.")
            if (count > 100) Text("Shorten your prompt before generating.", color = MaterialTheme.colorScheme.error)
        }
        if (engine == "ltx") Text("$count words · final prompt must fit the backend encoder; no automatic truncation")
        Button(enabled = !voiceBusy && !active && (experimental || (!localOnly && settings.configured())) && selectedModel.available &&
            duration in selectedModel.durations && resolution in selectedModel.resolutions &&
            (if (engine == "wan") count in 1..100 else prompt.isNotBlank() && prompt.length <= 2000),
            onClick = {
                runCatching { tasks.create(prompt, duration, resolution, engine, experimental, variationParent) }.onSuccess { id ->
                    projectId = id; task = tasks.read(id); message = ""
                }.onFailure { message = "Could not save or submit the video project." }
            }) { Text("Generate video") }
        task?.let { current ->
            Text(current.optString("title"), style = MaterialTheme.typography.titleMedium)
            Text(current.optString("stage"))
            current.optJSONArray("stages")?.let { stages ->
                Text("Video steps", style = MaterialTheme.typography.labelLarge)
                for (index in (stages.length() - 6).coerceAtLeast(0) until stages.length()) {
                    Text("${if (index == stages.length() - 1) "→" else "·"} ${stages.getJSONObject(index).getString("stage")}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            val elapsed = ((current.optLong("generationCompletedAt", now) - current.getLong("createdAt")) / 1000).coerceAtLeast(0)
            Text("${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')} elapsed")
            val remote = current.optJSONObject("remote")
            if (remote != null && !remote.isNull("progress")) Text("Sampling steps: ${(remote.getDouble("progress") * 100).toInt()}%")
            if (current.has("downloadedBytes")) Text("Downloaded ${current.optLong("downloadedBytes")} / ${current.optLong("totalBytes")} bytes")
            if (current.optString("status") == "DOWNLOADING") Text("Download elapsed: ${(now - current.optLong("downloadStartedAt", now)).coerceAtLeast(0) / 1000}s")
            if (active) {
                Text("You can leave this screen. The job is tracked in Projects.")
                OutlinedButton(onClick = { projectId?.let { id -> tasks.update(id) { it.put("cancelRequested", true) }; tasks.enqueue(id) } }) { Text("Cancel job") }
            }
            if (current.optString("status") in setOf("DOWNLOAD_FAILED", "WAITING_FOR_CONNECTION", "SUBMISSION_UNCERTAIN")) {
                OutlinedButton(onClick = { projectId?.let(tasks::enqueue) }) {
                    Text(if (current.optString("status") == "DOWNLOAD_FAILED") "Retry Download" else "Check same job")
                }
            }
            if (current.optString("status") == "FAILED" && current.optString("jobId").isNotBlank() && !localOnly) {
                OutlinedButton(onClick = { projectId?.let { id ->
                    tasks.update(id) { it.put("retryRequested", true).put("status", "RETRY_REQUESTED").put("cancelRequested", false) }
                    tasks.enqueue(id)
                } }) { Text("Retry generation from saved segments") }
            }
            TextButton(onClick = {
                val clip = android.content.ClipData.newPlainText("Video diagnostic", "Backend: ${current.optString("backend")}\nJob: ${current.optString("jobId")}\nStatus: ${current.optString("status")}\nStage: ${current.optString("stage")}")
                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
            }) { Text("Copy Diagnostic") }
        }
        Text("VIDEO OUTPUT", style = MaterialTheme.typography.titleMedium)
        if (file == null) Text("Your completed video will appear here.")
        else {
            AndroidView(factory = { player.apply { setMediaController(android.widget.MediaController(context).also { it.setAnchorView(this) }) } },
                update = { if (it.tag != file.absolutePath) { it.tag = file.absolutePath; it.setVideoPath(file.absolutePath) } },
                modifier = Modifier.fillMaxWidth().height(240.dp))
            Text(task?.optJSONObject("output")?.let { "${it.optString("resolution")} · ${it.optDouble("duration")} seconds · ${it.optLong("fileSize")} bytes · ${task?.getJSONObject("request")?.optString("engine")}" } ?: "")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { player.start() }) { Text("Play") }
                TextButton(onClick = { player.seekTo(0); player.start() }) { Text("Replay") }
                TextButton(onClick = { scope.launch { runCatching { VideoStorage.save(context, file, task?.optString("title")) }.onSuccess { message = "Saved MP4 to Movies/CoderAbyss" }.onFailure { message = "Save failed. Your project copy is retained." } } }) { Text("Save MP4") }
            }
            Row {
                TextButton(onClick = {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/mp4")
                        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share video"))
                }) { Text("Share") }
                TextButton(onClick = { title = task?.optString("title") ?: "Video"; rename = true }) { Text("Rename") }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
            }
            Row {
                TextButton(enabled = !voiceBusy && (experimental || (!localOnly && settings.configured())), onClick = {
                    val original = task?.getJSONObject("request") ?: return@TextButton
                    runCatching { tasks.create(original.getString("prompt"), original.getInt("duration"),
                        original.getString("resolution"), original.getString("engine"), experimental, projectId) }
                        .onSuccess { id -> projectId = id; task = tasks.read(id) }
                        .onFailure { message = "Could not create the regeneration project." }
                }) { Text("Regenerate") }
                TextButton(onClick = { variationParent = projectId; projectId = null; task = null }) { Text("Create Variation") }
            }
        }
        Text(message)
        if (!experimental) TextButton(onClick = onExperimental) { Text("Experimental — Very Slow on Mobile") }
    }
    if (recommendations) AlertDialog(onDismissRequest = { recommendations = false }, title = { Text("Recommended Models") },
        text = { Column { models.forEach { model -> Text("${model.title} · Hosted GPU\n${if (model.available) "Available on GPU Backend" else "Configure Backend / validation pending"}") } } },
        confirmButton = { TextButton(onClick = { recommendations = false; onSettings() }) { Text("Configure Backend") } })
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text("Rename video project") },
        text = { OutlinedTextField(title, { title = it.take(100) }) }, confirmButton = { TextButton(onClick = {
            projectId?.let { id -> tasks.update(id) { it.put("title", title.ifBlank { "Video" }) } }; rename = false
        }) { Text("Save") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete local video?") },
        text = { Text("This removes the local MP4. The prompt and job history will remain.") },
        confirmButton = { TextButton(onClick = { player.stopPlayback(); file?.delete(); projectId?.let { id -> tasks.update(id) { it.put("status", "DOWNLOAD_FAILED").put("stage", "Local file deleted. Retry Download if the backend still has it.") } }; confirmDelete = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } })
}

@Composable
fun VideoBackendSettingsPanel() {
    val context = LocalContext.current
    val settings = remember { VideoBackendSettings(context) }
    val scope = rememberCoroutineScope()
    var localOnly by remember { mutableStateOf(settings.localOnly) }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (settings.configured()) "Authentication configured" else "Disconnected") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Video Backend", style = MaterialTheme.typography.titleLarge)
        Text("Hugging Face · ${VideoBackendSettings.SPACE}")
        Row { Text("Local Only", Modifier.weight(1f)); Switch(localOnly, { localOnly = it; settings.localOnly = it; if (!it) VideoTasks(context).recover() }) }
        Text(if (localOnly) "Unavailable while Local Only is enabled." else status)
        OutlinedTextField(token, { token = it }, label = { Text("Private Space credential") }, singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        Text("Development access: use your own authorized Hugging Face credential. It is encrypted on this device.")
        Row {
            TextButton(onClick = { runCatching { settings.saveToken(token) }.onSuccess { token = ""; status = if (settings.configured()) "Authentication configured" else "Disconnected" }.onFailure { status = "Could not save authentication" } }) { Text("Save credential") }
            TextButton(enabled = !localOnly && settings.configured(), onClick = {
                status = "Authenticating"
                scope.launch { runCatching { HuggingFaceVideoClient(context).capabilities() }.onSuccess { status = "Connected" }.onFailure { status = "Unavailable — check authentication and backend" } }
            }) { Text("Test Backend") }
        }
    }
}

@Composable
fun VideoProjectsScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var projects by remember { mutableStateOf(VideoTasks(context).all()) }
    LaunchedEffect(Unit) { while (true) { projects = VideoTasks(context).all(); delay(2000) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Projects · Videos", style = MaterialTheme.typography.headlineMedium)
        if (projects.isEmpty()) Text("Generate a video to create your first persistent project.")
        projects.forEach { project ->
            TextButton(onClick = { onOpen(project.getString("projectId")) }) {
                Text("${project.optString("title")}\n${project.optString("stage")}")
            }
        }
    }
}
