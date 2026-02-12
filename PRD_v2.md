# vr2xr - Product Requirements Document v2

Status: Draft for review (execution-ready)
Last updated: 2026-02-12
Owner: nskaria (primary), Codex (implementation guide)

## 0) One-liner

vr2xr is a Samsung DeX-first Android player that correctly converts VR-style stereoscopic source video (especially 8192x4096 SBS HEVC) into clean SBS output for XREAL One / One Pro, with low-latency head-tracked viewport rendering and deterministic external-display behavior.

## 1) Final Product Outcome

A single user can:

1. Open local files or HTTPS links.
2. Play VR-style SBS without stretch/warp.
3. Get responsive head-tracked view changes on XREAL One / One Pro.
4. Force manual layout/projection overrides when detection is wrong.
5. See diagnostics proving physical display mode, decoder health, and tracking status.

## 2) Decisions Locked (based on your direction + research)

1. Device focus: Galaxy S23+ class hardware only (S23 and newer acceptable extension), running the latest firmware available on-device during development.
2. OS baseline: treat Android 15 / One UI 7 as the initial validated baseline for S23 family. Samsung announced One UI 7 rollout starting April 7, 2025 and including S23 series; keep runtime compatibility checks for newer firmware updates.
3. Distribution: sideload only (single-user app), no Play Store constraints for first releases.
4. Head tracking: use reverse-engineered XREAL path (no Unity dependency), based on the xreal_one_driver protocol behavior.
5. Rendering stack: Media3/ExoPlayer + custom OpenGL ES renderer is the primary implementation path.
6. VLC path: no LibVLC fallback in MVP phases. Treat it as post-MVP contingency only if MVP acceptance targets fail after optimization work.

### Revalidation gate at project kickoff

Before coding starts, record current device firmware versions and external mode behavior on your actual handset/glasses pair (single test session). This is a one-time calibration gate, not an open design question.

### MVP research scope (strictly limited)

Only these validations are required before MVP signoff:

1. External display physical mode reliability on target phone/glasses setup.
2. XREAL reverse-engineered IMU transport viability and reconnect behavior.
3. Playback sanity on one local 8192x4096 sample and one HTTPS sample.
4. Basic failure behavior for stream failure, unsupported decode, tracking loss, and display disconnect.

### Lessons adopted from VRto3D reference

MVP essentials:

1. Keep output window mode and internal eye render resolution independently configurable.
2. Use off-axis stereo frustum math for convergence handling (avoid toe-in camera errors).
3. Keep immediate on-screen runtime status for core signals (display mode, tracking state, decoder health).

Post-MVP enhancements:

1. Persist per-title profiles with one-tap reload to baseline.
2. Treat asynchronous reprojection as a user-tunable mode matrix after baseline stability is proven.

## 3) Architecture v1 (implementation target)

### 3.1 High-level modules

1. App shell (`app`): Activities, navigation, settings, intent ingestion.
2. Source ingestion (`source`): URL parser, SAF URI persistence, intent normalization.
3. Playback core (`player`): Media3/ExoPlayer lifecycle, decoder capability probing, buffering policy.
4. Tracking (`tracking`): XREAL IMU transport, packet parser, pose fusion, recenter.
5. Rendering (`render`): OES texture ingestion, reprojection shaders, SBS packer, frame loop.
6. Display orchestration (`display`): external display discovery, mode tracking, presentation lifecycle.
7. Diagnostics (`diag`): overlay, counters, event tracing.

### 3.2 Data flow

1. Input URI (file/content/http/https) -> normalized source descriptor.
2. Media3 decodes active playback frames onto `SurfaceTexture` / OES texture (no full-file predecode).
3. Renderer samples decoded texture and applies per-eye reprojection.
4. Pose provider supplies latest orientation each frame.
5. Renderer composes final SBS frame at active external physical mode.
6. Output rendered to external display surface.

### 3.4 Decode strategy (MVP)

1. Decode is frame-by-frame during playback, not whole-file ahead-of-time.
2. For standard SBS HEVC files, decode full source frames in hardware and do viewport selection in GPU reprojection.
3. Viewport-only decode is post-MVP and only applicable when source encoding supports tiled/region-based access.

