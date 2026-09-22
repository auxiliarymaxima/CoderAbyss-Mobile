package com.coderabyss.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Credentials never enter project/task metadata or Android backup. */
class VideoBackendSettings(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("video_backend", Context.MODE_PRIVATE)
    private val credential = File(app.noBackupFilesDir, "video-backend-credential")
    var localOnly: Boolean
        get() = prefs.getBoolean("local_only", false)
        set(value) { prefs.edit().putBoolean("local_only", value).commit() }

    fun requireRemoteAllowed() {
        check(!localOnly) { "Unavailable while Local Only is enabled." }
    }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun saveToken(value: String) {
        if (value.isBlank()) { credential.delete(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        val partial = File(credential.parentFile, credential.name + ".partial")
        partial.writeText(Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP))
        check(partial.renameTo(credential)) { "Could not store credential securely." }
    }

    fun token(): String {
        check(credential.isFile) { "Authentication required. Configure Video Backend in Settings." }
        try {
            val parts = credential.readText().split(":", limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            error("Stored authentication is unavailable. Configure the credential again.")
        }
    }

    fun configured() = credential.isFile

    companion object {
        const val SPACE = "andrewmonize/Coder-Abyss-Space"
        const val BASE_URL = "https://andrewmonize-coder-abyss-space.hf.space"
        private const val KEY = "coder_abyss_video_credential"
    }
}
