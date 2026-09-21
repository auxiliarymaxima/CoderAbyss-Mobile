package com.coderabyss.mobile

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalLlmEngine(
    context: Context
) {

    private val engine =
        AiChat.getInferenceEngine(
            context.applicationContext
        )

    private val mutex =
        Mutex()

    private var loadedModel: String? = null
    private var loadedSystemPrompt: String? = null

    private suspend fun waitUntilInitialized() {

        val state =
            engine.state.first {
                it is InferenceEngine.State.Initialized ||
                it is InferenceEngine.State.ModelReady ||
                it is InferenceEngine.State.Error
            }

        if (state is InferenceEngine.State.Error) {
            throw state.exception
        }
    }

    suspend fun generate(
        modelPath: String,
        systemPrompt: String,
        prompt: String,
        maxTokens: Int = 768,
        onToken: (String) -> Unit
    ) {

        mutex.withLock {

            waitUntilInitialized()

            val state =
                engine.state.value

            val reload =
                loadedModel != modelPath ||
                loadedSystemPrompt != systemPrompt ||
                !state.isModelLoaded

            if (reload) {

                if (
                    engine.state.value
                        .isModelLoaded
                ) {
                    engine.cleanUp()
                }

                if (
                    engine.state.value
                    is InferenceEngine.State.Error
                ) {
                    engine.cleanUp()
                }

                waitUntilInitialized()

                engine.loadModel(
                    modelPath
                )

                engine.setSystemPrompt(
                    systemPrompt
                )

                loadedModel =
                    modelPath

                loadedSystemPrompt =
                    systemPrompt
            }

            engine.sendUserPrompt(
                prompt,
                maxTokens
            ).collect {
                onToken(it)
            }
        }
    }
}
