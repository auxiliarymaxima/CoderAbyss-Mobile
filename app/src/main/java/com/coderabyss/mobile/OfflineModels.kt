package com.coderabyss.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.launch

enum class ModelKind {
    TEXT,
    SPEECH,
    IMAGE,
    VIDEO
}

data class OfflineModel(
    val id: String,
    val name: String,
    val family: String,
    val purpose: String,
    val kind: ModelKind,
    val quant: String,
    val sizeLabel: String,
    val fileName: String,
    val url: String,
    val additionalFiles: List<String> = emptyList()
)

object ModelCatalog {
    val wan = OfflineModel(
        id = "wan-2.1-1.3b-q4", name = "Wan 2.1 Video 1.3B",
        family = "Wan", purpose = "Offline video + text encoder + decoder (3 files)",
        kind = ModelKind.VIDEO, quant = "Q4_K_M", sizeLabel = "4.89 GB total",
        fileName = "Wan2.1-T2V-1.3B-Q4_K_M.gguf", url = "",
        additionalFiles = listOf("umt5-xxl-encoder-Q4_K_M.gguf", "wan_2.1_vae.safetensors")
    )


    val models = listOf(
        wan,

        OfflineModel(
            id = "qwen-coder-1.5b-q4",
            name = "Qwen 2.5 Coder 1.5B",
            family = "Qwen",
            purpose = "Coding • App building",
            kind = ModelKind.TEXT,
            quant = "Q4_K_M",
            sizeLabel = "1.12 GB",
            fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            url =
                "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf?download=true"
        ),

        OfflineModel(
            id = "qwen-coder-3b-q4",
            name = "Qwen 2.5 Coder 3B",
            family = "Qwen",
            purpose = "Coding • Stronger local model",
            kind = ModelKind.TEXT,
            quant = "Q4_K_M",
            sizeLabel = "2.10 GB",
            fileName = "qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            url =
                "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf?download=true"
        ),

        OfflineModel(
            id = "gemma-3-1b-q4",
            name = "Gemma 3 1B",
            family = "Gemma",
            purpose = "General assistant • Lightweight",
            kind = ModelKind.TEXT,
            quant = "Q4_K_M",
            sizeLabel = "0.81 GB",
            fileName = "google_gemma-3-1b-it-Q4_K_M.gguf",
            url =
                "https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf?download=true"
        ),

        OfflineModel(
            id = "llama-3.2-3b-q4",
            name = "Llama 3.2 3B",
            family = "Llama",
            purpose = "General reasoning • Writing",
            kind = ModelKind.TEXT,
            quant = "Q4_K_M",
            sizeLabel = "2.02 GB",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            url =
                "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf?download=true"
        ),

        OfflineModel(
            id = "whisper-tiny-en",
            name = "Whisper Tiny English",
            family = "Whisper",
            purpose = "Fast offline speech-to-text",
            kind = ModelKind.SPEECH,
            quant = "Q5_1",
            sizeLabel = "31 MB",
            fileName = "ggml-tiny.en-q5_1.bin",
            url =
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q5_1.bin?download=true"
        ),

        OfflineModel(
            id = "whisper-base-en",
            name = "Whisper Base English",
            family = "Whisper",
            purpose = "Higher quality offline speech-to-text",
            kind = ModelKind.SPEECH,
            quant = "FP16",
            sizeLabel = "148 MB",
            fileName = "ggml-base.en.bin",
            url =
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin?download=true"
        )
    )
}

enum class TransferStatus {
    NOT_INSTALLED,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    INSTALLED,
    FAILED
}

data class ModelTransferState(
    val status: TransferStatus,
    val progress: Int = 0,
    val downloaded: Long = 0,
    val total: Long = 0,
    val reason: Int = 0,
    val message: String = ""
)

class OfflineModelManager(context: Context) {

