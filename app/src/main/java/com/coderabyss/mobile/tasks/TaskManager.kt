package com.coderabyss.mobile.tasks

import android.content.Context
import android.util.AtomicFile
import androidx.work.*
import com.coderabyss.mobile.*
import com.coderabyss.mobile.models.*
import com.coderabyss.mobile.projects.ProjectRepository
import com.coderabyss.mobile.remote.SpaceRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class Operation { TEXT, IMAGE, VIDEO, EXPORT, WEB_SEARCH, TRANSCRIBE, SOURCE_PACKAGE, MEDIA_EXPORT, COPY_ASSET, CHART, IMPORT_ASSET, VIDEO_EDIT }

class PersistentTaskStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "Tasks").apply { mkdirs() }
    private fun file(id: String) = AtomicFile(File(root, "${UUID.fromString(id)}.json"))
    fun read(id: String): JSONObject = synchronized(lock) { JSONObject(file(id).openRead().bufferedReader().use { it.readText() }) }
    fun all(): List<JSONObject> = synchronized(lock) { root.listFiles()?.filter { it.extension == "json" }?.mapNotNull {
        runCatching { read(it.nameWithoutExtension) }.getOrNull()
    }?.sortedByDescending { it.optLong("createdAt") } ?: emptyList() }
    fun write(value: JSONObject) = synchronized(lock) {
        com.coderabyss.mobile.storage.AtomicJson.write(file(value.getString("taskId")).baseFile, value)
    }
    fun update(id: String, change: (JSONObject) -> Unit): JSONObject = synchronized(lock) {
        val value = read(id); val stage = value.optString("stage"); change(value)
        value.put("updatedAt", System.currentTimeMillis())
        if (stage != value.optString("stage")) {
            val log = value.optJSONArray("log") ?: JSONArray()
            log.put(JSONObject().put("at", System.currentTimeMillis()).put("stage", value.optString("stage")))
            while (log.length() > 100) log.remove(0)
            value.put("log", log)
        }
        write(value); value
    }
    companion object {
        private val lock = Any()
        val terminal = setOf("COMPLETED", "FAILED", "CANCELLED", "INTERRUPTED", "SUBMISSION_UNCERTAIN", "UNKNOWN", "DOWNLOAD_FAILED")
    }
}

