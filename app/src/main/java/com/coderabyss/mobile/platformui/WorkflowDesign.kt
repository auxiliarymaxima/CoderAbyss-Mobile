package com.coderabyss.mobile.platformui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coderabyss.mobile.models.Service
import com.coderabyss.mobile.tasks.PersistentTaskStore
import org.json.JSONArray
import org.json.JSONObject

internal val WorkflowCyan = Color(0xFF00DEFF)
internal val WorkflowViolet = Color(0xFFB52CFF)
internal val WorkflowNavy = Color(0xFF041524)
internal val WorkflowMuted = Color(0xFFA7BED3)
internal val WorkflowBorder = Color(0xFF155477)
internal val WorkflowGradient get() = Brush.horizontalGradient(listOf(Color(0xFF00CFEF), Color(0xFF3058FF), Color(0xFFBC21F4)))

internal fun workflowIcon(service: Service): ImageVector = when(service) {
    Service.APP -> Icons.Rounded.Code
    Service.VIDEO -> Icons.Rounded.Videocam
    Service.RESEARCH -> Icons.Rounded.Description
    else -> Icons.Rounded.Image
}

@Composable
internal fun WorkflowBrand(onBack: () -> Unit, onSettings: () -> Unit, actions: List<Pair<String, () -> Unit>> = emptyList()) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to projects", tint = WorkflowCyan) }
        Spacer(Modifier.weight(1f))
        Icon(androidx.compose.ui.res.painterResource(com.coderabyss.mobile.R.drawable.ic_abyss_foreground), null, tint = Color.Unspecified, modifier = Modifier.size(42.dp))
        Spacer(Modifier.width(8.dp))
        Text("Coder ", color = WorkflowCyan, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Abyss", color = Color(0xFF8B6FFF), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { if(actions.isEmpty()) onSettings() else menu = true }) { Icon(if(actions.isEmpty()) Icons.Rounded.Settings else Icons.Rounded.MoreVert, if(actions.isEmpty()) "Settings" else "Workflow options", tint = WorkflowCyan) }
            DropdownMenu(menu, { menu = false }) { (actions + ("Settings" to onSettings)).forEach { (label, action) -> DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; action() }) } }
        }
    }
    HorizontalDivider(color = WorkflowBorder.copy(alpha = .5f))
}

@Composable
internal fun WorkflowBadge(service: Service) {
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(
        Brush.linearGradient(if(service == Service.VIDEO) listOf(Color(0xFFFF5FE8), WorkflowViolet, Color(0xFF4160FF)) else listOf(WorkflowCyan, Color(0xFF134AFF)))), contentAlignment = Alignment.Center) {
        Icon(workflowIcon(service), null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
internal fun WorkflowPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(Color(0xFF081D31), WorkflowNavy)))
        .border(1.dp, WorkflowBorder, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
internal fun WorkflowAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
        .clip(RoundedCornerShape(16.dp)).background(if(enabled) WorkflowGradient else Brush.horizontalGradient(listOf(Color(0xFF183D56), Color(0xFF34254B))))
        .border(1.dp, if(enabled) Color(0xFF64DFFF) else WorkflowBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent, contentColor = Color.White, disabledContentColor = WorkflowMuted)) {
        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(23.dp)); Spacer(Modifier.width(10.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
internal fun WorkflowRoundAction(icon: ImageVector, label: String, enabled: Boolean = true, glowing: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).background(Brush.radialGradient(listOf(if(glowing) WorkflowViolet.copy(alpha = .45f) else WorkflowCyan.copy(alpha = .12f), Color.Transparent)), CircleShape), contentAlignment = Alignment.Center) {
            IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(60.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(if(glowing) Color(0xFF352072) else WorkflowNavy, Color(0xFF050C1A))))
                .border(if(glowing) 2.dp else 1.dp, if(enabled) Brush.linearGradient(listOf(WorkflowCyan, Color.White, WorkflowViolet)) else Brush.linearGradient(listOf(WorkflowBorder, WorkflowBorder)), CircleShape)) {
                Icon(icon, label, tint = if(enabled) Color.White else WorkflowMuted, modifier = Modifier.size(28.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
internal fun WorkflowPrompt(value: String, hint: String, counter: String, invalid: Boolean, compact: Boolean, voice: (@Composable () -> Unit)? = null, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }; var saved by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if(value != saved) { text = value; saved = value } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WorkflowNavy)
        .border(1.dp, if(invalid) MaterialTheme.colorScheme.error else Color(0xFF637FDA), RoundedCornerShape(14.dp))) {
        TextField(text, { text = it; saved = it; onChange(it) }, placeholder = { Text(hint, color = WorkflowMuted) },
            modifier = Modifier.fillMaxWidth().height(if(voice != null) 72.dp else if(compact) 124.dp else 172.dp), textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = WorkflowCyan))
        Text(counter, Modifier.align(Alignment.End).padding(end = 14.dp, bottom = 10.dp), color = if(invalid) MaterialTheme.colorScheme.error else WorkflowMuted, style = MaterialTheme.typography.labelSmall)
        if(voice != null) Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) { voice() }
    }
}

@Composable
internal fun WorkflowOption(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(WorkflowNavy, Color(0xFF062139))))
        .border(1.dp, WorkflowBorder, RoundedCornerShape(12.dp)).then(if(onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = WorkflowCyan, modifier = Modifier.size(25.dp)); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(label, color = WorkflowMuted, style = MaterialTheme.typography.labelSmall); Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        if(onClick != null) Icon(Icons.Rounded.ExpandMore, null, tint = WorkflowCyan, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun WorkflowChoice(label: String, value: String, values: List<String>, icon: ImageVector, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        WorkflowOption(label, value, icon, onClick = if(values.isEmpty()) null else { { open = true } })
        DropdownMenu(open, { open = false }) { values.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); open = false }) } }
    }
}

