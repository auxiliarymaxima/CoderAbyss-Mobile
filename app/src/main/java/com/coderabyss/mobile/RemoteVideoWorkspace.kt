package com.coderabyss.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.coderabyss.mobile.account.CoderAbyssBackendClient
import kotlinx.coroutines.launch

@Composable
fun VideoBackendSettingsPanel() {
    val context = LocalContext.current; val settings = remember { VideoBackendSettings(context) }
    var localOnly by remember { mutableStateOf(settings.localOnly) }; var status by remember { mutableStateOf("Not checked") }
    var busy by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AI Services", style = MaterialTheme.typography.titleLarge)
        Text("Local AI · Available with installed models")
        Row { Text("Local Only", Modifier.weight(1f)); Switch(localOnly, { localOnly = it; settings.localOnly = it; if(!it) com.coderabyss.mobile.tasks.TaskManager(context).recover() }) }
        Text(if(localOnly) "Cloud AI · Unavailable while Local Only is enabled." else "Cloud AI · $status")
        TextButton(enabled = !localOnly && !busy, onClick = { busy = true; scope.launch {
            runCatching { CoderAbyssBackendClient(context).capabilities() }.onSuccess { status = "Connected" }.onFailure { status = "Unavailable — sign in and check your account" }; busy = false
        } }) { Text("Test Connection") }
    } }
}