class TaskManager(context: Context) {
    private val app = context.applicationContext
    val store = PersistentTaskStore(app)
    val projects = ProjectRepository(app)
    fun submit(projectId: String, operation: Operation, model: String = "", parameters: JSONObject = JSONObject()): String = synchronized(com.coderabyss.mobile.projects.ProjectLocks.lock) {
        val project = projects.read(projectId)
        if(operation in setOf(Operation.TEXT, Operation.IMAGE, Operation.VIDEO, Operation.VIDEO_EDIT)) check(!activeForProject(projectId)) { "This project already has active work. Open Tasks to view it." }
        val id = UUID.randomUUID().toString(); val now = System.currentTimeMillis()
        val type = Service.valueOf(project.getString("type"))
        val settings = VideoBackendSettings(app)
        val remote = if (operation in setOf(Operation.TEXT, Operation.IMAGE, Operation.VIDEO)) {
            val descriptor = ModelRegistry.get(model)
            check(type in descriptor.supportedServices) { "Model does not support this service" }
            if (descriptor.executionType == ExecutionType.LOCAL) {
                check(OfflineModelManager(app).isInstalled(descriptor.local!!)) { "Download the selected local model first" }
                InferenceRouter.route(type, descriptor, DeviceCompatibility.snapshot(app), settings.localOnly, SpaceRegistry.online(app))
                false
            } else {
                check(!settings.localOnly) { "Unavailable in Local Only mode." }
                com.coderabyss.mobile.account.AuthorizationRepository.requireCloud(app)
                check(SpaceRegistry.capability(app, model)?.optBoolean("available") == true) { "Configure and test the Cloud GPU provider first" }
                true
            }
        } else false
        val input = JSONObject(parameters.toString())
        if (!input.has("prompt")) input.put("prompt", project.optString("prompt"))
        if(operation in setOf(Operation.TEXT, Operation.IMAGE, Operation.VIDEO)) {
            require(input.optString("prompt").isNotBlank()) { "Enter a prompt first" }
            if(remote) {
                val cap = SpaceRegistry.capability(app, model)!!
                require(input.getString("prompt").length <= cap.optInt("maxPromptCharacters", if(operation == Operation.TEXT) 24000 else if(operation == Operation.VIDEO && model == "wan") 4000 else 2000)) { "Prompt is too long for the selected backend. Shorten it without losing the original draft." }
                if(operation == Operation.VIDEO) {
                    if(model == "wan") VideoPromptRules.validateWan(input.getString("prompt"))
                    require((0 until cap.getJSONArray("durations").length()).any { cap.getJSONArray("durations").getInt(it) == input.optInt("duration") }) { "Unsupported video duration" }
                    require((0 until cap.getJSONArray("resolutions").length()).any { cap.getJSONArray("resolutions").getString(it) == input.optString("resolution") }) { "Unsupported video resolution" }
                }
                if(operation == Operation.IMAGE) require(input.optString("negativePrompt").length <= 2000) { "Negative prompt is too long" }
            }
        }
        val task = JSONObject().put("taskId", id).put("projectId", projectId).put("service", type.name)
            .put("operation", operation.name).put("model", model).put("remote", remote)
            .put("provider", if(remote) "gateway" else "local").put("accountUid", if(remote) com.coderabyss.mobile.account.AuthRepository.get(app).uid.value else "").put("space", "gateway").put("parameters", input).put("clientRequestId", id)
            .put("createdAt", now).put("updatedAt", now).put("status", "PENDING").put("stage", "Input saved")
            .put("jobId", "").put("cancelRequested", false).put("log", JSONArray())
        projects.update(projectId) { if (operation in setOf(Operation.TEXT, Operation.IMAGE, Operation.VIDEO)) it.put("preferredModel", model)
            it.put("activeTaskId", id) }
        store.write(task); enqueue(id); id
    }
    fun enqueue(id: String) {
        val task = store.read(id)
        if(task.optString("operation") == "MODEL_DOWNLOAD") {
            if(!task.optBoolean("paused")) ModelCatalog.models.firstOrNull { it.id == task.optString("model") }?.let { OfflineModelManager(app).enqueueDownload(it, id) }
            return
        }
        val request = OneTimeWorkRequestBuilder<PlatformTaskWorker>().setInputData(workDataOf("taskId" to id))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(app).enqueueUniqueWork("operation-$id", ExistingWorkPolicy.KEEP, request)
    }
    fun cancel(id: String) {
        runCatching { store.read(id) }.getOrNull()?.takeIf { it.optString("operation") == "MODEL_DOWNLOAD" }?.let { task -> ModelCatalog.models.firstOrNull { it.id == task.optString("model") }?.let { OfflineModelManager(app).cancelDownload(it) }; return }
        if (runCatching { store.read(id) }.isFailure) { VideoTasks(app).update(id) { it.put("cancelRequested", true) }; VideoTasks(app).enqueue(id); return }
        val value = store.update(id) { it.put("cancelRequested", true) }
        if (!value.optBoolean("remote")) WorkManager.getInstance(app).cancelUniqueWork("operation-$id")
        store.update(id) { it.put("status", if (value.optBoolean("remote")) "CANCELLING" else "CANCELLED").put("stage", "Cancellation requested") }
        if (value.optBoolean("remote")) enqueue(id)
    }
    fun retry(id: String, continuePartial: Boolean = false) {
        val task = runCatching { store.read(id) }.getOrElse {
            VideoTasks(app).update(id) { it.put("retryRequested", it.optString("status") == "FAILED").put("status", "RETRY_REQUESTED").put("cancelRequested", false) }
            VideoTasks(app).enqueue(id); return
        }
        if(task.optString("operation") == "MODEL_DOWNLOAD") { ModelCatalog.models.firstOrNull { it.id == task.optString("model") }?.let { OfflineModelManager(app).startDownload(it) }; return }
        if (task.optBoolean("remote")) {
            store.update(id) { it.put("retryRequested", task.optString("status") == "FAILED").put("status", "RETRYING").put("cancelRequested", false) }
            enqueue(id)
        } else {
            val input = JSONObject(task.getJSONObject("parameters").toString())
            if (continuePartial) input.put("prompt", input.optString("prompt") + "\nContinue from this saved draft:\n" + task.optString("partial"))
            submit(task.getString("projectId"), Operation.valueOf(task.getString("operation")), task.optString("model"), input)
        }
    }
    fun recover(processRestart: Boolean = false) {
        projects.migrate(); VideoTasks(app).recover()
        store.all().filter { it.optString("status") !in PersistentTaskStore.terminal }.forEach { task ->
            if (processRestart && task.optString("status") == "RECORDING") store.update(task.getString("taskId")) {
                it.put("status", "INTERRUPTED").put("stage", "Recording interrupted; captured audio retained")
            } else if(task.optString("status") != "RECORDING") enqueue(task.getString("taskId"))
        }
    }
    fun all(): List<JSONObject> = store.all() + VideoTasks(app).all().map { old ->
        JSONObject(old.toString()).put("taskId", old.getString("projectId")).put("legacyVideo", true).put("service", "VIDEO")
            .put("operation", "VIDEO").put("model", old.getJSONObject("request").optString("engine")).put("parameters", old.getJSONObject("request"))
    }
    fun observe() = flow { while (true) { emit(all()); delay(750) } }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    fun activeForProject(id: String) = store.all().any { it.optString("projectId") == id && it.optString("status") !in PersistentTaskStore.terminal } ||
        VideoTasks(app).all().any { it.optString("projectId") == id && it.optString("status") !in VideoTasks.finished }
}
