# Reference-guided service UI redesign

This follow-up implements the visual design requested in the four supplied images.
It builds on `a63c4f4` on `codex/scoped-workflows-video-editor`.

## Visual changes

- Branded service header with existing app icon, cyan/violet title, gradient service
  badge and compact model selector.
- Navy outlined prompt panels, visible counters, circular microphone and file
  controls, icon-led option cards, and a fixed cyan/blue/violet primary action.
- Video places its voice control inside the prompt panel. Its duration and size
  choices come from actual backend capabilities. A preview panel shows the latest
  real clip, or an empty state before a clip exists. Wan retains its 100-word rule.
- Build App shows app type and template cards that become generation instructions.
  Existing source generation/revisions are retained. It does not claim that source
  files have been compiled, tested or signed by a missing build server.
- Research places model selection below the voice/file controls. Results use a
  continuous paper card with cyan headings, reading controls, export actions and
  a separate revision view with editable spoken/typed instructions.
- Visuals uses type/style/size cards and a large result preview with selectable
  output thumbnails. Save, share, variation and full-screen zoom use real outputs.
- Tasks use recorded stages, elapsed time, stop/retry controls and expandable logs.
  Determinate progress is shown only when supplied by real bytes/backend/render
  progress. Finishing a watched generation opens its result.
- Version history uses selectable dated cards with draft excerpts and explicit
  restoration. Video editing remains available in the Edit tab.
- Service-only bottom navigation follows the reference: Home, Video, Research,
  Build/Visuals and More. More retains access to Projects, AI Models, all tasks and
  Settings. Navigation does not own or cancel jobs.

Small screens and large fonts retain scrolling as an accessibility fallback; the
primary generation button stays visible. Unsupported mockup actions, sample media,
fake percentages and fabricated release artifacts were not added.

## Files

Paths below are relative to `app/src/main/java/com/coderabyss/mobile/` unless noted.

| File | Purpose |
|---|---|
| `platformui/WorkflowDesign.kt` | Reference-style visual components, service navigation and task presentation. |
| `platformui/WorkflowResults.kt` | Media preview/gallery, save/share controls, paper reader and revision presentation. |
| `platformui/WorkspaceScreen.kt` | Applies distinct input layouts, fixed primary actions, voice styling and workflow states. |
| `platformui/ModelPicker.kt` | Optional compact outlined selector; existing model decisions remain. |
| `platformui/WorkflowAttachments.kt` | Optional circular file picker and visible error handling. |
| `platformui/WorkflowHistory.kt` | Inline history cards; existing restoration path remains. |
| `platformui/OutputGallery.kt` | Routes the four services to the new presentation; original file actions remain available. |
| `presentation/CoderAbyssShell.kt` | Selects service navigation only inside the four workflows; other pages keep their shell. |
| `app/src/test/java/com/coderabyss/mobile/WorkflowScreenshotTest.kt` | Native Robolectric rendering of actual Compose screens for visual inspection. |
| `docs/reference-ui-redesign.md` | Delivery and verification record. |

No changes to authentication, subscriptions, admin, backend security, model
downloads, native inference, task persistence or video encoding. Home and other
unrelated screens keep their existing design. The earlier report's backend and
production-signing limitations still apply.

## Verification

The host screenshot test renders the actual Android Compose screens at a 411-pixel
viewport using Robolectric native graphics, with no credentials or model execution.
Rendered images are written to `app/build/reports/workflow-screenshots/`.
This checks rendering/layout; it does not substitute for phone interaction or GPU
generation acceptance.

All 30 tests (including four native Compose render tests), `lintDebug`,
`assembleDebug` and `bundleRelease` passed. The debug APK passed signature,
16 KB ZIP/ELF alignment, JNI export and credential-pattern checks. Production
signing and GitHub download details are documented in `signed-builds.md`.

Inspected screenshots of the actual Compose input screens, with no backend account
configured in the test runtime:

- [Build an App](screenshots/reference-ui/app.png)
- [Create Video](screenshots/reference-ui/video.png)
- [Research Paper](screenshots/reference-ui/research.png)
- [Create Visuals](screenshots/reference-ui/visual.png)

The empty prompt disables generation; unavailable remote options accurately reflect
the test runtime's unconfigured backend. These images are host renders, not phone
screenshots or generated media. Physical-device interaction checks remain required.
