package com.coderabyss.mobile.models

/** Recommendations do not imply measured accelerator support or generation time. */
object WorkflowRecommendations {
    fun defaultModel(service: Service, device: DeviceResources, installed: Set<String>, localOnly: Boolean, measuredSlow: Set<String> = emptySet()): String {
        val models = ModelRegistry.forService(service)
        val local = models.filter { it.id !in measuredSlow && it.executionType == ExecutionType.LOCAL && DeviceCompatibility.evaluate(it, device).canExecute && (it.id in installed || device.freeStorage > it.approximateBytes * 1.15) }
            .sortedWith(compareBy<ModelDescriptor> { it.id !in installed }.thenBy { if(service == Service.APP) !it.id.startsWith("qwen-coder-1.5b") else !it.recommended }.thenBy { it.runtimeBytes })
        if(service in setOf(Service.APP, Service.RESEARCH) && local.isNotEmpty() && local.first().runtimeBytes <= 3_000_000_000L) return local.first().id
        return (if(localOnly) local.firstOrNull() else models.firstOrNull { it.executionType == ExecutionType.HUGGING_FACE_SPACE })?.id
            ?: models.first().id
    }
    fun needsWarning(model: ModelDescriptor, device: DeviceResources) =
        model.executionType == ExecutionType.LOCAL && DeviceCompatibility.evaluate(model, device).canExecute &&
            (model.runtimeBytes > 3_000_000_000L || DeviceCompatibility.evaluate(model, device).classification == Compatibility.MAY_RUN_SLOWLY)
}
