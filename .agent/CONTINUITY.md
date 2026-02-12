# CONTINUITY

Facts only. No transcripts. If unknown, write UNCONFIRMED.
Add dated entries with provenance tags per AGENTS.md: [USER], [CODE], [TOOL], [ASSUMPTION].

## Snapshot

Goal: [2026-02-12T18:20Z] [USER] Begin executing the active MVP execplan.
Now: [2026-02-12T19:25Z] [USER] On-device testing confirms both local file playback and URL streaming are working.
Next: [2026-02-12T19:25Z] [CODE] Continue implementation with remaining Phase 2 edge-vector checks (`ACTION_VIEW`/`ACTION_SEND`) and deeper Phase 4/5 rendering+tracking work.
Open Questions: [2026-02-12T19:25Z] [ASSUMPTION] UNCONFIRMED whether all sample variants (especially HEVC Rext 4:2:2) are validated across both local and URL paths; user reported overall playback success.

## Done (recent)

- [2026-02-12T18:20Z] [CODE] Added Phase 0 probe artifacts: `tools/probes/README.md`, `tools/probes/display_probe.md`, `tools/probes/tracking_probe.md`.
- [2026-02-12T18:20Z] [CODE] Created Android project scaffold: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, base resources/layouts.
- [2026-02-12T18:20Z] [CODE] Implemented app shell and source ingestion flow: `MainActivity`, `PlayerActivity`, `SourceResolver`, `IntentIngestor`, `SourceDescriptor`.
- [2026-02-12T18:20Z] [CODE] Implemented Phase 1 placeholder module boundaries and baseline player wiring: `VrPlayerEngine`, `CodecCapabilityProbe`, `ExternalDisplayController`, tracking/render/diag placeholders.
- [2026-02-12T18:20Z] [CODE] Added Phase 3 baseline decode plumbing in `VrPlayerEngine` (progressive Media3 source path, HTTP timeout defaults, decoder/metrics listeners).
- [2026-02-12T18:20Z] [CODE] Enabled cleartext network traffic in `AndroidManifest.xml` for MVP `http://` source support.
- [2026-02-12T18:20Z] [CODE] Declared OpenGL ES 2.0 feature requirement in `AndroidManifest.xml` for GLSurfaceView/OES renderer path.
- [2026-02-12T18:20Z] [CODE] Implemented external display add/change/remove callback handling in `ExternalDisplayController` and connected mode updates to `PlayerActivity` diagnostics overlay.
- [2026-02-12T18:20Z] [CODE] Replaced `SurfaceView` playback target with `GLSurfaceView` pipeline and `VrSbsRenderer` that creates OES texture/surface for Media3 decode handoff.
- [2026-02-12T18:20Z] [CODE] Added initial SBS split compositor shader path in `VrSbsRenderer` (left/right eye texture half mapping, swap-eyes support).
- [2026-02-12T18:20Z] [CODE] Added `kotlinx-coroutines-android` dependency required by `StateFlow` usage in `VrPlayerEngine`.
- [2026-02-12T18:20Z] [CODE] Updated active ExecPlan progress, discoveries, decisions, and outcomes with partial Phase 0-2 status plus environment blocker evidence.
- [2026-02-12T18:45Z] [CODE] Generated Gradle wrapper pinned to 8.7 (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) for reproducible Android builds.
- [2026-02-12T18:45Z] [CODE] Added `lint.xml` project-wide opt-in for `androidx.media3.common.util.UnstableApi` and resolved blocking lint errors.
- [2026-02-12T18:45Z] [TOOL] Validation results: `./gradlew :app:assembleDebug` PASS, `./gradlew :app:lint` PASS, `./gradlew :app:testDebugUnitTest` PASS.
- [2026-02-12T18:45Z] [TOOL] `adb install -r app/build/outputs/apk/debug/app-debug.apk` blocked by device authorization state (`unauthorized`).
- [2026-02-12T19:05Z] [USER] Device authorization resolved; `adb install -r app/build/outputs/apk/debug/app-debug.apk` returned `Success`.
- [2026-02-12T19:05Z] [USER] `adb shell am start -n com.vr2xr/.app.MainActivity` launched app successfully and user confirmed app is visible on phone.
- [2026-02-12T19:05Z] [USER] Android SDK command-line tools updated; `sdkmanager` now resolves from `cmdline-tools/latest/bin`.
- [2026-02-12T19:12Z] [CODE] Added test-only dev server script `tools/dev_fileserver.py` that serves `sample_files/` and prints concrete per-file URLs.
- [2026-02-12T19:12Z] [CODE] Added root `justfile` with `serve-samples` and `list-sample-urls` commands for repeatable URL testing workflow.
- [2026-02-12T19:12Z] [TOOL] Verified URL listing output via `python3 tools/dev_fileserver.py --list-only` and `just list-sample-urls`.
- [2026-02-12T19:18Z] [CODE] Updated dev server to generate short alias URLs (`/1.ext`, `/2.ext`, ...) and print each alias mapped to its real filename.
- [2026-02-12T19:18Z] [CODE] Added alias-routing support in server handler so short URLs are served transparently from `sample_files/`.
- [2026-02-12T19:18Z] [TOOL] Verified alias output via `just list-sample-urls` and validated script syntax with `python3 -m py_compile`.
- [2026-02-12T19:18Z] [CODE] Remuxed `FrenchAlps_EscapeVR_PREMIUM.mov` and `Murren_EscapeVR_PREVIEW_HIGH.mov` to MP4 for Android testing (`.mov` sources retained).
- [2026-02-12T19:18Z] [CODE] Updated dev server file filtering to advertise only `.mp4`/`.mkv` test files (no `.mov`/`.md` aliases).
- [2026-02-12T19:18Z] [CODE] Updated `sample_files/INFO.md` with Android-friendly MP4 set, conversion method, and HEVC Rext compatibility caveat.
- [2026-02-12T19:25Z] [USER] Verified local file playback works on device.
- [2026-02-12T19:25Z] [USER] Verified URL streaming works on device.
- [2026-02-12T16:57Z] [CODE] Added `.agent/execplans/active/EP-2026-02-12__vr2xr-mvp-build.md` with exhaustive phase-by-phase implementation and acceptance guidance.
- [2026-02-12T16:57Z] [CODE] Updated `.agent/execplans/INDEX.md` with active entry for the new plan.
- [2026-02-12T16:52Z] [CODE] Updated `PRD_v2.md` with strict MVP research scope and explicit decode model (frame-by-frame full-frame decode + GPU viewport sampling).
- [2026-02-12T16:50Z] [CODE] Finalized sample-file validation task and recorded keep-all decision in continuity.
- [2026-02-12T16:49Z] [CODE] Added `sample_files/INFO.md` documenting hard requirement checks (8K/SBS/VR180) and verification caveats.
- [2026-02-12T16:46Z] [TOOL] Completed `ffprobe` + SSIM-based split-eye checks for all three files in `sample_files/`.
- [2026-02-12T16:42Z] [CODE] Revised `PRD_v2.md` to remove LibVLC from MVP scope and incorporate VRto3D carryover patterns.
- [2026-02-12T16:34Z] [CODE] Added `reference/xreal_one_driver` as pinned git submodule for reverse-engineered IMU integration reference.
- [2026-02-12T16:42Z] [CODE] Added `reference/VRto3D` as pinned git submodule and initialized nested submodules for deeper implementation research.
- [2026-02-12T16:34Z] [CODE] Added project rule in `AGENTS.md` for managing third-party references under `reference/` as pinned submodules.

