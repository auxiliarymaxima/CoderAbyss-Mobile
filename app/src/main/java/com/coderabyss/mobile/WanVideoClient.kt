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
    ): Uri =
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
                "Saving video..."
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

    private fun saveVideo(
        url: String
    ): Uri {

        val request =
            Request.Builder()
                .url(url)
                .build()

        client.newCall(
            request
        ).execute().use { response ->

            if (!response.isSuccessful)
                error(
                    "Video download failed."
                )

            val resolver =
                context.contentResolver

            val values =
                ContentValues().apply {

                    put(
                        MediaStore.Video
                            .Media.DISPLAY_NAME,
                        "coder_abyss_wan_" +
                            System.currentTimeMillis() +
                            ".mp4"
                    )

                    put(
                        MediaStore.Video
                            .Media.MIME_TYPE,
                        "video/mp4"
                    )

                    put(
                        MediaStore.Video
                            .Media.RELATIVE_PATH,
                        "Movies/CoderAbyss"
                    )
                }

            val uri =
                resolver.insert(
                    MediaStore.Video
                        .Media
                        .EXTERNAL_CONTENT_URI,
                    values
                )
                    ?: error(
                        "Could not create video file."
                    )

            resolver
                .openOutputStream(
                    uri
                )!!.use { output ->

                    response.body!!
                        .byteStream()
                        .copyTo(
                            output
                        )
                }

            return uri
        }
    }
}