### 3.3 Suggested package/class map for implementation

1. `com.vr2xr.app.MainActivity`
2. `com.vr2xr.app.PlayerActivity`
3. `com.vr2xr.source.SourceResolver`
4. `com.vr2xr.source.IntentIngestor`
5. `com.vr2xr.player.VrPlayerEngine`
6. `com.vr2xr.player.CodecCapabilityProbe`
7. `com.vr2xr.display.ExternalDisplayController`
8. `com.vr2xr.render.VrSbsRenderer`
9. `com.vr2xr.render.ProjectionMode`
10. `com.vr2xr.tracking.XrealImuClient`
11. `com.vr2xr.tracking.PoseFusionEngine`
12. `com.vr2xr.diag.DiagnosticsOverlay`

## 4) External Display and DeX Strategy (must not be ambiguous)

### 4.1 Display selection

1. Use `DisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)` to discover preferred external display.
2. When present, create a dedicated fullscreen external rendering surface (Presentation-based path).
3. If no external display, allow local preview mode for debugging.

### 4.2 Physical-mode authority

1. Treat `Display.getMode().getPhysicalWidth()/getPhysicalHeight()/getRefreshRate()` as source of truth.
2. Never size render targets from logical app window size.
3. Diagnostics must always show both logical and physical values.

### 4.3 Mode handling

1. Subscribe to display-change callbacks and rebuild renderer targets on mode changes.
2. Default output mapping:
   - 3840x1080: Full SBS (1920x1080 per eye)
   - 1920x1080: Half SBS (960x1080 per eye)
3. Keep pixel aspect 1:1 and avoid non-uniform scaling in final SBS packing.

## 5) Head Tracking Strategy (reverse-engineered path)

### 5.1 Transport and protocol baseline

Based on reference repository behavior (`reference/xreal_one_driver`):

1. Connect TCP to `169.254.2.1:52998` with timeout.
2. Parse framed IMU packets with known header/sensor markers.
3. Extract gyro/accel/timestamp payload and reject invalid NaN/inf/outlier packets.

### 5.2 Orientation pipeline

1. Input: gyro + accel samples.
2. Fusion: complementary filter in quaternion space for MVP.
3. Output: stable yaw/pitch/roll and quaternion pose.
4. Recenter: yaw zero offset reset (user action).
5. Fallback: if tracking unavailable, switch to touch-drag look mode.

### 5.3 Latency behavior

1. Tracking thread writes latest pose to lock-free latest-value store.
2. Render thread always consumes freshest pose each display frame.
3. Optional smoothing modes: Off, Low, Medium.

## 6) Reprojection and Rendering Requirements

### 6.1 Supported projections in MVP+

1. VR180 equirect (required MVP).
2. Flat SBS (required MVP, mostly passthrough geometry path).
3. VR360 equirect (phase 2+, after MVP pipeline is stable).

### 6.2 Stereo layout handling

1. Detect SBS/TB from dimensions.
2. Infer per-eye resolution.
3. Use heuristic classification:
   - Per-eye approx 1:1 -> likely VR180/VR360 style
   - Per-eye approx 16:9 -> likely flat SBS movie
4. Always allow user override:
   - Layout: SBS / TB / Mono / Swap eyes
   - Projection: VR180 / VR360 / Flat
   - FOV and zoom

### 6.3 Render loop

1. Decoder cadence: source frame rate.
2. Render cadence: display cadence (vsync-driven via Choreographer).
3. For each render tick:
   - sample latest decoded frame
   - sample latest pose
   - render left/right eye reprojection
   - compose SBS output
4. Stereo projection math must use off-axis frustum shift for convergence/depth controls; do not rotate cameras inward (toe-in).

## 7) Performance Targets and Hard Constraints

### 7.1 User-facing targets

1. Start playback from HTTPS URL within 3 seconds on good Wi-Fi for test clips.
2. Stable external rendering at 60 Hz where display mode supports it.
3. Subjective head-tracking responsiveness with no visible stutter during normal motion.

### 7.2 Technical budgets (initial)

1. End-to-end pose-to-render target: median <= 20 ms, p95 <= 35 ms.
2. Dropped rendered frames: < 3% over 5-minute run on baseline device.
3. Decoder fatal errors: zero for supported test matrix files.

