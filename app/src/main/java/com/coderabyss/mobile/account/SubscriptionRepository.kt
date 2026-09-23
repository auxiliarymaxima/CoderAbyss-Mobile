package com.coderabyss.mobile.account

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.coderabyss.mobile.VideoBackendSettings
import kotlinx.coroutines.*
import org.json.JSONObject
import java.security.MessageDigest

/** Google returns product prices; only the gateway grants entitlement and acknowledges purchases. */
class SubscriptionRepository(private val context: Context, private val scope: CoroutineScope, private val message: (String) -> Unit) {
    private val client = BillingClient.newBuilder(context).enableAutoServiceReconnection()
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build())
        .setListener { result, purchases ->
            if(result.responseCode == BillingClient.BillingResponseCode.OK) purchases.orEmpty().forEach(::verify)
            else if(result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) message("Purchase unavailable. Try again in Google Play.")
        }.build()
    private var products = emptyList<ProductDetails>()
    fun connect(onProducts: (List<ProductDetails>) -> Unit) {
        if(VideoBackendSettings(context).localOnly) { message("Unavailable while Local Only is enabled."); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() { message("Google Play disconnected") }
            override fun onBillingSetupFinished(result: BillingResult) {
                if(result.responseCode != BillingClient.BillingResponseCode.OK) { message("Google Play unavailable"); return }
                scope.launch {
                    runCatching {
                        val config = CoderAbyssBackendClient(context).api("billing/products").getJSONArray("productIds")
                        if(config.length() == 0) { message("No subscription products are available yet"); return@launch }
                        val query = QueryProductDetailsParams.newBuilder().setProductList((0 until config.length()).map {
                            QueryProductDetailsParams.Product.newBuilder().setProductId(config.getString(it)).setProductType(BillingClient.ProductType.SUBS).build()
                        }).build()
                        client.queryProductDetailsAsync(query) { status, details ->
                            if(status.responseCode == BillingClient.BillingResponseCode.OK) { products = details.productDetailsList; onProducts(products) }
                            else message("Subscription products unavailable")
                        }
                    }.onFailure { message("Subscription setup is unavailable. Local features remain available.") }
                }
            }
        })
    }
    fun purchase(activity: Activity, product: ProductDetails, offerToken: String) {
        VideoBackendSettings(context).requireRemoteAllowed()
        val uid = AuthRepository.get(context).uid.value ?: error("Sign in first")
        val account = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setObfuscatedAccountId(account)
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).setOfferToken(offerToken).build())).build())
        if(result.responseCode != BillingClient.BillingResponseCode.OK) message("Purchase could not start")
    }
    fun restore() {
        if(VideoBackendSettings(context).localOnly) { message("Unavailable while Local Only is enabled."); return }
        if(!client.isReady) {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingServiceDisconnected() { message("Google Play disconnected") }
                override fun onBillingSetupFinished(result: BillingResult) {
                    if(result.responseCode == BillingClient.BillingResponseCode.OK) restore() else message("Could not connect to Google Play")
                }
            })
            return
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
            if(result.responseCode == BillingClient.BillingResponseCode.OK) purchases.forEach(::verify) else message("Could not restore purchases")
        }
    }
    private fun verify(purchase: Purchase) {
        if(purchase.purchaseState != Purchase.PurchaseState.PURCHASED) { message("Payment pending; access is not yet granted"); return }
        val uid = AuthRepository.get(context).uid.value ?: return
        scope.launch {
            runCatching {
                CoderAbyssBackendClient(context, uid).api("billing/google/verify", JSONObject().put("purchaseToken", purchase.purchaseToken))
                AuthorizationRepository.refresh(context)
            }.onSuccess { message("Plan refreshed from server") }.onFailure { message("Purchase verification pending. Restore purchases to retry; access has not been granted locally.") }
        }
    }
    fun close() = client.endConnection()
}
