package com.coderabyss.mobile.platformui

import android.content.Intent
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.coderabyss.mobile.exports.DocumentExporter
import com.coderabyss.mobile.tasks.Operation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Composable
internal fun WorkflowVideoStill(file: File, modifier: Modifier = Modifier) {
    var bitmap by remember(file.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file.path) {
        bitmap = withContext(Dispatchers.IO) { runCatching {
            val retriever = MediaMetadataRetriever()
            try { retriever.setDataSource(file.path); retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 360) }
            finally { retriever.release() }
        }.getOrNull() }
    }
    bitmap?.let { Image(it.asImageBitmap(), "Video preview", modifier, contentScale = ContentScale.Fit) }
}

@Composable
internal fun WorkflowMediaPreview(project: JSONObject, vm: WorkspaceViewModel, small: Boolean = false, onOpen: () -> Unit) {
    val outputs = project.optJSONArray("outputs") ?: JSONArray()
    val output = (0 until outputs.length()).map(outputs::getJSONObject).lastOrNull { it.optString("mimeType").startsWith("video/") }
    Box(Modifier.fillMaxWidth().height(if(small) 112.dp else 220.dp).clip(RoundedCornerShape(12.dp)).background(WorkflowNavy)
        .border(1.dp, WorkflowBorder, RoundedCornerShape(12.dp)).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
        if(output != null) {
            WorkflowVideoStill(vm.projects.file(project.getString("projectId"), output.getString("path")), Modifier.fillMaxSize())
            Icon(Icons.Rounded.PlayCircle, "Open video preview", tint = Color.White, modifier = Modifier.size(48.dp))
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Videocam, null, tint = WorkflowCyan, modifier = Modifier.size(30.dp))
            Text("Your video preview", color = WorkflowMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun WorkflowResults(project: JSONObject, vm: WorkspaceViewModel, onVary: () -> Unit) {
    val context = LocalContext.current; val id = project.getString("projectId")
    val array = project.optJSONArray("outputs") ?: JSONArray()
    val outputs = (0 until array.length()).map(array::getJSONObject).asReversed()
    var selectedPath by remember(id) { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf(false) }; var zoom by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val selected = outputs.firstOrNull { it.optString("path") == selectedPath } ?: outputs.firstOrNull()
    if(selected == null) {
        WorkflowPanel { Icon(Icons.Rounded.AutoAwesome, null, tint = WorkflowCyan); Text("Ready for your first result", style = MaterialTheme.typography.titleMedium); Text("Generated work will appear here.", color = WorkflowMuted) }
        return
    }
    val file = vm.projects.file(id, selected.getString("path")); val mime = selected.optString("mimeType")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if(mime.startsWith("image/")) "Preview & refine" else if(mime.startsWith("video/")) "Video preview" else "Downloads", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { detail = true }) { Icon(Icons.Rounded.MoreVert, "File actions", tint = WorkflowCyan) }
    }
    if(file.isFile) {
        if(mime.startsWith("image/")) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WorkflowNavy).border(1.dp, WorkflowBorder, RoundedCornerShape(14.dp))) {
            LocalImage(file, Modifier.fillMaxWidth().height(280.dp))
            IconButton(onClick = { zoom = true }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Rounded.Fullscreen, "Full screen", tint = Color.White) }
        } else if(mime.startsWith("video/")) {
            key(file.path) {
                val player = remember { android.widget.VideoView(context) }
                DisposableEffect(player) { onDispose { player.stopPlayback() } }
                Box(Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black).border(1.dp, WorkflowBorder, RoundedCornerShape(14.dp))) {
                    AndroidView(factory = { player.apply { setMediaController(android.widget.MediaController(context).also { it.setAnchorView(this) }); setVideoPath(file.path); setOnPreparedListener { seekTo(1) } } }, modifier = Modifier.fillMaxSize())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { player.start() }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PlayArrow, null); Text("Play") }
                    OutlinedButton(onClick = { player.seekTo(0); player.start() }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Replay, null); Text("Replay") }
                }
            }
            WorkflowAction("Add to Video") { runCatching { com.coderabyss.mobile.videoeditor.VideoTimeline.add(vm.projects, id, selected.getString("path")) }.onSuccess { message = "Clip added. Open Edit to arrange your video." }.onFailure { message = "Could not read this video." } }
        }
    }
    if(outputs.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        outputs.forEach { output -> val candidate = vm.projects.file(id, output.getString("path"))
            Box(Modifier.size(width = 84.dp, height = 60.dp).clip(RoundedCornerShape(10.dp)).background(WorkflowNavy)
                .border(if(output == selected) 2.dp else 1.dp, if(output == selected) WorkflowCyan else WorkflowBorder, RoundedCornerShape(10.dp)).clickable { selectedPath = output.getString("path") }, contentAlignment = Alignment.Center) {
                when { output.optString("mimeType").startsWith("image/") -> LocalImage(candidate, Modifier.fillMaxSize())
                    output.optString("mimeType").startsWith("video/") -> WorkflowVideoStill(candidate, Modifier.fillMaxSize())
                    else -> Icon(Icons.Rounded.Description, candidate.name, tint = WorkflowCyan) }
            }
        }
    }
    WorkflowPanel {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(if(file.isFile) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null, tint = if(file.isFile) Color(0xFF00DA9C) else MaterialTheme.colorScheme.error); Spacer(Modifier.width(10.dp)); Text(if(file.isFile) "Ready to save & share" else "Local file missing", fontWeight = FontWeight.SemiBold) }
        Text(selected.optString("displayName", file.name), style = MaterialTheme.typography.bodyMedium)
        Text(listOf(selected.optString("model"), selected.optString("resolution"), android.text.format.Formatter.formatFileSize(context, file.length())).filter(String::isNotBlank).joinToString(" · "), color = WorkflowMuted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = file.isFile, onClick = { runCatching { vm.tasks.submit(id, Operation.MEDIA_EXPORT, parameters = JSONObject(selected.toString()).put("title", selected.optString("displayName", project.optString("title")))) }.onSuccess { message = "Save queued. See Tasks for progress." }.onFailure { message = "Could not queue save." } }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save") }
            OutlinedButton(enabled = file.isFile, onClick = { runCatching { val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share output")) }.onFailure { message = "No sharing app is available." } }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share") }
        }
    }
    if(selected.optString("prompt").isNotBlank()) {
        Text(selected.optString("prompt"), color = WorkflowMuted, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { vm.projects.update(id) { p -> p.put("prompt", selected.optString("prompt")); selected.optString("model").takeIf(String::isNotBlank)?.let { p.put("preferredModel", it) }; selected.optJSONObject("settings")?.let { p.put("generationSettings", JSONObject(it.toString())) } }; onVary() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Create variation") }
    }
    if(message.isNotBlank()) Text(message, color = WorkflowCyan)
    if(zoom) Dialog(onDismissRequest = { zoom = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) { Surface(Modifier.fillMaxSize(), color = Color.Black) { Column { TextButton(onClick = { zoom = false }) { Text("Close") }; LocalImage(file, Modifier.fillMaxSize(), zoom = true) } } }
    if(detail) Dialog(onDismissRequest = { detail = false }) { Surface(shape = RoundedCornerShape(18.dp), color = WorkflowNavy) { Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = { detail = false }) { Text("Done") }
        // Keep existing rename, deletion safeguards, format export and diagnostic actions.
        OutputGallery(JSONObject(project.toString()).put("outputs", JSONArray().put(selected)), vm)
    } } }
}

