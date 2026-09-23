package com.coderabyss.mobile

import android.content.Context
import android.util.AtomicFile
import androidx.work.*
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Project/task records are durable. Compose only reads them and requests actions. */
class VideoTasks(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "Projects/Videos").apply { mkdirs() }

    fun directory(id: String): File = File(root, UUID.fromString(id).toString())
    fun output(id: String) = File(directory(id), "generations/$id.mp4")

    fun all(): List<JSONObject> = synchronized(lock) {
        root.listFiles()?.filter { it.isDirectory }?.mapNotNull {
            runCatching { read(it.name) }.getOrNull()
        }?.filter { it.has("request") }?.sortedByDescending { it.optLong("createdAt") } ?: emptyList()
    }

    fun read(id: String): JSONObject = synchronized(lock) {
        JSONObject(AtomicFile(File(directory(id), "project.json")).openRead().bufferedReader().use { it.readText() })
    }

    fun update(id: String, change: (JSONObject) -> Unit): JSONObject = synchronized(lock) {
        val value = read(id)
        val previousStage = value.optString("stage")
        change(value)
        value.put("updatedAt", System.currentTimeMillis())
        if (value.optString("stage") != previousStage) {
            val history = value.optJSONArray("stages") ?: org.json.JSONArray()
            history.put(JSONObject().put("stage", value.optString("stage")).put("at", System.currentTimeMillis()))
            while (history.length() > 24) history.remove(0)
            value.put("stages", history)
        }
        write(id, value)
        value
    }

    private fun write(id: String, value: JSONObject) {
        val folder = directory(id).apply { mkdirs() }
        val file = AtomicFile(File(folder, "project.json"))
        val stream = file.startWrite()
        try { stream.write(value.toString().toByteArray()); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }

    fun create(prompt: String, duration: Int, resolution: String, engine: String = "wan", local: Boolean = false, parentProjectId: String? = null): String {
        require(!local) { "Video generation is remote only." }
        if (engine == "wan") VideoPromptRules.validateWan(prompt)
        val id = UUID.randomUUID().toString()
        val ratio = when (resolution) { "256x256" -> "1:1"; "384x256" -> "3:2"; "512x288" -> "16:9"; "480x272" -> "30:17"; "320x192" -> "5:3"; "192x320" -> "3:5"; else -> "17:30" }
        val request = JSONObject().put("clientRequestId", id).put("engine", engine).put("prompt", prompt)
            .put("duration", duration).put("resolution", resolution).put("aspectRatio", ratio)
        val value = JSONObject().put("projectId", id).put("title", "Video ${all().size + 1}")
            .put("type", "VIDEO").put("schemaVersion", 2).put("preferredModel", engine)
            .put("backend", if (local) "local-wan" else VideoBackendSettings.SPACE).put("request", request)
            .put("createdAt", System.currentTimeMillis()).put("updatedAt", System.currentTimeMillis())
            .put("status", "READY_TO_SUBMIT").put("stage", "Prompt saved").put("jobId", "")
        parentProjectId?.let { value.put("parentProjectId", UUID.fromString(it).toString()) }
        synchronized(lock) { write(id, value) }
        listOf("prompts", "jobs", "generations", "renders", "exports").forEach { File(directory(id), it).mkdirs() }
        File(directory(id), "prompts/original.txt").writeText(prompt)
        enqueue(id)
        return id
    }

    fun enqueue(id: String) {
        val work = OneTimeWorkRequestBuilder<VideoTaskWorker>().setInputData(workDataOf("projectId" to id))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(app).enqueueUniqueWork("video-$id", ExistingWorkPolicy.KEEP, work)
    }

    fun recover() {
        all().filter { it.optString("status") !in finished || it.optString("status") == "SUBMISSION_UNCERTAIN" }.forEach { enqueue(it.getString("projectId")) }
    }

    companion object {
        private val lock = com.coderabyss.mobile.projects.ProjectLocks.lock
        val finished = setOf("COMPLETED", "FAILED", "CANCELLED", "UNKNOWN", "DOWNLOAD_FAILED", "SUBMISSION_UNCERTAIN")
    }
}

/** Legacy jobs have no verified gateway owner. Preserve their IDs rather than claim them. */
class VideoTaskWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("projectId") ?: return Result.failure()
        val tasks = VideoTasks(applicationContext)
        val task = tasks.read(id)
        if(task.optString("status") !in VideoTasks.finished) tasks.update(id) {
            it.put("status", "UNKNOWN").put("stage", "Legacy provider job preserved. Server-side ownership migration is required; no duplicate generation was submitted.")
        }
        return Result.success()
    }
}
