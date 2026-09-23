# Hybrid platform v0.7 implementation

The user confirmed v0.7; versionCode will increase from 6 to 7. The attached
v0.5-labelled specification is the functional requirement, not a downgrade.

Inspection: v0.6 already has secure HF credentials, a verified private Space,
Wan/LTX jobs, atomic video projects, WorkManager tracking and resumable MP4s.
Other services remain screen-owned text generators; Research/Visuals do not
yet provide the requested workspaces. DownloadManager verifies local models.
llama.cpp uses one shared engine/mutex. Whisper currently shares that mutex.
MainActivity owns navigation and most UI. Native ARM64 builds passed at v0.6.

Implementation order:
1. Central model registry, resource compatibility and execution router.
2. Shared Space transport/registry; retain v0.6 video API compatibility.
3. Durable project repository, operation store and foreground task execution;
   adapt existing video records without deleting or relocating their assets.
4. Service model selectors, global operations and persistent speech capture.
5. Structured research editor, real sources and DOCX/PDF/PPTX/XLSX exporters.
6. Remote image/text engines, visual workspace and managed asset copies.
7. Remote-only video policy, recovery/diagnostics, migration and full builds.
8. Verify actual GPU behavior, document limitations, then commit/push main.

No enormous model download is exposed on Android. Old local-Wan files are
retained for explicit user removal; existing videos remain playable. Model
availability must reflect verified backend capabilities. No build toolchain,
source verification, GPU generation or export may report fabricated success.