### 7.3 8K constraints and requirements

1. App process must be 64-bit only for high-res safety (Android docs note 32-bit process limits above 4096x4096).
2. Probe codec capabilities at runtime using MediaCodec performance/capability APIs.
3. If full 8K decode or render path is unstable, enter Performance Mode automatically.
4. MVP assumes full-frame decode per playback frame; no region-of-interest decode dependency.

### 7.4 Performance Mode (required)

Keep output SBS correctness while reducing internal cost:

1. Mode A (Balanced): lower internal eye render resolution.
2. Mode B (Aggressive): further reduce eye render resolution + lower sampling density.
3. Maintain final external output mode dimensions unchanged.

### 7.5 Streaming and buffering defaults (initial implementation)

1. HTTP connect timeout: 8 seconds.
2. HTTP read timeout: 15 seconds.
3. Initial playback buffer target: 1500-2500 ms for progressive URLs.
4. Rebuffer target: <= 1000 ms where network allows.
5. Use range-request capable datasource and keep redirects enabled.

If these defaults increase startup time on real links, tune in Phase 5 using diagnostics instead of guessing.

## 8) Source Input and Intent Contract

### 8.1 Supported source types

1. SAF-selected local file (`ACTION_OPEN_DOCUMENT` + persistable URI permission).
2. Manual URL input (`http://` and `https://`).
3. External launch intents:
   - `ACTION_VIEW` with `content://` and `http(s)://`
   - `ACTION_SEND` single video (`EXTRA_STREAM`)

### 8.2 Intent normalization rules

1. Resolve launch data priority:
   - explicit deep-link URL param
   - `Intent.getData()`
   - `EXTRA_STREAM`
2. Reject unsupported schemes with actionable error message.
3. Persist `content://` permissions whenever flags allow it.
4. Treat `file://` as best-effort legacy path only; do not rely on it.

### 8.3 Streaming assumptions

1. Progressive HTTPS video links are in scope.
2. No DRM/Widevine.
3. No authenticated cookie/token workflows in MVP.
4. Handle redirects and range requests via configurable Media3 HTTP datasource.

### 8.4 Manifest intent filters (required)

1. `ACTION_VIEW` with `BROWSABLE + DEFAULT` categories for `http` and `https`.
2. `ACTION_VIEW` with `DEFAULT` for `content` scheme and `video/*` MIME.
3. `ACTION_SEND` with `DEFAULT` and `video/*` MIME.

### 8.5 URI permission persistence

1. For `ACTION_OPEN_DOCUMENT`, request and store persistable read permission.
2. Re-open persisted URIs on app relaunch to populate recent items.
3. If persisted permission becomes invalid, mark item stale and prompt user to re-pick.

### 8.6 Per-source profile contract (post-MVP)

1. Store a global default profile and optional per-source overrides.
2. On open, resolve settings as: app defaults -> global profile -> source profile override.
3. Profile payload includes at minimum: projection mode, layout override, swap eyes, FOV/zoom, performance mode, smoothing mode.
4. Provide explicit actions: Save profile, Reload profile, Reset to global defaults.

## 9) UX Contract (minimal but complete)

### 9.1 Home

1. Open File
2. Open URL
3. Recent items (optional in MVP; strongly recommended in phase 2)

### 9.2 Player controls

1. Play/pause, seek, +/-10s
2. Recenter
3. Swap eyes
4. Projection toggle
5. FOV/zoom
6. Diagnostics toggle

### 9.3 Error states

1. Decoder unsupported
2. Tracking unavailable
3. External display missing/disconnected
4. Stream unreachable

Each state must include a recovery action.

## 10) Permissions and Platform Requirements

### 10.1 Manifest permissions

Required:

1. `android.permission.INTERNET`
2. `android.permission.ACCESS_NETWORK_STATE`
3. `android.permission.WAKE_LOCK` (recommended for stable playback)

Not required for primary file flow:

1. broad storage permissions when using SAF.

### 10.2 API and ABI policy

