package com.coderabyss.mobile.remote

import android.content.Context
import com.coderabyss.mobile.VideoBackendSettings
import okhttp3.OkHttpClient

/** Local Only is checked for both initial calls and network retries/redirects. */
object NetworkPolicy {
    fun client(context: Context): OkHttpClient.Builder {
        val settings = VideoBackendSettings(context)
        return OkHttpClient.Builder().addInterceptor { chain -> settings.requireRemoteAllowed(); chain.proceed(chain.request()) }
            .addNetworkInterceptor { chain -> settings.requireRemoteAllowed(); chain.proceed(chain.request()) }
    }
}
