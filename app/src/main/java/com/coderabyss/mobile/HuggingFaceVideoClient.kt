package com.coderabyss.mobile

import android.content.Context
import android.media.MediaMetadataRetriever
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
class HuggingFaceVideoClient(private val context: Context) {
    private val settings = VideoBackendSettings(context)
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain -> settings.requireRemoteAllowed(); chain.proceed(chain.request()) }
        .readTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    private fun request(url: String): Request.Builder {
        settings.requireRemoteAllowed()
        val parsed = url.toHttpUrl()
        check(parsed.scheme == "https" && parsed.host == VideoBackendSettings.BASE_URL.toHttpUrl().host) {
            "Backend returned an untrusted result address."
        }
        return Request.Builder().url(parsed).header("Authorization", "Bearer ${settings.token()}")
    }

    private fun checkResponse(code: Int) {
        when (code) {
            401, 403 -> throw IOException("Authentication failed. Check Video Backend settings.")
            429 -> throw IOException("Backend rate limit reached. Retry status later.")
        }
        if (code !in 200..299) throw IOException("Backend unavailable (HTTP $code).")
    }

    private suspend fun call(name: String, data: JSONArray): JSONArray = withContext(Dispatchers.IO) {
        val base = "${VideoBackendSettings.BASE_URL}/gradio_api/call/$name"
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

    suspend fun downloadVideoResult(jobId: String, destination: File, onBytes: (Long, Long) -> Unit): JSONObject {
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
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(partial.absolutePath)
                check((retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0) { "Downloaded video is invalid." }
                check(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "Downloaded file has no video." }
            } finally { retriever.release() }
            check(partial.renameTo(destination)) { "Could not save completed video." }
            metadata
        }
    }
}