1. Min SDK: 34 (Android 14) for faster delivery on target hardware class.
2. Target SDK: current stable at implementation time.
3. ABI packaging: arm64-v8a required; avoid 32-bit-only build outputs.

## 11) Diagnostics and Observability (non-optional)

Overlay fields:

1. Active display id + physical mode + refresh rate.
2. Logical viewport size vs physical mode.
3. Source resolution, codec, bit rate if available.
4. Detected layout/projection + user overrides.
5. Decoder dropped frames and playback state.
6. Tracking connection state, sample rate estimate, fusion latency estimate.
7. Current performance mode.

Persist lightweight session logs for repro (single local file, rotate by size).

## 12) Acceptance Criteria (quantified)

### 12.1 Functional acceptance

Given a known 8192x4096 SBS HEVC test file:

1. App classifies layout as SBS and flags VR-style candidate.
2. With external mode 3840x1080 and glasses in SBS mode:
   - proportions are correct (no horizontal/vertical distortion)
   - left/right eye mapping is correct
3. Head movement updates viewport continuously.

### 12.2 Input acceptance

1. Open from SAF picker works.
2. Open from HTTPS URL works.
3. Launch from `ACTION_VIEW` and `ACTION_SEND` works.

### 12.3 External-display acceptance

1. Diagnostics physical mode matches `Display.Mode` values in runtime.
2. Rendering output dimensions track display mode changes without app restart.

### 12.4 Reliability acceptance

1. External display unplug/replug recovers without force close.
2. Tracking disconnect recovers or falls back to touch mode.
3. Unreachable URL fails gracefully with retry.

### 12.5 MVP validation checklist

1. Validate physical mode detection on-device while switching DeX/output modes.
2. Validate XREAL IMU read/reconnect path on-device.
3. Validate one local 8192x4096 sample and one HTTPS sample through decode->render->SBS output.
4. Validate four basic failure paths: stream unreachable, decode unsupported, tracking unavailable, display disconnected.

## 13) Multi-Phase Execution Plan

### Phase 0 - Foundation and test harness

Deliverables:

1. Android project scaffold with module boundaries (`source`, `player`, `render`, `tracking`, `display`, `diag`).
2. Diagnostics overlay skeleton.
3. External display discovery probe screen.

Exit criteria:

1. App boots on S23+ baseline firmware.
2. External display mode is detected and shown.

### Phase 1 - Input and intent plumbing

Deliverables:

1. SAF picker flow with persistable URI storage.
2. URL entry flow.
3. `ACTION_VIEW` and `ACTION_SEND` ingestion.

Exit criteria:

1. Any valid source resolves to normalized playback request.

### Phase 2 - Decode pipeline

Deliverables:

1. Media3 player bound to external output-capable surface.
2. Decoder capability probing and analytics wiring.

Exit criteria:

1. Local + HTTPS progressive playback runs in baseline flat path.
2. Decode behavior is confirmed as frame-by-frame playback decode (no full-file predecode).

### Phase 3 - Reprojection renderer + SBS output

Deliverables:

1. OES texture ingestion from decoder.
2. Per-eye reprojection shaders (VR180 first).
3. SBS compositor targeting active external physical mode.

Exit criteria:

1. VR180 sample displays with correct proportions in SBS glasses mode.

### Phase 4 - XREAL tracking integration

Deliverables:

1. IMU TCP client and packet parser.
2. Quaternion fusion + recenter.
3. Render-loop pose injection.

Exit criteria:

1. Head motion drives viewport with stable behavior.

### Phase 5 - Performance mode and hardening

Deliverables:

1. Auto degrade policy (Balanced/Aggressive).
2. Thermal/backpressure handling.
3. Improved buffering defaults for HTTPS links.

Exit criteria:

1. 5-minute stress run meets dropped-frame and stability targets.

### Phase 6 - UX polish + failure recovery

Deliverables:

1. Controls sheet and manual overrides.
2. Clear recovery for tracking/display/network failures.
3. Recent items list.

Exit criteria:

1. End-to-end user flows pass without adb/manual intervention.

### Phase 7 - Sideload release build

Deliverables:

1. Signed release apk.
2. Quick start doc.
3. Known issues list.

Exit criteria:

1. Fresh install on target phone and full acceptance pass.

