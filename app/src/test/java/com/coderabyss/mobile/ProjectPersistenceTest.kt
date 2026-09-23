package com.coderabyss.mobile

import android.app.Application
import com.coderabyss.mobile.models.Service
import com.coderabyss.mobile.projects.ProjectRepository
import com.coderabyss.mobile.tasks.PersistentTaskStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProjectPersistenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Test fun managedCopySurvivesOriginalDeletionAndCannotEscapeProject() {
        val repo = ProjectRepository(context); val a = repo.create(Service.VISUAL); val b = repo.create(Service.APP)
        repo.file(a, "outputs/fixture.txt").writeText("user-owned asset")
        val copied = repo.copyAsset(a, "outputs/fixture.txt", b)
        repo.delete(a)
        assertEquals("user-owned asset", ProjectRepository(context).file(b, copied).readText())
        assertThrows(IllegalArgumentException::class.java) { repo.file(b, "../../outside.txt") }
        val copy = repo.duplicate(b)
        repo.delete(b)
        assertEquals("user-owned asset", repo.file(copy, copied).readText())
    }
    @Test fun legacyVideoMigrationPreservesSameJobAndPrompt() {
        val id = UUID.randomUUID().toString(); val dir = File(context.filesDir, "Projects/Videos/$id").apply { mkdirs() }
        val original = JSONObject().put("projectId", id).put("title", "Older video").put("backend", VideoBackendSettings.SPACE)
            .put("jobId", "same-backend-job").put("status", "GENERATING").put("request", JSONObject().put("engine", "wan").put("prompt", "Saved old prompt"))
        File(dir, "project.json").writeText(original.toString())
        val repo = ProjectRepository(context); repo.migrate(); repo.migrate()
        val recovered = ProjectRepository(context).read(id)
        assertEquals("same-backend-job", recovered.getString("jobId")); assertEquals("Saved old prompt", recovered.getString("prompt"))
        assertEquals("VIDEO", recovered.getString("type")); assertEquals("GENERATING", recovered.getString("status"))
        assertEquals(2, recovered.getInt("schemaVersion"))
    }
    @Test fun taskRestartPreservesProviderRequestIdentityAndPartialText() {
        val id = UUID.randomUUID().toString(); val store = PersistentTaskStore(context)
        store.write(JSONObject().put("taskId", id).put("jobId", "original-job").put("clientRequestId", "original-request")
            .put("space", "owner/original").put("status", "GENERATING").put("partial", "saved draft").put("log", JSONArray()))
        store.update(id) { it.put("status", "WAITING_FOR_CONNECTION").put("stage", "Connection interrupted") }
        val recovered = PersistentTaskStore(context).read(id)
        assertEquals("original-job", recovered.getString("jobId")); assertEquals("original-request", recovered.getString("clientRequestId"))
        assertEquals("owner/original", recovered.getString("space")); assertEquals("saved draft", recovered.getString("partial"))
        assertEquals(1, recovered.getJSONArray("log").length())
    }
    @Test fun localOnlyStopsClientBeforeCredentialsOrHttp() = runBlocking {
        VideoBackendSettings(context).localOnly = true
        try {
            try { HuggingFaceSpaceClient(context).getVideoJobStatus(UUID.randomUUID().toString()); fail("Network request must be rejected") }
            catch(e: IllegalStateException) { assertTrue(e.message!!.contains("Local Only")) }
        } finally { VideoBackendSettings(context).localOnly = false }
    }
}