@Composable
internal fun WorkflowTabs(tabs: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth()) { tabs.forEach { name ->
        Column(Modifier.weight(1f).clickable { onSelect(name) }.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, color = if(name == selected) WorkflowCyan else WorkflowMuted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(9.dp)); HorizontalDivider(thickness = if(name == selected) 2.dp else 1.dp, color = if(name == selected) WorkflowCyan else WorkflowBorder.copy(alpha = .4f))
        }
    } }
}

@Composable
internal fun WorkflowNavigation(service: Service, onHome: () -> Unit, onService: (Service) -> Unit, onProjects: () -> Unit, onModels: () -> Unit, onSettings: () -> Unit, onTasks: () -> Unit) {
    var more by remember { mutableStateOf(false) }
    Surface(color = Color(0xFF020A14)) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = WorkflowBorder)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                @Composable fun Item(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, action: () -> Unit) {
                    Column(modifier.clickable(onClick = action).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(icon, label, tint = if(active) WorkflowCyan else WorkflowMuted, modifier = Modifier.size(23.dp))
                        Text(label, color = if(active) WorkflowCyan else WorkflowMuted, style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.width(26.dp).height(2.dp).background(if(active) WorkflowCyan else Color.Transparent))
                    }
                }
                Item("Home", Icons.Rounded.Home, false, Modifier.weight(1f), onHome)
                Item("Video", Icons.Rounded.SmartDisplay, service == Service.VIDEO, Modifier.weight(1f)) { onService(Service.VIDEO) }
                Item("Research", Icons.Rounded.Description, service == Service.RESEARCH, Modifier.weight(1f)) { onService(Service.RESEARCH) }
                val fourth = if(service == Service.VISUAL) Service.VISUAL else Service.APP
                Item(if(fourth == Service.VISUAL) "Visuals" else "Build", workflowIcon(fourth), service == fourth, Modifier.weight(1f)) { onService(fourth) }
                Box(Modifier.weight(1f)) {
                    Item("More", Icons.Rounded.Menu, false, Modifier.fillMaxWidth()) { more = true }
                    DropdownMenu(more, { more = false }) {
                        listOf("Create Visuals" to { onService(Service.VISUAL) }, "Build an App" to { onService(Service.APP) }, "Projects" to onProjects, "AI Models" to onModels, "All tasks" to onTasks, "Settings" to onSettings).forEach { (label, action) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { more = false; action() })
                        }
                    }
                }
            }
        }
    }
}

/** Only recorded stages are displayed. No estimated completion or synthetic steps. */
@Composable
internal fun WorkflowProgress(task: JSONObject, vm: WorkspaceViewModel) {
    val status = task.optString("status"); val finished = status in PersistentTaskStore.terminal
    val successful = status == "COMPLETED"
    var details by remember(task.optString("taskId")) { mutableStateOf(false) }
    LaunchedEffect(status) { if(status in setOf("FAILED", "INTERRUPTED", "DOWNLOAD_FAILED", "SUBMISSION_UNCERTAIN", "UNKNOWN", "WAITING_FOR_CONNECTION")) details = true }
    val log = task.optJSONArray("log") ?: JSONArray()
    val stages = (0 until log.length()).map(log::getJSONObject).filter { it.optString("stage").isNotBlank() }.takeLast(6)
    WorkflowPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if(successful) "Complete" else if(finished) status.replace('_', ' ') else "In progress", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if(!finished) OutlinedButton(onClick = { vm.tasks.cancel(task.getString("taskId")) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF778C))) { Icon(Icons.Rounded.Stop, null, Modifier.size(16.dp)); Text("Stop") }
        }
        val elapsed = ((if(finished) task.optLong("completedAt", task.optLong("updatedAt")) else System.currentTimeMillis()) - task.optLong("createdAt")).coerceAtLeast(0) / 1000
        Text("${elapsed / 60}m ${elapsed % 60}s elapsed", color = WorkflowMuted, style = MaterialTheme.typography.bodySmall)
        stages.forEachIndexed { index, stage ->
            val past = index < stages.lastIndex || successful
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if(past) Icons.Rounded.CheckCircle else if(finished) Icons.Rounded.Info else Icons.Rounded.RadioButtonChecked, null, tint = if(past) Color(0xFF00DCA0) else WorkflowCyan, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp)); Text(stage.optString("stage"), style = MaterialTheme.typography.bodyMedium)
            }
            if(index < stages.lastIndex) Box(Modifier.padding(start = 12.dp).width(2.dp).height(16.dp).background(WorkflowBorder))
        }
        if(stages.isEmpty()) Text(task.optString("stage", status), color = WorkflowCyan)
        val progress = when {
            task.optLong("totalBytes") > 0 -> task.optLong("downloadedBytes").toFloat() / task.optLong("totalBytes")
            task.optString("operation") == "VIDEO_EDIT" && task.has("progress") -> task.optInt("progress") / 100f
            task.optJSONObject("remoteStatus")?.let { it.has("progress") && !it.isNull("progress") } == true -> task.getJSONObject("remoteStatus").optDouble("progress").toFloat()
            else -> null
        }
        if(!finished) {
            if(progress != null && progress.isFinite()) { LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = WorkflowCyan); Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", color = WorkflowMuted) }
            else LinearProgressIndicator(Modifier.fillMaxWidth(), color = WorkflowCyan)
        }
        if(task.optString("partial").isNotBlank()) Text(task.optString("partial").takeLast(1600), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        WorkflowOption("Diagnostics & retry", if(details) "Hide full logs" else "View full logs", Icons.Rounded.Terminal, onClick = { details = !details })
        if(details) TaskCard(task, vm)
    }
}
