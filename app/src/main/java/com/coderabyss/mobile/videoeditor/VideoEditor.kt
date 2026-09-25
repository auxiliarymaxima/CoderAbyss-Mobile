package com.coderabyss.mobile.videoeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.coderabyss.mobile.platformui.*
import com.coderabyss.mobile.tasks.Operation
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.delay

@Composable
fun VideoEditor(project: JSONObject, vm: WorkspaceViewModel) {
    val id = project.getString("projectId"); val context = LocalContext.current
    val a = project.optJSONArray("timeline") ?: JSONArray()
    val clips = (0 until a.length()).map(a::getJSONObject)
    var selected by remember(id) { mutableStateOf<String?>(null) }; var message by remember { mutableStateOf("") }
    fun modify(change: (MutableList<JSONObject>) -> Unit) { runCatching { VideoTimeline.edit(vm.projects, id, change) }.onFailure { message = "Could not update clip" } }
    Text("Video timeline", style = MaterialTheme.typography.titleLarge)
    WorkflowAttachments(project, vm)
    val assets = listOf(project.optJSONArray("assets"), project.optJSONArray("outputs")).flatMap { list -> if(list == null) emptyList() else (0 until list.length()).map(list::getJSONObject) }.filter { it.optString("mimeType").startsWith("video/") }
    ChoiceMenu("Add clip", "Generated / imported video", assets.map { it.getString("path") }) { path -> runCatching { VideoTimeline.add(vm.projects, id, path) }.onFailure { message = "Video could not be read" } }
    Text("Hold and drag to reorder · swipe to scroll", style = MaterialTheme.typography.bodySmall)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(100.dp)) {
        itemsIndexed(clips, key = { _, c -> c.getString("id") }) { index, clip ->
            var drag by remember { mutableFloatStateOf(0f) }
            OutlinedCard(onClick = { selected = clip.getString("id") }, modifier = Modifier.width(150.dp).fillMaxHeight().pointerInput(clip.getString("id"), index) {
                detectDragGesturesAfterLongPress(onDragStart = { drag = 0f }, onDrag = { change, amount -> change.consume(); drag += amount.x }, onDragEnd = {
                    val to = (index + (drag / 150.dp.toPx()).toInt()).coerceIn(0, clips.lastIndex)
                    if(to != index) modify { list -> list.add(to, list.removeAt(index)) }
                })
            }, border = BorderStroke(1.dp, if(selected == clip.getString("id")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                Text("Clip ${index + 1}", Modifier.padding(8.dp)); Text("${(clip.getLong("endMs") - clip.getLong("startMs")) / 1000.0}s", Modifier.padding(horizontal = 8.dp))
            }
        }
    }
    clips.firstOrNull { it.getString("id") == selected }?.let { clip ->
        key(clip.getString("id")) {
            val index = clips.indexOf(clip); val path = vm.projects.file(id, clip.getString("path")).path
            var range by remember(clip.toString()) { mutableStateOf(clip.getLong("startMs").toFloat()..clip.getLong("endMs").toFloat()) }
            var position by remember { mutableIntStateOf(0) }
            var previewAspect by remember { mutableFloatStateOf(16f / 9f) }
            val player = remember { android.widget.VideoView(context) }
            DisposableEffect(path) { onDispose { player.stopPlayback() } }
            LaunchedEffect(player, range) { while(true) { delay(100); position = player.currentPosition; if(player.isPlaying && position >= range.endInclusive) player.pause() } }
            Box(Modifier.fillMaxWidth().aspectRatio(previewAspect)) {
                AndroidView(factory = { player.apply { setVideoPath(path); setOnPreparedListener { media -> if(media.videoWidth > 0 && media.videoHeight > 0) previewAspect = media.videoWidth.toFloat() / media.videoHeight; seekTo(range.start.toInt()) } } }, modifier = Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    val left = ((clip.optDouble("left", -1.0) + 1) / 2 * size.width).toFloat()
                    val top = ((1 - clip.optDouble("top", 1.0)) / 2 * size.height).toFloat()
                    val width = ((clip.optDouble("right", 1.0) - clip.optDouble("left", -1.0)) / 2 * size.width).toFloat()
                    val height = ((clip.optDouble("top", 1.0) - clip.optDouble("bottom", -1.0)) / 2 * size.height).toFloat()
                    drawRect(Color.Cyan, Offset(left, top), Size(width, height), style = Stroke(3f))
                }
            }
            Text("${range.start.toLong() / 1000.0}s — ${range.endInclusive.toLong() / 1000.0}s · Playhead ${position / 1000.0}s")
            RangeSlider(value = range, onValueChange = { if(it.endInclusive - it.start >= 100) range = it }, valueRange = 0f..clip.getLong("durationMs").toFloat(), onValueChangeFinished = { modify { it[index].put("startMs", range.start.toLong()).put("endMs", range.endInclusive.toLong()) } })
            Row { TextButton(onClick = { player.seekTo(range.start.toInt()); player.start() }) { Text("Preview trim") }
                TextButton(enabled = index > 0, onClick = { modify { it.add(index - 1, it.removeAt(index)) } }) { Text("←") }
                TextButton(enabled = index < clips.lastIndex, onClick = { modify { it.add(index + 1, it.removeAt(index)) } }) { Text("→") }
            }
            var cropX by remember(clip.toString()) { mutableStateOf(clip.optDouble("left", -1.0).toFloat()..clip.optDouble("right", 1.0).toFloat()) }
            var cropY by remember(clip.toString()) { mutableStateOf(clip.optDouble("bottom", -1.0).toFloat()..clip.optDouble("top", 1.0).toFloat()) }
            ChoiceMenu("Frame crop", "Custom / Original", listOf("Original", "16:9", "9:16", "1:1")) { choice ->
                runCatching {
                var x = 1.0; var y = 1.0
                if(choice != "Original") {
                    val reader = android.media.MediaMetadataRetriever()
                    try { reader.setDataSource(path); var w = reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toDouble(); var h = reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toDouble()
                        if((reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0) % 180 != 0) { val t = w; w = h; h = t }
                        val parts = choice.split(':'); val wanted = parts[0].toDouble() / parts[1].toDouble(); val ratio = w / h
                        if(ratio > wanted) x = wanted / ratio else y = ratio / wanted
                    } finally { reader.release() }
                }
                modify { it[index].put("left", -x).put("right", x).put("bottom", -y).put("top", y) }
                }.onFailure { message = "Cannot read the source video for cropping" }
            }
            Text("Free crop · horizontal / vertical")
            RangeSlider(cropX, { if(it.endInclusive - it.start > .05f) cropX = it }, valueRange = -1f..1f, onValueChangeFinished = { modify { it[index].put("left", cropX.start).put("right", cropX.endInclusive) } })
            RangeSlider(cropY, { if(it.endInclusive - it.start > .05f) cropY = it }, valueRange = -1f..1f, onValueChangeFinished = { modify { it[index].put("bottom", cropY.start).put("top", cropY.endInclusive) } })
            var volume by remember(clip.toString()) { mutableFloatStateOf(clip.optDouble("volume", 1.0).toFloat()) }
            Text("Volume ${(volume * 100).toInt()}%")
            Slider(volume, { volume = it }, valueRange = 0f..2f, onValueChangeFinished = { modify { it[index].put("volume", volume) } })
            Row {
                TextButton(onClick = { modify { it[index].put("volume", 0) } }) { Text("Mute") }
                TextButton(onClick = { modify { it[index].put("rotation", (it[index].optInt("rotation") + 90) % 360) } }) { Text("Rotate") }
                TextButton(onClick = { modify { it.add(index + 1, JSONObject(clip.toString()).put("id", UUID.randomUUID().toString())) } }) { Text("Duplicate") }
                TextButton(onClick = { modify { it.removeAt(index) }; selected = null }) { Text("Delete") }
            }
        }
    }
    val export = project.optJSONObject("videoExport") ?: JSONObject()
    ChoiceMenu("Export size", "${export.optInt("height", 720)}p", listOf("480p", "720p", "1080p")) { choice -> vm.projects.update(id) { it.put("videoExport", JSONObject(export.toString()).put("height", choice.removeSuffix("p").toInt())) } }
    ChoiceMenu("Target maximum frame rate", "${export.optInt("frameRate", 30)} fps", listOf("24 fps", "30 fps")) { choice -> vm.projects.update(id) { it.put("videoExport", JSONObject(export.toString()).put("frameRate", choice.substringBefore(' ').toInt())) } }
    Button(enabled = clips.isNotEmpty() && !vm.tasks.activeForProject(id), modifier = Modifier.fillMaxWidth(), onClick = {
        runCatching { vm.tasks.submit(id, Operation.VIDEO_EDIT, parameters = JSONObject(export.toString()).put("clips", JSONArray(a.toString()))); message = "Rendering MP4. Track it in Tasks; preview and save in Results." }.onFailure { message = "Could not queue export" }
    }) { Text("Render preview / Export MP4") }
    Text("Cuts only. Rendering applies trim, crop, rotation and audio. Frame-rate control drops excess frames; it does not add frames to slower sources. Originals stay unchanged.", style = MaterialTheme.typography.bodySmall)
    if(message.isNotBlank()) Text(message)
}
