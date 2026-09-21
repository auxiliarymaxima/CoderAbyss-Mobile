package com.coderabyss.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

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
    val url: String
)

object ModelCatalog {

    val models = listOf(

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
    INSTALLED,
    FAILED
}

data class ModelTransferState(
    val status: TransferStatus,
    val progress: Int = 0,
    val downloaded: Long = 0,
    val total: Long = 0,
    val reason: Int = 0
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

    fun isInstalled(model: OfflineModel): Boolean {
        val file = modelFile(model)

        return file.exists() &&
                file.isFile &&
                file.length() > 1024 * 1024
    }

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

    private fun downloadKey(model: OfflineModel) =
        "download_${model.id}"

    fun startDownload(
        model: OfflineModel
    ): Long {

        if (isInstalled(model))
            return -1L

        cancelDownload(model)

        modelDirectory.mkdirs()

        val request =
            DownloadManager.Request(
                Uri.parse(model.url)
            )
                .setTitle(model.name)
                .setDescription(
                    "Downloading ${model.sizeLabel} for offline use"
                )
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(
                    DownloadManager.Request
                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(
                    appContext,
                    null,
                    "models/${model.fileName}"
                )

        if (wifiOnly()) {

            request.setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI
            )

            request.setAllowedOverMetered(false)

        } else {

            request.setAllowedOverMetered(true)
        }

        val id =
            downloadManager.enqueue(request)

        prefs.edit()
            .putLong(
                downloadKey(model),
                id
            )
            .apply()

        return id
    }

    fun cancelDownload(model: OfflineModel) {

        val id =
            prefs.getLong(
                downloadKey(model),
                -1L
            )

        if (id > 0) {
            downloadManager.remove(id)
        }

        prefs.edit()
            .remove(downloadKey(model))
            .apply()
    }

    fun remove(model: OfflineModel) {

        cancelDownload(model)

        val file = modelFile(model)

        if (file.exists()) {
            file.delete()
        }

        if (
            selectedModelId() == model.id
        ) {
            selectedId = null
            prefs.edit()
                .remove(
                    "selected_text_model"
                )
                .apply()
        }
    }

    fun state(
        model: OfflineModel
    ): ModelTransferState {

        if (isInstalled(model)) {

            return ModelTransferState(
                status =
                    TransferStatus.INSTALLED,
                progress = 100
            )
        }

        val id =
            prefs.getLong(
                downloadKey(model),
                -1L
            )

        if (id <= 0) {

            return ModelTransferState(
                TransferStatus.NOT_INSTALLED
            )
        }

        val query =
            DownloadManager.Query()
                .setFilterById(id)

        downloadManager
            .query(query)
            .use { cursor ->

                if (!cursor.moveToFirst()) {

                    return ModelTransferState(
                        TransferStatus.NOT_INSTALLED
                    )
                }

                val status =
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_STATUS
                        )
                    )

                val downloaded =
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager
                                .COLUMN_BYTES_DOWNLOADED_SO_FAR
                        )
                    )

                val total =
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager
                                .COLUMN_TOTAL_SIZE_BYTES
                        )
                    )

                val reason =
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_REASON
                        )
                    )

                val progress =
                    if (total > 0)
                        (
                            downloaded * 100L /
                                    total
                            ).toInt()
                    else
                        0

                val transferStatus =
                    when (status) {

                        DownloadManager
                            .STATUS_PENDING ->
                            TransferStatus.QUEUED

                        DownloadManager
                            .STATUS_RUNNING ->
                            TransferStatus.DOWNLOADING

                        DownloadManager
                            .STATUS_PAUSED ->
                            TransferStatus.PAUSED

                        DownloadManager
                            .STATUS_SUCCESSFUL ->
                            TransferStatus.INSTALLED

                        DownloadManager
                            .STATUS_FAILED ->
                            TransferStatus.FAILED

                        else ->
                            TransferStatus.NOT_INSTALLED
                    }

                return ModelTransferState(
                    status = transferStatus,
                    progress = progress,
                    downloaded = downloaded,
                    total = total,
                    reason = reason
                )
            }
    }
}