### Phase 8 - Post-MVP quality features

Deliverables:

1. Per-source profile persistence (save/reload/reset).
2. Optional async/smoothing tuning modes exposed to user.
3. Profile state indicator in diagnostics overlay.

Exit criteria:

1. Post-MVP features do not regress core MVP acceptance criteria.

## 14) Risks and Mitigations

1. IMU protocol drift in future firmware.
   - Mitigation: parser versioning + signature checks + fallback mode.
2. 8K decode instability on some thermal states.
   - Mitigation: proactive capability probe + performance mode + user warning.
3. DeX/display mode inconsistencies across cables/adapters.
   - Mitigation: runtime mode assertions + reconnection handling + diagnostics.
4. Heuristic misclassification of projection/layout.
   - Mitigation: one-tap manual override controls always visible.

## 15) Explicit Non-Goals (still)

1. DRM playback.
2. Multi-user/cloud/social features.
3. Content scraping and metadata library management.
4. Full XR platform features (controllers/room mapping).

## 16) Deferred by User

1. Sample video acquisition and curation under `sample_files/` is deferred and user-owned for now.

## 17) Source-backed Notes

1. Android Display APIs explicitly distinguish physical mode size from app-visible scaled size; render targets must use physical mode for this product.
2. MediaCodec performance points and codec capability APIs should be used for runtime gating.
3. Android docs indicate 32-bit processes have limits above 4096x4096; this impacts 8192x4096 workloads and reinforces 64-bit-only runtime policy.
4. Samsung documentation confirms DeX external-display variability and supported-device dependence; runtime detection and diagnostics are required.
5. `reference/xreal_one_driver` confirms reverse-engineered TCP IMU path, packet parsing baseline, and MIT licensing for reference implementation.
6. `reference/VRto3D` and its shared library show practical patterns worth carrying over. For MVP we apply: separate render/output dimensions, off-axis stereo convergence handling, and on-screen status messaging. Profile persistence and async tuning are post-MVP.
7. `reference/VRto3D` is LGPL-3.0 licensed. Reuse design ideas and workflow patterns, but avoid direct code copy into this project unless LGPL compliance obligations are intentionally accepted.

## 18) Reference Links

Android:

1. https://developer.android.com/reference/android/view/Display.Mode
2. https://developer.android.com/reference/android/hardware/display/DisplayManager
3. https://developer.android.com/reference/android/app/Presentation
4. https://developer.android.com/reference/android/app/ActivityOptions
5. https://developer.android.com/media/optimize/performance/codec
6. https://developer.android.com/reference/android/media/MediaCodecInfo.VideoCapabilities
7. https://developer.android.com/media/media3/exoplayer
8. https://developer.android.com/media/media3/exoplayer/progressive
9. https://developer.android.com/reference/androidx/media3/datasource/DefaultHttpDataSource
10. https://developer.android.com/reference/androidx/media3/exoplayer/analytics/AnalyticsListener
11. https://developer.android.com/reference/androidx/media3/exoplayer/DecoderCounters
12. https://developer.android.com/reference/android/view/Choreographer
13. https://developer.android.com/develop/connectivity/network-ops/connecting
14. https://developer.android.com/reference/android/content/Intent
15. https://developer.android.com/guide/topics/providers/document-provider

Samsung/XREAL:

1. https://news.samsung.com/us/samsung-one-ui-7-announces-official-rollout-starting-from-april-7-2025/
2. https://www.samsung.com/us/support/troubleshoot/TSG10001535/
3. https://developer.samsung.com/samsung-dex/how-it-works.html
4. https://www.xreal.com/us/one-pro
5. https://github.com/rohitsangwan01/xreal_one_driver
6. https://github.com/oneup03/VRto3D

Local reference in this repo:

1. `reference/xreal_one_driver`
2. `reference/xreal_one_driver/src/lib.rs`
3. `reference/xreal_one_driver/include/xreal_one_driver.h`
4. `reference/VRto3D`
5. `reference/VRto3D/vrto3d/src/hmd_device_driver.cpp`
6. `reference/VRto3D/external/VRto3DLib/src/json_manager.cpp`
7. `reference/VRto3D/changelog.md`