## Working set

- `.agent/execplans/INDEX.md`
- `.agent/execplans/active/EP-2026-02-12__vr2xr-mvp-build.md`
- `tools/probes/README.md`
- `tools/probes/display_probe.md`
- `tools/probes/tracking_probe.md`
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/vr2xr/app/MainActivity.kt`
- `app/src/main/java/com/vr2xr/app/PlayerActivity.kt`
- `app/src/main/java/com/vr2xr/source/SourceResolver.kt`
- `app/src/main/java/com/vr2xr/source/IntentIngestor.kt`
- `app/src/main/java/com/vr2xr/source/SourceDescriptor.kt`
- `app/src/main/java/com/vr2xr/player/VrPlayerEngine.kt`
- `app/src/main/java/com/vr2xr/player/CodecCapabilityProbe.kt`
- `app/src/main/java/com/vr2xr/player/PlayerMetrics.kt`
- `app/src/main/java/com/vr2xr/display/ExternalDisplayController.kt`
- `app/src/main/java/com/vr2xr/tracking/XrealImuClient.kt`
- `app/src/main/java/com/vr2xr/tracking/PoseFusionEngine.kt`
- `app/src/main/java/com/vr2xr/diag/DiagnosticsOverlay.kt`
- `lint.xml`
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle/wrapper/gradle-wrapper.jar`
- `justfile`
- `tools/dev_fileserver.py`
- `sample_files/FrenchAlps_EscapeVR_PREMIUM.mp4`
- `sample_files/Murren_EscapeVR_PREVIEW_HIGH.mp4`
- `sample_files/INFO.md`
- `sample_files/FrenchAlps_EscapeVR_PREMIUM.mov`
- `sample_files/Murren_EscapeVR_PREVIEW_HIGH.mov`
- `sample_files/metro-10s-709.mp4`
- `PRD_v2.md`
- `AGENTS.md`
- `.gitmodules`
- `reference/xreal_one_driver`
- `reference/xreal_one_driver/src/lib.rs`
- `reference/VRto3D`
- `reference/VRto3D/vrto3d/src/hmd_device_driver.cpp`
- `.agent/CONTINUITY.md`

