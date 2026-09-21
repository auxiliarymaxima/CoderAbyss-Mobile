# Coder Abyss v0.4

**Local AI. Real Creation. A Brighter Tomorrow.**

Android creation workspace built with Kotlin and Jetpack Compose.

- Tap to Speak in Build App, Create Video, Research Paper and Create Visuals uses an installed Whisper model. Stop recording to append the transcription to the prompt, edit it, then submit explicitly.
- Build App, Research Paper and Create Visuals each save their own default installed GGUF model under Change Model. Existing global selection seeds a workflow on first use; subsequent workflow choices are independent.
- Create Video keeps its separate Wan2.1 T2V 1.3B default and the existing Hugging Face / Fal queue and Movies/CoderAbyss save flow. Video requires internet and a Hugging Face token; text generation and transcription run locally.
- Model Manager has one shared selected text model indicator. This is the global selection, not a claim that the model is resident in memory. Running a text workflow selects its saved model globally without changing other workflow defaults.
- One process-wide lock serializes local LLM loading, generation and cleanup. Native LLM memory is released after each request, including cancellation and failures.
- llama.cpp initialization, load, context allocation, prompt and token decode failures surface actionable messages.

## Build

Use Java 17, Gradle 8.9, Android SDK 35, NDK 29.0.13113456 and CMake 3.31.6. Initialize submodules with `git submodule update --init --recursive`, then run `gradle assembleDebug bundleRelease --console=plain`. GitHub Actions builds v0.4 APK and AAB artifacts on pushes to main.

## Device regression checks

1. Install Whisper and two text GGUF models. In each of the four workflows, dictate into a nonempty prompt. Confirm existing text remains, the transcript can be edited, and no request runs until submission. Check permission denial, silence, navigation while recording and transcription failure.
2. Choose different defaults for Build App, Research Paper and Create Visuals. Navigate away, restart the app and verify each choice persists. Video must still show Wan. Removing a saved text model must require choosing an installed replacement.
3. In Model Manager select text model A, then B: only B is selected. Run a workflow with A: only A becomes globally selected, with other workflow defaults unchanged.
4. Start a long generation, stop or navigate away, then start another workflow using a different model. Verify native cleanup completes before the next load and no overlapping models remain in memory.
5. Try an invalid GGUF and an oversized prompt. Confirm useful errors, then successfully run a valid model. Test a compatible APK on a device with limited memory for allocation errors.
6. Submit an edited video prompt with a valid token. Confirm Wan queue progress, video playback and saving to Movies/CoderAbyss still work.
