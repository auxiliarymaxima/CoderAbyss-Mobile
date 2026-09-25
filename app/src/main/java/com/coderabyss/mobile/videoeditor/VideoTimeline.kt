package com.coderabyss.mobile.videoeditor

import com.coderabyss.mobile.projects.ProjectRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object VideoTimeline {
    fun validate(clip: JSONObject) {
        require(clip.getLong("startMs") >= 0 && clip.getLong("endMs") > clip.getLong("startMs"))
        require(clip.getLong("endMs") <= clip.getLong("durationMs"))
        require(clip.optDouble("volume", 1.0) in 0.0..2.0)
        require(clip.optDouble("left", -1.0) < clip.optDouble("right", 1.0))
        require(clip.optDouble("bottom", -1.0) < clip.optDouble("top", 1.0))
        listOf("left", "right", "bottom", "top").forEach { require(clip.optDouble(it, if(it in setOf("left", "bottom")) -1.0 else 1.0) in -1.0..1.0) }
    }
    fun add(projects: ProjectRepository, projectId: String, path: String) {
        val file = projects.file(projectId, path)
        val reader = android.media.MediaMetadataRetriever()
        val duration = try { reader.setDataSource(file.path); reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() } finally { reader.release() }
        require(duration > 0)
        projects.update(projectId) { p -> val clips = p.optJSONArray("timeline") ?: JSONArray()
            clips.put(JSONObject().put("id", UUID.randomUUID().toString()).put("path", path).put("durationMs", duration)
                .put("startMs", 0).put("endMs", duration).put("volume", 1.0)); p.put("timeline", clips)
        }
    }
    fun edit(projects: ProjectRepository, projectId: String, action: (MutableList<JSONObject>) -> Unit) {
        projects.update(projectId) { p -> val a = p.optJSONArray("timeline") ?: JSONArray()
            val clips = (0 until a.length()).map(a::getJSONObject).toMutableList(); action(clips)
            clips.forEach(::validate); p.put("timeline", JSONArray(clips))
        }
    }
}
