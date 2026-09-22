package com.coderabyss.mobile

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

class WanVideoClient(
    private val context: Context
) {

    companion object {
        const val MODEL_ID = "Wan-AI/Wan2.1-T2V-1.3B"
    }

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(
                30,
                TimeUnit.SECONDS
            )
            .readTimeout(
                90,
                TimeUnit.SECONDS
            )
            .build()

    private val mediaType =
        "application/json"
            .toMediaType()

    suspend fun generate(
        hfToken: String,
        prompt: String,
        onStatus:
            suspend (String) -> Unit
    ): java.io.File =
        withContext(
            Dispatchers.IO
        ) {

            require(
                hfToken.startsWith(
                    "hf_"
                )
            ) {
                "Enter a Hugging Face token."
            }

            require(
                prompt.isNotBlank()
            ) {
                "Enter a video prompt."
            }

            onStatus(
                "Submitting to official Wan-AI..."
            )

            val submitUrl =
                "https://router.huggingface.co/" +
                "fal-ai/fal-ai/wan/v2.1/1.3b/" +
                "text-to-video?_subdomain=queue"

            val submitJson =
                JSONObject()
                    .put(
                        "prompt",
                        prompt
                    )
                    .toString()

            val submit =
                Request.Builder()
                    .url(submitUrl)
                    .header(
                        "Authorization",
                        "Bearer $hfToken"
                    )
                    .post(
                        submitJson
                            .toRequestBody(
                                mediaType
                            )
                    )
                    .build()

            val submitObject =
                executeJson(
                    submit
                )

            val responseUrl =
                submitObject
                    .optString(
                        "response_url"
                    )

            if (
                responseUrl.isBlank()
            ) {
                error(
                    "Wan service did not return a response URL."
                )
            }

            val responsePath =
                URI(responseUrl)
                    .rawPath

            val statusUrl =
                "https://router.huggingface.co/" +
                "fal-ai$responsePath/status" +
                "?_subdomain=queue"

            val resultUrl =
                "https://router.huggingface.co/" +
                "fal-ai$responsePath" +
                "?_subdomain=queue"

            var complete =
                false

            var attempts =
                0

            while (
                !complete &&
                attempts < 1800
            ) {

                delay(1000)

                attempts++

                val request =
                    Request.Builder()
                        .url(statusUrl)
                        .header(
                            "Authorization",
                            "Bearer $hfToken"
                        )
                        .get()
                        .build()

                val status =
                    executeJson(
                        request
                    )
                    .optString(
                        "status"
                    )

                onStatus(
                    when (status) {

                        "IN_QUEUE" ->
                            "Wan request queued..."

                        "IN_PROGRESS" ->
                            "Wan is generating video..."

                        "COMPLETED" ->
                            "Video complete."

                        else ->
                            "Wan status: $status"
                    }
                )

                if (
                    status == "FAILED" ||
                    status == "CANCELLED"
                ) {
                    error(
                        "Wan generation failed: $status"
                    )
                }

                complete =
                    status ==
                    "COMPLETED"
            }

            if (!complete)
                error(
                    "Wan generation timed out."
                )

            val resultRequest =
                Request.Builder()
                    .url(resultUrl)
                    .header(
                        "Authorization",
                        "Bearer $hfToken"
                    )
                    .get()
                    .build()

            val result =
                executeJson(
                    resultRequest
                )

            val videoUrl =
                result
                    .getJSONObject(
                        "video"
                    )
                    .getString(
                        "url"
                    )

            onStatus(
                "Downloading preview..."
            )

            saveVideo(
                videoUrl
            )
        }

    private fun executeJson(
        request: Request
    ): JSONObject {

        client.newCall(
            request
        ).execute().use { response ->

            val text =
                response.body
                    ?.string()
                    .orEmpty()

            if (!response.isSuccessful) {

                error(
                    "Service error " +
                    "${response.code}: $text"
                )
            }

            return JSONObject(
                text
            )
        }
    }

    private fun saveVideo(url: String): java.io.File {
        val dir = java.io.File(context.cacheDir, "video-previews").apply { mkdirs() }
        val preview = java.io.File.createTempFile("hosted-wan-", ".mp4", dir)
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "Video download failed: HTTP ${response.code}" }
                val body = response.body ?: error("Empty video response")
                val written = preview.outputStream().use { output -> body.byteStream().use { it.copyTo(output) } }
                check(written > 0 && (body.contentLength() < 0 || written == body.contentLength())) { "Video download was incomplete" }
            }
            return preview
        } catch (e: Exception) { preview.delete(); throw e }
    }
}
