package com.coderabyss.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun VideoBackendSettingsPanel() {
    val context = LocalContext.current
    val settings = remember { VideoBackendSettings(context) }
    val scope = rememberCoroutineScope()
    var localOnly by remember { mutableStateOf(settings.localOnly) }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (settings.configured()) "Authentication configured" else "Disconnected") }
    var space by remember { mutableStateOf(com.coderabyss.mobile.remote.SpaceRegistry.configured(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AI Provider / Video Backend", style = MaterialTheme.typography.titleLarge)
        Text("Hugging Face · shared by Video, Visuals, Coding and Research")
        OutlinedTextField(space, { space = it }, label = { Text("Space owner/name") }, singleLine = true)
        TextButton(onClick = { runCatching { com.coderabyss.mobile.remote.SpaceRegistry.configure(context, space.trim()) }.onSuccess { status = "Provider saved. Test connection. Existing jobs keep their original provider." }.onFailure { status = "Invalid Space name. Use owner/Space-name." } }) { Text("Save provider") }
        Row { Text("Local Only", Modifier.weight(1f)); Switch(localOnly, { localOnly = it; settings.localOnly = it; if (!it) com.coderabyss.mobile.tasks.TaskManager(context).recover() }) }
        Text(if (localOnly) "Unavailable while Local Only is enabled." else status)
        OutlinedTextField(token, { token = it }, label = { Text("Private Space credential") }, singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        Text("One-time private development access: your authorized credential is encrypted on this device and reused across services. Normal distribution needs user-specific access or an authenticated gateway; a personal token is never bundled in the APK.")
        Row {
            TextButton(onClick = { runCatching { settings.saveToken(token) }.onSuccess { token = ""; status = if (settings.configured()) "Authentication configured" else "Disconnected" }.onFailure { status = "Could not save authentication" } }) { Text("Save credential") }
            TextButton(enabled = !localOnly && settings.configured(), onClick = {
                status = "Authenticating"
                scope.launch { runCatching { HuggingFaceVideoClient(context).capabilities() }.onSuccess { status = "Connected" }.onFailure { status = "Unavailable — check authentication and backend" } }
            }) { Text("Test Backend") }
        }
    }
}
