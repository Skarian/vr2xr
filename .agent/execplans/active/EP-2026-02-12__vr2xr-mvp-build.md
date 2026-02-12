# Build vr2xr MVP End-to-End for Sideload Use

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan must be maintained in accordance with `.agent/PLANS.md`.

- Plan ID: EP-2026-02-12__vr2xr-mvp-build
- Status: ACTIVE
- Created: 2026-02-12
- Last Updated: 2026-02-12
- Owner: nskaria

## Purpose / Big Picture

This plan delivers a complete, personal-use Android application (`vr2xr`) that runs on Samsung S23+ class hardware with DeX and XREAL One / One Pro, plays local/HTTPS SBS VR-style videos, applies head-tracked viewport reprojection, and outputs correct SBS frames to the external display mode. After this plan, the app is sideload-installable and usable end-to-end without cloud services, accounts, or broad-market product hardening.

The user-visible proof is straightforward: install the APK, open a local 8192x4096 SBS file or HTTPS link, switch glasses to SBS mode, and confirm correct proportions plus responsive head-tracked view changes while diagnostics report external physical mode and runtime health.

## Progress

- [x] (2026-02-12T17:00Z) ExecPlan created from `PRD_v2.md` and aligned with single-user MVP scope.
- [ ] (2026-02-12T18:20Z) Phase 0 partially completed (completed: probe docs and execution templates in `tools/probes/`; remaining: run on target device and fill go/no-go outcomes).
- [x] (2026-02-12T19:05Z) Phase 1 completed (Kotlin Android scaffold, manifest permissions, two-screen shell, module placeholders, wrapper generation, `:app:assembleDebug` success, lint success, unit-test task success, and on-device launch verification).
- [ ] (2026-02-12T19:25Z) Phase 2 nearly completed (completed: SAF picker, manual URL path, normalized `SourceDescriptor`, and user-confirmed local file + URL streaming playback; remaining: explicit `ACTION_VIEW`/`ACTION_SEND` vector validation on device).
- [ ] (2026-02-12T19:25Z) Phase 3 partially completed (completed: Media3 ExoPlayer progressive decode path, HTTP timeout defaults, codec probe + metrics plumbing, initial decode-to-OES handoff, and user-confirmed runtime playback success via local file + URL stream; remaining: expanded sample/profile validation and performance tuning on device).
- [ ] (2026-02-12T18:20Z) Phase 4 partially completed (completed: external display mode controller callbacks, diagnostics wiring, and initial SBS split compositor shader path; remaining: Presentation-based external surface binding and VR180 reprojection math beyond split pass-through).
- [x] (2026-02-12T19:12Z) Added standalone URL-test support tooling (`tools/dev_fileserver.py` + root `justfile` recipes) to accelerate Phase 2/3 runtime validation without modifying app code paths.
- [x] (2026-02-12T19:18Z) Converted MOV samples to Android-friendly MP4 test assets and updated test server listings to MP4/MKV-only aliases.
- [ ] Phase 5 completed: reverse-engineered XREAL IMU transport + pose fusion + recenter.
- [ ] Phase 6 completed: performance mode, diagnostics overlay, and failure handling.
- [ ] Phase 7 completed: MVP acceptance run, release signing, and sideload package.

## Surprises & Discoveries

- Observation: `xreal_one_driver` is a TCP IMU transport with packet parsing and no Android-specific wrapper, so integration requires native/JNI or a Kotlin reimplementation of protocol parsing.
  Evidence: `reference/xreal_one_driver/src/lib.rs`.
- Observation: VRto3D repeatedly corrected convergence math and stability around async behavior; off-axis convergence and conservative defaults are safer for MVP than aggressive toggles.
  Evidence: `reference/VRto3D/changelog.md` and `reference/VRto3D/vrto3d/src/hmd_device_driver.cpp`.
- Observation: sample files in workspace are already high-resolution HEVC SBS and suitable as initial local sanity assets.
  Evidence: `sample_files/INFO.md`.
