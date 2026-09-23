package com.coderabyss.mobile.platformui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.coderabyss.mobile.*
import com.coderabyss.mobile.models.*
import com.coderabyss.mobile.remote.SpaceRegistry
import kotlinx.coroutines.delay

@Composable
fun ModelPicker(service: Service, selected: String, onSelect: (String) -> Unit, onModels: () -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("Models ▾") }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Models for ${service.name.lowercase()}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            for (execution in ExecutionType.entries) {
                Text(if (execution == ExecutionType.LOCAL) "ON DEVICE" else "CLOUD GPU", style = MaterialTheme.typography.labelLarge)
                ModelRegistry.forService(service).filter { it.executionType == execution }.forEach { model ->
                    TextButton(onClick = { onSelect(model.id); open = false }) { Text("${if (model.id == selected) "✓ " else ""}${model.name} ${model.version}") }
                    Text(if (execution == ExecutionType.LOCAL) DeviceCompatibility.evaluate(model, DeviceCompatibility.snapshot(context)).reason
                        else if (VideoBackendSettings(context).localOnly) "Unavailable in Local Only mode." else if (SpaceRegistry.capability(context, model.id)?.optBoolean("available") == true) "Available on Cloud GPU" else "Sign in / Test AI Services", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { open = false; onModels() }) { Text("AI Models") } })
}

@Composable
fun PlatformModelsScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current; val manager = remember { OfflineModelManager(context) }
    val activeModel by LocalLlmEngine.activeModelPath.collectAsState()
    var tick by remember { mutableIntStateOf(0) }; var error by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ON DEVICE") }; var requirements by remember { mutableStateOf<ModelDescriptor?>(null) }
    LaunchedEffect(Unit) { while (true) { tick++; delay(1000) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.coderabyss.mobile.presentation.ScreenHeader("AI Models", "On-device models and authorized cloud services", onBack)
        Column { listOf("ON DEVICE", "CLOUD GPU", "INSTALLED", "AVAILABLE").chunked(2).forEach { choices -> Row { choices.forEach { choice -> FilterChip(filter == choice, { filter = choice }, label = { Text(choice) }) } } } }
        var wifiOnly by remember { mutableStateOf(manager.wifiOnly()) }
        Row { Text("Download on Wi-Fi only", Modifier.weight(1f)); Switch(wifiOnly, { wifiOnly = it; manager.setWifiOnly(it) }) }
        Text("Downloads install automatically only after full size and SHA-256 verification.")
        Text(error, color = MaterialTheme.colorScheme.error)
        ModelRegistry.all().filter { when(filter) { "CLOUD GPU" -> it.executionType == ExecutionType.HUGGING_FACE_SPACE; "INSTALLED" -> it.local?.let(manager::isInstalled) == true; "AVAILABLE" -> it.local?.let(manager::isInstalled) != true; else -> it.executionType == ExecutionType.LOCAL } }.forEach { model ->
            Card(Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF0A587A))) { Column(Modifier.padding(14.dp)) {
                Text(model.name, style = MaterialTheme.typography.titleMedium); Text(model.version)
                if (model.local != null) {
                    val state = remember(model.id, tick) { manager.state(model.local) }
                    val compatibility = DeviceCompatibility.evaluate(model, DeviceCompatibility.snapshot(context))
                    Text("${model.local.sizeLabel} · ${compatibility.classification.name.replace('_', ' ')}")
                    if(activeModel == manager.modelFile(model.local).path) Text("Active — local text model")
                    Text(state.status.name.replace('_', ' ')); if (state.status in setOf(TransferStatus.DOWNLOADING, TransferStatus.PAUSED, TransferStatus.QUEUED, TransferStatus.VERIFYING)) Text("${state.progress}% · ${state.downloaded} / ${state.total} bytes")
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                    Row {
                        if (state.status == TransferStatus.INSTALLED) {
                            if (model.local.kind == ModelKind.TEXT) TextButton(onClick = { manager.select(model.local) }) { Text(if (manager.selectedModelId() == model.id) "Selected" else "Use") }
                            TextButton(onClick = { manager.remove(model.local) }) { Text("Remove") }
                        } else {
                            TextButton(enabled = !VideoBackendSettings(context).localOnly && state.status !in setOf(TransferStatus.DOWNLOADING, TransferStatus.VERIFYING), onClick = { runCatching { manager.startDownload(model.local) }.onFailure { error = "Download could not start. Check available storage and connection." } }) { Text(if (state.status == TransferStatus.PAUSED) "Resume" else if (state.status == TransferStatus.FAILED) "Retry" else "Download") }
                            if (state.status in setOf(TransferStatus.DOWNLOADING, TransferStatus.QUEUED) && manager.managedTask(model.local) != null) TextButton(onClick = { manager.pauseDownload(model.local) }) { Text("Pause") }
                            if (state.status in setOf(TransferStatus.DOWNLOADING, TransferStatus.PAUSED, TransferStatus.QUEUED, TransferStatus.FAILED)) TextButton(onClick = { manager.cancelDownload(model.local) }) { Text("Cancel") }
                        }
                    }
                } else {
                    Text("Cloud GPU · ${model.supportedServices.joinToString { it.name.lowercase() }}")
                    val capability = SpaceRegistry.capability(context, model.id)
                    Text(if (VideoBackendSettings(context).localOnly) "Unavailable in Local Only mode." else if (capability?.optBoolean("available") == true) "Available" else "Check AI Services")
                    TextButton(onClick = onSettings) { Text("Configure / Test Connection") }
                }
                TextButton(onClick = { requirements = model }) { Text("Requirements / Source") }
            } }
        }
        if (manager.modelFile(ModelCatalog.wan).exists() || ModelCatalog.wan.additionalFiles.any { java.io.File(manager.modelDirectory, it).exists() }) {
            Text("Legacy local video model files are retained. Video now runs remotely.")
            TextButton(onClick = { manager.remove(ModelCatalog.wan); tick++ }) { Text("Remove legacy video weights") }
        }
    }
    requirements?.let { model -> AlertDialog(onDismissRequest = { requirements = null }, title = { Text(model.name) }, text = {
        Text("${model.capabilities.joinToString()}\n${if (model.requiresGpu) "Remote GPU required" else "Estimated runtime RAM: ${model.runtimeBytes / 1_000_000} MB; minimum total RAM: ${model.minimumRamBytes / 1_000_000} MB"}\n${model.license}\n${model.huggingFaceModelRepo ?: model.localDownloadUrl.orEmpty()}")
    }, confirmButton = { TextButton(onClick = { requirements = null }) { Text("Close") } }) }
}
