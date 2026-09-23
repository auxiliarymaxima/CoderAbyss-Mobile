package com.coderabyss.mobile.account

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coderabyss.mobile.VideoBackendSettings
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun AccountPanel(compact: Boolean = false) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val auth = remember { AuthRepository.get(context) }; val uid by auth.uid.collectAsStateWithLifecycle()
    val access by AuthorizationRepository.access.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var products by remember { mutableStateOf(emptyList<com.android.billingclient.api.ProductDetails>()) }
    val billing = remember { SubscriptionRepository(context, scope) { message = it } }
    DisposableEffect(billing) { onDispose { billing.close() } }
    fun action(block: suspend () -> Unit) { if(busy) return; busy = true; scope.launch { try { block() } catch(_: Exception) { message = "Account service unavailable. Check your connection and sign in again; local projects remain available." } finally { busy = false } } }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if(!compact) Text("Account", style = MaterialTheme.typography.titleLarge)
        if(uid == null) {
            Button(enabled = !busy && !VideoBackendSettings(context).localOnly, onClick = { action { auth.signIn(context as Activity); AuthorizationRepository.refresh(context) } }) { Text("Continue with Google") }
            if(!auth.configured) Text("Account sign-in is not configured for this build. Local projects are available.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(auth.displayName); Text("Plan: ${access.plan} · ${access.state}")
            TextButton(enabled = !busy, onClick = { action { AuthorizationRepository.refresh(context); message = "Account refreshed" } }) { Text("Refresh account") }
            TextButton(onClick = { action { auth.signOut(); products = emptyList(); message = "Signed out. Local projects preserved." } }) { Text("Sign out") }
            if(access.role !in setOf(AccountRole.OWNER, AccountRole.ADMIN)) {
                TextButton(onClick = { billing.connect { products = it } }) { Text("View subscriptions") }
                products.forEach { product -> product.subscriptionOfferDetails.orEmpty().forEach { offer ->
                    val price = offer.pricingPhases.pricingPhaseList.joinToString(" → ") { "${it.formattedPrice} / ${it.billingPeriod}" }
                    OutlinedButton(onClick = { runCatching { billing.purchase(context as Activity, product, offer.offerToken) }.onFailure { message = "Sign in and disable Local Only before purchasing" } }) { Text("${product.name} · $price") }
                } }
                TextButton(onClick = { billing.restore() }) { Text("Restore purchases") }
            }
        }
        if(message.isNotBlank()) Text(message)
    } }
    if(!compact && uid == access.uid && access.role in setOf(AccountRole.OWNER, AccountRole.ADMIN)) AdministrationPanel(access.role)
}

@Composable
private fun AdministrationPanel(role: AccountRole) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }; var users by remember { mutableStateOf(org.json.JSONArray()) }
    var provider by remember { mutableStateOf(JSONObject()) }; var audit by remember { mutableStateOf(org.json.JSONArray()) }
    var newSpace by remember { mutableStateOf("") }; var secret by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    fun request(path: String, body: JSONObject? = null, done: (JSONObject) -> Unit = {}) {
        if(busy) return; busy = true
        scope.launch { try { done(CoderAbyssBackendClient(context).api(path, body)); status = "Server operation completed" }
        catch(_: Exception) { status = "Administration unavailable or denied. Reauthenticate and check server configuration." }
        finally { secret = ""; busy = false } }
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Administration", style = MaterialTheme.typography.titleLarge)
        TextButton(enabled = !busy, onClick = { request("admin/users") { users = it.getJSONArray("users") } }) { Text("Users and administrators") }
        for(i in 0 until users.length()) { val user = users.getJSONObject(i)
            Text("${user.optString("name")} · ${user.optString("email")} · ${user.optString("role")}")
            if(role == AccountRole.OWNER && user.optString("role") != "OWNER") TextButton(onClick = {
                val id = java.net.URLEncoder.encode(user.getString("uid"), "UTF-8")
                request("admin/users/$id/${if(user.optString("role") == "ADMIN") "revoke-admin" else "grant-admin"}", JSONObject())
            }) { Text(if(user.optString("role") == "ADMIN") "Revoke Admin" else "Grant Admin Access") }
        }
        TextButton(enabled = !busy, onClick = { request("ai/health") { status = it.optString("status", "Unknown") } }) { Text("Backend health") }
        if(role == AccountRole.OWNER) {
            Text("AI Infrastructure", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { request("admin/provider/status") { provider = it } }) { Text("Load provider status") }
            Text("Space: ${provider.optString("space", "Not loaded")}\nCredential: ${if(provider.optBoolean("configured")) "Configured" else "Not confirmed"}\nVersion: ${provider.optString("version", "—")}\nChanged: ${provider.optString("changedAt", "—")}")
            TextButton(onClick = { request("admin/provider/test", JSONObject()) }) { Text("Test Backend") }
            OutlinedTextField(newSpace, { newSpace = it }, label = { Text("New Space owner/name") })
            TextButton(onClick = { request("admin/provider/space", JSONObject().put("space", newSpace)) }) { Text("Change Space") }
            OutlinedTextField(secret, { secret = it }, label = { Text("New credential (write only)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Text("Existing credentials cannot be retrieved. The new value is cleared after the request.", style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(enabled = secret.isNotBlank() && !busy, onClick = { confirm = "rotate" }) { Text("Rotate") }
                TextButton(enabled = secret.isNotBlank() && !busy, onClick = { confirm = "emergency-rotate" }) { Text("Emergency Replace") }
            }
            TextButton(onClick = { confirm = "rollback" }) { Text("Rollback previous safe version") }
            TextButton(onClick = { request("admin/audit") { audit = it.getJSONArray("events") } }) { Text("Audit Log") }
            for(i in 0 until audit.length()) { val event = audit.getJSONObject(i); Text("${event.optString("at")} · ${event.optString("action")} · ${event.optString("result")}") }
            TextButton(onClick = { scope.launch { runCatching { AuthRepository.get(context).signIn(context as Activity); AuthorizationRepository.refresh(context) }.onFailure { status = "Reauthentication failed" } } }) { Text("Reauthenticate for sensitive changes") }
        }
        Text(status)
    } }
    confirm?.let { operation -> AlertDialog(onDismissRequest = { confirm = null; secret = "" }, title = { Text("Confirm credential change") }, text = { Text(if(operation == "emergency-rotate") "Stop using the previous credential. Revoke it separately at Hugging Face; this action cannot promise provider-side revocation." else "The server must validate and authorize this change. Rollback is only available within the configured safe window.") }, confirmButton = {
        TextButton(onClick = { request("admin/provider/credential/$operation", if(operation == "rollback") JSONObject() else JSONObject().put("credential", secret)); confirm = null }) { Text("Confirm") }
    }, dismissButton = { TextButton(onClick = { confirm = null; secret = "" }) { Text("Cancel") } }) }
}