@Composable
internal fun WorkflowPaper(project: JSONObject, vm: WorkspaceViewModel, busy: Boolean, onRevise: () -> Unit, onAdvanced: () -> Unit, tasks: List<JSONObject>, onModels: () -> Unit, onRevision: (String) -> Unit) {
    var revising by remember { mutableStateOf(false) }
    val content = DocumentExporter.content(project)
    val sections = project.optJSONArray("sections") ?: JSONArray()
    var export by remember { mutableStateOf(false) }; var search by remember { mutableStateOf("") }; var find by remember { mutableStateOf(false) }
    var font by remember { mutableFloatStateOf(16f) }; var error by remember { mutableStateOf("") }
    if(sections.length() == 0) { WorkflowPanel { Text("Your paper will appear here", style = MaterialTheme.typography.titleMedium) }; return }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Your paper", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { find = !find }) { Icon(Icons.Rounded.Search, "Search paper", tint = WorkflowCyan) }
        if(!revising) IconButton(onClick = { export = true }) { Icon(Icons.Rounded.Download, "Export paper", tint = WorkflowCyan) }
        IconButton(onClick = onAdvanced) { Icon(Icons.Rounded.Edit, "Edit sections & sources", tint = WorkflowCyan) }
    }
    if(find) { OutlinedTextField(search, { search = it }, placeholder = { Text("Find in paper") }, singleLine = true, modifier = Modifier.fillMaxWidth()); Row { TextButton(onClick = { font = (font - 2).coerceAtLeast(12f) }) { Text("A−") }; TextButton(onClick = { font = (font + 2).coerceAtMost(28f) }) { Text("A+") } } }
    if(revising) {
        WorkflowPanel {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(content.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); IconButton(onClick = { revising = false }) { Icon(Icons.Rounded.Close, "Back to paper", tint = WorkflowCyan) } }
            Text("Saved draft", color = WorkflowCyan, style = MaterialTheme.typography.labelSmall)
            Text(content.sections.firstOrNull()?.text.orEmpty(), maxLines = 10, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, lineHeight = 24.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(color = Color(0xFF0C3152), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(.9f)) {
                Text("Describe the changes you want below.", Modifier.padding(16.dp), color = WorkflowMuted)
            }
        }
        WorkflowPrompt(project.optString("prompt"), "Ask for changes…", "Editable instructions", false, true) { value -> vm.projects.update(project.getString("projectId")) { it.put("prompt", value) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { PersistentVoiceControl(project.getString("projectId"), vm, tasks, onModels, workflowStyle = true) }
            Box(Modifier.widthIn(max = 220.dp)) { WorkflowAction("Revise Paper", !busy && project.optString("prompt").isNotBlank()) { onRevision(project.optString("prompt")) } }
        }
        TextButton(onClick = onRevise) { Text("Full prompt & model options") }
        return
    }
    WorkflowPanel {
        Text(content.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if(content.author.isNotBlank()) Text(content.author, color = WorkflowMuted)
        content.sections.filter { search.isBlank() || it.title.contains(search, true) || it.text.contains(search, true) }.forEach { section ->
            Text(section.title, color = WorkflowCyan, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            Text(section.text, fontSize = font.sp, lineHeight = (font * 1.55f).sp, color = Color(0xFFD5E4F3))
        }
    }
    WorkflowOption("Refine your paper", "Ask for changes…", Icons.Rounded.Edit, onClick = { revising = true; vm.projects.update(project.getString("projectId")) { it.put("prompt", "") } })
    if(error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    if(export) AlertDialog(onDismissRequest = { export = false }, title = { Text("Download paper") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DocumentExporter.formats.keys.forEach { format -> WorkflowAction(format.uppercase(), !busy) { runCatching { vm.tasks.submit(project.getString("projectId"), Operation.EXPORT, parameters = JSONObject().put("format", format)); export = false }.onFailure { error = "Could not queue export." } } }
    } }, confirmButton = { TextButton(onClick = { export = false }) { Text("Close") } })
}
