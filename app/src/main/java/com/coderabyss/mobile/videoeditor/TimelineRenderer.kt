package com.coderabyss.mobile.videoeditor

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.C
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.*
import com.coderabyss.mobile.projects.ProjectRepository
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object TimelineRenderer {
    suspend fun render(context: Context, projects: ProjectRepository, projectId: String, clips: JSONArray, relative: String, height: Int = 720, frameRate: Int = 30, progress: (Int) -> Unit) = withContext(Dispatchers.Main) {
        require(height in setOf(480, 720, 1080) && frameRate in setOf(24, 30))
        require(clips.length() > 0) { "Add clips first" }
        val items = (0 until clips.length()).map { n ->
            val clip = clips.getJSONObject(n); VideoTimeline.validate(clip)
            val source = projects.file(projectId, clip.getString("path")); check(source.isFile)
            val media = MediaItem.Builder().setUri(android.net.Uri.fromFile(source)).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.getLong("startMs")).setEndPositionMs(clip.getLong("endMs")).build()).build()
            EditedMediaItem.Builder(media).setEffects(Effects(listOf(VolumeProcessor(clip.optDouble("volume", 1.0).toFloat())), listOf(
                Crop(clip.optDouble("left", -1.0).toFloat(), clip.optDouble("right", 1.0).toFloat(), clip.optDouble("bottom", -1.0).toFloat(), clip.optDouble("top", 1.0).toFloat()),
                ScaleAndRotateTransformation.Builder().setRotationDegrees(clip.optDouble("rotation", 0.0).toFloat()).build(),
                androidx.media3.effect.Presentation.createForHeight(height),
                androidx.media3.effect.FrameDropEffect.createDefaultFrameDropEffect(frameRate.toFloat())
            ))).build()
        }
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(items).build()).experimentalSetForceAudioTrack(true).build()
        val final = projects.file(projectId, relative); final.parentFile?.mkdirs()
        val partial = File(final.path + ".partial"); partial.delete()
        var transformer: Transformer? = null
        val monitor = launch { val holder = ProgressHolder(); while(isActive) { delay(500); transformer?.let { if(it.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progress(holder.progress) } } }
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val renderer = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) { if(continuation.isActive) continuation.resume(Unit) }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { if(continuation.isActive) continuation.resumeWithException(exportException) }
                    }).build()
                transformer = renderer
                continuation.invokeOnCancellation { android.os.Handler(android.os.Looper.getMainLooper()).post { renderer.cancel() } }
                try { renderer.start(composition, partial.path) } catch(e: Exception) { if(continuation.isActive) continuation.resumeWithException(e) }
            }
            withContext(Dispatchers.IO) {
                check(partial.length() > 0)
                val meta = MediaMetadataRetriever()
                try { meta.setDataSource(partial.path); check((meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0); check(meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"); val frame = meta.getFrameAtTime(0) ?: error("Rendered video has no decodable frame"); frame.recycle() } finally { meta.release() }
                check(partial.renameTo(final))
            }
        } finally { monitor.cancel(); transformer?.cancel(); partial.delete() }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class VolumeProcessor(private val volume: Float) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if(inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val output = replaceOutputBuffer(inputBuffer.remaining())
        while(inputBuffer.remaining() >= 2) output.putShort((inputBuffer.short * volume).toInt().coerceIn(-32768, 32767).toShort())
        output.flip()
    }
}
