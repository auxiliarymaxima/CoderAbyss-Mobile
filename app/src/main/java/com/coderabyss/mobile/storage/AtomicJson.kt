package com.coderabyss.mobile.storage

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Same-directory atomic replacement; failure never removes the previous record. */
object AtomicJson {
    fun write(file: File, value: JSONObject) {
        file.parentFile?.mkdirs()
        val pending = File(file.path + ".pending")
        try {
            pending.outputStream().use { it.write(value.toString().toByteArray(Charsets.UTF_8)); it.fd.sync() }
            Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { pending.delete() }
    }
}
