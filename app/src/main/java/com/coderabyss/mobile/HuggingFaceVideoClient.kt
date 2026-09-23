package com.coderabyss.mobile

import android.content.Context
import java.io.File

/** Compatibility adapter; all services share the same Space client and credentials. */
class HuggingFaceVideoClient(context: Context, space: String = com.coderabyss.mobile.remote.SpaceRegistry.configured(context)) {
    private val client = HuggingFaceSpaceClient(context, space)
    suspend fun capabilities() = client.capabilities()
    suspend fun submitVideoJob(parameters: org.json.JSONObject) = client.submitVideoJob(parameters)
    suspend fun getVideoJobStatus(id: String) = client.getVideoJobStatus(id)
    suspend fun findSubmittedJob(id: String) = client.findSubmittedJob(id)
    suspend fun cancelVideoJob(id: String) = client.cancelVideoJob(id)
    suspend fun retryVideoJob(id: String) = client.retryVideoJob(id)
    suspend fun downloadVideoResult(id: String, destination: File, progress: (Long, Long) -> Unit) = client.downloadResult(id, destination, progress)
}
