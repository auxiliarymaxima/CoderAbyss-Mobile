package com.coderabyss.mobile.projects

import android.content.Context
import android.util.AtomicFile
import com.coderabyss.mobile.models.Service
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ProjectLocks { val lock = Any() }

/** Every asset path is relative to a UUID project. Shared assets are copied, never linked destructively. */
class ProjectRepository(context: Context) {
    private val root = File(context.applicationContext.filesDir, "Projects").apply { mkdirs() }
    private val folders = mapOf(Service.APP to "Apps", Service.VIDEO to "Videos", Service.RESEARCH to "Research",
        Service.VISUAL to "Visuals", Service.COMPANION to "Companion")
    fun all(): List<JSONObject> = synchronized(ProjectLocks.lock) {
        folders.flatMap { (service, folder) -> File(root, folder).listFiles()?.filter { it.isDirectory }?.mapNotNull {
            runCatching { readAt(it).apply { if (!has("type")) put("type", service.name) } }.getOrNull()
        } ?: emptyList() }.sortedByDescending { it.optLong("updatedAt", it.optLong("createdAt")) }
    }
    fun directory(id: String): File {
        val uuid = UUID.fromString(id).toString()
        return folders.values.map { File(root, "$it/$uuid") }.firstOrNull { File(it, "project.json").exists() }
            ?: error("Project not found")
    }
    fun file(id: String, relative: String): File {
        require(relative.isNotBlank() && !File(relative).isAbsolute) { "Invalid asset path" }
        val base = directory(id).canonicalFile
        return File(base, relative).canonicalFile.also { require(it.path.startsWith(base.path + File.separator)) { "Asset path escapes its project" } }
    }
    fun read(id: String): JSONObject = synchronized(ProjectLocks.lock) { readAt(directory(id)) }
    private fun readAt(dir: File) = JSONObject(AtomicFile(File(dir, "project.json")).openRead().bufferedReader().use { it.readText() })
    fun update(id: String, change: (JSONObject) -> Unit): JSONObject = synchronized(ProjectLocks.lock) {
        val value = read(id); change(value); value.put("updatedAt", System.currentTimeMillis()); write(directory(id), value); value
    }
    private fun write(dir: File, value: JSONObject) = com.coderabyss.mobile.storage.AtomicJson.write(File(dir, "project.json"), value)
    fun create(service: Service, title: String = "Untitled ${service.name.lowercase()}"): String = synchronized(ProjectLocks.lock) {
        require(service in folders)
        val id = UUID.randomUUID().toString(); val now = System.currentTimeMillis()
        val dir = File(root, "${folders.getValue(service)}/$id")
        val value = JSONObject().put("schemaVersion", 2).put("projectId", id).put("type", service.name)
            .put("title", title).put("createdAt", now).put("updatedAt", now).put("status", "DRAFT")
            .put("prompt", "").put("preferredModel", "").put("outputs", JSONArray()).put("history", JSONArray())
            .put("metadata", JSONObject()).put("sections", JSONArray()).put("sources", JSONArray()).put("notes", "")
        write(dir, value)
        listOf("prompts", "tasks", "outputs", "exports", "assets", "versions", "source").forEach { File(dir, it).mkdirs() }
        id
    }
    fun migrate() = synchronized(ProjectLocks.lock) {
        all().forEach { project ->
            if (project.optInt("schemaVersion") < 2) update(project.getString("projectId")) {
                it.put("schemaVersion", 2).put("type", project.optString("type", "VIDEO"))
                if (!it.has("prompt")) it.put("prompt", it.optJSONObject("request")?.optString("prompt") ?: "")
                if (!it.has("preferredModel")) it.put("preferredModel", it.optJSONObject("request")?.optString("engine") ?: "")
                if (!it.has("outputs")) it.put("outputs", JSONArray())
                if (!it.has("history")) it.put("history", JSONArray())
            }
            val id = project.getString("projectId")
            val legacy = file(id, "generations/$id.mp4")
            if (project.optString("type") == "VIDEO" && legacy.isFile && legacy.length() > 0) attach(id, "generations/$id.mp4", "video/mp4", project.optJSONObject("output") ?: JSONObject())
        }
    }
    fun attach(id: String, relative: String, mime: String, metadata: JSONObject = JSONObject()) {
        val target = file(id, relative); check(target.isFile && target.length() > 0) { "Output is missing or empty" }
        update(id) { value ->
            val outputs = value.optJSONArray("outputs") ?: JSONArray()
            if ((0 until outputs.length()).none { outputs.getJSONObject(it).optString("path") == relative })
                outputs.put(metadata.put("path", relative).put("mimeType", mime).put("size", target.length()).put("createdAt", System.currentTimeMillis()))
            value.put("outputs", outputs)
        }
    }
    fun checkpoint(id: String) {
        val data = read(id).toString()
        val target = file(id, "versions/${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
        target.parentFile?.mkdirs(); target.writeText(data)
        val source = file(id, "source")
        if(source.isDirectory && source.walkTopDown().any { it.isFile }) java.util.zip.ZipOutputStream(File(target.path + ".source.zip").outputStream()).use { zip ->
            source.walkTopDown().filter { it.isFile }.forEach { asset -> zip.putNextEntry(java.util.zip.ZipEntry(asset.relativeTo(source).invariantSeparatorsPath)); asset.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
        }
    }
    fun versions(id: String): List<File> = file(id, "versions").listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.name } ?: emptyList()
    fun restoreDraft(id: String, version: String) {
        require(version.matches(Regex("[0-9]+-[a-f0-9-]+\\.json")))
        val old = JSONObject(file(id, "versions/$version").readText())
        checkpoint(id)
        update(id) { current -> listOf("title", "prompt", "sections", "sources", "metadata", "findings", "preferredModel", "generationSettings").forEach { field -> if(old.has(field)) current.put(field, old.get(field)) } }
        val source = file(id, "versions/$version.source.zip")
        if(source.isFile) java.util.zip.ZipFile(source).use { zip -> zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
            require(!entry.name.contains("..") && !entry.name.startsWith("/"))
            val output = file(id, "source/${entry.name}"); output.parentFile?.mkdirs(); val partial = File(output.path + ".partial")
            zip.getInputStream(entry).use { input -> partial.outputStream().use { input.copyTo(it) } }; check(partial.renameTo(output))
        } }
    }
    fun duplicate(id: String): String = synchronized(ProjectLocks.lock) {
        val source = directory(id); val original = read(id); val copyId = UUID.randomUUID().toString()
        val destination = File(source.parentFile, copyId)
        try {
            check(source.copyRecursively(destination, overwrite = false)) { "Could not duplicate project" }
            original.put("projectId", copyId).put("title", original.optString("title") + " copy")
                .put("parentProjectId", id).put("createdAt", System.currentTimeMillis()).put("updatedAt", System.currentTimeMillis())
                .put("status", "DRAFT").remove("activeTaskId")
            if (original.optString("type") == "VIDEO" && original.has("request")) {
                val video = File(destination, "generations/$id.mp4")
                if (video.isFile) { original.put("status", "COMPLETED") }
                else original.put("status", "UNKNOWN").put("stage", "Copied project has no completed local video")
            }
            write(destination, original); copyId
        } catch (e: Exception) { destination.deleteRecursively(); throw e }
    }
    fun copyAsset(fromId: String, relative: String, toId: String): String {
        val source = file(fromId, relative); check(source.isFile)
        val suffix = source.extension.take(10).filter { it.isLetterOrDigit() }
        val destination = "assets/${UUID.randomUUID()}.$suffix"; val final = file(toId, destination)
        final.parentFile?.mkdirs(); val partial = File(final.path + ".partial")
        source.copyTo(partial, overwrite = false); check(partial.length() == source.length()); check(partial.renameTo(final))
        return destination
    }
    fun delete(id: String) = synchronized(ProjectLocks.lock) {
        val target = directory(id).canonicalFile
        require(target.parentFile?.parentFile == root.canonicalFile && target.name == UUID.fromString(id).toString())
        check(target.deleteRecursively()) { "Could not delete all project files" }
    }
}
