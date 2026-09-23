package com.coderabyss.mobile.account

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class AccountRole { FREE, SUBSCRIBER, ADMIN, OWNER }
data class AccountAccess(val uid: String = "", val role: AccountRole = AccountRole.FREE, val cloud: Boolean = false, val plan: String = "FREE", val state: String = "Unverified")
object AuthorizationRepository {
    private val value = MutableStateFlow(AccountAccess())
    val access = value.asStateFlow()
    fun clear() { value.value = AccountAccess() }
    suspend fun refresh(context: Context): AccountAccess {
        val uid = AuthRepository.get(context).uid.value ?: error("Sign in first")
        val result = CoderAbyssBackendClient(context).api("account")
        check(AuthRepository.get(context).uid.value == uid && result.getString("uid") == uid) { "Account changed; retry" }
        return AccountAccess(uid, AccountRole.valueOf(result.getString("role")), result.getBoolean("cloudAccess"), result.getString("plan"), result.getString("entitlementState")).also { value.value = it }
    }
    fun requireCloud(context: Context) {
        val current = value.value
        check(current.uid.isNotBlank() && current.uid == AuthRepository.get(context).uid.value && current.cloud) { "Sign in and refresh your plan in Settings to use Cloud AI." }
        // A UI convenience only: the gateway independently verifies authorization on every request.
    }
}
