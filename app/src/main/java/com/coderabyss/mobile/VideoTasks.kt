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

class VideoTaskWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("projectId") ?: return Result.failure()
        val tasks = VideoTasks(applicationContext)
        val settings = VideoBackendSettings(applicationContext)
        var task = tasks.read(id)
        val client = HuggingFaceVideoClient(applicationContext, task.optString("backend").takeIf { it.contains("/") } ?: VideoBackendSettings.SPACE)
        if (task.optString("status") in setOf("COMPLETED", "CANCELLED", "FAILED", "UNKNOWN")) return Result.success()
        if (task.optBoolean("cancelRequested") && !task.optBoolean("submissionAttempted") && task.optString("jobId").isBlank()) {
            tasks.update(id) { it.put("status", "CANCELLED").put("stage", "Cancelled before submission") }
            return Result.success()
        }
        if (task.optString("backend") == "local-wan") {
            tasks.update(id) { it.put("status", "FAILED").put("stage", "Local video generation is no longer supported. Prompt and existing files were preserved; choose Cloud GPU.") }
            return Result.success()
        }
        if (settings.localOnly) {
            tasks.update(id) { it.put("status", "LOCAL_ONLY").put("stage", "Unavailable while Local Only is enabled.") }
            return Result.retry()
        }
        try {
            var jobId = task.optString("jobId")
            if (task.optBoolean("retryRequested") && jobId.isNotBlank()) {
                // Check first: an interrupted retry response must not restart a running job.
                val current = client.getVideoJobStatus(jobId)
                tasks.update(id) { it.put("retryRequested", false).put("status", "RECOVERING") }
                val resumed = if (current.optString("status") == "FAILED") client.retryVideoJob(jobId) else current
                tasks.update(id) { it.put("retryRequested", false).put("remote", resumed).put("status", "RECOVERING") }
            }
            if (jobId.isBlank()) {
                // Record intent BEFORE sending. Process death cannot silently create a second job.
                if (task.optBoolean("submissionAttempted")) {
                    val found = client.findSubmittedJob(task.getJSONObject("request").getString("clientRequestId"))
                    if (found.optString("status") == "UNKNOWN") {
                        tasks.update(id) { it.put("status", "SUBMISSION_UNCERTAIN").put("stage", "Backend cannot find this submission. No new generation was started.") }
                        return Result.success()
                    }
                    jobId = found.getString("jobId")
                    tasks.update(id) { it.put("jobId", jobId).put("remote", found).put("status", "RECOVERING") }
                } else {
                    tasks.update(id) { it.put("submissionAttempted", true).put("status", "SUBMITTING").put("stage", "Submitting") }
                    val submitted = client.submitVideoJob(task.getJSONObject("request"))
                    jobId = submitted.getString("jobId")
                    tasks.update(id) { it.put("jobId", jobId).put("remote", submitted).put("status", "RECOVERING") }
                }
            }
            repeat(60) {
                if (settings.localOnly) return Result.retry()
                task = tasks.read(id)
                if (task.optBoolean("cancelRequested")) {
                    val cancelled = client.cancelVideoJob(jobId)
                    if (cancelled.getString("status") != "COMPLETED") {
                        tasks.update(id) { it.put("remote", cancelled).put("status", cancelled.getString("status")).put("stage", cancelled.optString("stage")) }
                        return Result.success()
                    }
                    tasks.update(id) { it.put("cancelRequested", false) }
                }
                val remote = client.getVideoJobStatus(jobId)
                val status = remote.getString("status")
                tasks.update(id) {
                    it.put("remote", remote).put("status", if (status == "COMPLETED") "RESULT_READY" else status).put("stage", remote.optString("stage"))
                    if (status == "COMPLETED" && !it.has("generationCompletedAt")) {
                        val elapsed = ((remote.optDouble("updatedAt") - remote.optDouble("createdAt")) * 1000).toLong().coerceAtLeast(0)
                        it.put("generationElapsedMs", elapsed).put("generationCompletedAt", it.getLong("createdAt") + elapsed)
                    }
                }
                if (status in setOf("FAILED", "CANCELLED", "UNKNOWN")) return Result.success()
                if (status == "COMPLETED") {
                    tasks.update(id) { it.put("status", "DOWNLOADING").put("stage", "Downloading").put("downloadStartedAt", System.currentTimeMillis()) }
                    try {
                        val metadata = client.downloadVideoResult(jobId, tasks.output(id)) { bytes, total ->
                            tasks.update(id) { it.put("downloadedBytes", bytes).put("totalBytes", total) }
                        }
                        tasks.update(id) { it.put("status", "COMPLETED").put("stage", "Complete").put("output", metadata).put("completedAt", System.currentTimeMillis()) }
                        com.coderabyss.mobile.projects.ProjectRepository(applicationContext).attach(id, "generations/$id.mp4", "video/mp4", metadata)
                    } catch (error: Exception) {
                        if (settings.localOnly) {
                            tasks.update(id) { it.put("status", "LOCAL_ONLY").put("stage", "Unavailable while Local Only is enabled.") }
                            return Result.retry()
                        }
                        tasks.update(id) { it.put("status", "DOWNLOAD_FAILED").put("stage", "Download failed. Retry Download.") }
                        if (error is kotlinx.coroutines.CancellationException) throw error
                    }
                    return Result.success()
                }
                delay(5_000)
            }
            return Result.retry()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            task = tasks.read(id)
            tasks.update(id) {
                if (task.optString("jobId").isBlank()) it.put("status", "SUBMISSION_UNCERTAIN").put("stage", "Submission interrupted. Check backend before retrying.")
                else it.put("status", "WAITING_FOR_CONNECTION").put("stage", "Waiting for connection or backend authentication. The same job will be checked.")
            }
            return if (task.optString("jobId").isBlank()) Result.success() else Result.retry()
        }
    }

    private suspend fun runLocal(id: String, tasks: VideoTasks, task: JSONObject): Result {
        if (task.optBoolean("cancelRequested")) {
            tasks.update(id) { it.put("status", "CANCELLED").put("stage", "Cancelled") }
            return Result.success()
        }
        if (task.optBoolean("localStarted")) {
            tasks.update(id) { it.put("status", "FAILED").put("stage", "Local generation was interrupted. Prompt retained; start a new generation to retry.") }
            return Result.success()
        }
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.createNotificationChannel(android.app.NotificationChannel("video-tasks", "Video tasks", android.app.NotificationManager.IMPORTANCE_LOW))
        val intent = android.app.PendingIntent.getActivity(applicationContext, 0,
            android.content.Intent(applicationContext, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, "video-tasks")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Coder Abyss")
            .setContentText("Generating video on this phone").setContentIntent(intent).setOngoing(true).build()
        setForeground(ForegroundInfo(id.hashCode(), notification, if (android.os.Build.VERSION.SDK_INT >= 35)
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        tasks.update(id) { it.put("localStarted", true).put("status", "GENERATING").put("stage", "Preparing local Wan") }
        return try {
            val request = task.getJSONObject("request")
            val size = request.getString("resolution").split("x").map(String::toInt)
            val source = LocalWanEngine(applicationContext).generate(OfflineModelManager(applicationContext), request.getString("prompt"),
                size[0], size[1], request.getInt("duration") * 16 + 1, 20) { stage ->
                if (tasks.read(id).optBoolean("cancelRequested")) throw kotlinx.coroutines.CancellationException("User cancelled local generation")
                tasks.update(id) { it.put("stage", stage) }
            }
            val target = tasks.output(id)
            val partial = File(target.parentFile, target.name + ".partial")
            source.copyTo(partial, overwrite = true)
            val retriever = android.media.MediaMetadataRetriever()
            val duration = try {
                retriever.setDataSource(partial.absolutePath)
                (retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000.0
            } finally { retriever.release() }
            check(duration > 0 && partial.length() > 0) { "Local video output is invalid" }
            check(partial.renameTo(target)) { "Could not save video" }
            source.delete()
            tasks.update(id) { it.put("status", "COMPLETED").put("stage", "Complete").put("generationCompletedAt", System.currentTimeMillis())
                .put("output", JSONObject().put("duration", duration).put("resolution", request.getString("resolution")).put("fileSize", target.length()).put("mimeType", "video/mp4")) }
            Result.success()
        } catch (error: Exception) {
            val cancelled = tasks.read(id).optBoolean("cancelRequested")
            tasks.update(id) { it.put("status", if (cancelled) "CANCELLED" else "FAILED").put("stage", if (cancelled) "Cancelled" else "Local generation failed or was interrupted. Prompt retained.") }
            if (error is kotlinx.coroutines.CancellationException && !cancelled) throw error
            Result.success()
        }
    }
}
