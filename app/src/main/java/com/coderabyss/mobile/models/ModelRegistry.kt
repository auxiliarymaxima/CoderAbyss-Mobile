package com.coderabyss.mobile.models

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.coderabyss.mobile.*

enum class Service { COMPANION, APP, RESEARCH, VISUAL, VIDEO, VOICE }
enum class ExecutionType { LOCAL, HUGGING_FACE_SPACE }
enum class Compatibility { IDEAL_FOR_DEVICE, SUPPORTED, MAY_RUN_SLOWLY, REMOTE_RECOMMENDED, REMOTE_ONLY }
data class ModelDescriptor(
    val id: String, val name: String, val version: String, val provider: String,
    val executionType: ExecutionType, val capabilities: Set<String>, val supportedServices: Set<Service>,
    val approximateBytes: Long = 0, val minimumRamBytes: Long = 0, val runtimeBytes: Long = 0,
    val localDownloadUrl: String? = null, val huggingFaceSpace: String? = null,
    val huggingFaceModelRepo: String? = null, val requiresGpu: Boolean = false,
    val recommended: Boolean = false, val license: String = "See model source", val local: OfflineModel? = null
)
data class DeviceResources(val totalRam: Long, val availableRam: Long, val freeStorage: Long, val arm64: Boolean, val thermalStatus: Int = 0)
data class CompatibilityResult(val classification: Compatibility, val canExecute: Boolean, val reason: String)

object DeviceCompatibility {
    fun snapshot(context: Context): DeviceResources {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return DeviceResources(info.totalMem, info.availMem,
            (context.getExternalFilesDir(null) ?: context.filesDir).usableSpace,
            Build.SUPPORTED_ABIS.contains("arm64-v8a"),
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus)
    }
    fun evaluate(model: ModelDescriptor, device: DeviceResources): CompatibilityResult {
        if (model.executionType != ExecutionType.LOCAL) return CompatibilityResult(Compatibility.REMOTE_ONLY, false, "Cloud GPU • Internet required")
        if (!device.arm64) return CompatibilityResult(Compatibility.REMOTE_ONLY, false, "This local runtime requires ARM64")
        if (device.totalRam < model.minimumRamBytes || device.availableRam < model.runtimeBytes * 0.85)
            return CompatibilityResult(Compatibility.REMOTE_RECOMMENDED, false, "Not enough available memory. Close other apps or choose a smaller/cloud model.")
        if (device.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE)
            return CompatibilityResult(Compatibility.MAY_RUN_SLOWLY, false, "Let the phone cool before starting local inference.")
        if (device.availableRam < model.runtimeBytes * 1.4)
            return CompatibilityResult(Compatibility.MAY_RUN_SLOWLY, true, "May run slowly; estimated runtime memory ${(model.runtimeBytes / 1e9).toFloat()} GB")
        return CompatibilityResult(if (model.recommended) Compatibility.IDEAL_FOR_DEVICE else Compatibility.SUPPORTED, true, "Local • Private • Offline")
    }
}

object ModelRegistry {
    private const val GB = 1_000_000_000L
    fun all(): List<ModelDescriptor> = ModelCatalog.models.filter { it.kind == ModelKind.TEXT || it.kind == ModelKind.SPEECH }.map { model ->
        val speech = model.kind == ModelKind.SPEECH
        val size = when (model.id) {
            "lfm-2.5-1.2b-q4" -> 730895168L
            "qwen-coder-1.5b-q4" -> 1117320768L
            "qwen-coder-3b-q4" -> 2104932800L
            "gemma-3-1b-q4" -> 806058496L
            "llama-3.2-3b-q4" -> 2019377696L
            "whisper-tiny-en" -> 32166155L
            else -> 147964211L
        }
        ModelDescriptor(model.id, model.name, if (model.id.startsWith("lfm")) "2.5" else model.family,
            "On device", ExecutionType.LOCAL, setOf(if (speech) "speech-to-text" else "text-generation"),
            if (speech) setOf(Service.VOICE) else setOf(Service.COMPANION, Service.APP, Service.RESEARCH),
            size, if (speech) 2 * GB else if (size > 2 * GB) 6 * GB else 4 * GB,
            if (speech) size * 3 + 200_000_000 else (size * 1.5).toLong() + 500_000_000,
            license = when { model.id.startsWith("lfm") -> "LFM Open License v1.0 (commercial revenue conditions; see source)"; model.id.startsWith("qwen") -> "Apache-2.0"; model.id.startsWith("whisper") -> "MIT"; model.id.startsWith("gemma") -> "Gemma Terms of Use"; else -> "Llama 3.2 Community License" },
            localDownloadUrl = model.url, recommended = model.id in setOf("lfm-2.5-1.2b-q4", "qwen-coder-1.5b-q4", "whisper-tiny-en"), local = model)
    } + listOf(
        remote("wan", "Wan", "2.1 1.3B", setOf(Service.VIDEO), "Wan-AI/Wan2.1-T2V-1.3B-Diffusers", setOf("text-to-video"), "Apache-2.0"),
        remote("ltx", "LTX-Video", "0.9.7 distilled 13B", setOf(Service.VIDEO), "Lightricks/LTX-Video-0.9.7-distilled", setOf("text-to-video", "continuation"), "LTX Open Weights 0.X"),
        remote("sdxl", "SDXL", "1.0", setOf(Service.VISUAL), "stabilityai/stable-diffusion-xl-base-1.0", setOf("text-to-image"), "CreativeML Open RAIL++-M"),
        remote("qwen-coder", "Qwen Coder", "2.5 7B", setOf(Service.APP), "Qwen/Qwen2.5-Coder-7B-Instruct", setOf("text-generation", "coding"), "Apache-2.0"),
        remote("qwen-research", "Qwen Research", "2.5 7B", setOf(Service.RESEARCH, Service.COMPANION), "Qwen/Qwen2.5-7B-Instruct", setOf("text-generation"), "Apache-2.0")
    )
    private fun remote(id: String, name: String, version: String, services: Set<Service>, repo: String, caps: Set<String>, license: String) =
        ModelDescriptor(id, name, version, "Hugging Face", ExecutionType.HUGGING_FACE_SPACE, caps, services,
            huggingFaceSpace = VideoBackendSettings.SPACE, huggingFaceModelRepo = repo, requiresGpu = true, recommended = true, license = license)
    fun get(id: String) = all().first { it.id == id }
    fun forService(service: Service) = all().filter { service in it.supportedServices }
}

enum class InferenceRoute { LOCAL_INFERENCE, HUGGING_FACE_INFERENCE }
object InferenceRouter {
    fun route(service: Service, model: ModelDescriptor, device: DeviceResources, localOnly: Boolean, online: Boolean): InferenceRoute {
        require(service in model.supportedServices) { "This model does not support this service." }
        if (model.executionType == ExecutionType.LOCAL) {
            val result = DeviceCompatibility.evaluate(model, device)
            check(result.canExecute) { result.reason }
            return InferenceRoute.LOCAL_INFERENCE
        }
        check(!localOnly) { "Unavailable in Local Only mode." }
        check(online) { "Waiting for connection" }
        return InferenceRoute.HUGGING_FACE_INFERENCE
    }
}
