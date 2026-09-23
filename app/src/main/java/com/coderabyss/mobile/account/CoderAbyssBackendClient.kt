package com.coderabyss.mobile.account

import com.coderabyss.mobile.*

import com.coderabyss.mobile.remote.*
import android.content.Context
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Gradio's short call/event exchange is encapsulated here, never in Compose. */
class CoderAbyssBackendClient(private val context: Context, private val expectedUid: String? = null) {
    private val space = "gateway"
    private val endpoint = context.getString(R.string.gateway_url).trimEnd('/')
    private val settings = VideoBackendSettings(context)
    private val http = NetworkPolicy.client(context).connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    private suspend fun request(url: String): Request.Builder {
        settings.requireRemoteAllowed()
        check(endpoint.isNotBlank()) { "Cloud AI is not configured for this build." }
        val parsed = url.toHttpUrl()
        val origin = endpoint.toHttpUrl()
        check(origin.scheme == "https" && origin.port == 443 && origin.encodedPath == "/" && origin.username.isEmpty() && origin.password.isEmpty()) { "Invalid Cloud AI configuration" }
        check(parsed.scheme == "https" && parsed.port == 443 && parsed.host == origin.host) { "Untrusted result address" }
        val auth = AuthRepository.get(context)
        if (expectedUid != null) check(expectedUid.isNotBlank() && auth.uid.value == expectedUid) { "Sign in with the account that started this task." }
        return Request.Builder().url(parsed).header("Authorization", "Bearer ${auth.token(expectedUid)}")
    }

    suspend fun api(path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        require(!path.contains("..") && !path.startsWith("/") && !path.contains("://"))
        val builder = request("$endpoint/$path")
        if(body != null) builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        http.newCall(builder.build()).execute().use { response ->
            checkResponse(response.code)
            JSONObject(response.body?.string() ?: throw IOException("Empty backend response"))
        }
    }

    private fun checkResponse(code: Int) {
        val health = when(code) { 401,403 -> ProviderHealth.AUTHENTICATION_REQUIRED; 429 -> ProviderHealth.RATE_LIMITED; 502,503,504 -> ProviderHealth.STARTING; in 200..299 -> ProviderHealth.READY; else -> ProviderHealth.OFFLINE }
        SpaceRegistry.health(context, space, health)
        if(code !in 200..299) throw BackendUnavailable(health, when(health) {
            ProviderHealth.AUTHENTICATION_REQUIRED -> "Sign in and check your Cloud AI plan in Settings."
            ProviderHealth.RATE_LIMITED -> "Provider rate limit reached. The same request will be checked later."
            ProviderHealth.STARTING -> "Starting AI server..."
            else -> "Backend unavailable (HTTP $code)."
        })
    }

    suspend fun call(name: String, data: JSONArray): JSONArray {
        val response = when(name) {
            "capabilities" -> api("ai/capabilities")
            "submit_job" -> api("tasks", data.getJSONObject(0))
            "find_job" -> api("tasks/find/${safeId(data.getString(0))}")
            "get_job_status" -> api("tasks/${safeId(data.getString(0))}")
            "cancel_job" -> api("tasks/${safeId(data.getString(0))}/cancel", JSONObject())
            "retry_job" -> api("tasks/${safeId(data.getString(0))}/retry", JSONObject())
            "get_result" -> api("tasks/${safeId(data.getString(0))}/result")
            else -> error("Unsupported gateway operation")
        }
        return if(name == "get_result") JSONArray().put(response.getJSONObject("metadata")).put(JSONObject().put("url", "$endpoint/tasks/${safeId(data.getString(0))}/content")) else JSONArray().put(response)
    }
    private fun safeId(id: String): String { require(id.matches(Regex("[A-Za-z0-9-]{1,128}"))); return id }

    suspend fun capabilities(): JSONObject = call("capabilities", JSONArray()).getJSONObject(0).also { VideoModelCatalog.save(context, it) }
    suspend fun submitVideoJob(parameters: JSONObject): JSONObject {
        if (parameters.getString("engine") == "wan") VideoPromptRules.validateWan(parameters.getString("prompt"))
        return call("submit_job", JSONArray().put(parameters)).getJSONObject(0)
    }
    suspend fun getVideoJobStatus(jobId: String) = call("get_job_status", JSONArray().put(jobId)).getJSONObject(0)
    suspend fun findSubmittedJob(requestId: String) = call("find_job", JSONArray().put(requestId)).getJSONObject(0)
    suspend fun cancelVideoJob(jobId: String) = call("cancel_job", JSONArray().put(jobId)).getJSONObject(0)
    suspend fun retryVideoJob(jobId: String) = call("retry_job", JSONArray().put(jobId)).getJSONObject(0)

    suspend fun downloadResult(jobId: String, destination: File, onBytes: (Long, Long) -> Unit): JSONObject {
        val result = call("get_result", JSONArray().put(jobId))
        val metadata = result.getJSONObject(0)
        val url = result.getJSONObject(1).getString("url")
        val total = metadata.getLong("fileSize")
        return withContext(Dispatchers.IO) {
            val partial = File(destination.parentFile, destination.name + ".partial")
            destination.parentFile?.mkdirs()
            val offset = partial.takeIf { it.isFile && it.length() < total }?.length() ?: 0L
            val builder = request(url)
            if (offset > 0) builder.header("Range", "bytes=$offset-")
            // Backend result URLs refer to immutable, unique job output files.
            http.newBuilder().callTimeout(0, TimeUnit.SECONDS).build().newCall(builder.build()).execute().use { response ->
                checkResponse(response.code)
                val resume = offset > 0 && response.code == 206
                if (resume) check(response.header("Content-Range")?.startsWith("bytes $offset-") == true) { "Invalid download range." }
                var downloaded = if (resume) offset else 0L
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(partial, resume).use { output ->
                        val bytes = ByteArray(128 * 1024)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            settings.requireRemoteAllowed()
                            if(expectedUid != null) check(AuthRepository.get(context).uid.value == expectedUid) { "Account changed" }
                            val count = input.read(bytes)
                            if (count < 0) break
                            output.write(bytes, 0, count)
                            downloaded += count
                            check(downloaded <= total) { "Download exceeds expected size." }
                            onBytes(downloaded, total)
                        }
                        output.fd.sync()
                    }
                } ?: throw IOException("Empty video download")
            }
            check(partial.length() == total && total > 0) { "Video download is incomplete. Retry Download." }
            if (metadata.getString("mimeType").startsWith("video/")) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(partial.absolutePath)
                check((retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0) { "Downloaded video is invalid." }
                check(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "Downloaded file has no video." }
            } finally { retriever.release() }
            } else if (metadata.getString("mimeType").startsWith("image/")) {
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(partial.path, options)
                check(options.outWidth > 0 && options.outHeight > 0) { "Downloaded image is invalid." }
            } else {
                check(metadata.getString("mimeType") == "text/plain") { "Unsupported output format" }
                check(partial.readText().isNotBlank()) { "Text result is empty" }
            }
            check(partial.renameTo(destination)) { "Could not save completed video." }
            metadata
        }
    }
}