## Decisions

- D001 ACTIVE: [2026-02-12T16:34Z] [USER] Head tracking integration uses reverse-engineered XREAL method; no Unity dependency.
- D002 ACTIVE: [2026-02-12T16:34Z] [CODE] Core player architecture is Media3/ExoPlayer decode + custom OpenGL reprojection/SBS output.
- D003 ACTIVE: [2026-02-12T16:34Z] [CODE] LibVLC is not MVP core path; optional short spike allowed in Phase 0.
- D003 SUPERSEDED: [2026-02-12T16:42Z] [CODE] LibVLC removed from MVP phases entirely; only post-MVP contingency if acceptance targets fail.
- D004 ACTIVE: [2026-02-12T16:34Z] [CODE] Target hardware policy is Galaxy S23+ class, latest installed firmware, sideload distribution only.
- D005 ACTIVE: [2026-02-12T16:42Z] [CODE] Adopt VRto3D-inspired patterns: per-source profile persistence, off-axis convergence math, async mode as tunable, and explicit runtime status overlays.
- D006 ACTIVE: [2026-02-12T16:46Z] [CODE] MVP includes only: render/output resolution decoupling, off-axis convergence math, and core diagnostics. Per-source profiles and async tuning are post-MVP.
- D007 ACTIVE: [2026-02-12T16:50Z] [USER] Keep all three files in `sample_files/` for now; no removals applied.
- D008 ACTIVE: [2026-02-12T16:52Z] [USER] MVP decode strategy is full-frame per playback frame; region-of-interest decode is deferred post-MVP.
- D009 ACTIVE: [2026-02-12T16:57Z] [USER] Delivery format is an exhaustive multi-phase ExecPlan under `.agent/execplans/active/` aligned to AGENTS/PLANS conventions.
- D010 ACTIVE: [2026-02-12T18:20Z] [CODE] Execution continues with scaffold-first code delivery; build/runtime validation items are explicitly deferred until Android wrapper/toolchain is available in this environment.
- D011 ACTIVE: [2026-02-12T18:20Z] [CODE] Wrapper/bootstrap downloads are not assumed because DNS to `services.gradle.org` fails from this shell; toolchain artifacts must come from local/offline provisioning.
- D012 ACTIVE: [2026-02-12T18:20Z] [CODE] Use `GLSurfaceView` + OES `SurfaceTexture` as the first GPU decode handoff path before implementing full VR reprojection math.
- D013 ACTIVE: [2026-02-12T18:45Z] [CODE] Keep lint strict and satisfy Media3 unstable API requirements through `lint.xml` project opt-in instead of broad class-level `@UnstableApi` propagation.
- D014 ACTIVE: [2026-02-12T19:05Z] [CODE] Environment standardization uses Android Studio JBR + SDK `latest` cmdline tools with PATH precedence to avoid legacy Java/SDK tool mismatch.
- D015 ACTIVE: [2026-02-12T19:12Z] [CODE] Provide URL-path testing infrastructure as a standalone dev utility (`tools/dev_fileserver.py`) invoked via `just`, isolated from app runtime code.
- D016 ACTIVE: [2026-02-12T19:18Z] [USER] URL test inputs should be simplified; server now exposes numbered aliases and prints explicit alias->file mapping.
- D017 ACTIVE: [2026-02-12T19:18Z] [USER] Test assets should be Android-compatible containers; converted MOV samples to MP4 and switched server to MP4/MKV-only listings.
- D018 ACTIVE: [2026-02-12T19:25Z] [USER] Local file playback and URL streaming paths are both functioning on target device.

