package com.coderabyss.mobile.platformui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.coderabyss.mobile.tasks.Operation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Composable
fun LocalImage(file: File, modifier: Modifier = Modifier, zoom: Boolean = false) {
    var bitmap by remember(file.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file.path) { bitmap = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
        val sample = (maxOf(bounds.outWidth, bounds.outHeight) / 2048).coerceAtLeast(1)
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } }
    var scale by remember(file.path) { mutableFloatStateOf(1f) }; var x by remember(file.path) { mutableFloatStateOf(0f) }; var y by remember(file.path) { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { factor, offset, _ -> scale = (scale * factor).coerceIn(1f, 5f); x += offset.x; y += offset.y }
    bitmap?.let { Image(it.asImageBitmap(), "Generated image", modifier.then(if(zoom) Modifier.graphicsLayer(scaleX = scale, scaleY = scale, translationX = x, translationY = y).transformable(transform) else Modifier)) }
}

@Composable
fun OutputGallery(project: JSONObject, vm: WorkspaceViewModel, designed: Boolean = false, onVary: () -> Unit = {}) {
    if(designed) { WorkflowResults(project, vm, onVary); return }
    val id = project.getString("projectId"); val context = LocalContext.current
    val array = project.optJSONArray("outputs") ?: JSONArray(); val outputs = (0 until array.length()).map(array::getJSONObject)
    val images = outputs.filter { it.optString("mimeType").startsWith("image/") && vm.projects.file(id, it.getString("path")).isFile }
    var fullscreen by remember { mutableStateOf<Int?>(null) }; var rename by remember { mutableStateOf<JSONObject?>(null) }; var deletion by remember { mutableStateOf<JSONObject?>(null) }
    var name by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }
    Text("Outputs and history", style = MaterialTheme.typography.titleLarge)
    if(outputs.isEmpty()) Text("Completed results will appear here. Each generation is kept separately.")
    outputs.asReversed().forEach { output -> val path = output.getString("path"); val file = vm.projects.file(id, path); val mime = output.getString("mimeType")
        key(path) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(output.optString("displayName", file.name), style = MaterialTheme.typography.titleMedium)
            if(!file.isFile) Text("Local file is missing. Use Retry Download on its original task if available.")
            else if(mime.startsWith("image/")) {
                LocalImage(file, Modifier.fillMaxWidth().height(240.dp))
                TextButton(onClick = { fullscreen = images.indexOf(output) }) { Text("Full screen / Zoom") }
                ChoiceMenu("Save image", "Original quality", listOf("Original", "PNG", "JPEG", "WEBP")) { choice -> vm.tasks.submit(id, Operation.MEDIA_EXPORT, parameters = JSONObject(output.toString()).put("format", if(choice == "Original") "original" else choice.lowercase()).put("title", project.optString("title"))) }
            } else if(mime.startsWith("video/")) {
                val player = remember(path) { android.widget.VideoView(context) }
                DisposableEffect(path) { onDispose { player.stopPlayback() } }
                AndroidView(factory = { player.apply { setMediaController(android.widget.MediaController(context).also { it.setAnchorView(this) }); setVideoPath(file.path) } }, modifier = Modifier.fillMaxWidth().height(240.dp))
                TextButton(onClick = { runCatching { com.coderabyss.mobile.videoeditor.VideoTimeline.add(vm.projects, id, path) }.onSuccess { message = "Added to Video. Open Edit to arrange clips." }.onFailure { message = "Could not add video" } }) { Text("Add to Video") }
                Row { TextButton(onClick = { player.start() }) { Text("Play") }; TextButton(onClick = { player.seekTo(0); player.start() }) { Text("Replay") } }
            }
            Text("${output.optString("model")} · ${output.optString("resolution")} · ${file.length()} bytes${if(output.has("duration")) " · ${output.optDouble("duration")} s" else ""}")
            if(output.optString("prompt").isNotBlank()) Text(output.optString("prompt"))
            Row {
                TextButton(enabled = file.isFile, onClick = { runCatching {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }.onFailure { message = "No installed app can open this file. Use Share or Save." } }) { Text("Open") }
                TextButton(enabled = file.isFile, onClick = { val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file); runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share output")) } }) { Text("Share") }
                TextButton(enabled = file.isFile, onClick = { vm.tasks.submit(id, Operation.MEDIA_EXPORT, parameters = JSONObject(output.toString()).put("title", output.optString("displayName", project.optString("title")))) }) { Text("Save") }
            }
            Row {
                TextButton(onClick = { rename = output; name = output.optString("displayName", file.name) }) { Text("Rename") }
                TextButton(enabled = (project.optJSONArray("timeline") ?: JSONArray()).let { clips -> (0 until clips.length()).none { clips.getJSONObject(it).optString("path") == path } }, onClick = { deletion = output }) { Text("Delete") }
                if(output.optString("prompt").isNotBlank()) TextButton(onClick = { vm.projects.update(id) { it.put("prompt", output.optString("prompt")).put("preferredModel", output.optString("model", it.optString("preferredModel"))); output.optJSONObject("settings")?.let { settings -> it.put("generationSettings", JSONObject(settings.toString())) } }; message = "Original prompt and settings restored above. Edit for a variation, then Generate." }) { Text("Regenerate / Vary") }
            }
        } } }
    }
    Text(message)
    fullscreen?.let { initial -> if(images.isNotEmpty()) Dialog(onDismissRequest = { fullscreen = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { Column { TextButton(onClick = { fullscreen = null }) { Text("Close · swipe between images · pinch to zoom") }
            val pager = rememberPagerState(initialPage = initial.coerceIn(0, images.lastIndex), pageCount = { images.size })
            HorizontalPager(pager, Modifier.fillMaxSize()) { page -> LocalImage(vm.projects.file(id, images[page].getString("path")), Modifier.fillMaxSize(), true) }
        } }
    } }
    rename?.let { target -> AlertDialog(onDismissRequest = { rename = null }, title = { Text("Rename output") }, text = { OutlinedTextField(name, { name = it.take(120) }) }, confirmButton = { TextButton(onClick = { vm.projects.update(id) { p -> val a = p.getJSONArray("outputs"); for(n in 0 until a.length()) if(a.getJSONObject(n).optString("path") == target.getString("path")) a.getJSONObject(n).put("displayName", name) }; rename = null }) { Text("Save") } }) }
    deletion?.let { target -> AlertDialog(onDismissRequest = { deletion = null }, title = { Text("Delete local output?") }, text = { Text("The prompt and task history stay saved. Managed copies in other projects are unaffected.") }, confirmButton = { TextButton(onClick = {
        val file = vm.projects.file(id, target.getString("path")); if(!file.exists() || file.delete()) vm.projects.update(id) { p -> val a = p.getJSONArray("outputs"); for(n in a.length() - 1 downTo 0) if(a.getJSONObject(n).optString("path") == target.getString("path")) a.remove(n) }; deletion = null
    }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deletion = null }) { Text("Keep") } }) }
}

