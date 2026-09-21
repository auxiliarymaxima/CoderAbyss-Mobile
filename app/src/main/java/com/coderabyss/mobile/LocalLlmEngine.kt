package com.coderabyss.mobile

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalLlmEngine(context: Context) {
    private val engine = AiChat.getInferenceEngine(context.applicationContext)

    companion object {
        // Every workflow wraps the same native engine. Hold this through cleanup.
        private val mutex = Mutex()
    }

    suspend fun generate(
        modelPath: String,
        systemPrompt: String,
        prompt: String,
        maxTokens: Int = 768,
        onToken: (String) -> Unit
    ) {
        mutex.withLock {
            engine.state.first {
                it is InferenceEngine.State.Initialized ||
                    it is InferenceEngine.State.ModelReady ||
                    it is InferenceEngine.State.Error
            }
            if (engine.state.value.isModelLoaded || engine.state.value is InferenceEngine.State.Error) {
                withContext(Dispatchers.IO) { engine.cleanUp() }
            }
            try {
                engine.loadModel(modelPath)
                engine.setSystemPrompt(systemPrompt)
                engine.sendUserPrompt(prompt, maxTokens).collect { onToken(it) }
            } finally {
                // Free native memory on success, failure, navigation and cancellation.
                withContext(NonCancellable + Dispatchers.IO) {
                    if (engine.state.value.isModelLoaded || engine.state.value is InferenceEngine.State.Error) {
                        engine.cleanUp()
                    }
                }
            }
        }
    }
}