- Observation: local execution environment lacks Android wrapper/toolchain prerequisites (`./gradlew` missing; Java is 1.8), so compile/lint/test steps cannot be executed yet from this shell.
  Evidence: `./gradlew --version` -> `no such file or directory`; `java -version` -> `1.8.0_292`.
- Observation: network package bootstrap from this shell cannot resolve Gradle distribution host, so wrapper download is not currently available as a fallback.
  Evidence: `curl -I https://services.gradle.org/distributions/gradle-8.7-bin.zip` -> `Could not resolve host`.
- Observation: once commands were run outside the sandbox, Gradle wrapper bootstrap and Android build tasks succeeded, confirming the scaffold compiles and lint is enforceable.
  Evidence: `./gradlew :app:assembleDebug` and `./gradlew :app:lint` both succeeded.
- Observation: device install validation is currently blocked by phone-side ADB authorization, not by build output.
  Evidence: `adb devices` shows target as `unauthorized`; `adb install` rejected.
- Observation: device authorization was resolved and baseline runtime launch is now verified on physical hardware.
  Evidence: `adb install -r app/build/outputs/apk/debug/app-debug.apk` -> `Success`; `adb shell am start -n com.vr2xr/.app.MainActivity` launched successfully.
- Observation: `sdkmanager` from `cmdline-tools/latest` reports correctly but emits a non-fatal shell warning on macOS due to launcher script/JAVA path parsing.
  Evidence: `sdkmanager --version` -> `20.0` with script warning, while command succeeds.
- Observation: LAN host auto-detection for URL printing may be environment-dependent; script always prints localhost and supports explicit host override through `just` recipe parameters.
  Evidence: `tools/dev_fileserver.py --list-only` output and `just serve-samples HOST=...`.
- Observation: simplified numbered URL aliases reduce manual input errors during on-device testing and preserve a deterministic mapping to underlying test files.
  Evidence: `just list-sample-urls` prints entries like `/1.mp4 -> FrenchAlps_EscapeVR_PREMIUM.mp4`.
- Observation: container conversion alone does not guarantee Android decode compatibility for every HEVC profile; one sample remains HEVC Rext 4:2:2 10-bit and may fail on some devices.
  Evidence: `ffprobe` reports `Murren_EscapeVR_PREVIEW_HIGH.mp4` as `profile=Rext` and `pix_fmt=yuv422p10le`.
- Observation: initial on-device smoke testing now confirms local file playback and URL streaming are functioning.
  Evidence: user-reported runtime test result on 2026-02-12.

## Decision Log

- Decision: MVP core stack is Media3/ExoPlayer + custom OpenGL ES renderer.
  Rationale: Needed for deterministic Android-native decode-to-texture and custom reprojection/SBS output control.
  Date/Author: 2026-02-12 / Codex + User.
- Decision: No LibVLC fallback in MVP phases.
  Rationale: Single-path execution lowers risk and complexity for the personal-use MVP timeline.
  Date/Author: 2026-02-12 / User.
- Decision: MVP decode model is frame-by-frame full-frame decode with GPU viewport sampling.
  Rationale: Realistic for standard SBS HEVC files; ROI/tiled decode is out of MVP scope.
  Date/Author: 2026-02-12 / User.
- Decision: MVP scope remains single-user and sideload-focused.
  Rationale: Avoid broad compatibility, policy, and distribution work that does not serve immediate usage.
  Date/Author: 2026-02-12 / User.
- Decision: Continue execution with scaffold-first implementation and mark device/toolchain-gated checks as explicit remaining work rather than blocking all forward coding.
  Rationale: Keeps momentum on code structure and contracts while preserving honest status for build/runtime verification.
  Date/Author: 2026-02-12 / Codex.
- Decision: Implement initial GPU decode handoff using `GLSurfaceView` + OES `SurfaceTexture` before full VR reprojection math.
  Rationale: Establishes the critical decoder-to-GL surface contract early so Phase 4 can iterate on projection/compositor math without changing playback plumbing again.
  Date/Author: 2026-02-12 / Codex.
