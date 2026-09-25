package com.coderabyss.mobile

import com.coderabyss.mobile.models.*
import com.coderabyss.mobile.projects.ProjectRepository
import com.coderabyss.mobile.videoeditor.VideoTimeline
import com.coderabyss.mobile.research.ResearchDraft
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkflowEditingTest {
    private val device = DeviceResources(8_000_000_000, 4_000_000_000, 20_000_000_000, true)
    private fun clip(id: String) = JSONObject().put("id", id).put("path", "assets/source.mp4")
        .put("durationMs", 10000).put("startMs", 1000).put("endMs", 3000).put("volume", 1.0)
    @Test fun videoAlwaysDefaultsToHostedWan() {
        assertEquals("wan", WorkflowRecommendations.defaultModel(Service.VIDEO, device, emptySet(), false))
    }
    @Test fun smallCoderPreferredAndUnsupportedHardwareRoutesRemotely() {
        assertEquals("qwen-coder-1.5b-q4", WorkflowRecommendations.defaultModel(Service.APP, device, emptySet(), false))
        assertEquals("qwen-coder", WorkflowRecommendations.defaultModel(Service.APP, device.copy(arm64 = false), emptySet(), false))
    }
    @Test fun measuredSlowModelsAreExcludedOnlyFromDefaults() {
        val slow = ModelRegistry.forService(Service.APP).filter { it.executionType == ExecutionType.LOCAL }.map { it.id }.toSet()
        assertEquals("qwen-coder", WorkflowRecommendations.defaultModel(Service.APP, device, slow, false, slow))
        assertTrue(ModelRegistry.get("qwen-coder-1.5b-q4").supportedServices.contains(Service.APP))
        assertTrue(DeviceCompatibility.evaluate(ModelRegistry.get("qwen-coder-1.5b-q4"), device).canExecute)
    }
    @Test fun invalidTrimAndCropRejected() {
        assertTrue(runCatching { VideoTimeline.validate(clip("a").put("startMs", 4000)) }.isFailure)
        assertTrue(runCatching { VideoTimeline.validate(clip("a").put("endMs", 11000)) }.isFailure)
        assertTrue(runCatching { VideoTimeline.validate(clip("a").put("left", 1).put("right", -1)) }.isFailure)
    }
    @Test fun sameSourceIndependentRangesPersistAndReorder() {
        val context = RuntimeEnvironment.getApplication()
        val repository = ProjectRepository(context); val id = repository.create(Service.VIDEO)
        repository.update(id) { it.put("timeline", JSONArray().put(clip("a")).put(clip("b").put("startMs", 6000).put("endMs", 9000))) }
        VideoTimeline.edit(repository, id) { it.add(0, it.removeAt(1)); it[0].put("volume", 0) }
        val reopened = ProjectRepository(context).read(id).getJSONArray("timeline")
        assertEquals("b", reopened.getJSONObject(0).getString("id"))
        assertEquals(6000, reopened.getJSONObject(0).getInt("startMs"))
        assertEquals(1000, reopened.getJSONObject(1).getInt("startMs"))
        assertEquals(1.0, reopened.getJSONObject(1).getDouble("volume"), 0.0)
    }
    @Test fun documentHeadingsComeFromGeneratedContent() {
        val sections = ResearchDraft.sections("# Findings\nFirst finding\n## Limits\nScope limits")
        assertEquals(2, sections.length())
        assertEquals("Limits", sections.getJSONObject(1).getString("title"))
        assertEquals("Scope limits", sections.getJSONObject(1).getString("text"))
    }
}