    private val appContext = context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            "coder_abyss_models",
            Context.MODE_PRIVATE
        )

    private val downloadManager =
        appContext.getSystemService(
            Context.DOWNLOAD_SERVICE
        ) as DownloadManager

    private val storageRoot: File =
        appContext.getExternalFilesDir(null)
            ?: appContext.filesDir

    val modelDirectory: File =
        File(storageRoot, "models").apply {
            mkdirs()
        }

    fun modelFile(model: OfflineModel): File =
        File(modelDirectory, model.fileName)

    private val manifest = org.json.JSONObject(appContext.assets.open("model-manifest.json").bufferedReader().use { it.readText() })
    private val checking = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val epochs = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    private fun files(model: OfflineModel) = listOf(model.fileName) + model.additionalFiles
    private fun spec(name: String) = manifest.getJSONObject(name)
    private fun size(name: String) = spec(name).getLong("size")
    private fun key(name: String) = "asset_download_$name"
    private fun verified(name: String): Boolean {
        val f = File(modelDirectory, name)
        return f.isFile && f.length() == size(name) &&
            prefs.getString("verified_$name", null) == "${spec(name).getString("sha256")}:${f.lastModified()}"
    }

    fun isInstalled(model: OfflineModel): Boolean = files(model).all { verified(it) }

    fun installedModels(): List<OfflineModel> =
        ModelCatalog.models.filter {
            isInstalled(it)
        }

    fun installedTextModels(): List<OfflineModel> =
        ModelCatalog.models.filter {
            it.kind == ModelKind.TEXT &&
                    isInstalled(it)
        }

    var selectedId by androidx.compose.runtime.mutableStateOf(prefs.getString("selected_text_model", null))
        private set

    fun selectedModelId(): String? = selectedId

    fun workflowModel(workflow: String): OfflineModel? {
        val key = "workflow_model_$workflow"
        val id = prefs.getString(key, null) ?: selectedModelId()?.also {
            prefs.edit().putString(key, it).apply()
        }
        return installedTextModels().firstOrNull { it.id == id }
    }

    fun videoModelId(): String {
        val key = "workflow_model_VIDEO"
        return prefs.getString(key, null) ?: WanVideoClient.MODEL_ID.also {
            prefs.edit().putString(key, it).apply()
        }
    }

    fun selectForWorkflow(workflow: String, model: OfflineModel) {
        if (model.kind == ModelKind.TEXT && isInstalled(model)) {
            prefs.edit().putString("workflow_model_$workflow", model.id).apply()
        }
    }


    fun selectedTextModel(): OfflineModel? {

        val selected = selectedModelId()

        return ModelCatalog.models
            .firstOrNull {
                it.id == selected &&
                        it.kind == ModelKind.TEXT &&
                        isInstalled(it)
            }
    }

    fun select(model: OfflineModel): Boolean {

        if (model.kind != ModelKind.TEXT)
            return false

        if (!isInstalled(model))
            return false

        prefs.edit()
            .putString(
                "selected_text_model",
                model.id
            )
            .apply()

        selectedId = model.id
        return true
    }

    fun wifiOnly(): Boolean =
        prefs.getBoolean(
            "wifi_only_downloads",
            true
        )

    fun setWifiOnly(value: Boolean) {
        prefs.edit()
            .putBoolean(
                "wifi_only_downloads",
                value
            )
            .apply()
    }

    private fun downloadId(model: OfflineModel, name: String): Long =
        prefs.getLong(key(name), if (name == model.fileName) prefs.getLong("download_${model.id}", -1L) else -1L)

    @Synchronized
    fun startDownload(model: OfflineModel): Long {
        if (isInstalled(model)) return -1L
        if (files(model).any { checking.contains(it) }) return -1L
        var lastId = -1L
        try {
            for (name in files(model)) {
                if (verified(name)) continue
                val old = downloadId(model, name)
                if (old > 0) downloadManager.remove(old)
                File(modelDirectory, "$name.part").delete()
                File(modelDirectory, name).delete()
                prefs.edit().remove("error_$name").remove("verified_$name").apply()
                val request = DownloadManager.Request(Uri.parse(spec(name).getString("url")))
                    .setTitle("${model.name}: $name")
                    .setDescription("Downloading model file; verification follows")
                    .setAllowedOverRoaming(false)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(appContext, null, "models/$name.part")
                if (wifiOnly()) request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI).setAllowedOverMetered(false)
                else request.setAllowedOverMetered(true)
                lastId = downloadManager.enqueue(request)
                prefs.edit().putLong(key(name), lastId).remove("download_${model.id}").apply()
            }
        } catch (e: Exception) {
            prefs.edit().putString("error_${model.fileName}", e.message ?: "Could not start download").apply()
        }
        return lastId
    }

    @Synchronized
    fun cancelDownload(model: OfflineModel) {
        for (name in files(model)) {
            epochs[name] = (epochs[name] ?: 0) + 1
            val id = downloadId(model, name)
            if (id > 0) downloadManager.remove(id)
            File(modelDirectory, "$name.part").delete()
            prefs.edit().remove(key(name)).remove("error_$name").apply()
        }
        prefs.edit().remove("download_${model.id}").apply()
    }

    @Synchronized
    fun remove(model: OfflineModel) {
        cancelDownload(model)
        for (name in files(model)) {
            File(modelDirectory, name).delete()
            prefs.edit().remove("verified_$name").apply()
        }
        if (selectedId == model.id) {
            selectedId = null
            prefs.edit().remove("selected_text_model").apply()
        }
    }

    private fun verifyAsync(name: String, file: File) {
        if (!checking.add(name)) return
        val epoch = epochs[name] ?: 0
        scope.launch {
            try {
                ModelFileVerifier.verify(file, size(name), spec(name).getString("sha256"))
                synchronized(this@OfflineModelManager) {
                    if ((epochs[name] ?: 0) == epoch) {
                        val finalFile = File(modelDirectory, name)
                        check(file == finalFile || file.renameTo(finalFile)) { "Could not finalize downloaded model" }
                        prefs.edit().putString("verified_$name", "${spec(name).getString("sha256")}:${finalFile.lastModified()}")
                            .remove("error_$name").remove(key(name)).apply()
                    }
                }
            } catch (e: Exception) {
                synchronized(this@OfflineModelManager) {
                    if ((epochs[name] ?: 0) == epoch) prefs.edit().putString("error_$name", e.message ?: "Verification failed").apply()
                }
            } finally { checking.remove(name) }
        }
    }

    private fun assetState(model: OfflineModel, name: String): ModelTransferState {
        val expected = size(name)
        if (verified(name)) return ModelTransferState(TransferStatus.INSTALLED, 100, expected, expected)
        prefs.getString("error_$name", null)?.let { return ModelTransferState(TransferStatus.FAILED, total = expected, message = it) }
        if (checking.contains(name)) return ModelTransferState(TransferStatus.VERIFYING, 99, expected, expected, message = "Checking SHA-256: $name")
        val id = downloadId(model, name)
        if (id > 0) {
            downloadManager.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (c.moveToFirst()) {
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)).coerceAtLeast(0)
                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    val transfer = when (status) {
                        DownloadManager.STATUS_PENDING -> TransferStatus.QUEUED
                        DownloadManager.STATUS_RUNNING -> TransferStatus.DOWNLOADING
                        DownloadManager.STATUS_PAUSED -> TransferStatus.PAUSED
                        DownloadManager.STATUS_FAILED -> TransferStatus.FAILED
                        else -> TransferStatus.VERIFYING
                    }
                    if (status != DownloadManager.STATUS_SUCCESSFUL) {
                        val detail = when {
                            status == DownloadManager.STATUS_FAILED -> "Download failed (code $reason). Check connection, storage and source availability; tap Retry."
                            status == DownloadManager.STATUS_PAUSED -> "Paused (code $reason); waiting for network/Wi-Fi."
                            else -> "Downloading $name"
                        }
                        return ModelTransferState(transfer, (bytes * 100 / expected).toInt().coerceIn(0, 99), bytes, expected, reason, detail)
                    }
                }
            }
        }
        val part = File(modelDirectory, "$name.part")
        val old = File(modelDirectory, name)
        val candidate = if (part.exists()) part else old
        if (candidate.exists()) {
            if (candidate.length() != expected) return ModelTransferState(TransferStatus.FAILED, downloaded = candidate.length(), total = expected,
                message = "Incomplete file: ${candidate.length()} of $expected bytes. Tap Retry.")
            verifyAsync(name, candidate)
            return ModelTransferState(TransferStatus.VERIFYING, 99, expected, expected, message = "Checking SHA-256: $name")
        }
        return ModelTransferState(TransferStatus.NOT_INSTALLED, total = expected)
    }

    fun state(model: OfflineModel): ModelTransferState {
        val parts = files(model).map { assetState(model, it) }
        val total = parts.sumOf { it.total }
        val downloaded = parts.sumOf { it.downloaded }
        val status = when {
            parts.all { it.status == TransferStatus.INSTALLED } -> TransferStatus.INSTALLED
            parts.any { it.status == TransferStatus.FAILED } -> TransferStatus.FAILED
            parts.any { it.status == TransferStatus.DOWNLOADING } -> TransferStatus.DOWNLOADING
            parts.any { it.status == TransferStatus.VERIFYING } -> TransferStatus.VERIFYING
            parts.any { it.status == TransferStatus.PAUSED } -> TransferStatus.PAUSED
            parts.any { it.status == TransferStatus.QUEUED } -> TransferStatus.QUEUED
            else -> TransferStatus.NOT_INSTALLED
        }
        return ModelTransferState(status, if (status == TransferStatus.INSTALLED) 100 else (downloaded * 100 / total).toInt().coerceIn(0,99),
            downloaded, total, message = parts.firstOrNull { it.status == status }?.message.orEmpty())
    }
}
