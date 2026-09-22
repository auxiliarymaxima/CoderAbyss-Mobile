# Video backend migration: inspection and deployment gate

Inspected 2026-09-22 at Android commit
`8b2dc44b8f1d5a808f756d6d9fe9d4a91af44325` (v0.5).
This records findings and pending work, not a completed release.

## Existing architecture

| Concern | Existing implementation | Migration consequence |
| --- | --- | --- |
| Navigation and services | `MainActivity.kt`, `CoderAbyssApp`, `FeatureWorkspace` | Preserve Compose styling and existing services; extract video UI into its own module/file. |
| Video UI | `WanVideoWorkspace`; local/hosted selector, editable Whisper input, status text, VideoView, Save MP4 | UI currently owns a coroutine job and loses it on navigation. Replace ownership with persistent operations observed by Compose. |
| Hosted video | `WanVideoClient.kt`, OkHttp, Hugging Face router/Fal queue | Reuse networking conventions; encapsulate private Gradio API separately. Existing provider job IDs are not persisted. |
| Local video | `LocalWanEngine.kt`, `wanLib`, stable-diffusion.cpp, three verified model assets | Retain as optional experimental mode, not the default GPU path. |
| Encoding/export | `Mp4Encoder.kt`, `VideoStorage.kt`; AVC/MP4, scoped MediaStore export | Reuse preview/export; remote downloads need validated partial-to-final project files. |
| Projects | Placeholder screen | No existing project repository to reuse. Introduce central project storage with durable metadata and managed outputs. |
| Tasks/background | No central task repository, WorkManager, or foreground service | Introduce durable task/recovery ownership. Do not describe current navigation behavior as persistent. |
| Progress | Status strings and local native sampling steps; indeterminate Compose indicator | No persisted step tracker or elapsed-time implementation currently exists. Add truthful stages and timestamps. |
| Models | `OfflineModels.kt`, manifest, Android DownloadManager, checksum verifier | Keep one source of download/installed state and workflow defaults; add hosted capability metadata rather than phone downloads for GPU-only engines. |
| Local inference | `LocalInferenceGate`, singleton llama runtime; Whisper and Wan use shared mutex | Preserve single heavy local inference at a time. Remote inference must not load video models on the phone. |
| Privacy/settings | Local First explanation and Wi-Fi download setting | No enforced Local Only switch exists yet. Add one policy consulted before every remote call, including polling/downloads. |
| Credentials | Hosted video token is held in Compose memory | No persistent secure credential store exists. Use Android Keystore-backed encryption and exclude credential ciphertext from backup. |
| Research/visuals | Local text generation with feature-specific system prompts | These are not yet document/image project workspaces. Earlier attached requirements remain outstanding. |
| Native build | ARM64 llamaLib/whisperLib/wanLib; recursive submodules | Preserve native configuration and verify APK libraries/signature/alignment in the final build. |

## Required deployment gate

Target: private `andrewmonize/Coder-Abyss-Space`, Gradio + ZeroGPU.
The existing sibling directory was empty and was not a Git checkout. A minimal
diagnostic has been prepared there, without overwriting remote repository files.
Reconcile with the authenticated remote checkout before committing.

Hugging Face device authorization is now verified as andrewmonize. The private
Space has been deployed through the authenticated Hub API and its Git history
has been fetched into the sibling checkout. Git transport uses an ephemeral
authorization header; no credential is stored in repository configuration.

The diagnostic must build in Spaces and report a successful real CUDA operation,
GPU identity, and VRAM before any large video model is installed. Local Python
syntax validation alone does not meet this gate.

Current ZeroGPU documentation describes Gradio-decorated GPU allocation and
specific model-placement behavior. Validate request context/quota attribution
before choosing job execution: an arbitrary detached Python worker must not be
assumed compatible. Android must receive a job ID quickly and never retain one
HTTP request for the entire generation. This transport requirement does not
justify bypassing ZeroGPU's allocation lifecycle.

