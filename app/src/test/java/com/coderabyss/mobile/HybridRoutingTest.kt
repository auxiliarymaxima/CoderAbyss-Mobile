package com.coderabyss.mobile

import com.coderabyss.mobile.models.*
import org.junit.Assert.*
import org.junit.Test

class HybridRoutingTest {
    private val device = DeviceResources(8_000_000_000, 5_000_000_000, 20_000_000_000, true)
    @Test fun heavyModelsNeverRouteLocally() {
        listOf("wan", "ltx", "sdxl", "qwen-coder", "qwen-research").forEach { id ->
            val model = ModelRegistry.get(id)
            assertNull(model.localDownloadUrl)
            assertEquals(Compatibility.REMOTE_ONLY, DeviceCompatibility.evaluate(model, device).classification)
            assertThrows(IllegalStateException::class.java) { InferenceRouter.route(model.supportedServices.first(), model, device, true, true) }
        }
    }
    @Test fun localCompanionWorksOfflineButRejectsResourceShortage() {
        val model = ModelRegistry.get("lfm-2.5-1.2b-q4")
        assertEquals(InferenceRoute.LOCAL_INFERENCE, InferenceRouter.route(Service.COMPANION, model, device, true, false))
        assertFalse(DeviceCompatibility.evaluate(model, device.copy(availableRam = 100_000_000)).canExecute)
        assertFalse(DeviceCompatibility.evaluate(model, device.copy(arm64 = false)).canExecute)
    }
    @Test fun modelsCannotCrossUnsupportedServices() {
        assertThrows(IllegalArgumentException::class.java) { InferenceRouter.route(Service.RESEARCH, ModelRegistry.get("sdxl"), device, false, true) }
    }
}
