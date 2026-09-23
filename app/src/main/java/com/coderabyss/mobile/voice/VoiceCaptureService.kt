package com.coderabyss.mobile.voice

import android.app.*
import android.content.Intent
import android.media.*
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.coderabyss.mobile.MainActivity
import com.coderabyss.mobile.tasks.TaskManager
import kotlinx.coroutines.*

/** Recording belongs to a microphone foreground service, not to a Compose screen. */
class VoiceCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var recording = false
    private var recorder: AudioRecord? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { recording = false; runCatching { recorder?.stop() }; return START_NOT_STICKY }
        val id = intent?.getStringExtra("taskId") ?: return START_NOT_STICKY
        if (recording) return START_NOT_STICKY
        val notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notifications.createNotificationChannel(NotificationChannel("voice-capture", "Speech capture", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, VoiceCaptureService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(7341, NotificationCompat.Builder(this, "voice-capture").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Coder Abyss · Listening").setContentText("Tap Stop to transcribe. Audio stays on this phone.")
            .setOngoing(true).setContentIntent(open).addAction(android.R.drawable.ic_media_pause, "Stop", stop).build(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        recording = true
        scope.launch {
            val manager = TaskManager(applicationContext)
            try {
                val task = manager.store.read(id)
                val file = manager.projects.file(task.getString("projectId"), task.getJSONObject("parameters").getString("audio"))
                file.parentFile?.mkdirs()
                val size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(8192)
                @android.annotation.SuppressLint("MissingPermission")
                val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
                check(audio.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
                recorder = audio; audio.startRecording()
                manager.store.update(id) { it.put("status", "RECORDING").put("stage", "Listening — tap Stop to transcribe") }
                try {
                    file.outputStream().use { output ->
                        val buffer = ByteArray(size); var total = 0L
                        while (recording && total < 16000L * 2 * 600 && !manager.store.read(id).optBoolean("cancelRequested")) {
                            val count = audio.read(buffer, 0, buffer.size)
                            if (count > 0) { output.write(buffer, 0, count); total += count }
                            else if (count < 0 && recording) error("Audio capture failed")
                        }
                        output.fd.sync()
                    }
                } finally { runCatching { audio.stop() }; audio.release(); recorder = null }
                if (manager.store.read(id).optBoolean("cancelRequested")) manager.store.update(id) { it.put("status", "CANCELLED").put("stage", "Recording cancelled") }
                else {
                    check(file.length() >= 3200)
                    manager.store.update(id) { it.put("status", "QUEUED").put("stage", "Recorded audio saved; waiting for transcription") }
                    manager.enqueue(id)
                }
            } catch (_: Exception) { manager.store.update(id) { it.put("status", "INTERRUPTED").put("stage", "Recording interrupted; captured audio retained") } }
            finally { recording = false; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { recording = false; runCatching { recorder?.stop() }; scope.cancel(); super.onDestroy() }
}
