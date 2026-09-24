# Coder Abyss 0.7.1 corrective integration report

## Delivery status

Repair branch: `fix/v07-v06-ui-auth-subscriptions-branding`.
Functional base: `6380193aef2b87d58feee667ca1bcd9c8789a5ae`.
Visual contract: `d2bf748ab8c32f1d0c855c9c43d8ca48936d1480`.
Android versionCode **8**, versionName **0.7.1**, compile/target SDK **36**.
Build tools: AGP 8.9.2, Gradle 8.11.1, Java 17. Native revisions are unchanged.

This is a buildable integration candidate, **not a deployed subscription product**.
The owner supplied Firebase Android/OAuth configuration and confirmed Google
sign-in is Enabled on September 23, 2026. Play/Cloud Run setup remains pending. No paid infrastructure was provisioned.
Normal cloud access fails closed until configured. The repair is delivered on its
branch; main stays unchanged pending deployment configuration and phone acceptance.
The delivery message records the exact commit, push and CI result.

## Restored visual presentation

`MainActivity` now opens `presentation.CoderAbyssShell`, never `PlatformApp`.
The v0.6 implementations of Header, LogoMark, AssistantFace, MicrophoneButton,
StatusPill, QuickActions/QuickAction, ModelsPanel/CompactModelRow, SettingCard,
ScreenHeader and AbyssBottomBar were extracted directly from the reference commit.
AbyssBlack/Panel/Panel2/Blue/Blue2/Green/Text/Muted/Danger colors are retained.
Home restores the assistant/microphone area, four action cards, model panel, spacing
and bottom-navigation identity. The microphone is wired to persistent v0.7 voice,
not the old screen-owned recorder. The status badge now truthfully distinguishes
Local Only from Hybrid AI; Library routes to central AI Models.

Projects, models and service workspaces use the same dark surfaces, cyan outlines,
rounded panels and v0.6 screen header. New account/admin controls inherit this
single theme. Existing research/gallery/source controls are reused for behavior;
new v0.7 functionality necessarily adds controls absent from v0.6. There was no
physical Android device available, so this is **source-level visual comparison**,
not a claim of screenshot/pixel-equivalent device verification. Phone comparison
of all screens against v0.6 remains required.

## Functional systems retained

ProjectRepository/AtomicJson, migration, persistent assets/checkpoints, TaskManager,
PersistentTaskStore, WorkManager recovery, global tasks, ModelRegistry, resumable
verified downloads, compatibility checks, per-service/project defaults, single text
LLM gate, independent Whisper, foreground VoiceCaptureService, editable dictation,
research sections/sources/charts, Office/PDF exporters, source packaging, media
preview/share/save and Local Only policy remain. No native submodule was changed.
Wan word limits and supported settings remain capability driven; long LTX and SDXL
use the gateway instead of a phone-owned provider credential.

## Authentication, billing and roles

- Credential Manager Google sign-in exchanges the Google token for Firebase Auth.
  Firebase manages session persistence; sign-out clears authorization state without
  deleting local projects/models. Android backup is disabled to protect account
  state. The Google Services plugin reads the supplied app/google-services.json
  and generates the default Firebase options and Web OAuth audience. No service-account key or HF token is a build property.
- Gateway validates Firebase signature/project/revocation, verified email and Google
  provider identity. No client email, role or paid boolean grants permission.
- Play Billing 8 obtains current product/offer prices, launches the purchase UI,
  supplies SHA256(UID) as obfuscated account ID, supports restore, and sends purchase
  tokens for server verification. No client callback grants entitlement.
- Google Developer API subscriptionsv2 verification checks product, UID association,
  state and expiry. Server acknowledges verified purchases. Pub/Sub RTDN verifies
  the configured Google OIDC audience/sender before refreshing Play state.
- Roles: OWNER, ADMIN, SUBSCRIBER (derived from verified entitlement), FREE. OWNER
  and configured ADMIN may bypass paid subscription; resource limits still apply.
- Owner bootstrap uses server `CODER_ABYSS_OWNER_UID`, or verified-email bootstrap,
  then pins immutable UID transactionally. Only OWNER grants/revokes ADMIN for users
  who have signed in. ADMIN cannot create owners or rotate credentials.
- Account/admin UI provides server-returned plan, refresh, sign-out, subscriptions,
  users/admin management, health, provider status, rotation and audit. User/audit
  lists currently return the first/latest 100 records; pagination/search are not yet
  implemented. Recent Google authentication protects sensitive changes.

## Gateway and secret management

New Python FastAPI gateway source stays on GitHub under `gateway/`. The private HF
Space stays the GPU runtime and was not redeployed or replaced. It remains at
`2eb39bec65dd7f097f1a2f8b20d97280fc61c7ed`.

HF payloads exist only in server Secret Manager / transient gateway memory. Normal
Settings has no provider token input. Existing development credentials are retired
from phone storage at startup; direct mobile HF transport is disabled. Owner can
enter a NEW value in a non-persisted password field, but cannot retrieve the stored
one. Gateway validation errors and audit responses cannot echo request secrets.

