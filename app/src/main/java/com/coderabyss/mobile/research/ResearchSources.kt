package com.coderabyss.mobile.research

import android.content.Context
import com.coderabyss.mobile.VideoBackendSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

object CitationFormatter {
    val styles = listOf("APA", "MLA", "Chicago", "Harvard", "IEEE")
    fun reference(source: JSONObject, style: String, index: Int = 1): String {
        val author = source.optString("author").trim()
        val title = source.optString("title").trim()
        val date = source.optString("date").take(4).ifBlank { "n.d." }
        val publisher = source.optString("publisher").trim()
        val url = source.optString("url").trim()
        val doi = source.optString("doi").trim()
        val locator = if (doi.isNotEmpty()) "https://doi.org/$doi" else url
        val name = author.ifBlank { title }
        return when (style) {
            "IEEE" -> "[${source.optInt("citationNumber", index)}] " + listOf(author, "\"$title\"", publisher, date, locator).filter { it.isNotBlank() }.joinToString(", ")
            "MLA" -> listOf(author, "\"$title.\"", publisher, date, locator, "Accessed ${source.optString("accessed")}").filter { it.isNotBlank() }.joinToString(". ")
            "Chicago" -> listOf(author, "\"$title.\"", publisher, date, locator).filter { it.isNotBlank() }.joinToString(" ")
            "Harvard" -> "$name ($date) ${if (author.isBlank()) "" else title}. $publisher. Available at: $locator (Accessed: ${source.optString("accessed")})."
            else -> "$name ($date). ${if (author.isBlank()) "" else title + ". "}$publisher. $locator"
        }
    }
}

/** Crossref returns bibliographic metadata/abstracts, not verified full-text evidence. */
class ResearchSources(private val context: Context) {
    private val settings = VideoBackendSettings(context)
    private val http = com.coderabyss.mobile.remote.NetworkPolicy.client(context)
        .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
    suspend fun search(query: String): JSONArray = withContext(Dispatchers.IO) {
        settings.requireRemoteAllowed(); require(query.isNotBlank() && query.length <= 500)
        val url = "https://api.crossref.org/works".toHttpUrl().newBuilder().addQueryParameter("query.bibliographic", query)
            .addQueryParameter("rows", "8").build()
        http.newCall(Request.Builder().url(url).header("User-Agent", "CoderAbyss/0.7 (research source lookup)").build()).execute().use { response ->
            check(response.isSuccessful) { "Source search unavailable (HTTP ${response.code})" }
            val items = JSONObject(response.body!!.string()).getJSONObject("message").getJSONArray("items")
            JSONArray().apply { for (i in 0 until items.length()) {
                val item = items.getJSONObject(i); val author = item.optJSONArray("author") ?: JSONArray()
                val dateParts = item.optJSONObject("published")?.optJSONArray("date-parts")?.optJSONArray(0)
                put(JSONObject().put("id", UUID.randomUUID().toString()).put("title", item.optJSONArray("title")?.optString(0) ?: "")
                    .put("author", (0 until author.length()).joinToString("; ") { n -> author.getJSONObject(n).let { listOf(it.optString("given"), it.optString("family")).filter(String::isNotBlank).joinToString(" ") } })
                    .put("publisher", item.optString("publisher")).put("date", dateParts?.let { (0 until it.length()).joinToString("-") { n -> it.getInt(n).toString() } } ?: "")
                    .put("url", item.optString("URL")).put("doi", item.optString("DOI")).put("accessed", LocalDate.now().toString())
                    .put("provenance", "Crossref metadata").put("abstract", item.optString("abstract").replace(Regex("<[^>]*>"), ""))
                    .put("notes", "").put("fullTextVerified", false))
            } }
        }
    }
}
