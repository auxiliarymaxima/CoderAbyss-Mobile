package com.coderabyss.mobile.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.coderabyss.mobile.VideoBackendSettings
import org.json.JSONObject

data class SpaceDescriptor(val id: String, val displayName: String, val endpoint: String, val supportsQueue: Boolean = true, val supportsCancel: Boolean = true)
enum class ProviderHealth { READY, STARTING, BUSY, OFFLINE, AUTHENTICATION_REQUIRED, RATE_LIMITED, UNTESTED }
class BackendUnavailable(val health: ProviderHealth, message: String) : java.io.IOException(message)
object SpaceRegistry {
    fun health(context: Context) = runCatching { ProviderHealth.valueOf(context.getSharedPreferences("video_backend", 0).getString("health", "UNTESTED")!!) }.getOrDefault(ProviderHealth.UNTESTED)
    fun health(context: Context, space: String, value: ProviderHealth) {
        if(space == "gateway" || space == configured(context)) context.getSharedPreferences("video_backend", 0).edit().putString("health", value.name).apply()
    }
    fun configured(context: Context): String = context.getSharedPreferences("video_backend", Context.MODE_PRIVATE)
        .getString("space_id", VideoBackendSettings.SPACE) ?: VideoBackendSettings.SPACE
    fun endpoint(id: String): String {
        require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,95}/[A-Za-z0-9][A-Za-z0-9-]{0,95}"))) { "Enter owner/Space-name" }
        return "https://${id.replace('/', '-').lowercase()}.hf.space"
    }
    fun configure(context: Context, id: String) {
        endpoint(id)
        context.getSharedPreferences("video_backend", Context.MODE_PRIVATE).edit().putString("space_id", id).remove("capabilities").remove("health").commit()
    }
    fun descriptor(context: Context) = configured(context).let { SpaceDescriptor(it, "Hugging Face", endpoint(it)) }
    fun capability(context: Context, engine: String): JSONObject? = runCatching {
        val raw = context.getSharedPreferences("video_backend", Context.MODE_PRIVATE).getString("capabilities", null) ?: return null
        val all = JSONObject(raw).getJSONArray("engines")
        (0 until all.length()).map(all::getJSONObject).firstOrNull { it.optString("engine") == engine }
    }.getOrNull()
    fun online(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}

interface RemoteInferenceProvider {
    suspend fun submit(parameters: JSONObject): JSONObject
    suspend fun status(jobId: String): JSONObject
    suspend fun cancel(jobId: String): JSONObject
}
class HuggingFaceProvider(context: Context, space: String) : RemoteInferenceProvider {
    val client = com.coderabyss.mobile.HuggingFaceSpaceClient(context, space)
    override suspend fun submit(parameters: JSONObject) = client.call("submit_job", org.json.JSONArray().put(parameters)).getJSONObject(0)
    override suspend fun status(jobId: String) = client.getVideoJobStatus(jobId)
    override suspend fun cancel(jobId: String) = client.cancelVideoJob(jobId)
}

class GatewayProvider(context: Context, uid: String) : RemoteInferenceProvider {
    val client = com.coderabyss.mobile.account.CoderAbyssBackendClient(context, uid)
    override suspend fun submit(parameters: JSONObject) = client.submitVideoJob(parameters)
    override suspend fun status(jobId: String) = client.getVideoJobStatus(jobId)
    override suspend fun cancel(jobId: String) = client.cancelVideoJob(jobId)
}