- Decision: Handle Media3 `UnsafeOptInUsageError` via project lint opt-in (`lint.xml`) rather than propagating `@UnstableApi` annotations across activity classes.
  Rationale: Keeps lint strict while avoiding unstable-API annotation leakage into app-level type surfaces.
  Date/Author: 2026-02-12 / Codex.

## Outcomes & Retrospective

Milestone update (2026-02-12): Phase 0 documentation artifacts are in place, Phase 1 is complete and validated on device launch, Phase 2 has user-confirmed local file + URL playback behavior (with intent-share vectors still to explicitly validate), Phase 3 includes an initial Media3-to-OES decode path through a GL-owned surface with runtime playback confirmed, and Phase 4 includes first-pass SBS compositing plus external display mode callback handling. Build/lint/unit-test validation passes. Remaining work is intent-vector completion, deeper reprojection/external-presentation, tracking integration, and performance tuning.
Additional milestone support update (2026-02-12): test-only sample serving infrastructure was added (`tools/dev_fileserver.py`, root `justfile`) so URL ingestion/playback can be validated quickly from local desktop assets and repeatably reused through implementation phases.
Additional sample update (2026-02-12): MOV sample sources were remuxed to MP4 outputs for Android-oriented testing, and the sample dev server now advertises simplified MP4/MKV alias URLs only.

## Context and Orientation

The repository now contains requirements, references, and an initial Android implementation skeleton for `com.vr2xr` with source ingestion and baseline playback wiring.

Key repository paths:

- `PRD_v2.md`: product and technical requirements baseline.
- `reference/xreal_one_driver`: reverse-engineered XREAL IMU protocol reference.
- `reference/VRto3D`: reference implementation patterns for stereo output/config lifecycle.
- `sample_files/`: local test assets.

The implementation to be created by this plan will live in a new Android project structure rooted at the repository top level, with one primary `app` module and package namespace `com.vr2xr`.

The core technical flow is: source URI ingestion -> Media3 decode to GPU texture -> reprojection per eye -> SBS packing to external display physical mode -> pose updates from XREAL IMU applied every render tick.

## Plan of Work

This work proceeds in seven phases. Each phase has explicit deliverables, files to create/update, commands to run, and exit criteria. Do not start a new phase until the current phase’s exit criteria are satisfied or explicitly deferred with a logged reason.

Phase 0 validates two MVP-critical risks before heavy coding. Phases 1–5 build the app core in slices from shell to playback to rendering and tracking. Phase 6 hardens reliability and diagnostics. Phase 7 packages the sideload-ready release and closes MVP acceptance.

## Phase 0: Viability Probes (No Heavy App Build Yet)

Implement short probes to prove that the target hardware path is viable.

Create:

- `tools/probes/README.md`
- `tools/probes/display_probe.md`
- `tools/probes/tracking_probe.md`

Work:

1. Document exact baseline hardware/firmware used for MVP validation in `tools/probes/README.md`.
2. Define an Android snippet plan for logging external display IDs and `Display.Mode` physical size/refresh during connect/disconnect.
3. Define a tracking probe using `xreal_one_driver` protocol assumptions and expected reconnection behavior.
4. Record “go/no-go” outcomes for both probes.

Exit criteria:

1. External physical mode can be observed and logged during DeX transitions.
2. IMU transport path is reachable and returns valid samples or has a clearly understood fallback behavior.

## Phase 1: Android Project Scaffold and Core Boundaries

Create a clean Android app skeleton with deterministic package boundaries.

