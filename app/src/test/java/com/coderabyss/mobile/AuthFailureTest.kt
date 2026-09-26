package com.coderabyss.mobile

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.coderabyss.mobile.account.*
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthFailureTest {
    @Test fun providerReauthenticationFailureIsNotReportedAsUserCancellation() {
        assertEquals(AuthFailureKind.CREDENTIAL_MANAGER, authenticationFailure(GetCredentialCancellationException("[16] Account reauth failed")).kind)
        assertEquals(AuthFailureKind.CONFIGURATION, authenticationFailure(GetCredentialCancellationException("DEVELOPER_ERROR: configuration rejected")).kind)
        assertEquals(AuthFailureKind.CANCELLED, authenticationFailure(GetCredentialCancellationException("User cancelled")).kind)
    }
    @Test fun failuresDistinguishConfigurationCredentialsAndNetwork() {
        assertEquals(AuthFailureKind.CONFIGURATION, authenticationFailure(GetCredentialProviderConfigurationException()).kind)
        assertEquals(AuthFailureKind.NO_CREDENTIAL, authenticationFailure(NoCredentialException()).kind)
        assertEquals(AuthFailureKind.NETWORK, authenticationFailure(FirebaseNetworkException("private detail")).kind)
        assertEquals(AuthFailureKind.CONFIGURATION, authenticationFailure(FirebaseAuthException("ERROR_OPERATION_NOT_ALLOWED", "private detail")).kind)
        assertEquals(AuthFailureKind.REJECTED, authenticationFailure(FirebaseAuthException("ERROR_INVALID_CREDENTIAL", "private detail")).kind)
        assertEquals(AuthFailureKind.LOCAL_ONLY, authenticationFailure(AuthLocalOnlyException()).kind)
    }
    @Test fun arbitraryProviderDataNeverEntersSafeDiagnostics() {
        val marker = "PRIVATE_ACCOUNT_OR_TOKEN_VALUE"
        val failure = authenticationFailure(FirebaseAuthException(marker, marker))
        assertFalse(failure.message.contains(marker))
        assertFalse(failure.diagnostic.contains(marker))
    }
}
