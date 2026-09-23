package com.coderabyss.mobile.models

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.coderabyss.mobile.*
import com.coderabyss.mobile.tasks.PersistentTaskStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ModelDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val store = PersistentTaskStore(context)
    override suspend fun doWork(): Result {
        val id = inputData.getString("taskId") ?: return Result.failure()
        val task = store.read(id); val model = ModelCatalog.models.firstOrNull { it.id == task.optString("model") } ?: return Result.failure()
        val manager = OfflineModelManager(applicationContext)
        return locks.getOrPut(model.id) { Mutex() }.withLock {
            if(store.read(id).optBoolean("paused") || store.read(id).optBoolean("cancelRequested")) return@withLock Result.success()
            val settings = VideoBackendSettings(applicationContext)
            if(settings.localOnly) { store.update(id) { it.put("status", "PAUSED").put("stage", "Unavailable in Local Only mode.") }; return@withLock Result.retry() }
            val spec = manager.downloadSpec(model); val total = spec.getLong("size"); val part = File(manager.modelDirectory, model.fileName + ".resume.part")
            val notifications = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notifications.createNotificationChannel(NotificationChannel("model-downloads", "Model downloads", NotificationManager.IMPORTANCE_LOW))
            val notification = NotificationCompat.Builder(applicationContext, "model-downloads").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Coder Abyss").setContentText("Downloading ${model.name}").setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", com.coderabyss.mobile.tasks.TaskCancelReceiver.intent(applicationContext, id)).build()
            setForeground(ForegroundInfo(id.hashCode(), notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            try {
                if(!manager.isInstalled(model)) {
                    if(part.length() > total) check(part.delete())
                    if(part.length() < total) {
                        val offset = part.length(); val builder = Request.Builder().url(spec.getString("url"))
                        if(offset > 0) builder.header("Range", "bytes=$offset-")
                        val http = com.coderabyss.mobile.remote.NetworkPolicy.client(applicationContext).connectTimeout(30, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
                        http.newCall(builder.build()).execute().use { response ->
                            check(response.isSuccessful) { "Model source unavailable" }
                            val resume = offset > 0 && response.code == 206
                            if(resume) check(response.header("Content-Range")?.startsWith("bytes $offset-") == true)
                            var bytes = if(resume) offset else 0L; var saved = 0L
                            store.update(id) { it.put("status", "DOWNLOADING").put("stage", "Downloading ${model.name}").put("totalBytes", total) }
                            response.body!!.byteStream().use { input -> FileOutputStream(part, resume).use { output ->
                                val buffer = ByteArray(128 * 1024)
                                while(true) {
                                    currentCoroutineContext().ensureActive(); settings.requireRemoteAllowed()
                                    val state = store.read(id); if(state.optBoolean("paused") || state.optBoolean("cancelRequested")) throw CancellationException()
                                    val count = input.read(buffer); if(count < 0) break
                                    output.write(buffer, 0, count); bytes += count; check(bytes <= total)
                                    if(System.currentTimeMillis() - saved > 750) { store.update(id) { it.put("downloadedBytes", bytes) }; saved = System.currentTimeMillis() }
                                }
                                output.fd.sync()
                            } }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    store.update(id) { it.put("status", "VERIFYING").put("stage", "Verifying full size and SHA-256") }
                    ModelFileVerifier.verify(part, total, spec.getString("sha256"))
                    currentCoroutineContext().ensureActive()
                    check(!store.read(id).optBoolean("cancelRequested") && !store.read(id).optBoolean("paused"))
                    check(part.renameTo(manager.modelFile(model))); manager.markVerified(model)
                }
                store.update(id) { it.put("status", "COMPLETED").put("stage", "Model verified and installed").put("downloadedBytes", total).put("completedAt", System.currentTimeMillis()) }
                Result.success()
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) {
                if(settings.localOnly) { store.update(id) { it.put("status", "PAUSED").put("stage", "Unavailable in Local Only mode.") }; Result.retry() }
                else if(part.length() >= total) { store.update(id) { it.put("status", "FAILED").put("stage", "Model verification failed. Cancel, then download again.") }; Result.success() }
                else { store.update(id) { it.put("status", "WAITING_FOR_CONNECTION").put("stage", "Download interrupted; saved bytes will resume") }; Result.retry() }
            }
        }
    }
    companion object { private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>() }
}
