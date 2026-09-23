package com.coderabyss.mobile.exports

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import java.io.File

object MediaExporter {
    fun save(context: Context, file: File, mime: String, title: String, format: String = "original"): String {
        check(file.isFile && file.length() > 0)
        val image = mime.startsWith("image/"); val video = mime.startsWith("video/")
        require(format in setOf("original", "png", "jpeg", "webp"))
        val extension = if(image && format != "original") format else file.extension
        val outputMime = if(image && format != "original") "image/$format" else mime
        val collection = if(image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else if(video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val safe = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "_").take(80).ifBlank { "CoderAbyss" }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${safe}_${System.currentTimeMillis()}.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, outputMime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if(image) "Pictures/CoderAbyss" else if(video) "Movies/CoderAbyss" else "Download/CoderAbyss")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver; val uri = resolver.insert(collection, values) ?: error("Could not create export")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                if(image && format != "original") {
                    val bitmap = BitmapFactory.decodeFile(file.path) ?: error("Invalid image")
                    try { check(bitmap.compress(when(format) { "jpeg" -> Bitmap.CompressFormat.JPEG; "webp" -> Bitmap.CompressFormat.WEBP_LOSSLESS; else -> Bitmap.CompressFormat.PNG }, 100, output)) }
                    finally { bitmap.recycle() }
                } else file.inputStream().use { it.copyTo(output) }
            } ?: error("Could not open export")
            resolver.openAssetFileDescriptor(uri, "r")?.use { check(it.length > 0) } ?: error("Export not readable")
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0); check(resolver.update(uri, values, null, null) > 0)
            return uri.toString()
        } catch(e: Exception) { resolver.delete(uri, null, null); throw e }
    }
}
