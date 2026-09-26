package com.coderabyss.mobile.account

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.coderabyss.mobile.R
import com.coderabyss.mobile.VideoBackendSettings
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import java.security.MessageDigest

data class AuthStatus(
    val busy: Boolean = false,
    val message: String = "",
    val diagnostic: String = "None",
    val tokenVerified: Boolean = false,
    val cleanupPending: Boolean = false,
)

/** Firebase owns refresh-token persistence. Roles are never read from client preferences. */
class AuthRepository private constructor(private val context: Context) {
    private val initialization = runCatching {
        // Google Services generates the default app options from google-services.json.
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(context) ?: return@runCatching null
        FirebaseAuth.getInstance(app)
    }
    private val firebase = initialization.getOrNull()
    // The account screen can disappear as soon as Firebase publishes a new UID.
    // Its coroutine must not own token verification or sign-out cleanup.
    private val operations = AuthOperationOwner()
    private val statusState = MutableStateFlow(AuthStatus())
    val status = statusState.asStateFlow()
    private val state = MutableStateFlow(firebase?.currentUser?.uid)
    val uid = state.asStateFlow()
    init { firebase?.addAuthStateListener {
        val next = it.currentUser?.uid
        if(state.value != next) statusState.value = statusState.value.copy(tokenVerified = false)
        state.value = next
    } }
    val configured get() = firebase != null && context.getString(R.string.default_web_client_id).isNotBlank()
    val displayName get() = firebase?.currentUser?.displayName ?: "Google account"
    private fun requireRemote() {
        if(VideoBackendSettings(context).localOnly) throw AuthLocalOnlyException()
    }
    private suspend fun operation(message: String, block: suspend () -> Unit) = operations.run {
        check(!statusState.value.busy) { "Authentication operation already running" }
        statusState.value = statusState.value.copy(busy = true, message = message, diagnostic = "None")
        try { block() }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            val failure = authenticationFailure(e)
            statusState.value = statusState.value.copy(message = failure.message, diagnostic = failure.diagnostic)
            throw e
        } finally { statusState.value = statusState.value.copy(busy = false) }
    }

    suspend fun signIn(activity: Activity) = operation("Signing in with Google…") {
        requireRemote()
        if(!configured) throw AuthConfigurationException()
        val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
        val result = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        check(result.credential is CustomCredential && result.credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google did not return a supported sign-in credential."
        }
        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
        requireRemote()
        firebase!!.signInWithCredential(GoogleAuthProvider.getCredential(credential.idToken, null)).await()
        statusState.value = statusState.value.copy(message = "Signed in with Google. Verifying Firebase session…", cleanupPending = false)
        verifyCurrentSession()
    }
    suspend fun token(expectedUid: String? = null): String {
        requireRemote()
        val user = firebase?.currentUser ?: error("Sign in to use Cloud AI.")
        if(expectedUid != null) check(user.uid == expectedUid) { "Sign in with the account that started this task." }
        val token = user.getIdToken(false).await().token ?: error("Sign in again to use Cloud AI.")
        check(firebase.currentUser?.uid == user.uid) { "Account changed" }
        check(token.isNotBlank()) { "Firebase returned an empty ID token" }
        return token
    }
    private suspend fun verifyCurrentSession() {
        try {
            token() // Verify retrieval; never store or expose the token.
            statusState.value = statusState.value.copy(message = "Signed in with Google. Firebase session verified.", tokenVerified = true, diagnostic = "None")
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            val failure = authenticationFailure(e)
            statusState.value = statusState.value.copy(message = "Google sign-in succeeded. Firebase session verification needs retry. ${failure.message}", tokenVerified = false, diagnostic = failure.diagnostic)
        }
    }
    suspend fun verifySession() = operation("Verifying Firebase session…") { verifyCurrentSession() }

    suspend fun signOut() = operation("Signing out…") {
        firebase?.signOut()
        state.value = null
        AuthorizationRepository.clear()
        statusState.value = statusState.value.copy(tokenVerified = false)
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            statusState.value = statusState.value.copy(message = "Signed out. Google credential state cleared. Local projects preserved.", cleanupPending = false)
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) {
            statusState.value = statusState.value.copy(message = "Signed out of Firebase. Google credential cleanup failed; retry cleanup before signing in again.", cleanupPending = true, diagnostic = authenticationFailure(e).diagnostic)
        }
    }

    fun diagnostics(): String = buildString {
        appendLine("Application ID: ${context.packageName}")
        appendLine("Build type: ${if(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) "debug" else "release"}")
        appendLine("Firebase project: ${firebase?.app?.options?.projectId ?: "Unavailable"}")
        appendLine("Firebase initialized: ${firebase != null}")
        appendLine("Initialization exception: ${initialization.exceptionOrNull()?.javaClass?.simpleName ?: "None"}")
        appendLine("Web OAuth client present: ${context.getString(R.string.default_web_client_id).isNotBlank()}")
        val signatures = runCatching {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())).signingInfo?.apkContentsSigners
        }.getOrNull()
        if(signatures.isNullOrEmpty()) appendLine("Signing certificates: unavailable")
        signatures?.forEachIndexed { index, signature ->
            listOf("SHA-1", "SHA-256").forEach { algorithm ->
                val fingerprint = MessageDigest.getInstance(algorithm).digest(signature.toByteArray()).joinToString(":") { "%02X".format(it.toInt() and 255) }
                appendLine("Signer ${index + 1} $algorithm: $fingerprint")
            }
        }
        appendLine("Firebase signed in: ${firebase?.currentUser != null}")
        appendLine("ID token retrieval verified this session: ${statusState.value.tokenVerified}")
        appendLine("Credential cleanup pending: ${statusState.value.cleanupPending}")
        append("Last authentication diagnostic: ${statusState.value.diagnostic}")
    }
    companion object {
        @Volatile private var instance: AuthRepository? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: AuthRepository(context.applicationContext).also { instance = it } }
    }
}
