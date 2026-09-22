# Coder Abyss v0.6

Android creation workspace with local text/speech and a private GPU video backend.

## Model downloads

The model manager downloads into `.part` files. A model becomes installed only after the complete byte count and SHA-256 match the pinned manifest. Existing v0.4 files are checked when their model card is displayed; incomplete or damaged files require Retry. Progress includes actual bytes, queued/paused states and a separate verification stage. Retry retains verified Wan components. Cancel stops unfinished components; Remove deletes the entire package.

Wan 2.1 T2V 1.3B Q4 includes three downloads totaling 4,891,677,270 bytes (4.89 GB):

- Wan2.1-T2V-1.3B-Q4_K_M.gguf (982,716,640 bytes)
- umt5-xxl-encoder-Q4_K_M.gguf (3,655,145,312 bytes)
- wan_2.1_vae.safetensors (253,815,318 bytes)

`app/src/main/assets/model-manifest.json` pins source revisions, exact sizes and checksums. `python scripts/update-model-manifest.py` deliberately refreshes the manifest from Hugging Face metadata. Model files are downloaded separately, not included in the APK.

## Create Video

Video defaults to the private Gradio/ZeroGPU Space `andrewmonize/Coder-Abyss-Space`.
In Settings → Video Backend, configure a Hugging Face credential with access to
that private Space, then Test Connection. Credentials are encrypted with an
Android Keystore AES-GCM key and stored in the app's no-backup directory. They are
never embedded in the APK, project files, BuildConfig or source control. No backend
secret is required for the public model downloads. For distribution, each user
needs authorized access or an authenticated intermediary; the owner's personal
token must never be bundled. Prompts and generated videos are processed on the
remote Space when hosted generation is enabled.

QUICK uses Wan 1.3B with a live 100-word limit. Whisper appends editable text;
generation is disabled above the limit, without truncation. LONG uses LTX 13B
distilled with conditioned five-second segments. The backend has generated and
validated 15- and 60-second 320x192 MP4s. The 30/45-second options use the same
segment assembly. This is continuation, not native minute-long generation;
continuity is not guaranteed. The 60-second test required explicit retries after
GPU allocation failures. Other Wan presets are implemented but only one-second
256x256 was exercised on the live deployment.

Create Video queries the backend's capabilities. GPU-only recommendations say
Available on GPU Backend; they are not phone downloads. Existing phone-local Wan
remains an optional **Experimental — Very Slow on Mobile** mode, using the pinned
stable-diffusion.cpp runtime. Local text, Whisper and local Wan share the existing
native inference gate. The phone-local model package remains in AI Models.

Projects are stored under the app-private `Projects/Videos/<UUID>/` directory,
with `project.json`, prompts, jobs, generations, renders and exports folders.
WorkManager owns execution/tracking; Compose only observes persisted records.
Navigation, screen changes and process recreation do not resubmit remote jobs.
An uncertain submission is looked up by its original client request ID. Network
recovery checks the same backend job. Retry Download only retrieves the existing
result; explicit generation retry resumes saved LTX segments when available.

Space storage is currently ephemeral. A Space rebuild can remove backend jobs
and results. The phone retains its metadata and shows an unknown/restarted job;
it never silently creates another generation. Configure `CODER_ABYSS_DATA_DIR`
on an attached persistent volume to retain backend files across restarts.
Android background scheduling is subject to OS delays. Experimental local
inference uses a foreground worker; process death interrupts local computation
and requires an explicit new generation.

Results download through partial files with HTTP Range support, byte count and
Android media validation before completion. Preview supports playback/replay,
Save MP4 to Movies/CoderAbyss, Share, Rename, Delete, and a variation/regeneration
prompt. Each new generation currently creates its own video project.

**Local Only** blocks hosted submission, status checks, diagnostics and result
downloads. Existing local videos remain playable/exportable. It does not cancel
an already-running GPU job remotely; tracking resumes after Local Only is off.

Build App, Research Paper and Create Visuals preserve their independent default text models. Tap to Speak appends local Whisper transcription to the editable prompt; submission is always explicit.

## Build and verification

Java 17, Gradle 8.9, Android SDK 35, NDK 29.0.13113456 and CMake 3.31.6 are required. Initialize submodules recursively, then run `gradle :app:testDebugUnitTest assembleDebug bundleRelease --console=plain`. The APK targets ARM64 Android 13+.

GitHub Actions verifies the APK signature and ZIP alignment and publishes the APK with its SHA-256 and exact size. The download regression checks reject truncated files, corruption with unchanged file size, and partial files larger than the old 1 MB threshold. Building successfully does not establish device video performance.

## Manual checks when a device is available

- Start a large download and confirm it remains downloading after 1 MB. Pause Wi-Fi, resume, cancel and retry; restart the app mid-download. Confirm a file is usable only after verification.
- Verify an old v0.4 file; confirm a partial/corrupt one fails and a complete valid one is retained.
- Complete all three Wan files, disconnect the network, generate a local clip, preview it, then save to the gallery as MP4.
- Cancel during loading/sampling and switch to a text workflow; verify cleanup before the next model starts.
- Configure Video Backend, Test Connection, generate QUICK at 1 second / 256x256.
- Navigate to Projects, switch Android apps, then restart Coder Abyss: confirm the same job ID remains.
- Disconnect/reconnect internet and confirm no duplicate generation; retry a failed download only.
- Paste 101 Wan words, confirm Generate disables, shorten to 100 and confirm it enables.
- Enable Local Only: hosted calls stop while saved videos still play and export.
- Preview, share, save and reopen the completed project. Then try LONG at 15 seconds.

Backend CUDA, Wan and LTX tests ran against real ZeroGPU hardware. All eight Android unit tests, debug APK and release AAB builds passed. APK
signature and 16KB ZIP/ELF alignment passed. Android phone
navigation/process-death/network/preview checks require a connected device and
are not claimed as completed by a desktop build.

## Runtime and model sources

The native video runtime is [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) (MIT), statically linked with a private GGML copy to avoid collisions with llama.cpp. Public Wan weights originate from [Wan2.1](https://github.com/Wan-Video/Wan2.1) (Apache-2.0); the manifest names the quantized/repackaged distribution sources. Bundled runtime license notices are in `app/src/main/assets/wan-runtime-licenses.txt`.
