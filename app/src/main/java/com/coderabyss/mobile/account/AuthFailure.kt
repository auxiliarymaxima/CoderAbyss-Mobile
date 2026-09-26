package com.coderabyss.mobile.account

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import java.io.IOException

enum class AuthFailureKind { CANCELLED, NO_CREDENTIAL, CREDENTIAL_MANAGER, CONFIGURATION, REJECTED, NETWORK, LOCAL_ONLY, UNKNOWN }

/** Only fixed, non-sensitive descriptions cross the authentication/UI boundary. */
data class AuthFailure(val kind: AuthFailureKind, val message: String, val diagnostic: String)

class AuthConfigurationException : IllegalStateException()
class AuthLocalOnlyException : IllegalStateException()

fun authenticationFailure(error: Exception): AuthFailure {
    val firebaseCode = (error as? FirebaseAuthException)?.errorCode
    // Play Services sometimes wraps configuration/reauth errors as cancellation.
    // Examine the message locally; never display or log it (it may contain account data).
    val detail = error.message.orEmpty().lowercase()
    val kind = when {
        error is AuthLocalOnlyException -> AuthFailureKind.LOCAL_ONLY
        error is AuthConfigurationException || error is GetCredentialProviderConfigurationException -> AuthFailureKind.CONFIGURATION
        firebaseCode in setOf("ERROR_OPERATION_NOT_ALLOWED", "ERROR_INVALID_API_KEY", "ERROR_APP_NOT_AUTHORIZED", "ERROR_INVALID_OAUTH_CLIENT_ID") -> AuthFailureKind.CONFIGURATION
        error is GetCredentialCancellationException && ("developer_error" in detail || "configuration" in detail) -> AuthFailureKind.CONFIGURATION
        error is GetCredentialCancellationException && "reauth" in detail -> AuthFailureKind.CREDENTIAL_MANAGER
        error is GetCredentialCancellationException -> AuthFailureKind.CANCELLED
        error is NoCredentialException -> AuthFailureKind.NO_CREDENTIAL
        error is FirebaseNetworkException || error is IOException -> AuthFailureKind.NETWORK
        error is GetCredentialException -> AuthFailureKind.CREDENTIAL_MANAGER
        error is FirebaseAuthException -> AuthFailureKind.REJECTED
        else -> AuthFailureKind.UNKNOWN
    }
    val message = when(kind) {
        AuthFailureKind.CANCELLED -> "Google sign-in did not finish. If you closed account selection, retry when ready. If you did not cancel, check the Google account on your phone and the app certificate in Firebase."
        AuthFailureKind.NO_CREDENTIAL -> "No Google credential is available. Add or reauthenticate a Google account in Android Settings, then retry."
        AuthFailureKind.CREDENTIAL_MANAGER -> "Google Credential Manager could not complete sign-in. Check your Google account and Google Play services, then retry."
        AuthFailureKind.CONFIGURATION -> "Google sign-in configuration was rejected or is missing. Verify this app's signing certificate, Web OAuth client, and enabled Google provider in Firebase."
        AuthFailureKind.REJECTED -> "Firebase rejected the Google credential. Retry sign-in; if it persists, check the account and Firebase configuration."
        AuthFailureKind.NETWORK -> "Authentication could not reach Google/Firebase. Check your internet connection and retry."
        AuthFailureKind.LOCAL_ONLY -> "Google sign-in is unavailable while Local Only is enabled."
        AuthFailureKind.UNKNOWN -> "Authentication could not complete. Retry or copy the authentication diagnostics."
    }
    val allowedCodes = setOf("ERROR_OPERATION_NOT_ALLOWED", "ERROR_INVALID_API_KEY", "ERROR_APP_NOT_AUTHORIZED", "ERROR_INVALID_OAUTH_CLIENT_ID", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_DISABLED", "ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN", "ERROR_TOO_MANY_REQUESTS", "ERROR_NETWORK_REQUEST_FAILED", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL")
    val safeClass = error.javaClass.simpleName.takeIf { it.matches(Regex("[A-Za-z0-9_]{1,100}")) } ?: "Exception"
    return AuthFailure(kind, message, "$safeClass / ${firebaseCode?.takeIf { it in allowedCodes } ?: kind.name}")
}
