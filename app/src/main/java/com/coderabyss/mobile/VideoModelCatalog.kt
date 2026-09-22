package com.coderabyss.mobile

import android.content.Context
import org.json.JSONObject

data class HostedVideoModel(val engine: String, val title: String, val available: Boolean,
                            val durations: List<Int>, val resolutions: List<String>, val description: String)

/** Shared by the full manager, service recommendations, and video controls. */
object VideoModelCatalog {
    fun save(context: Context, capabilities: JSONObject) {
        context.getSharedPreferences("video_backend", Context.MODE_PRIVATE).edit()
            .putString("capabilities", capabilities.toString()).apply()
    }
    fun models(context: Context): List<HostedVideoModel> {
        val cached = context.getSharedPreferences("video_backend", Context.MODE_PRIVATE).getString("capabilities", null)
        val engines = runCatching { cached?.let { JSONObject(it).getJSONArray("engines") } }.getOrNull()
        return listOf("wan" to "Wan", "ltx" to "LTX-Video").map { (id, title) ->
            val model = engines?.let { values -> (0 until values.length()).map { values.getJSONObject(it) }.firstOrNull { it.optString("engine") == id } }
            val durations = model?.optJSONArray("durations")
            val sizes = model?.optJSONArray("resolutions")
            HostedVideoModel(id, title, model?.optBoolean("available") == true,
                durations?.let { (0 until it.length()).map(it::getInt) } ?: emptyList(),
                sizes?.let { (0 until it.length()).map(it::getString) } ?: emptyList(),
                model?.optString("description") ?: "Configure and test GPU backend")
        }
    }
}
