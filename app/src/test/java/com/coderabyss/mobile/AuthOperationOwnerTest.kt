package com.coderabyss.mobile

import com.coderabyss.mobile.account.AuthOperationOwner
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AuthOperationOwnerTest {
    @Test fun accountScreenCancellationDoesNotCancelPendingVerificationOrCleanup() = runBlocking {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val owner = AuthOperationOwner(appScope)
            val started = CompletableDeferred<Unit>()
            val serverReply = CompletableDeferred<Unit>()
            val completed = CompletableDeferred<Unit>()
            val screen = launch {
                owner.run { started.complete(Unit); serverReply.await(); completed.complete(Unit) }
            }
            started.await()
            screen.cancelAndJoin()
            serverReply.complete(Unit)
            withTimeout(2000) { completed.await() }
            assertTrue(screen.isCancelled)
        } finally { appScope.cancel() }
    }
    @Test fun failureIsReturnedWithoutDisablingLaterAuthentication() = runBlocking {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val owner = AuthOperationOwner(appScope)
            assertTrue(runCatching { owner.run { error("Test failure") } }.isFailure)
            assertEquals("retry", owner.run { "retry" })
        } finally { appScope.cancel() }
    }
}
