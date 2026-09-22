package com.coderabyss.mobile

import android.content.Context
import com.coderabyss.wan.WanNative
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

class LocalWanEngine(private val context: Context) {
    suspend fun generate(manager: OfflineModelManager, prompt: String, width: Int, height: Int,
                         frames: Int, steps: Int, onStatus: (String) -> Unit): File =
        LocalInferenceGate.mutex.withLock {
            check(manager.isInstalled(ModelCatalog.wan)) { "Download and verify the complete Wan package in Model Manager first." }
            require(prompt.isNotBlank()) { "Enter a prompt first." }
            val dir = File(context.cacheDir, "video-previews").apply { mkdirs() }
            val raw = File.createTempFile("wan-", ".rgb", dir)
            val mp4 = File.createTempFile("wan-", ".mp4", dir)
            try {
                try { WanNative.reset() } catch (e: LinkageError) {
                    throw IllegalStateException("The local Wan runtime could not load on this device: ${e.message}", e)
                }
                coroutineScope {
                    val render = async(Dispatchers.IO) {
                        WanNative.generate(manager.modelDirectory.absolutePath, prompt, raw.absolutePath, width, height, frames, steps)
                    }
                    val progress = launch {
                        while (render.isActive) { onStatus(WanNative.status()); delay(500) }
                    }
                    try {
                        val count = render.await()
                        ensureActive()
                        onStatus("Encoding MP4 preview...")
                        withContext(Dispatchers.IO) { Mp4Encoder.encode(raw, mp4, width, height, count, 16) }
                    } finally {
                        progress.cancel()
                        WanNative.cancel()
                        withContext(NonCancellable) { render.join() }
                    }
                }
                mp4
            } catch (e: Throwable) {
                mp4.delete()
                throw e
            } finally { raw.delete() }
        }
}
