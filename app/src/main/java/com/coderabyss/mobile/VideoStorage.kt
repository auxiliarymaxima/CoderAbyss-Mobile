package com.coderabyss.mobile

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoStorage {
    suspend fun save(context: Context, preview: File): Uri = withContext(Dispatchers.IO) {
        check(preview.isFile && preview.length() > 0) { "Preview file is missing. Generate a video first." }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "coder_abyss_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CoderAbyss")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create saved video")
        try {
            resolver.openOutputStream(uri)?.use { output -> preview.inputStream().use { it.copyTo(output) } }
                ?: error("Could not open saved video")
            values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
    }
}