Create/update:

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/vr2xr/app/MainActivity.kt`
- `app/src/main/java/com/vr2xr/app/PlayerActivity.kt`
- `app/src/main/java/com/vr2xr/source/`
- `app/src/main/java/com/vr2xr/player/`
- `app/src/main/java/com/vr2xr/render/`
- `app/src/main/java/com/vr2xr/display/`
- `app/src/main/java/com/vr2xr/tracking/`
- `app/src/main/java/com/vr2xr/diag/`

Work:

1. Initialize Android app with Kotlin and minimum SDK that matches PRD MVP policy.
2. Add required permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`.
3. Create a minimal two-screen flow: home screen and player screen.
4. Wire package/class placeholders listed in `PRD_v2.md` so all modules compile.

Exit criteria:

1. `./gradlew :app:assembleDebug` succeeds.
2. App launches to home screen on target device.

## Phase 2: Source Ingestion and Intent Contract

Build all source entry paths needed for MVP.

Create/update:

- `app/src/main/java/com/vr2xr/source/SourceResolver.kt`
- `app/src/main/java/com/vr2xr/source/IntentIngestor.kt`
- `app/src/main/java/com/vr2xr/source/SourceDescriptor.kt`
- `app/src/main/java/com/vr2xr/app/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`

Work:

1. Implement SAF picker flow with persistable URI permission capture.
2. Implement manual URL entry for `http(s)`.
3. Implement `ACTION_VIEW` and `ACTION_SEND` ingestion rules from PRD.
4. Normalize all source types into one internal `SourceDescriptor`.

Exit criteria:

1. A local file, shared file intent, and HTTPS URL all route to player initialization.
2. Invalid schemes fail with actionable message, not crash.

## Phase 3: Media3 Decode Pipeline (Frame-by-Frame, Full-Frame Decode)

Implement decoding into a GPU-consumable surface for local and HTTPS playback.

Create/update:

- `app/src/main/java/com/vr2xr/player/VrPlayerEngine.kt`
- `app/src/main/java/com/vr2xr/player/CodecCapabilityProbe.kt`
- `app/src/main/java/com/vr2xr/player/PlayerMetrics.kt`
- `app/src/main/java/com/vr2xr/app/PlayerActivity.kt`

Work:

1. Configure Media3 ExoPlayer with progressive HTTP datasource.
2. Attach decoder output to `SurfaceTexture`/OES path for renderer use.
3. Add basic codec capability probe and expose results to diagnostics.
4. Ensure decode model stays frame-by-frame playback decode, not predecode.

Exit criteria:

1. One local sample and one HTTPS sample both decode and display in baseline path.
2. Decoder counters and failures are available to diagnostics.

## Phase 4: External Display + Reprojection + SBS Compositor

Implement physically-correct output to external display modes.

Create/update:

- `app/src/main/java/com/vr2xr/display/ExternalDisplayController.kt`
- `app/src/main/java/com/vr2xr/render/VrSbsRenderer.kt`
- `app/src/main/java/com/vr2xr/render/ProjectionMode.kt`
- `app/src/main/java/com/vr2xr/render/gl/` (shader + GL utility files)

Work:

1. Discover and bind to presentation display category for DeX external screen.
2. Use `Display.Mode` physical dimensions as render target authority.
3. Implement per-eye viewport mapping and SBS compositor output:
   - 3840x1080 -> full SBS halves.
   - 1920x1080 -> half SBS halves.
4. Implement VR180 equirect reprojection path with off-axis stereo convergence math.
5. Add layout/projection toggles and eye-swap control.

Exit criteria:

1. External output dimensions track physical mode changes without restart.
2. SBS output displays with correct proportions in glasses SBS mode.

## Phase 5: XREAL IMU Integration + Pose Fusion + Recenter

Integrate reverse-engineered tracking and apply pose per render tick.

Create/update:

- `app/src/main/java/com/vr2xr/tracking/XrealImuClient.kt` (or JNI bridge + wrapper if native path selected)
- `app/src/main/java/com/vr2xr/tracking/PoseFusionEngine.kt`
- `app/src/main/java/com/vr2xr/tracking/PoseState.kt`
- `app/src/main/java/com/vr2xr/render/VrSbsRenderer.kt`

Work:

