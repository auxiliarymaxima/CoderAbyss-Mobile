# Scoped workflow update: delivery and acceptance record

Base: `8e3f15e` (Coder Abyss 0.7.1). Branch: `codex/scoped-workflows-video-editor`.
No version bump, application-ID change, signing change, schema migration or native
submodule update. The supplied reference pictures are layout references only.

## Exact source changes and why

All Kotlin paths below are relative to `app/src/main/java/com/coderabyss/mobile/`.

| File | Required change |
|---|---|
| `app/build.gradle.kts` | Adds Media3 Transformer/effect 1.5.1 for local editing; no existing dependency upgrades. |
| `platformui/WorkspaceScreen.kt` | Compact Create/Results/Tasks/Edit views for the four workflows, advanced controls, retained voice/task/project integration, revision context and editable file input. Companion keeps its previous presentation. |
| `platformui/ModelPicker.kt` | v0.6-inspired navy/cyan rounded model cards; real byte progress; read-only suitability information and deliberate heavy-model warning. Downloader/registry unchanged. |
| `platformui/OutputGallery.kt` | Adds generated videos to the timeline; protects directly referenced source videos from deletion. Existing export/share/history actions remain. |
| `platformui/ResearchWorkspace.kt` | Opens completed documents in the viewer; adds search and text-size controls. Existing citation/section/chart/export controls remain available. |
| `platformui/WorkflowHistory.kt` | Inspect saved draft content and explicitly restore a selected version through existing checkpoints. |
| `platformui/WorkflowAttachments.kt` | SAF file picker queues managed imports through existing tasks. |
| `models/WorkflowRecommendations.kt` | Conservative workflow defaults from existing runtime, RAM, thermal and storage checks; preserves explicit model choices. |
| `models/WorkflowPerformance.kt` | Caches measured characters/second from completed local text runs, including load time; no invented token counts or inference estimates. |
| `research/ResearchDraft.kt` | Parses model-produced headings into dynamic document sections. |
| `videoeditor/VideoTimeline.kt` | Non-destructive per-instance trim/crop/volume validation and persisted ordering in project metadata. |
| `videoeditor/VideoEditor.kt` | Horizontal draggable clips, import/add, range trim, crop outline/presets/free bounds, duplicate/delete, audio/rotation and export controls. |
| `videoeditor/TimelineRenderer.kt` | Background-worker-owned H.264/AAC MP4 composition, partial output, real Transformer progress and output validation. |
| `tasks/TaskManager.kt` | Adds two operation identifiers and duplicate-active-render guard; retains WorkManager/persistence architecture. |
| `tasks/PlatformTaskWorker.kt` | Executes imports/editor rendering, records local performance, and commits whole-paper revisions through existing checkpoints. |
| `app/src/test/java/com/coderabyss/mobile/WorkflowEditingTest.kt` | Tests default routing, invalid edits, independent repeated source ranges, persisted reorder and dynamic research sections. |
| `docs/scoped-workflows.md` | Scope, limitations, validation and phone acceptance record. |

## Workflow results

- **Build an App:** compact prompt/model/voice input; source generation, saved files,
  source viewer, ZIP release and checkpoint history retained. Invalid source-file responses fail instead of being reported as source generation success. Requests include
  bounded existing-file context, so later prompts can modify saved source. The
  existing runtime does NOT have an isolated Android compiler/signing worker.
  Automatic compile/repair, live app preview, installation and signed generated
  APK/AAB releases are not implemented or represented as successful.
- **Video:** Wan remains remote by default; LTX and backend-reported durations and
  resolutions are retained. Wan still enforces 100 words without truncation.
  Completed/imported videos can be added to the persistent local editor. Playback,
  saving/sharing and generation history use the existing output gallery.
- **Research:** Generate Paper makes a whole draft with dynamic headings; another
  prompt revises the saved draft. Existing project checkpoints preserve versions.
  Viewer search/text sizing and the existing PDF/DOCX/PPTX/XLSX exports remain.
  It is a document viewer, not an embedded PDF renderer with page navigation.
- **Visuals:** compact prompt/model/style/resolution input; negative prompt and seed
  move to More options. Style is real prompt input, not an advertised new model
  capability. Existing gallery, zoom, source-prompt variations, save/share/history
  remain. Current SDXL backend does not offer contextual image editing or AI
  upscaling; those buttons are not fabricated.

## Device/model behavior

The existing ModelRegistry and DeviceCompatibility remain the source of truth.
Defaults consider ARM64/runtime support, RAM/available memory, thermal state, disk
space, installed models and conservative model size. No GPU/NPU support is inferred
from a marketing name. Explicit workflow defaults and project choices take priority.
Eligible larger text models remain selectable, with a warning and Run Locally Anyway.
Existing true incompatibility/resource checks remain intact.

Actual completed text work supplies cached throughput (characters/s including load),
invalidated after 30 days or an OS build change. This is not a synthetic benchmark,
not tokens/s and not a promised task duration. No dedicated accelerator benchmark
or dynamic NPU runtime has been added. Measurements are shown to aid user selection. A cached run below two characters/s
excludes that model from automatic defaults, never from deliberate user selection.

There is no verified practical local image/video runtime in the current registry.
Video and visuals therefore retain their secure remote route; Local Only does not
silently fall back to cloud. The legacy native Wan implementation/files remain, but
this change does not manufacture a new verified download manifest or enable it as
a recommended/usable model in the new workflow. Phone-local Wan override remains
unexposed. Heavy local text override is supported.

