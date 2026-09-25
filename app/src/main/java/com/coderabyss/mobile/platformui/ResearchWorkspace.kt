package com.coderabyss.mobile.platformui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coderabyss.mobile.VideoBackendSettings
import com.coderabyss.mobile.exports.DocumentExporter
import com.coderabyss.mobile.research.CitationFormatter
import com.coderabyss.mobile.tasks.Operation
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Composable
fun ResearchWorkspace(project: JSONObject, vm: WorkspaceViewModel, model: String, busy: Boolean) {
    val id = project.getString("projectId"); val context = LocalContext.current
    var tab by remember(id) { mutableStateOf(if((project.optJSONArray("sections")?.length() ?: 0) > 0) "PREVIEW" else "EDIT") }; var message by remember { mutableStateOf("") }
    var sourceEditor by remember { mutableStateOf<JSONObject?>(null) }; var sectionDelete by remember { mutableStateOf<String?>(null) }
    val sections = project.optJSONArray("sections") ?: JSONArray(); val sources = project.optJSONArray("sources") ?: JSONArray()
    val metadata = project.optJSONObject("metadata") ?: JSONObject(); val style = metadata.optString("citationStyle", "APA")
    fun updateSection(sectionId: String, change: (JSONObject) -> Unit) { vm.projects.update(id) { p ->
        val array = p.getJSONArray("sections"); for (n in 0 until array.length()) if (array.getJSONObject(n).getString("id") == sectionId) change(array.getJSONObject(n))
    } }
    fun generate(section: JSONObject, action: String) {
        runCatching {
            vm.projects.checkpoint(id)
            val sourceIds = section.optJSONArray("sourceIds") ?: JSONArray()
            val evidence = (0 until sources.length()).map(sources::getJSONObject).filter { source -> (0 until sourceIds.length()).any { sourceIds.optString(it) == source.optString("id") } }
            val prompt = "Topic: ${project.optString("prompt")}\nSection: ${section.optString("title")}\nAction: $action\nSaved draft: ${section.optString("text")}\nProvided source metadata/notes (not full-text verification):\n" + evidence.joinToString("\n") { it.toString() }
            vm.tasks.submit(id, Operation.TEXT, model, JSONObject().put("prompt", prompt).put("sectionId", section.getString("id")))
        }.onFailure { message = it.message ?: "Generation could not start" }
    }
    Row { listOf("EDIT", "PREVIEW", "EXPORT").forEach { choice -> FilterChip(tab == choice, { tab = choice }, label = { Text(choice) }) } }
    when(tab) {
        "EDIT" -> {
            listOf("author", "institution", "abstract", "keywords", "notes").forEach { field -> SavedTextField(metadata.optString(field), field.replaceFirstChar { it.uppercase() }, { value -> vm.projects.update(id) { it.put("metadata", (it.optJSONObject("metadata") ?: JSONObject()).put(field, value)) } }) }
            ChoiceMenu("Citation style", style, CitationFormatter.styles) { value -> vm.projects.update(id) { it.put("metadata", (it.optJSONObject("metadata") ?: JSONObject()).put("citationStyle", value)) } }
            Text("Sections", style = MaterialTheme.typography.titleLarge)
            if (sections.length() == 0) TextButton(onClick = { vm.projects.update(id) { p -> p.put("sections", JSONArray().apply {
                listOf("Introduction", "Background", "Methodology", "Findings", "Discussion", "Conclusion").forEach { put(JSONObject().put("id", UUID.randomUUID().toString()).put("title", it).put("text", "").put("sourceIds", JSONArray())) }
            }) } }) { Text("Add paper outline") }
            for(n in 0 until sections.length()) { val section = sections.getJSONObject(n); val sectionId = section.getString("id")
                key(sectionId) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SavedTextField(section.optString("title"), "Section title", { value -> updateSection(sectionId) { it.put("title", value) } })
                    SavedTextField(section.optString("text"), "Section text", { value -> updateSection(sectionId) { it.put("text", value).put("aiDraft", false) } }, 5)
                    if(section.optBoolean("aiDraft")) Text("AI draft — review claims against the original sources.")
                    Row {
                        TextButton(enabled = n > 0, onClick = { vm.projects.update(id) { p -> val a = p.getJSONArray("sections"); val previous = a.getJSONObject(n - 1); a.put(n - 1, a.getJSONObject(n)); a.put(n, previous) } }) { Text("↑") }
                        TextButton(enabled = n < sections.length() - 1, onClick = { vm.projects.update(id) { p -> val a = p.getJSONArray("sections"); val next = a.getJSONObject(n + 1); a.put(n + 1, a.getJSONObject(n)); a.put(n, next) } }) { Text("↓") }
                        TextButton(enabled = !busy, onClick = { generate(section, "Write or regenerate this section only") }) { Text("Generate") }
                        TextButton(enabled = !busy, onClick = { generate(section, "Expand this section, preserving supported claims") }) { Text("Expand") }
                    }
                    Row {
                        TextButton(enabled = !busy, onClick = { generate(section, "Rewrite for clarity without inventing evidence") }) { Text("Rewrite") }
                        TextButton(enabled = !busy, onClick = { generate(section, "Continue the saved draft") }) { Text("Continue") }
                        TextButton(enabled = !busy, onClick = { sectionDelete = sectionId }) { Text("Remove") }
                    }
                    if(sources.length() > 0) ChoiceMenu("Insert citation", "Choose a saved source", (0 until sources.length()).map { "${it + 1}. ${sources.getJSONObject(it).optString("title")}" }) { choice ->
                        val index = choice.substringBefore('.').toInt() - 1; val source = sources.getJSONObject(index)
                        val number = source.optInt("citationNumber", index + 1)
                        vm.projects.update(id) { p -> p.getJSONArray("sources").getJSONObject(index).put("citationNumber", number) }
                        val citation = if(style == "IEEE") "[$number]" else "(${source.optString("author").ifBlank { source.optString("title") }}, ${source.optString("date").take(4).ifBlank { "n.d." }})"
                        updateSection(sectionId) { it.put("text", it.optString("text") + " " + citation); val ids = it.optJSONArray("sourceIds") ?: JSONArray(); if((0 until ids.length()).none { n -> ids.optString(n) == source.getString("id") }) ids.put(source.getString("id")); it.put("sourceIds", ids) }
                    }
                } } }
            }
            TextButton(onClick = { vm.projects.update(id) { p -> val a = p.optJSONArray("sections") ?: JSONArray(); a.put(JSONObject().put("id", UUID.randomUUID().toString()).put("title", "New section").put("text", "").put("sourceIds", JSONArray())); p.put("sections", a) } }) { Text("Add section") }
            Text("Sources and evidence", style = MaterialTheme.typography.titleLarge)
            Text("Search retrieves real Crossref bibliographic metadata and available abstracts. Read the original source before treating a claim as verified.")
            val web = metadata.optBoolean("webAccess")
            Row { Text("Allow web source lookup", Modifier.weight(1f)); Switch(web, { value -> vm.projects.update(id) { it.put("metadata", (it.optJSONObject("metadata") ?: JSONObject()).put("webAccess", value)) } }, enabled = !VideoBackendSettings(context).localOnly) }
            SavedTextField(metadata.optString("sourceQuery"), "Source search terms", { value -> vm.projects.update(id) { it.put("metadata", (it.optJSONObject("metadata") ?: JSONObject()).put("sourceQuery", value)) } })
            Row {
                TextButton(enabled = web && !busy && !VideoBackendSettings(context).localOnly && metadata.optString("sourceQuery").isNotBlank(), onClick = { vm.tasks.submit(id, Operation.WEB_SEARCH, parameters = JSONObject().put("webAccess", true).put("query", metadata.optString("sourceQuery"))) }) { Text("Find sources") }
                TextButton(onClick = { sourceEditor = JSONObject().put("id", UUID.randomUUID().toString()).put("provenance", "Manually entered").put("accessed", java.time.LocalDate.now().toString()) }) { Text("Add source") }
            }
            for(n in 0 until sources.length()) { val source = sources.getJSONObject(n)
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) {
                    Text(CitationFormatter.reference(source, style, n + 1)); Text(source.optString("provenance"), style = MaterialTheme.typography.bodySmall)
                    val cited = (0 until sections.length()).map(sections::getJSONObject).filter { section -> val ids = section.optJSONArray("sourceIds") ?: JSONArray(); (0 until ids.length()).any { ids.optString(it) == source.optString("id") } }.joinToString { it.optString("title") }
                    Text("Used in: ${cited.ifBlank { "Not yet cited" }}")
                    Row {
                        TextButton(onClick = { sourceEditor = JSONObject(source.toString()) }) { Text("Edit / Notes") }
                        TextButton(enabled = !VideoBackendSettings(context).localOnly && source.optString("url").startsWith("https://"), onClick = { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(source.optString("url")))) } }) { Text("Original") }
                        TextButton(enabled = cited.isBlank(), onClick = { vm.projects.update(id) { it.getJSONArray("sources").remove(n) } }) { Text("Remove") }
                    }
                } }
            }
            FindingsEditor(project, vm)
            TextButton(enabled = !busy, onClick = { vm.tasks.submit(id, Operation.CHART) }) { Text("Create chart from numeric findings") }
            Text("Charts use saved numeric values with one common unit. Imported images and charts are included in DOCX, PDF and PPTX exports.")
        }
        "PREVIEW" -> {
            val content = DocumentExporter.content(project)
            Text(content.title, style = MaterialTheme.typography.headlineMedium); Text(content.author)
            var search by remember { mutableStateOf("") }; var font by remember { mutableFloatStateOf(16f) }
            OutlinedTextField(search, { search = it }, label = { Text("Find in document") }, singleLine = true)
            Row { TextButton(onClick = { font = (font - 2).coerceAtLeast(12f) }) { Text("A−") }; TextButton(onClick = { font = (font + 2).coerceAtMost(28f) }) { Text("A+") } }
            content.sections.filter { search.isBlank() || it.title.contains(search, true) || it.text.contains(search, true) }.forEach { section ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp)); Text(section.text, fontSize = font.sp)
                } }
            }
        }
        else -> {
            Text("Exports use the saved sections and source metadata. PPTX creates a concise presentation; XLSX contains structured sources and findings.")
            DocumentExporter.formats.keys.forEach { format -> Button(enabled = !busy && sections.length() > 0, onClick = { vm.tasks.submit(id, Operation.EXPORT, parameters = JSONObject().put("format", format)) }) { Text("Export ${format.uppercase()}") } }
            Text("Exported files appear below and remain available offline.")
        }
    }
    Text(message, color = MaterialTheme.colorScheme.error)
    sectionDelete?.let { target -> AlertDialog(onDismissRequest = { sectionDelete = null }, title = { Text("Remove this section?") }, text = { Text("A saved project version will preserve the current text.") }, confirmButton = { TextButton(onClick = {
        vm.projects.checkpoint(id); vm.projects.update(id) { p -> val a = p.getJSONArray("sections"); for(n in a.length() - 1 downTo 0) if(a.getJSONObject(n).getString("id") == target) a.remove(n) }; sectionDelete = null
    }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { sectionDelete = null }) { Text("Keep") } }) }
    sourceEditor?.let { source -> AlertDialog(onDismissRequest = { sourceEditor = null }, title = { Text("Source metadata") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        listOf("title", "author", "publisher", "date", "url", "doi", "accessed", "abstract", "notes").forEach { field -> SavedTextField(source.optString(field), field, { source.put(field, it) }) }
    } }, confirmButton = { TextButton(onClick = { vm.projects.update(id) { p -> val a = p.optJSONArray("sources") ?: JSONArray(); val index = (0 until a.length()).firstOrNull { a.getJSONObject(it).optString("id") == source.getString("id") }; if(index == null) { source.put("citationNumber", ((0 until a.length()).maxOfOrNull { a.getJSONObject(it).optInt("citationNumber", it + 1) } ?: 0) + 1); a.put(source) } else a.put(index, source); p.put("sources", a) }; sourceEditor = null }) { Text("Save metadata") } }, dismissButton = { TextButton(onClick = { sourceEditor = null }) { Text("Cancel") } }) }
}

@Composable
fun FindingsEditor(project: JSONObject, vm: WorkspaceViewModel) {
    val id = project.getString("projectId"); val rows = project.optJSONArray("findings") ?: JSONArray()
    Text("Structured findings / data", style = MaterialTheme.typography.titleMedium)
    for(n in 0 until rows.length()) { val row = rows.getJSONObject(n)
        Card { Column(Modifier.padding(10.dp)) { listOf("finding", "value", "unit", "source").forEach { field -> SavedTextField(row.optString(field), field, { value -> vm.projects.update(id) { it.getJSONArray("findings").getJSONObject(n).put(field, value) } }) }
            TextButton(onClick = { vm.projects.update(id) { it.getJSONArray("findings").remove(n) } }) { Text("Remove row") }
        } }
    }
    TextButton(onClick = { vm.projects.update(id) { val a = it.optJSONArray("findings") ?: JSONArray(); a.put(JSONObject()); it.put("findings", a) } }) { Text("Add finding / data row") }
}
