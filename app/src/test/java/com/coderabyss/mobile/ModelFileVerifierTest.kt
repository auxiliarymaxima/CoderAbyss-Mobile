package com.coderabyss.mobile

import java.io.File
import org.junit.Test

class ModelFileVerifierTest {
    private val abcHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    private fun file(text: String, block: (File) -> Unit) {
        val file = File.createTempFile("model-test", ".part")
        try { file.writeText(text); block(file) } finally { file.delete() }
    }
    @Test fun acceptsCompleteVerifiedFile() = file("abc") { ModelFileVerifier.verify(it, 3, abcHash) }
    @Test(expected = IllegalStateException::class)
    fun rejectsTruncatedFile() = file("ab") { ModelFileVerifier.verify(it, 3, abcHash) }
    @Test(expected = IllegalStateException::class)
    fun rejectsSameSizeCorruption() = file("abd") { ModelFileVerifier.verify(it, 3, abcHash) }
    @Test(expected = IllegalStateException::class)
    fun rejectsPartialFileLargerThanOldOneMegabyteThreshold() = file("x".repeat(2 * 1024 * 1024)) {
        ModelFileVerifier.verify(it, 1117320768, abcHash)
    }
}
