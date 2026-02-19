# vr2xr

Android app for stereoscopic SBS video playback with deterministic external-display behavior.

## Current Scope

- Source ingestion from `file://`, `content://`, and `http(s)://`.
- Media3/ExoPlayer playback pipeline.
- OpenGL ES SBS rendering path.
- External display detection and presentation routing.
- Full phone transport controls (play/pause, seek bar, rewind/fast-forward) via Media3 controls.
- Runtime diagnostics overlay for display and decoder status.
- Guided external tracking setup flow backed by `oneproxr`.
- Manual touch-look fallback when external tracking is unavailable.
- DeX warning policy with mirroring-only playback guidance.

## Tracking UX Flow

1. Home screen shows visual XREAL connection status (`connected` / `not connected` / `error`).
2. User chooses a video source.
3. If glasses are connected, app opens `TrackingSetupActivity`:
   - Step 1: place glasses on flat surface and run calibration.
   - Step 2: put glasses on face and press `Zero View`.
   - Continue into player.
4. If glasses are not connected, app goes straight to player in manual fallback mode.
5. During playback, if tracking drops, app continues in manual fallback without stopping video.
6. If tracking reconnects during playback, user can recalibrate/zero-view from player controls.

## oneproxr Integration Boundary

- Submodule path: `reference/one-pro-imu`
- Android library module used by app: `reference/one-pro-imu/oneproxr`
- App-level integration owner: `app/src/main/java/com/vr2xr/tracking/OneProTrackingSessionManager.kt`
- Guided setup screen: `app/src/main/java/com/vr2xr/app/TrackingSetupActivity.kt`

When tracking is active, player uses `poseData` from `oneproxr` (mapped into `PoseState`).
When tracking is inactive/error, player falls back to manual touch look.

## Display Routing

- Player uses an explicit route state machine: `ExternalActive`, `ExternalPending`, `NoOutput`.
- Rendering is glasses-only. The phone is controls/status only and never an active video target.
- If glasses are unavailable, player stays in waiting state and does not run hidden playback.
- During disconnect/reconnect churn, playback continues headless (`NoOutput`) and rebinds to glasses when external surface is ready.
- If playback was active before disconnect, it auto-resumes on glasses after reconnect.

## Playback Continuity

- Playback session ownership is application-scoped so `PlayerActivity` recreation does not reload media.
- Phone rotation rebinds surfaces/UI without restarting playback from the beginning.

## Samsung DeX Policy

- Samsung DeX mode is intentionally unsupported for playback.
- App shows a persistent warning recommending mirroring mode.
- Required user flow: turn off DeX, enable screen mirroring, launch from phone.
- In supported mode, controls stay on phone and video is routed to glasses.

## SBS Mode Behavior

- App does not change device SBS input mode.
- User controls SBS mode directly on the glasses/device.

## Requirements

- JDK 17
- Android SDK / build tools for `compileSdk = 35`

## Local Development

Build:

```bash
./gradlew :app:assembleDebug
```

Lint:

```bash
./gradlew :app:lintDebug
```

## CI APK Automation

GitHub Actions workflow: `.github/workflows/release-apk-latest.yml`

- Trigger: every push to `main`
- Action: builds `app-debug.apk`
- Published file name: `vr2xr.apk`
- Publish target: GitHub Releases
- Release strategy: rolling single release/tag named `latest` (updated on each push)
- Asset strategy: replaces prior `vr2xr.apk` on that release
