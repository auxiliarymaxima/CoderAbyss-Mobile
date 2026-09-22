package com.coderabyss.mobile

import java.io.File
import java.security.MessageDigest

object ModelFileVerifier {
    fun verify(file: File, expectedBytes: Long, expectedSha256: String) {
        check(file.isFile && file.length() == expectedBytes) { "Incomplete model file. Expected $expectedBytes bytes; download again." }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        check(actual.equals(expectedSha256, ignoreCase = true)) { "Model checksum mismatch. The file is damaged; tap Retry." }
    }
}
