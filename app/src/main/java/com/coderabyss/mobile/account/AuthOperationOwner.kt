package com.coderabyss.mobile.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/** Account-screen disposal must not interrupt a submitted Firebase operation or cleanup. */
internal class AuthOperationOwner(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    suspend fun <T> run(block: suspend () -> T): T = scope.async { block() }.await()
}
