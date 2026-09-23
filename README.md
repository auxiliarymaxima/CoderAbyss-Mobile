# Coder Abyss v0.7.1 — corrective integration

Android creation workspaces with small local AI models and a private Hugging Face
GPU backend. The APK targets ARM64 Android 13+ (versionCode 8).

## What runs where

| Service | On device | Cloud GPU |
| --- | --- | --- |
| AI Companion | LFM2.5-1.2B Q4; other compatible installed text models | Qwen 2.5 7B |
| Build an App | Qwen Coder 1.5B / 3B Q4, subject to available RAM | Qwen Coder 2.5 7B |
| Research Paper | Compatible installed text models | Qwen 2.5 7B |
| Speech | Whisper Tiny / Base | None |
| Create Visuals | Editing, preview and export | SDXL 1.0 |
| Create Video | Preview and export | QUICK: Wan 1.3B; LONG: LTX 13B distilled |

`models/ModelRegistry.kt` is the shared metadata source for AI Models and every
service selector. Services and individual projects remember their own model.
One shared native mutex permits only one text LLM at a time. Whisper has a
separate gate and may coexist with text inference when memory permits.
Available RAM, total RAM, ABI, storage and thermal status inform compatibility.
These are estimates, not device performance guarantees. LFM uses the
[LFM Open License](https://www.liquid.ai/pricing), including commercial revenue
conditions; individual model cards identify their licenses/sources.

Wan/LTX/image weights cannot be downloaded or executed through the Android UI.
Legacy local-Wan weights remain removable and existing videos remain usable.
Native module revisions are preserved for build compatibility; the phone-local
video execution path is disabled.

## Restored presentation and production account boundary

The v0.6 header, assistant face, cyan/dark panels, Quick Actions, microphone and
bottom navigation are restored in `presentation/`. MainActivity opens
`CoderAbyssShell`, not `PlatformApp`. v0.7 repositories, workers, exports, research,
model registry and independent Whisper/text execution remain underneath.

**Deployment status:** Google/Firebase/Play/Cloud Run configuration is not supplied.
The integration code builds, but live account/billing/gateway acceptance is not
complete. Cloud generation fails closed until configured. Local features remain
accessible through “Continue with local projects.” Do not treat this as an already
launched subscription product.

Normal users sign in with Google through Credential Manager and Firebase Auth.
The server validates signed, non-revoked identity tokens and returns role/plan
state. Play supplies product prices; purchases/restore send tokens for server-side
Developer API verification and acknowledgement. A client purchase callback never
grants PRO. OWNER is pinned to a verified UID; OWNER can grant/revoke ADMIN, while
ADMIN cannot rotate credentials or create owners. FREE retains local features;
SUBSCRIBER receives verified paid cloud access. OWNER/authorized ADMIN bypass paid
subscription, not configured resource limits.

Android calls the Coder Abyss gateway. The HF credential lives exclusively in
server Secret Manager; no normal-user token entry remains. Old development tokens
are removed from device storage at upgrade and direct HF calls are disabled.
Owner-only administration provides write-only rotation, emergency replacement,
time-limited rollback, provider changes and audit metadata, with recent sign-in
required. The server enforces all permissions independently of visible buttons.
Changing the Space/credential does not require a new APK.

See [gateway setup/security](gateway/README.md), [API contract](gateway/API.md), and
[corrective verification report](docs/corrective-merge.md). No actual secret values
are documented or bundled. Public Firebase app identifiers are build configuration,
not the server credential. Projects remain device-local, accessible across sign-out;
cloud sync is not implemented. New tasks are bound to the authenticated account.
Pre-authentication remote jobs retain their IDs/files but require owner-reviewed
server ownership migration. They are never assigned to the first login or silently
resubmitted. Already downloaded videos/images remain usable.

Hosted prompts/results are processed remotely. **Local Only** blocks hosted
submission, polling, result downloads, provider tests, web source lookup and new
model downloads. Local text, speech, editing, projects and local exports remain
available. It cannot cancel an already-running server job without making a
network request; tracking resumes after it is disabled.

## Persistent projects and operations

Projects live in app-private `Projects/{Apps,Videos,Research,Visuals,Companion}/UUID`.
They contain metadata, prompts, sections/sources, outputs, assets, exports,
source files and saved versions. Legacy video projects migrate in place without
changing backend job IDs; remote legacy ownership reconciliation is explicit. Rename, duplicate, deletion and draft restoration are
available. Restoring a draft does not resubmit generation or remove outputs.
Managed cross-project assets are copies; deleting an original cannot break them.
Source ZIP exports include managed non-audio assets.

`tasks/TaskManager`, `PersistentTaskStore` and `PlatformTaskWorker` own execution.
Compose observes repository flows; Activity/navigation do not own generation.
Inputs and a client request ID are persisted before submission. Remote job IDs
are saved immediately. Recovery queries the same job. Uncertain submissions are
looked up by request ID and never blindly resubmitted. Explicit retry of a failed
backend job preserves its identity; download retry retrieves the same result.
The global Tasks view includes stages, elapsed time, real byte counts, diagnostics,
cancellation, retries and saved partial text. Video displays its real stage trail.
Only actual backend text token metrics and diffusion progress are shown.

Local text uses a foreground worker and saves partial output. Process death
interrupts local computation; Continue/Retry is explicit. Microphone capture uses
a foreground service, writes PCM locally, then queues Whisper. Dictation is added
to the editable project prompt and never triggers generation automatically.
Android scheduling/battery policies can delay tracking; a force-stop prevents
background work until the app is opened again.

New model transfers use one resumable worker behind the existing model manager.
Pause retains bytes; Resume uses HTTP Range where supported. Full pinned size and
SHA-256 verification is mandatory before automatic installation. Cancel removes
the incomplete transfer. Existing OS DownloadManager transfers remain visible
until finished/cancelled. Local Only stops those legacy transfers; they may need
restarting. `model-manifest.json` pins revisions, sizes and checksums.

## Research, visuals and video

Research provides persistent metadata, editable/reorderable sections, individual
Generate/Expand/Rewrite/Continue, manual sources, optional real Crossref lookup,
source notes, section citation links, findings/data rows, preview and export.
Crossref results are bibliographic metadata/available abstracts, not verified
full-text evidence. Generated prose remains an AI draft. Citation formatting
helpers cover APA/MLA/Chicago/Harvard/IEEE from saved fields; review scholarly
formatting and original claims before publication. No missing author/date/DOI
is invented. Numeric findings with a shared unit can produce a local chart.

DOCX, PPTX and XLSX are genuine Office Open XML ZIP packages. PDF contains real
text pages. PPTX creates a concise section summary; XLSX contains structured
sources/findings rather than a pasted essay. Managed images/charts embed in
DOCX/PDF/PPTX. Images are bounded to 2048 pixels for document memory use; original
project images are retained. Open, Share, Save, Rename and Delete are available
for outputs. MediaStore exports use pending entries; image export offers original
bytes, PNG, quality-100 JPEG and lossless WebP.

Visuals use SDXL remotely, with prompt, negative prompt, supported resolution,
seed, persistent history, full-screen swipe/pinch preview and independent outputs.
SDXL's two 77-token encoder limits are enforced by the server without truncation.

Video settings come from real backend capabilities. Wan has a live **X / 100
words** counter; overlong prompts remain editable but cannot generate. LTX uses
conditioned five-second segments for 15/30/45/60 seconds at 320x192. Live 15- and
60-second outputs were verified; 30/45 use the same segment assembly. This is
continuation, not native minute-long inference, and continuity is not guaranteed.
The 60-second test needed explicit resumes after GPU allocation failures. Wan's
one-second 256x256 path was tested live; other advertised Wan presets still need
phone acceptance testing. MP4s download, validate and attach to the project for
play/replay/save/share. Restore an output's prompt/settings to regenerate or edit
a variation; generations remain separate outputs in the same project.

## App source and remaining boundaries

Coding models generate JSON source bundles which are validated into a project
source tree. Files can be edited, versioned and exported as ZIP with assets.
Malformed/truncated model output remains a text draft for inspection; it is never
reported as a compiled app. No isolated Android build worker is connected, so
in-app APK/AAB compilation is explicitly unavailable. Generated code is not run
as arbitrary shell commands on the inference Space.

The current Space uses ephemeral storage. Deployments can remove backend jobs
and results; Android retains IDs and reports unknown/restarted jobs without
regenerating. Download finished outputs before redeploying or attach durable
storage. GPU quota, allocation time and model availability are external limits.
Gateway deployment/live account onboarding, automated provider fallback,
and a production isolated app-build service remain external integration work.
Phone UI, background restrictions, microphone, local inference performance and
media exports still require physical-device acceptance testing.

## Build and verification

Use Java 17, Gradle 8.11.1, Android SDK 36 (native modules also use SDK 35), NDK 29.0.13113456 and CMake 3.31.6.
Initialize submodules recursively, then run:

```text
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleRelease --console=plain
```

Tests cover model corruption, prompt limits, routing, Office package structure,
embedded assets, project migration, managed copy isolation, durable job identity
and Local Only blocking. Android behavior tests use
[Robolectric 4.14](https://robolectric.org/compatibility_table/), compatible with
the API 34 test configuration and this Java toolchain. DOCX/PPTX/XLSX packages were additionally opened
by independent Python Office readers. Historical v0.7 direct-Space GPU tests generated and downloaded
an SDXL PNG and both Qwen text outputs; no sample output substitutes generation.

GitHub Actions builds the APK/AAB and publishes exact APK bytes and SHA-256,
checks APK signing and 16 KB ZIP alignment. Download the **Coder-Abyss-APK-v0.7.1**
artifact, extract it, and install `app-debug.apk` (not the artifact ZIP or AAB).
See `docs/corrective-merge.md` for current results and required phone checks;
`docs/v0.7-verification.md` is historical.

## Launcher branding

An original cyan aperture/diamond mark uses scalable Android VectorDrawable layers,
with a near-black adaptive background, round icon resource, Android 13 monochrome
layer and matching native splash screen. The symbol stays within the adaptive
safe area; there is no default Android icon or added splash delay. Physical launcher,
themed-icon and Recent Apps checks remain on the device checklist.

## Public Android deployment configuration

Set these **public** values using Gradle properties (local user Gradle properties
or protected CI configuration). Do not put private service credentials in them:

- `coderAbyss.gateway_url` — trusted HTTPS gateway origin, no path.
- `coderAbyss.google_web_client_id` — Web OAuth client ID.
- `coderAbyss.firebase_app_id` — registered Firebase Android app ID.
- `coderAbyss.firebase_project_id` — Firebase project ID.
- `coderAbyss.firebase_api_key` — public Firebase API identifier, restricted to the
  appropriate app/APIs in Google Cloud. It is not an authentication bypass.

Empty defaults intentionally disable account/cloud access. Register each signing
certificate correctly. Fresh CI debug keystores may differ; production upgrades
need a stable protected signing key/Play App Signing. Do not uninstall an existing
app containing needed projects just to work around a signing mismatch.