Neodragon research: https://huggingface.co/Qualcomm-AI-Research/Neodragon describes
its published fast path on Qualcomm Hexagon NPU. This does not verify Exynos A56
support. MobileWan research: https://arxiv.org/abs/2607.06173 likewise does not supply
an integrated, verified Android runtime for this repository. No sub-60-second
phone video result is claimed.

## Editing and persistence

Each timeline entry has its own UUID, source project-relative path, duration,
start/end, normalized crop bounds, volume and rotation. One continuous range per
entry. Add the same source again or Duplicate to select another section. The
original media is not modified. Entries are saved through existing atomic project
updates; no database or project migration was introduced.

Hold and drag horizontally to reorder; arrow buttons are also available. The source preview
shows trim playback and a crop outline. Full edited-timeline preview is obtained by
rendering, then playing the real MP4 in Results. It is not a live CompositionPlayer.
Cut joins only; crossfade was optional and is not implemented. Export offers 480p,
720p or 1080p and a target maximum of 24/30 fps with H.264/AAC. FrameDropEffect
reduces higher frame rates toward the target; it does not interpolate slower sources. Export size is not AI upscaling and device
codec limitations can cause a real failure. Renderer checks nonempty output, video
track, duration and a decodable frame before attaching the result.

Rendering runs in the existing foreground WorkManager operation and receives a
snapshot of the timeline. Navigation never owns/cancels that work. On interruption,
edit metadata remains saved; retry may re-render rather than resume mid-encode.
Imports use persistent SAF grants and partial files. Plain-text assets are bounded
and included as untrusted reference material for app/research requests. Other file
formats are stored; document extraction and image-conditioned inference are not
claimed. Imported files can also be copied using the existing cross-project picker.

## Preservation and limitations

- Unrelated screens changed: **No**. Companion's existing workspace remains separate.
- Authentication/Google/Firebase/account changed: **No**.
- Subscription/Admin/Super Admin/security/gateway architecture changed: **No**.
- Model Manager backend replaced: **No**. Download/pause/resume/cancel/verification/
  remove/current-model selection still call the original manager.
- Progress uses real state: **Yes**. Bytes/total when known; queued/verifying stages
  are indeterminate. Installed state replaces the transfer bar on the next poll.
- v0.6 resemblance: historical ModelManagerScreen/ModelCard at `d2bf748` was inspected;
  rounded navy cards and electric blue/cyan progress restored. Device visual QA is
  still required; pixel equivalence is not claimed.
- Active jobs survive navigation: existing architecture retained; editor render is
  worker-owned. Device background/process-death verification is still required.
- Heavy remote routes: existing gateway/HF routes are reused without security changes.
  Live gateway deployment, user entitlement and GPU quota remain prerequisites.
- Heavy local override: available for technically supported text models; no new
  image/video/NPU support is claimed.
- Generated clips → timeline: implemented. Trim/reorder/crop/duplicate/delete/audio/
  rotate/render/export: implemented; physical hardware codec testing remains required.
- Real download/pause/resume/verification UI: bound to existing state; a fresh multi-GB
  model download has not been performed merely for UI verification.
- Unrelated issue observed, not modified: existing deprecated ArrowBack Compose icon.

## Validation

Final verification on 24 September 2026: `testDebugUnitTest`, `lintDebug`,
`assembleDebug` and `bundleRelease` all passed together (`BUILD SUCCESSFUL`).
All 26 unit tests passed with no failures, errors or skips. `git diff --check` passed.
The debug APK passed APK signature verification, 16 KB ZIP alignment, the repository's
credential-pattern scan, and its ARM64 native verification: all 18 shared libraries
retain 16 KB ELF alignment, including the llama.cpp, Whisper and Wan JNI entry points.

Artifacts (paths relative to the repository):

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 105,015,611 | `8e960861b029a4b491605e74057018201c6b108d1320ef4135eff87d77f08ed4` |
| `app/build/outputs/bundle/release/app-release.aab` | 68,864,401 | `02158c62a5f46a3bccf84cb3a5b3261c531fb3b73dedb1b6b255c3933d90cddc` |

The APK uses the existing local debug certificate registered for Firebase. Its
certificate SHA-256 is `dd92ddbddbe9d953f9f1c8857d27e088a655e95c86b39787c799ec45c0743ef8`.
The release AAB contains no signing entries: it is **unsigned**, not ready for Play
submission. Production signing was not configured or changed in this scoped task.
These artifacts are Coder Abyss itself, not proof of a generated-app build pipeline.

No connected phone was available for actual Google sign-in, visual screenshot
comparison, GPU generation, native inference or MediaCodec export acceptance.

## Exact next phone test

1. Open a Video project and Edit. Import two short videos (one with audio, one silent).
2. Add each to the timeline; add the first source a second time.
3. Select different trim ranges, crop one portrait, mute one, reorder by dragging.
4. Leave to Home/Projects and reopen. Confirm all edits persist.
5. Render MP4. Navigate away during render; return to Tasks, then play the result in
   Results. Verify order, cuts, crop, rotation, audio, size and frame rate. Save/share.
6. In AI Models, start a small real model download; check bytes/progress, pause,
   resume and verification, and navigate away/back. Do not expect fake progress.
7. Recheck Google sign-in, sign-out, voice-to-editable-prompt and a research export.