Rotation tests new identity/Space access before adding/activating a version.
Firestore compare-and-swap protects provider configuration. Previous secret version
references support an explicit rollback window. Emergency replacement clears
rollback and disables old versions where IAM permits; it does not pretend to revoke
at Hugging Face. Already in-flight requests cannot be recalled. Space changes
validate access and apply server-side without new APKs; existing jobs pin their
original Space and require credentials that still authorize it.

## Persistence, schemas and migration

Android project schema remains version 2. New remote task fields: `provider` =
`gateway` and `accountUid`; existing prompt/job IDs/outputs remain intact. New jobs
are bound to the account that created them, including token-fetch and download
checks. Sign-out/account changes pause access rather than reassign task ownership.

Legacy unauthenticated provider jobs are marked UNKNOWN with an ownership-migration
explanation, preserving IDs and local output. They cannot be automatically claimed
by the first Google login. An owner-reviewed server migration is still required for
those unfinished jobs; no duplicate generation is submitted. Saved local media
remains available. Local projects are device-local; cloud sync is not implemented.

Firestore server-only collections: users, configuration, purchases, jobs, usage,
audit. Mobile Firestore rules deny access. A deterministic UID/request key reserves
each job before submission. Unknown submissions are found by the SAME provider
request ID, never blindly resubmitted. Quotas/rates/concurrency are transactional
and explicitly deployment-configured. Conservative uncertain submission/retry
reservations may need operator reconciliation. Retry/download routes enforce UID
ownership. Paid expiry blocks new/retried work while retaining owned result access.

## Android branding

Original cyan aperture/diamond VectorDrawable, dark adaptive background,
`mipmap-anydpi-v26/ic_launcher` and `ic_launcher_round`, monochrome layer, and native
Android splash are wired into manifest/theme for debug and release. No bitmap
upscaling, default Android icon or startup delay. Artwork remains inside the central
safe area. `branding-preview.png` is a design/mask illustration, not a phone screenshot.
Physical circle/squircle/round, themed icon, Recent Apps and splash checks remain.

## Verification

- Android unit tests: **19 passed** (including gateway Local Only/fail-closed tests).
- Gateway tests: **15 passed**, covering authorization matrix, subscription states,
  purchase association, client privilege rejection, secret-safe validation, recent
  auth, invalid rotation and emergency version behavior. These use isolated test
  fakes; **no live Google purchase or deployed secret rotation was claimed**.
