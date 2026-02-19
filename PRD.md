# vr2xr Product Requirements

Status: Active
Last updated: 2026-02-19
Owner: nskaria

## One-liner

`vr2xr` is an Android player for stereoscopic video playback with deterministic external-display behavior and low-friction source ingestion (file or URL), using phone + mirrored glasses mode.

## Product outcome

A single user can:

1. Open local files or HTTPS links.
2. See visual XREAL connection status on the home screen.
3. If connected, complete a guided setup flow (calibration on flat surface, then zero-view on-head) before entering playback.
4. Play stereoscopic SBS media without stretch/warp.
5. Route rendering to an external display when present.
6. Use manual touch look controls and recenter controls when tracking is unavailable.
7. See diagnostics for display mode, tracking mode, and decoder behavior.
8. Receive clear guidance when Samsung DeX is active to switch to mirroring mode before playback.

## Scope

In scope:

1. Source ingestion (`file://`, `content://`, `http(s)://`).
2. Playback via Media3/ExoPlayer.
3. OpenGL ES render path for SBS presentation.
4. External display lifecycle and mode handling.
5. Guided calibration + zero-view flow for external tracking setup.
6. Runtime external tracking integration via `oneproxr`.
7. Diagnostics overlay and runtime error surfacing.
8. DeX warning policy with mirroring guidance.

Out of scope:

1. Vendor-specific motion sensor extraction.
2. Reverse-engineered device SDK bring-up.
3. Sensor protocol research and validation.
4. Samsung DeX desktop playback support.

## Architecture

1. `app`: activities, lifecycle, error UI.
2. `source`: URI normalization and intent ingestion.
3. `player`: decode lifecycle and codec capability probing.
4. `render`: SBS render pipeline and pose application.
5. `display`: external presentation discovery and routing.
6. `diag`: runtime diagnostics overlay.

## Integration boundary

Motion/orientation data is expected to come from an external library maintained outside this repository.

Repository responsibilities:

1. Accept pose updates in app/render layers.
2. Provide guided setup UX for calibration and zero-view before tracked playback.
3. Keep manual touch controls as fallback.
4. Keep SBS display-input mode as user-managed outside the app.
5. Avoid embedding vendor SDK extraction logic.

## Acceptance criteria

1. `:app:assembleDebug` succeeds.
2. `:app:lintDebug` succeeds.
3. Main screen opens URL/file and launches player.
4. Connected devices route through calibration/zero-view setup before player launch.
5. Player renders on internal surface and external display surface when available.
6. Recenter and touch look controls remain functional in fallback/manual mode.
7. Tracking reconnect during playback does not interrupt video and exposes recalibration/zero-view controls.
8. App does not issue SBS mode requests and playback remains functional regardless of manual SBS toggling timing.
9. If Samsung DeX is active, app shows clear guidance to disable DeX and enable mirroring.

## Risks

1. Device-specific decoder capability differences for high-resolution files.
2. External display availability/mode behavior can vary by device profile (personal/work); DeX mode is intentionally unsupported.
3. Future pose-provider API mismatch during external library integration.

## Next milestones

1. Stabilize reconnection UX messaging in player diagnostics and setup screen.
2. Add automated tests for source ingest routing and tracking setup gating logic.
3. Add instrumentation coverage for tracked vs manual fallback playback transitions.