1. Implement IMU sample ingestion with timeout and reconnect policy.
2. Implement quaternion-based complementary fusion for stable yaw/pitch/roll.
3. Add recenter action (yaw zero offset reset).
4. Feed latest pose lock-free to render loop each frame.
5. Add fallback touch-drag look mode if tracking unavailable.

Exit criteria:

1. Head motion visibly changes viewport during playback.
2. Disconnect/reconnect does not crash app and resumes tracking or fallback.

## Phase 6: Performance Mode, Diagnostics, and Failure Handling

Harden MVP behavior under load and edge conditions.

Create/update:

- `app/src/main/java/com/vr2xr/diag/DiagnosticsOverlay.kt`
- `app/src/main/java/com/vr2xr/player/PerformanceModeController.kt`
- `app/src/main/java/com/vr2xr/app/ErrorUiController.kt`

Work:

1. Add diagnostics overlay fields from PRD MVP scope.
2. Implement Performance Mode A/B that lowers internal render cost while preserving final SBS output dimensions.
3. Implement four required failure flows:
   - stream unreachable,
   - decoder unsupported,
   - tracking unavailable,
   - display disconnected.
4. Ensure failures are recoverable with explicit user actions.

Exit criteria:

1. 5-minute stress run meets MVP budget targets or logs bounded deviations.
2. All four failure paths are demonstrably handled without app termination.

## Phase 7: MVP Acceptance, Release Build, and Sideload Delivery

Complete end-to-end validation and package release artifact.

Create/update:

- `docs/MVP_ACCEPTANCE.md`
- `docs/SIDELOAD_INSTALL.md`
- `docs/KNOWN_ISSUES.md`
- release signing config files as appropriate for local secure workflow.

Work:

1. Run acceptance checklist from `PRD_v2.md` section 12 and capture outcomes.
2. Build signed release APK.
3. Install on fresh device state and run smoke suite.
4. Document exact setup and known caveats for personal use.

Exit criteria:

1. User can install APK and run local + HTTPS + tracking + SBS output flows end-to-end.
2. Required diagnostics and controls are present and functional.

## Concrete Steps

Run all commands from repository root unless specified.

1. Validate environment and Gradle setup:

    ./gradlew --version
    ./gradlew tasks

2. Build and compile frequently after each phase:

    ./gradlew :app:assembleDebug

3. Run static checks after meaningful code edits:

    ./gradlew :app:lint
    ./gradlew :app:testDebugUnitTest

4. Install and smoke test on device:

    adb devices
    adb install -r app/build/outputs/apk/debug/app-debug.apk

5. Capture runtime logs during DeX/tracking tests:

    adb logcat | rg -i "vr2xr|display|mode|imu|tracking|decoder"

Expected high-signal outcomes during implementation:

- Build commands succeed without new warnings/errors.
- Player loads both local and HTTPS sources.
- Logs show external physical mode and tracking state transitions.

Current environment note:

- `./gradlew` is not present and Java runtime is 1.8 in this shell, so build/lint/test commands are currently blocked until wrapper/toolchain setup is added.
- DNS/network restrictions in this shell prevent downloading Gradle distributions directly (`services.gradle.org` unresolved).

## Validation and Acceptance

MVP is accepted when all statements below are true on target hardware:

1. Launch vectors all function: home open file, open URL, intent open.
2. 8192x4096 SBS local sample plays with correct proportions in glasses SBS mode.
3. HTTPS sample starts and sustains playback under normal network.
4. Head tracking updates viewport with recenter working.
5. External display mode changes are detected and compositor retargets correctly.
6. Diagnostics overlay reports physical mode, decoder health, and tracking state.
7. Four required failure paths are graceful and recoverable.

If a criterion fails, document exact repro steps and keep work in the same phase until resolved or explicitly deferred.

## Idempotence and Recovery

This plan is designed to be resumable and safe:

1. Phases are additive; re-running build/lint/test commands is safe.
2. Keep configuration defaults in source control to avoid hidden local state.
3. For risky changes, commit at phase boundaries so rollback is file-scoped and auditable.
4. If a phase is partially complete, split the corresponding `Progress` item into completed vs remaining details before pausing.

## Artifacts and Notes

Capture these artifacts during execution and keep paths stable:

1. Probe outcomes: `tools/probes/*.md`.
2. Acceptance results: `docs/MVP_ACCEPTANCE.md`.
3. Known issues and mitigation: `docs/KNOWN_ISSUES.md`.
4. Install/runbook: `docs/SIDELOAD_INSTALL.md`.

Keep evidence concise; include short log excerpts and exact command invocations that prove behavior.

## Interfaces and Dependencies

Use these interfaces and module responsibilities to keep implementation coherent.

Required Kotlin interfaces/classes (minimum signatures may evolve, but responsibilities must not):

1. `com.vr2xr.source.SourceResolver`
   - `fun resolve(intent: Intent?): SourceDescriptor?`
2. `com.vr2xr.player.VrPlayerEngine`
   - `fun prepare(source: SourceDescriptor, surface: Surface)`
   - `fun play()`
   - `fun pause()`
   - `fun release()`
3. `com.vr2xr.display.ExternalDisplayController`
   - `fun start()`
   - `fun stop()`
   - `fun currentPhysicalMode(): PhysicalDisplayMode?`
4. `com.vr2xr.tracking.XrealImuClient`
   - `fun connect()`
   - `fun disconnect()`
   - `fun latestSample(): ImuSample?`
5. `com.vr2xr.tracking.PoseFusionEngine`
   - `fun update(sample: ImuSample): PoseState`
   - `fun recenter()`
6. `com.vr2xr.render.VrSbsRenderer`
   - `fun onSurfaceCreated()`
   - `fun onSurfaceChanged(width: Int, height: Int)`
   - `fun renderFrame(frameTexId: Int, pose: PoseState, mode: RenderMode)`
7. `com.vr2xr.diag.DiagnosticsOverlay`
   - `fun update(state: DiagnosticsState)`

Dependency policy for MVP:

1. Use AndroidX + Media3 as core runtime dependencies.
2. Keep dependencies minimal and avoid adding optional stacks not required by MVP acceptance.
3. Do not copy code from LGPL repositories directly into app source; use ideas/patterns only unless compliance work is intentionally accepted.

At the completion of each phase, update this file’s `Progress`, `Decision Log`, and `Surprises & Discoveries` before proceeding.

## Plan Revision Notes (bottom-of-file change notes)

- (2026-02-12) Marked Phases 0-2 as partial/mostly complete and Phases 3-4 as partially complete based on implemented files, and documented local toolchain blocker (`gradlew` missing, Java 8 only) with explicit remaining validation work.
- (2026-02-12) Added environment finding that Gradle distribution bootstrap is DNS-blocked in this shell, so wrapper/toolchain must be supplied locally before build validation steps can run.
- (2026-02-12) Updated plan status after real validation runs: generated `gradlew`, confirmed `assembleDebug`/`lint`/`testDebugUnitTest`, and initially recorded ADB authorization blocker.
- (2026-02-12) Updated plan again after user runtime validation: device authorization resolved, APK installed successfully, and `MainActivity` launch on physical device confirmed.
- (2026-02-12) Added standalone sample file dev server and `just` recipes to provide deterministic local URL test inputs during MVP runtime validation.
- (2026-02-12) Updated sample dev server to expose short numbered aliases (`/N.ext`) and explicit alias-to-filename mapping for faster manual URL-entry testing.
- (2026-02-12) Converted MOV samples to MP4 (video/audio stream copy with MOV timecode track dropped), updated sample documentation, and restricted dev-server listings to MP4/MKV for Android testing.
- (2026-02-12) Updated plan status after user-confirmed runtime checks: local file playback and URL streaming both working on device; retained remaining intent-vector and deeper rendering/tracking tasks.
