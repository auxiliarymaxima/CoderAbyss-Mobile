package com.coderabyss.mobile

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.sync.withLock

class WhisperVoiceEngine {

    companion object {
        const val SAMPLE_RATE = 16000
        private val speechMutex = kotlinx.coroutines.sync.Mutex()
    }

    private var recorder: AudioRecord? =
        null

    private var recordThread: Thread? =
        null

    @Volatile
    private var recording =
        false

    private var audioBytes =
        ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {

        if (recording)
            return false

        val minBuffer =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

        val size =
            maxOf(
                minBuffer,
                8192
            )

        val audioRecord =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                size * 2
            )

        if (
            audioRecord.state !=
            AudioRecord.STATE_INITIALIZED
        ) {
            audioRecord.release()
            return false
        }

        audioBytes.reset()

        recorder =
            audioRecord

        recording =
            true

        audioRecord.startRecording()

        recordThread =
            Thread {

                val buffer =
                    ByteArray(size)

                while (recording) {

                    val count =
                        audioRecord.read(
                            buffer,
                            0,
                            buffer.size
                        )

                    if (count > 0) {

                        synchronized(
                            audioBytes
                        ) {
                            audioBytes.write(
                                buffer,
                                0,
                                count
                            )
                        }
                    }
                }
            }.apply {
                name =
                    "CoderAbyssWhisperRecorder"

                start()
            }

        return true
    }

    fun stopRecording():
        FloatArray {

        recording =
            false

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        try {
            recordThread?.join(
                1500
            )
        } catch (_: Exception) {
        }

        recorder?.release()

        recorder =
            null

        recordThread =
            null

        val bytes =
            synchronized(audioBytes) {
                audioBytes
                    .toByteArray()
            }

        if (bytes.size < 2)
            return FloatArray(0)

        val buffer =
            ByteBuffer
                .wrap(bytes)
                .order(
                    ByteOrder.LITTLE_ENDIAN
                )

        val samples =
            FloatArray(
                bytes.size / 2
            )

        var index = 0

        while (
            buffer.remaining() >= 2 &&
            index < samples.size
        ) {

            samples[index++] =
                buffer.short /
                    32768.0f
        }

        return samples
    }

    suspend fun transcribe(
        model: java.io.File,
        audio: FloatArray
    ): String {

        require(
            model.exists()
        ) {
            "Whisper model file not found."
        }

        require(
            audio.isNotEmpty()
        ) {
            "No microphone audio was recorded."
        }

        return withContext(
            Dispatchers.IO
        ) {

            speechMutex.withLock {
            val whisper = WhisperContext.createContextFromFile(model.absolutePath)
            try {
                whisper.transcribeData(audio, printTimestamp = false).trim()
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) { whisper.release() }
            }
            }
        }
    }
}
