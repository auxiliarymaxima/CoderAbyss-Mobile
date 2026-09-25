package com.coderabyss.mobile.research

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ResearchDraft {
    fun sections(text: String): JSONArray {
        val result = JSONArray()
        var heading = "Draft"
        val body = StringBuilder()
        fun flush() {
            if(body.isNotBlank()) result.put(JSONObject().put("id", UUID.randomUUID().toString())
                .put("title", heading).put("text", body.toString().trim()).put("aiDraft", true).put("sourceIds", JSONArray()))
            body.clear()
        }
        text.lineSequence().forEach { line ->
            val match = Regex("^#{1,6}\\s+(.+)$").matchEntire(line)
            if(match != null) { flush(); heading = match.groupValues[1] } else body.appendLine(line)
        }
        flush()
        require(result.length() > 0) { "Empty document" }
        return result
    }
}
