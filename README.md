# Coder Abyss v0.5

Android creation workspace with local text, speech and Wan video generation.

## Model downloads

The model manager downloads into `.part` files. A model becomes installed only after the complete byte count and SHA-256 match the pinned manifest. Existing v0.4 files are checked when their model card is displayed; incomplete or damaged files require Retry. Progress includes actual bytes, queued/paused states and a separate verification stage. Retry retains verified Wan components. Cancel stops unfinished components; Remove deletes the entire package.

Wan 2.1 T2V 1.3B Q4 includes three downloads totaling 4,891,677,270 bytes (4.89 GB):

- Wan2.1-T2V-1.3B-Q4_K_M.gguf (982,716,640 bytes)
- umt5-xxl-encoder-Q4_K_M.gguf (3,655,145,312 bytes)
- wan_2.1_vae.safetensors (253,815,318 bytes)

`app/src/main/assets/model-manifest.json` pins source revisions, exact sizes and checksums. `python scripts/update-model-manifest.py` deliberately refreshes the manifest from Hugging Face metadata. Model files are downloaded separately, not included in the APK.

## Create Video

On-device is the default. After the full Wan package is verified, prompts run through a pinned stable-diffusion.cpp CPU runtime via JNI. Internet and provider tokens are not needed for local generation. Choose a low-resolution 17, 33 or 49 frame clip at 16 fps. Results are silent H.264 MP4 files encoded with Android MediaCodec. This is real model inference, not a slideshow or server call.

Hosted Wan through Hugging Face/Fal remains optional. The video screen remembers the selected mode and local preset. Both modes produce a private cached preview with Play and Save MP4 buttons. Save copies the video into Movies/CoderAbyss; generation itself does not publish to the gallery. The last preview is restored while its cache file exists.

CPU video generation can be very slow and uses substantial RAM. More capable hardware does not guarantee success: Android may terminate the process under memory pressure. No on-device performance or quality claim is made. Keep the video screen open during generation; leaving cancels it. Cancellation during model loading waits for that load to return. Text LLM, Whisper and Wan share a process-wide inference gate; native resources are freed before another operation starts.

Build App, Research Paper and Create Visuals preserve their independent default text models. Tap to Speak appends local Whisper transcription to the editable prompt; submission is always explicit.

## Build and verification

Java 17, Gradle 8.9, Android SDK 35, NDK 29.0.13113456 and CMake 3.31.6 are required. Initialize submodules recursively, then run `gradle :app:testDebugUnitTest assembleDebug bundleRelease --console=plain`. The APK targets ARM64 Android 13+.

GitHub Actions verifies the APK signature and ZIP alignment and publishes the APK with its SHA-256 and exact size. The download regression checks reject truncated files, corruption with unchanged file size, and partial files larger than the old 1 MB threshold. Building successfully does not establish device video performance.

## Manual checks when a device is available

- Start a large download and confirm it remains downloading after 1 MB. Pause Wi-Fi, resume, cancel and retry; restart the app mid-download. Confirm a file is usable only after verification.
- Verify an old v0.4 file; confirm a partial/corrupt one fails and a complete valid one is retained.
- Complete all three Wan files, disconnect the network, generate a local clip, preview it, then save to the gallery as MP4.
- Cancel during loading/sampling and switch to a text workflow; verify cleanup before the next model starts.
- Generate with hosted Wan and verify the same preview/save behavior. Denied tokens and failed downloads must display errors.

## Runtime and model sources

The native video runtime is [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) (MIT), statically linked with a private GGML copy to avoid collisions with llama.cpp. Public Wan weights originate from [Wan2.1](https://github.com/Wan-Video/Wan2.1) (Apache-2.0); the manifest names the quantized/repackaged distribution sources. Bundled runtime license notices are in `app/src/main/assets/wan-runtime-licenses.txt`.
