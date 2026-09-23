package com.coderabyss.mobile

import kotlinx.coroutines.*

class CoderAbyssApplication : android.app.Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        // Retire only the old development provider credential. Projects/models are untouched.
        java.io.File(noBackupFilesDir, "video-backend-credential").delete()
        java.io.File(noBackupFilesDir, "video-backend-credential.partial").delete()
        applicationScope.launch { com.coderabyss.mobile.tasks.TaskManager(this@CoderAbyssApplication).recover(processRestart = true) }
    }
}
