package com.coderabyss.mobile

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
class HuggingFaceSpaceClient(private val context: Context, private val space: String = com.coderabyss.mobile.remote.SpaceRegistry.configured(context)) {
    private val endpoint = com.coderabyss.mobile.remote.SpaceRegistry.endpoint(space)
    private val settings = VideoBackendSettings(context)
    private val http = NetworkPolicy.client(context).connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    private fun request(url: String): Request.Builder {
        settings.requireRemoteAllowed()
        val parsed = url.toHttpUrl()
        check(parsed.scheme == "https" && parsed.port == 443 && parsed.host == endpoint.toHttpUrl().host) {
            "Backend returned an untrusted result address."
        }
        if(!settings.configured()) { SpaceRegistry.health(context, space, ProviderHealth.AUTHENTICATION_REQUIRED); throw BackendUnavailable(ProviderHealth.AUTHENTICATION_REQUIRED, "Authentication required. Configure the provider in Settings.") }
        return Request.Builder().url(parsed).header("Authorization", "Bearer ${settings.token()}")
    }

    private fun checkResponse(code: Int) {
        val health = when(code) { 401,403 -> ProviderHealth.AUTHENTICATION_REQUIRED; 429 -> ProviderHealth.RATE_LIMITED; 502,503,504 -> ProviderHealth.STARTING; in 200..299 -> ProviderHealth.READY; else -> ProviderHealth.OFFLINE }
        SpaceRegistry.health(context, space, health)
        if(code !in 200..299) throw BackendUnavailable(health, when(health) {
            ProviderHealth.AUTHENTICATION_REQUIRED -> "Authentication failed. Check provider settings."
            ProviderHealth.RATE_LIMITED -> "Provider rate limit reached. The same request will be checked later."
            ProviderHealth.STARTING -> "Starting AI server..."
            else -> "Backend unavailable (HTTP $code)."
        })
    }

    suspend fun call(name: String, data: JSONArray): JSONArray = withContext(Dispatchers.IO) {
        val base = "${endpoint}/gradio_api/call/$name"
        val body = JSONObject().put("data", data).toString().toRequestBody("application/json".toMediaType())
        val eventId = http.newCall(request(base).post(body).build()).execute().use { response ->
            checkResponse(response.code)
            JSONObject(response.body?.string() ?: throw IOException("Empty backend response")).getString("event_id")
        }
        check(eventId.matches(Regex("[a-zA-Z0-9-]{1,128}"))) { "Invalid backend event ID." }
        http.newCall(request("$base/$eventId").build()).execute().use { response ->
            checkResponse(response.code)
            val reader = response.body?.charStream()?.buffered() ?: throw IOException("Empty backend response")
            var event = ""
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("event: ")) event = line.removePrefix("event: ").trim()
                if (line.startsWith("data: ") && event == "complete") return@withContext JSONArray(line.removePrefix("data: "))
                if (event == "error") throw IOException("Backend rejected the request. Check supported parameters and authentication.")
            }
            throw IOException("Backend response interrupted. Check the same job; do not regenerate.")
        }
    }

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
