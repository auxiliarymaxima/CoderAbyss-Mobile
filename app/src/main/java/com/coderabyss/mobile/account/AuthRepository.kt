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

/** Firebase owns refresh-token persistence. Roles are never read from client preferences. */
class AuthRepository private constructor(private val context: Context) {
    private val firebase: FirebaseAuth? = runCatching {
        // Google Services generates the default app options from google-services.json.
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(context) ?: return@runCatching null
        FirebaseAuth.getInstance(app)
    }.getOrNull()
    private val state = MutableStateFlow(firebase?.currentUser?.uid)
    val uid = state.asStateFlow()
    init { firebase?.addAuthStateListener { state.value = it.currentUser?.uid } }
    val configured get() = firebase != null && context.getString(R.string.default_web_client_id).isNotBlank()
    val displayName get() = firebase?.currentUser?.displayName ?: "Google account"
    suspend fun signIn(activity: Activity) {
        VideoBackendSettings(context).requireRemoteAllowed()
        check(configured) { "Account sign-in is not configured for this build. Local projects remain available." }
        val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
        val result = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        check(result.credential is CustomCredential && result.credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google did not return a supported sign-in credential."
        }
        val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
        VideoBackendSettings(context).requireRemoteAllowed()
        firebase!!.signInWithCredential(GoogleAuthProvider.getCredential(credential.idToken, null)).await()
    }
    suspend fun token(expectedUid: String? = null): String {
        VideoBackendSettings(context).requireRemoteAllowed()
        val user = firebase?.currentUser ?: error("Sign in to use Cloud AI.")
        if(expectedUid != null) check(user.uid == expectedUid) { "Sign in with the account that started this task." }
        val token = user.getIdToken(false).await().token ?: error("Sign in again to use Cloud AI.")
        check(firebase.currentUser?.uid == user.uid) { "Account changed" }
        return token
    }
    suspend fun signOut() {
        firebase?.signOut()
        state.value = null
        AuthorizationRepository.clear()
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }
    companion object {
        @Volatile private var instance: AuthRepository? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: AuthRepository(context.applicationContext).also { instance = it } }
    }
}
