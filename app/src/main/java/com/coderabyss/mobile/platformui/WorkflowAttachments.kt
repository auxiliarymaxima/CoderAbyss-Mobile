package com.coderabyss.mobile.platformui

import android.content.Intent
import androidx.compose.material.icons.rounded.AttachFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.coderabyss.mobile.tasks.Operation
import org.json.JSONObject

@Composable
fun WorkflowAttachments(project: JSONObject, vm: WorkspaceViewModel, compact: Boolean = false) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val mime = context.contentResolver.getType(it) ?: "application/octet-stream"
                vm.tasks.submit(project.getString("projectId"), Operation.IMPORT_ASSET, parameters = JSONObject()
                    .put("uri", it.toString()).put("mimeType", mime)
                    .put("extension", android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"))
                message = "Import queued"
            }.onFailure { message = "Could not import this file" }
        }
    }
    if(compact) WorkflowRoundAction(androidx.compose.material.icons.Icons.Rounded.AttachFile, "Add Files", onClick = { picker.launch(arrayOf("*/*")) })
    else OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Add Files") }
    if(message.isNotBlank() && !compact) Text(message)
    if(compact && message.isNotBlank() && message != "Import queued") AlertDialog(onDismissRequest = { message = "" }, title = { Text("Import file") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { message = "" }) { Text("OK") } })
    if(!compact) Text("Files are saved as project assets. Only supported generation inputs are sent to the model.", style = MaterialTheme.typography.bodySmall)
}
