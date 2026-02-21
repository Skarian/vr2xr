# vr2xr Product Requirements

Status: Active
Last updated: 2026-02-21
Owner: nskaria

## One-liner

`vr2xr` is an Android player for stereoscopic video playback with deterministic external-display behavior and low-friction source ingestion (file or URL), using phone controls with glasses-only video output.

## Product outcome

A single user can:

1. Open local files or HTTPS links.
2. See visual XREAL connection status on the home screen.
3. If connected, complete a guided setup flow (calibration on flat surface, then zero-view on-head) before entering playback.
4. Play stereoscopic SBS media without stretch/warp.
5. Route rendering to an external display when present.
6. Control playback from phone with standard transport controls (play/pause, timeline seek, rewind/fast-forward).
7. Use tracking controls (recenter/calibration/zero-view) when the tracking stream is active.
8. Enable diagnostics for display mode, tracking mode, and decoder behavior only when explicitly requested.
9. Continue playback without restart through phone rotation and external display mode churn.
10. Receive clear guidance when Samsung DeX is active to switch to mirroring mode before playback.

## Scope

In scope:

1. Source ingestion (`file://`, `content://`, `http(s)://`).
2. Playback via Media3/ExoPlayer.
3. OpenGL ES render path for SBS presentation.
4. External display lifecycle and mode handling.
5. Route state machine (`ExternalPending`, `ExternalActive`, `NoOutput`) for deterministic external-only playback.
6. Service-owned playback session ownership (`MediaSessionService`) for lifecycle continuity.
7. Guided calibration + zero-view flow for external tracking setup.
8. Runtime external tracking integration via `oneproxr`.
9. Diagnostics overlay and runtime error surfacing, with diagnostics disabled by default.
10. DeX warning policy with mirroring guidance.

Out of scope:

1. Vendor-specific motion sensor extraction.
2. Reverse-engineered device SDK bring-up.
3. Sensor protocol research and validation.
4. Samsung DeX desktop playback support.

## Architecture

1. `app`: activities, lifecycle, error UI.
2. `source`: URI normalization and intent ingestion.
3. `player`: service-owned decode lifecycle, session ownership, and coordinator state.
4. `render`: SBS render pipeline and pose application.
5. `display`: external presentation discovery and routing.
6. `diag`: runtime diagnostics overlay.

## Integration boundary

Motion/orientation data is expected to come from an external library maintained outside this repository.

Repository responsibilities:

1. Accept pose updates in app/render layers.
2. Provide guided setup UX for calibration and zero-view before tracked playback.
3. Keep playback glasses-first with no manual touch-look fallback path.
4. Keep SBS display-input mode as user-managed outside the app.
5. Avoid embedding vendor SDK extraction logic.

## Acceptance criteria

1. `:app:assembleDebug` succeeds.
2. `:app:lintDebug` succeeds.
3. Main screen opens URL/file and launches player.
4. Connected devices route through calibration/zero-view setup before player launch.
5. Player renders to external glasses display only; phone remains controls/status UI.
6. Phone transport controls (play/pause/seek/rewind/fast-forward) operate during playback.
7. Recenter and zero-view controls require an active tracking stream; no manual touch-look fallback path exists.
8. Tracking reconnect during playback does not interrupt video and exposes recalibration/zero-view controls.
9. App does not issue SBS mode requests and playback remains functional regardless of manual SBS toggling timing.
10. During external display mode churn, playback does not switch to phone-active output and resumes on glasses after reconnect.
11. If glasses are disconnected, player remains in waiting state without hidden playback until output reconnects.
12. Phone rotation does not restart playback from the beginning.
13. Playback controls operate via `MediaController` connected to `VrPlaybackService`.
14. If app is backgrounded or phone is locked, playback pauses and does not continue via lock-screen/background media controls.
15. If an active playback session exists, relaunching `vr2xr` from launcher returns user to that session in `PlayerActivity` without resetting position.
16. If Samsung DeX is active, app shows clear guidance to disable DeX and enable mirroring.
17. If playback is paused and the app is foreground with external output available, headset output shows a visible paused frame within 2 seconds without forced auto-resume.
18. Diagnostics overlay and routing logs remain disabled unless diagnostics are explicitly enabled.

## Risks

1. Device-specific decoder capability differences for high-resolution files.
2. External display availability/mode behavior can vary by device profile (personal/work); DeX mode is intentionally unsupported.
3. Future pose-provider API mismatch during external library integration.

## Next milestones

1. Add instrumentation coverage for orientation and external-route continuity on physical devices.
2. Expand diagnostics with player metrics (decoder dropped frames and route transition timestamps).
3. Add tests for source ingest routing and tracking setup gating edge cases.