@Composable
fun ManagedAssets(project: JSONObject, vm: WorkspaceViewModel) {
    val id = project.getString("projectId"); var open by remember { mutableStateOf(false) }
    Text("Project assets", style = MaterialTheme.typography.titleMedium)
    Text("Imported assets are independent copies. Deleting the original project does not remove them.")
    val assets = project.optJSONArray("assets") ?: JSONArray()
    for(n in 0 until assets.length()) { val asset = assets.getJSONObject(n); val file = vm.projects.file(id, asset.getString("path"))
        Text(file.name); if(asset.optString("mimeType").startsWith("image/") && file.isFile) LocalImage(file, Modifier.fillMaxWidth().height(150.dp))
    }
    TextButton(onClick = { open = true }) { Text("Import from another project") }
    if(open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Copy a project output") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        vm.projects.all().filter { it.getString("projectId") != id }.forEach { source -> val outputs = source.optJSONArray("outputs") ?: JSONArray()
            for(n in 0 until outputs.length()) { val output = outputs.getJSONObject(n)
                TextButton(onClick = { vm.tasks.submit(id, Operation.COPY_ASSET, parameters = JSONObject(output.toString()).put("fromProject", source.getString("projectId"))); open = false }) { Text("${source.optString("title")} · ${output.optString("displayName", output.optString("path"))}") }
            }
        }
    } }, confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } })
}
