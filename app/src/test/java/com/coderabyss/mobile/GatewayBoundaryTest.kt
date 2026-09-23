package com.coderabyss.mobile

import org.robolectric.RuntimeEnvironment
import com.coderabyss.mobile.account.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GatewayBoundaryTest {
    @Test fun localOnlyBlocksGatewayBeforeIdentityOrNetwork() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        VideoBackendSettings(context).localOnly = true
        val error = runCatching { CoderAbyssBackendClient(context).api("account") }.exceptionOrNull()
        assertTrue(error?.message?.contains("Local Only") == true)
        VideoBackendSettings(context).localOnly = false
    }
    @Test fun missingDeploymentCannotGrantAccess() {
        val context = RuntimeEnvironment.getApplication()
        AuthorizationRepository.clear()
        assertEquals(AccountRole.FREE, AuthorizationRepository.access.value.role)
        assertFalse(AuthorizationRepository.access.value.cloud)
        assertTrue(runCatching { AuthorizationRepository.requireCloud(context) }.isFailure)
    }
}