- All five gateway engine payloads accepted by the existing Space validator.
- Final Gradle `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `bundleRelease`:
  **BUILD SUCCESSFUL**. APK signature and 16 KB ZIP alignment verified.
- All 18 native libraries are ARM64/16 KB aligned. JNI checks: llama 10 exports,
  Whisper 12, Wan 4. No native gitlink revisions changed.
- APK credential-pattern scan passed for HF token, private-key and service-account
  payload patterns. This is defense in depth, not proof against every encoding.
- Local debug APK: **94,818,668 bytes**; SHA256
  `76cece18894721893cc6477a4edf95b5368121745e93b837b71021771a192c34`.
  CI artifacts have their own checksums.
- Existing live Space generation evidence belongs to v0.7; gateway-mediated live
  generation has not yet been tested without deployment.

Local artifact paths:
`app/build/outputs/apk/debug/app-debug.apk`
`app/build/outputs/bundle/release/app-release.aab`.
GitHub artifacts: `Coder-Abyss-APK-v0.7.1` and `Coder-Abyss-AAB-v0.7.1`.
Fresh CI debug signing certificates may differ from an installed APK. Preserve
needed projects; use the original signer/stable production signing before upgrading.

## External configuration and next device checks

Follow [gateway deployment/security setup](../gateway/README.md) and
[API contract](../gateway/API.md). Configure public Android properties listed in
[README](../README.md). Required server secret name: **coder-abyss-hf-token**, referenced
through `CODER_ABYSS_HF_SECRET`; never submit its value to Git or the normal app UI.
Provide Firebase project/app/Web client IDs, Play products/license testers, service
account IAM, Firestore rules, owner identity, HTTPS Cloud Run origin, RTDN audience,
quota values, and stable signing/Play App Signing. Google Cloud tooling/configuration
was not present, so deployment and paid-resource provisioning were not attempted.

First phone check: install the compatible-signed repair APK, select **Continue with
local projects**, compare Home/Quick Actions/bottom navigation to v0.6, open an old
project and preview/export its saved media. Test offline Whisper dictation into an
editable prompt, local text, downloads, research exports, navigation/restart and the
new launcher/themed/splash branding. No live sign-in or paid cloud success is expected
from an APK built with empty public deployment configuration.

After staging deployment: test real Google owner/free/admin accounts, role rejection,
Play purchase/restore/expiry/RTDN, rotation/rollback/emergency IAM, and gateway Wan,
LTX, SDXL/Qwen. Verify same-job process-death/network recovery, download retry, account
switching and Local Only. Production security/load review, legacy ownership
reconciliation, subscription linked-token/plan replacement, and all physical-device
acceptance are outstanding. Existing isolated app-build-worker and ephemeral Space
storage limitations remain unchanged.

## Files

- `.github/workflows/android-build.yml` — modified
- `.gitignore` — modified
- `README.md` — modified
- `app/build.gradle.kts` — modified
- `app/src/main/AndroidManifest.xml` — modified
- `app/src/main/java/com/coderabyss/mobile/CoderAbyssApplication.kt` — modified
- `app/src/main/java/com/coderabyss/mobile/HuggingFaceSpaceClient.kt` — modified
- `app/src/main/java/com/coderabyss/mobile/MainActivity.kt` — modified
- `app/src/main/java/com/coderabyss/mobile/RemoteVideoWorkspace.kt` — modified
- `app/src/main/java/com/coderabyss/mobile/VideoTasks.kt` — modified
- `app/src/main/java/com/coderabyss/mobile/platformui/ModelPicker.kt` — modified
- `app/src/main/java/com/coderabyss/mobile/platformui/PlatformNavigation.kt` — modified
- `app/src/main/java/com/coderabyss/mobile/platformui/WorkspaceScreen.kt` — modified
- `app/src/main/java/com/coderabyss/mobile/remote/SpaceRegistry.kt` — modified
- `app/src/main/java/com/coderabyss/mobile/tasks/PlatformTaskWorker.kt` — modified
- `app/src/main/java/com/coderabyss/mobile/tasks/TaskManager.kt` — modified
- `app/src/main/res/values/styles.xml` — modified
- `app/src/main/java/com/coderabyss/mobile/account/AccountPanel.kt` — added
- `app/src/main/java/com/coderabyss/mobile/account/AuthRepository.kt` — added
- `app/src/main/java/com/coderabyss/mobile/account/AuthorizationRepository.kt` — added
- `app/src/main/java/com/coderabyss/mobile/account/CoderAbyssBackendClient.kt` — added
- `app/src/main/java/com/coderabyss/mobile/account/SubscriptionRepository.kt` — added
- `app/src/main/java/com/coderabyss/mobile/presentation/AbyssComponents.kt` — added
- `app/src/main/java/com/coderabyss/mobile/presentation/CoderAbyssShell.kt` — added
- `app/src/main/res/drawable/ic_abyss_foreground.xml` — added
- `app/src/main/res/drawable/ic_abyss_monochrome.xml` — added
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — added
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` — added
- `app/src/main/res/values/colors.xml` — added
- `app/src/test/java/com/coderabyss/mobile/GatewayBoundaryTest.kt` — added
- `docs/branding-preview.png` — added
- `docs/corrective-merge.md` — added
- `gateway/API.md` — added
- `gateway/Dockerfile` — added
- `gateway/README.md` — added
- `gateway/app.py` — added
- `gateway/firestore.rules` — added
- `gateway/policy.py` — added
- `gateway/provider.py` — added
- `gateway/requirements-test.txt` — added
- `gateway/requirements.txt` — added
- `gateway/tests/test_policy.py` — added
- `gateway/tests/test_security.py` — added
- `scripts/verify-apk-secrets.py` — added

## API 36 follow-up

Current Google Play submission policy requires API 36. The corrective follow-up
updates compile/target SDK to 36, AGP to 8.9.2 and Gradle to 8.11.1, retains native
module pins, and adds explicit status-bar insets for the restored shell. The
credential replacement field requests password input with autocorrect disabled.
The API 36 run passed all required tasks: 19 Android tests, debug lint, debug APK
and release AAB. Signature, ARM64/16 KB checks and credential-pattern scan passed
on the API 36 APK. The checksum above is for that final local APK.

Policy reference: https://developer.android.com/google/play/requirements/target-sdk
Toolchain reference: https://developer.android.com/build/releases/about-agp

Final local API 36 AAB size: 66559246 bytes.

## Firebase configuration follow-up (September 23, 2026)

The supplied Android configuration matches package `com.coderabyss.mobile` and
this machine's debug signing certificate. The owner confirmed Google provider is
Enabled. Google Services 4.4.4 generates the default Firebase configuration and
Web OAuth audience. Credential Manager/Firebase Auth dependencies remain pinned.
The existing sign-in UI is retained; cancelling the picker is handled explicitly,
and cloud account lookup failure does not invalidate successful Google sign-in.

Validation: testDebugUnitTest (20 passed), lintDebug and assembleDebug succeeded.
The actual APK signature matches the registered local debug certificate. All 18
ARM64 libraries pass 16 KB alignment; llama/Whisper/Wan JNI exports and APK secret
pattern checks passed. APK size: 94,821,188 bytes. APK SHA-256:
`58a746d76588380fd9e3a1c480ace9a6207ffcce10d47f29258405f7bbec0149`.

No device was connected. Real account-picker login, session restoration after
process restart and sign-out must still be exercised on the phone. This follow-up
builds the debug APK; the previously built release AAB predates this configuration.