Sources:
- https://huggingface.co/docs/hub/spaces-zerogpu
- https://huggingface.co/docs/hub/spaces-storage

## Implementation requirements retained

- Private backend authentication with replaceable client credential strategy;
  no embedded personal credential, secret logging, or credentials in Git.
- Modular Wan/LTX provider capabilities; real supported durations/resolutions;
  no advertised long-video duration until tested. Optional continuation must be
  explicit, with persisted segments and retry of failed segments only.
- Durable jobs, client request idempotency, constrained queue/concurrency,
  cancellation semantics, sanitized errors, and validated atomic MP4 outputs.
- Persist backend/job/project/request/timestamps/status before and after every
  important transition. Recover the same job after process death or network loss.
- No automatic generation retry on an ambiguous submission or missing job after
  a backend restart. Retry download separately from inference.
- Project previews and Play/Replay/Save/Share/Rename/Delete/Regenerate/Variation.
- Global tasks and elapsed time; stages/percentages only when real.
- Local Only blocks all HF traffic, including existing-job status queries;
  local files remain viewable/exportable. Enabling Local Only cannot cancel a
  remote job without a network request; retain its last known state locally.
- Wan live `X / 100 words`, whitespace-safe counting, no truncation, disabled
  submission over 100, editable Whisper output, enhancement limit enforcement.
- Compact service-specific recommended models backed by central metadata.
- Earlier sequence also includes research projects, verified citations, section
  editing, legitimate DOCX/PDF/PPTX/XLSX export; image provider abstraction,
  galleries, PNG/JPEG/WEBP export; cross-project assets; and persistent tasks
  for local text, transcription, builds, downloads, and exports. These are
  retained requirements, not functionality already present in v0.5.

## Validation and release status

- Diagnostic syntax: passed locally; dependency release availability checked.
- HF access: verified; Space remains private.
- Space build and synchronous ZeroGPU diagnostic: PASSED at commit
  `74fbd094b4ed79f827cdf65720615fe14720b9f7`. CUDA 12.8, PyTorch 2.8.0+cu128,
  NVIDIA RTX PRO 6000 Blackwell Server Edition MIG 2g.48gb, 50,868,518,912
  total VRAM bytes and 50,369,724,416 free bytes at diagnostic time.
  A real CUDA tensor computation returned the expected result.
- Background allocation after client disconnect: PASSED.
- Wan: real 1-second 256x256 MP4 generation, complete FFmpeg decode, download
  and HTTP Range resume validated.
- LTX distilled 13B: real 15-second and 60-second 320x192 generation validated.
  The latter required two explicit resumes and reused already encoded segments.
- Backend capabilities now advertise supported continuation durations; one native
  60-second diffusion pass is not claimed. Current storage remains ephemeral.
- Android: encrypted credentials, Local Only guard, project-backed WorkManager
  jobs, recovery by original job/request ID, explicit retry, MP4 download/preview,
  model capability catalog, word counter and optional local foreground execution.
- Android Kotlin, eight unit tests, debug APK and release AAB: PASSED.
  Native submodules remain at their original pinned revisions. APK signature,
  ZIP integrity/alignment and all 18 AArch64 libraries' 16KB ELF alignment pass.
  Version code 6 / version name 0.6, minimum Android 13 (API 33).
- HF backend deployment: RUNNING at
  `89d3ded42b8224a59a4d48c98294f28c7859d460`.
- Six backend unit tests pass; live invalid engine/duration/101-word requests
  are rejected. No secret values are stored in either repository.
- No Space secrets are required. Android needs a user-configured authorized HF
  credential. Persistent server storage needs a mounted volume and the non-secret
  `CODER_ABYSS_DATA_DIR` setting.
- Phone navigation/process-death/network/preview tests remain device checks.
- Broader research/visuals/export/all-service task requirements listed above are
  outside this video implementation and are not claimed complete.
