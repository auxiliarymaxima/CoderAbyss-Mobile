package com.coderabyss.mobile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coderabyss.mobile.*
import com.coderabyss.mobile.account.*
import com.coderabyss.mobile.models.Service
import com.coderabyss.mobile.platformui.*
import com.coderabyss.mobile.tasks.PersistentTaskStore

@Composable
fun AbyssTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = AbyssBlue, secondary = AbyssBlue2,
        background = AbyssBlack, surface = AbyssPanel, surfaceVariant = AbyssPanel2,
        onSurface = AbyssText, onBackground = AbyssText, onSurfaceVariant = AbyssMuted,
        outline = AbyssMuted, error = AbyssDanger),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(17.dp), large = RoundedCornerShape(20.dp)), content = content)
}

/** v0.6 visual shell. All long-running work remains owned by the v0.7 task system. */
@Composable
fun CoderAbyssShell(vm: WorkspaceViewModel = viewModel()) {
    val context = LocalContext.current
    val auth = remember { AuthRepository.get(context) }
    val uid by auth.uid.collectAsStateWithLifecycle()
    var localEntry by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf("Home") }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var showTasks by remember { mutableStateOf(false) }
    val tasks by remember(vm) { vm.tasks.observe() }.collectAsStateWithLifecycle(emptyList())
    val open: (String) -> Unit = { projectId = it; page = "Workspace" }
    val service: (Service) -> Unit = { type -> open(vm.projects.all().firstOrNull { it.optString("type") == type.name }?.getString("projectId") ?: vm.projects.create(type)) }
    val notifications = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("ui_permissions", 0)
        if(!prefs.getBoolean("notifications_asked", false)) { prefs.edit().putBoolean("notifications_asked", true).apply(); notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
    LaunchedEffect(uid) { AuthorizationRepository.clear(); if(uid != null && !VideoBackendSettings(context).localOnly) runCatching { AuthorizationRepository.refresh(context) } }
    if(uid == null && !localEntry && !VideoBackendSettings(context).localOnly) {
        Column(Modifier.fillMaxSize().background(AbyssBlack).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Header(); Spacer(Modifier.height(32.dp)); AssistantFace(); Spacer(Modifier.height(24.dp))
            Text("Your private AI creation environment", color = AbyssMuted)
            AccountPanel(compact = true)
            TextButton(onClick = { localEntry = true }) { Text("Continue with local projects") }
        }; return
    }
    Scaffold(containerColor = AbyssBlack, topBar = {
        Column(Modifier.statusBarsPadding()) {
            if(tasks.isNotEmpty()) TextButton(onClick = { showTasks = true }) { Text("Tasks · ${tasks.count { it.optString("status") !in PersistentTaskStore.terminal }}") }
        }
    }, bottomBar = { AbyssBottomBar(listOf("Home", "Projects", "Models", "Settings").indexOf(page)) { page = listOf("Home", "Projects", "Models", "Settings")[it] } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(AbyssBlack, AbyssPanel, AbyssBlack)))) {
            when(page) {
                "Home" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Header(); Spacer(Modifier.height(26.dp)); AssistantFace(); Spacer(Modifier.height(18.dp))
                    val companionId = remember { vm.projects.all().firstOrNull { it.optString("type") == Service.COMPANION.name }?.getString("projectId") ?: vm.projects.create(Service.COMPANION) }
                    PersistentVoiceControl(companionId, vm, tasks, { page = "Models" }, prominent = true)
                    TextButton(onClick = { open(companionId) }) { Text("Open AI Companion · Edit prompt and send") }
                    Spacer(Modifier.height(16.dp)); StatusPill(if(VideoBackendSettings(context).localOnly) "Local AI · Offline" else "Local AI + Cloud AI")
                    Spacer(Modifier.height(24.dp)); QuickActions { service(when(it) { FeatureType.APP -> Service.APP; FeatureType.VIDEO -> Service.VIDEO; FeatureType.RESEARCH -> Service.RESEARCH; FeatureType.VISUALS -> Service.VISUAL }) }
                    Spacer(Modifier.height(22.dp)); ModelsPanel(remember { OfflineModelManager(context) }) { page = "Models" }
                }
                "Projects" -> ProjectsScreen(vm, open)
                "Models" -> PlatformModelsScreen({ page = "Home" }, { page = "Settings" })
                "Settings" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScreenHeader("Settings", "Your account, models and AI services") { page = "Home" }
                    AccountPanel(); VideoBackendSettingsPanel()
                }
                "Workspace" -> projectId?.let { WorkspaceScreen(it, vm, { page = "Projects" }, { page = "Models" }, { page = "Settings" }) }
            }
        }
    }
    if(showTasks) AlertDialog(onDismissRequest = { showTasks = false }, title = { Text("Tasks") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        tasks.forEach { task -> TaskCard(task, vm) { showTasks = false; if(task.optString("operation") == "MODEL_DOWNLOAD") page = "Models" else if(runCatching { vm.projects.read(task.optString("projectId")) }.isSuccess) open(task.getString("projectId")) } }
    } }, confirmButton = { TextButton(onClick = { showTasks = false }) { Text("Close") } })
}