## Receipts

- [2026-02-12T16:34Z] [TOOL] Android Display docs reviewed for physical mode authority: `Display.Mode` + `DisplayManager` + `Presentation`.
- [2026-02-12T16:34Z] [TOOL] Android media performance docs reviewed: `MediaCodecInfo.VideoCapabilities` indicates 32-bit >4096x4096 limitation and performance/capability gating requirements.
- [2026-02-12T16:34Z] [TOOL] Media3 docs reviewed for ExoPlayer progressive playback, HTTP datasource, analytics counters.
- [2026-02-12T16:34Z] [TOOL] VideoLAN docs reviewed: LibVLC capability/licensing and callback rendering performance caveat.
- [2026-02-12T16:34Z] [TOOL] Samsung references reviewed for DeX behavior context and One UI 7 rollout timing (April 7, 2025 start including S23).
- [2026-02-12T16:34Z] [CODE] Local protocol evidence captured from `reference/xreal_one_driver` (TCP endpoint, packet parse model, IMU fields, MIT license).
- [2026-02-12T16:42Z] [TOOL] `reference/VRto3D` and nested `VRto3DLib` reviewed; extracted practical patterns from `hmd_device_driver.cpp`, `json_manager.cpp`, and `changelog.md` for PRD_v2 updates.
- [2026-02-12T16:46Z] [TOOL] `sample_files/FrenchAlps_EscapeVR_PREMIUM.mov` probed: HEVC Main10, 8192x4096, 59.94 fps, 495.85s, 7.01 GiB.
- [2026-02-12T16:46Z] [TOOL] `sample_files/Murren_EscapeVR_PREVIEW_HIGH.mov` probed: HEVC Rext, 8192x4096, 59.94 fps, 100.18s, 2.47 GiB.
- [2026-02-12T16:46Z] [TOOL] `sample_files/metro-10s-709.mp4` probed: HEVC Main, 8192x4096, 29.97 fps, 10.03s, 0.06 GiB.
- [2026-02-12T16:46Z] [TOOL] L/R half-frame SSIM sampled over 2s windows: FrenchAlps=0.815, Murren=0.854, metro=0.779; confirms split-eye structure but is not conclusive proof of VR180 stereo intent without explicit metadata or headset validation.
- [2026-02-12T16:49Z] [CODE] Added `sample_files/INFO.md` documenting hard requirement checks (8K/SBS/VR180), inferred-vs-explicit metadata caveat, and pending deletion confirmation.
- [2026-02-12T16:50Z] [USER] Confirmed keep-all policy for current `sample_files/` set; removal step deferred.
- [2026-02-12T16:52Z] [CODE] `PRD_v2.md` now includes `MVP research scope (strictly limited)`, `Decode strategy (MVP)`, and `MVP validation checklist` sections aligned to user decisions.
- [2026-02-12T16:57Z] [CODE] New active ExecPlan created at `.agent/execplans/active/EP-2026-02-12__vr2xr-mvp-build.md` and indexed in `.agent/execplans/INDEX.md`.
- [2026-02-12T18:20Z] [TOOL] `./gradlew --version` failed with `no such file or directory: ./gradlew`.
- [2026-02-12T18:20Z] [TOOL] `java -version` returned `openjdk version \"1.8.0_292\"`.
- [2026-02-12T18:20Z] [TOOL] `curl -I https://services.gradle.org/distributions/gradle-8.7-bin.zip` failed with `Could not resolve host`.
- [2026-02-12T18:20Z] [TOOL] `rg --files /Users/nskaria/projects -g 'gradle-wrapper.jar'` returned no local wrapper jar candidate to reuse.
- [2026-02-12T18:45Z] [TOOL] `gradle wrapper --gradle-version 8.7` succeeded and wrapper artifacts were generated in repo.
- [2026-02-12T18:45Z] [TOOL] `./gradlew :app:assembleDebug` succeeded and produced `app/build/outputs/apk/debug/app-debug.apk`.
- [2026-02-12T18:45Z] [TOOL] `./gradlew :app:lint` succeeded after adding `lint.xml` opt-in for `androidx.media3.common.util.UnstableApi`.
- [2026-02-12T18:45Z] [TOOL] `./gradlew :app:testDebugUnitTest` succeeded (`NO-SOURCE`, no failing tests).
- [2026-02-12T18:45Z] [TOOL] `adb devices` showed `R5CW522YY0W unauthorized`; install blocked pending device confirmation dialog.
- [2026-02-12T19:05Z] [USER] `adb install -r app/build/outputs/apk/debug/app-debug.apk` -> `Success`.
- [2026-02-12T19:05Z] [USER] `adb shell am start -n com.vr2xr/.app.MainActivity` -> `Starting: Intent { cmp=com.vr2xr/.app.MainActivity }`.
- [2026-02-12T19:05Z] [USER] `which java` points to Android Studio JBR and `java -version` reports OpenJDK 21.0.8.
- [2026-02-12T19:05Z] [USER] `which sdkmanager` points to `$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager` and reports version `20.0` (with non-fatal shell warning).
- [2026-02-12T19:12Z] [TOOL] `python3 tools/dev_fileserver.py --list-only` printed direct sample URLs under `sample_files/`.
- [2026-02-12T19:12Z] [TOOL] `just --list` shows `serve-samples` and `list-sample-urls` recipes; `just list-sample-urls` executed successfully.
- [2026-02-12T19:18Z] [TOOL] `python3 tools/dev_fileserver.py --list-only` printed `Simple test URLs` entries such as `/1.mov -> FrenchAlps_EscapeVR_PREMIUM.mov`.
- [2026-02-12T19:18Z] [TOOL] `just list-sample-urls` confirms alias output format and still prints direct URLs for fallback.
- [2026-02-12T19:18Z] [TOOL] `ffmpeg -i <mov> -map 0:v -map '0:a?' -c copy -movflags +faststart <mp4>` succeeded for both large MOV files after dropping unsupported MOV timecode data track.
- [2026-02-12T19:18Z] [TOOL] `just list-sample-urls` now prints MP4-only aliases: `/1.mp4`, `/2.mp4`, `/3.mp4`.
- [2026-02-12T19:25Z] [USER] Manual runtime test result: local file playback path successful.
- [2026-02-12T19:25Z] [USER] Manual runtime test result: URL streaming path successful.
