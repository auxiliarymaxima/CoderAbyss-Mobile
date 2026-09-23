package com.coderabyss.mobile.research

import android.graphics.*
import com.coderabyss.mobile.projects.ProjectRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/** Charts only explicit numeric project findings; no generated or inferred data. */
object ResearchChart {
    fun create(repo: ProjectRepository, projectId: String, taskId: String): String {
        val findings = repo.read(projectId).optJSONArray("findings") ?: JSONArray()
        val rows = (0 until findings.length()).map(findings::getJSONObject).mapNotNull { row -> row.optString("value").toDoubleOrNull()?.takeIf { it.isFinite() }?.let { row to it } }.take(12)
        require(rows.isNotEmpty()) { "Add numeric findings first" }
        require(rows.map { it.first.optString("unit") }.distinct().size <= 1) { "Chart rows must use the same unit" }
        val bitmap = Bitmap.createBitmap(1200, 180 + rows.size * 90, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 24f }
        val bar = Paint().apply { color = Color.rgb(0, 115, 150) }
        val maximum = rows.maxOf { abs(it.second) }.coerceAtLeast(1e-12)
        canvas.drawText("Project findings (${rows.first().first.optString("unit")})", 36f, 48f, label)
        rows.forEachIndexed { i, (row, value) ->
            val y = 95f + i * 90; canvas.drawText(row.optString("finding").take(34), 36f, y, label)
            val zero = 720f; val width = (value / maximum * 320).toFloat()
            canvas.drawRect(minOf(zero, zero + width), y - 25, maxOf(zero, zero + width), y + 8, bar)
            canvas.drawText(row.optString("value"), 1070f, y, label)
        }
        canvas.drawText("Data entered in this project's Findings table; review source notes.", 36f, bitmap.height - 28f, label)
        val path = "assets/$taskId.png"; val final = repo.file(projectId, path); final.parentFile?.mkdirs(); val partial = File(final.path + ".partial")
        try { partial.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)); it.fd.sync() }; check(partial.length() > 0); check(partial.renameTo(final)) }
        finally { bitmap.recycle() }
        repo.update(projectId) { p -> val a = p.optJSONArray("assets") ?: JSONArray(); a.put(JSONObject().put("path", path).put("mimeType", "image/png").put("caption", "Chart of saved project findings")); p.put("assets", a) }
        repo.attach(projectId, path, "image/png", JSONObject().put("displayName", "Findings chart"))
        return path
    }
}
