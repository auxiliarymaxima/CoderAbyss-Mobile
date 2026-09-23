package com.coderabyss.mobile

import kotlinx.coroutines.*

class CoderAbyssApplication : android.app.Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { com.coderabyss.mobile.tasks.TaskManager(this@CoderAbyssApplication).recover(processRestart = true) }
    }
}
