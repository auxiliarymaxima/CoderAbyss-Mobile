package com.coderabyss.mobile.exports

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.coderabyss.mobile.projects.ProjectRepository
import com.coderabyss.mobile.research.CitationFormatter
import org.json.JSONObject
import java.io.File

object DocumentExporter {
    val formats = mapOf("docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "pdf" to "application/pdf", "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    fun content(project: JSONObject): DocumentContent {
        val sections = project.optJSONArray("sections") ?: org.json.JSONArray()
        val sources = project.optJSONArray("sources") ?: org.json.JSONArray()
        val metadata = project.optJSONObject("metadata") ?: JSONObject()
        val body = (0 until sections.length()).map { sections.getJSONObject(it).let { DocumentSection(it.optString("title"), it.optString("text")) } }.toMutableList()
        if(metadata.optString("abstract").isNotBlank()) body.add(0, DocumentSection("Abstract", metadata.optString("abstract")))
        if(metadata.optString("keywords").isNotBlank()) body.add(0, DocumentSection("Keywords", metadata.optString("keywords")))
        if (sources.length() > 0) body.add(DocumentSection("References", (0 until sources.length()).joinToString("\n") { CitationFormatter.reference(sources.getJSONObject(it), metadata.optString("citationStyle", "APA"), it + 1) }))
        val findings = project.optJSONArray("findings") ?: org.json.JSONArray()
        return DocumentContent(project.optString("title"), metadata.optString("author"), body,
            (0 until sources.length()).map { i -> sources.getJSONObject(i).let { source -> listOf("title", "author", "publisher", "date", "url", "doi", "accessed", "notes").map(source::optString) } },
            (0 until findings.length()).map { i -> findings.getJSONObject(i).let { row -> listOf("finding", "value", "unit", "source").map(row::optString) } })
    }
    fun export(repository: ProjectRepository, projectId: String, taskId: String, format: String): String {
        require(format in formats)
        val project = repository.read(projectId)
        val images = mutableListOf<DocumentImage>()
        if(format != "xlsx") {
            val assets = project.optJSONArray("assets") ?: org.json.JSONArray()
            for(n in 0 until assets.length().coerceAtMost(30)) {
                val asset = assets.getJSONObject(n)
                if(asset.optString("mimeType").startsWith("image/")) {
                    val file = repository.file(projectId, asset.getString("path"))
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }; android.graphics.BitmapFactory.decodeFile(file.path, bounds)
                    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 2048).coerceAtLeast(1) }
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.path, options) ?: continue
                    try { val stream = java.io.ByteArrayOutputStream(); check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)); images.add(DocumentImage(stream.toByteArray(), bitmap.width, bitmap.height, asset.optString("caption", file.name))) }
                    finally { bitmap.recycle() }
                }
            }
        }
        val doc = content(project); val relative = "exports/$taskId.$format"
        val final = repository.file(projectId, relative); final.parentFile?.mkdirs(); val partial = File(final.path + ".partial")
        when (format) { "docx" -> OfficeDocuments.docx(partial, doc); "xlsx" -> OfficeDocuments.xlsx(partial, doc); "pptx" -> OfficeDocuments.pptx(partial, doc); "pdf" -> pdf(partial, doc, images) }
        DocumentAssets.embed(partial, format, images)
        check(partial.length() > 0)
        check(partial.renameTo(final)) { "Could not finish export" }
        repository.attach(projectId, relative, formats.getValue(format)); return relative
    }
    private fun pdf(file: File, doc: DocumentContent, images: List<DocumentImage>) {
        val pdf = PdfDocument(); val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        val heading = Paint(body).apply { textSize = 17f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        var number = 0; var page: PdfDocument.Page? = null; var y = 54f
        fun nextPage() {
            page?.let { it.canvas.drawText("${number}", 540f, 806f, body); pdf.finishPage(it) }
            number++; page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create()); y = 54f
        }
        fun line(value: String, paint: Paint) {
            var remaining = value
            if (remaining.isEmpty()) { y += 12; return }
            while (remaining.isNotEmpty()) {
                if (y > 775) nextPage()
                val count = paint.breakText(remaining, true, 487f, null).coerceAtLeast(1)
                val split = if (count < remaining.length) remaining.lastIndexOf(' ', count).takeIf { it > 0 } ?: count else count
                page!!.canvas.drawText(remaining.take(split), 54f, y, paint); y += paint.textSize * 1.5f
                remaining = remaining.drop(split).trimStart()
            }
        }
        try {
            nextPage(); line(doc.title, heading); line(doc.author, body); y += 12
            doc.sections.forEach { section -> if (y > 730) nextPage(); line(section.title, heading); section.text.split('\n').forEach { line(it, body) }; y += 12 }
            images.forEach { image ->
                nextPage(); line(image.caption, body)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(image.png, 0, image.png.size)
                try { val scale = minOf(487f / image.width, 650f / image.height); page!!.canvas.drawBitmap(bitmap, null, android.graphics.RectF(54f, y + 10, 54f + image.width * scale, y + 10 + image.height * scale), body) } finally { bitmap.recycle() }
            }
            page?.let { it.canvas.drawText("$number", 540f, 806f, body); pdf.finishPage(it) }; page = null
            file.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
    }
}
