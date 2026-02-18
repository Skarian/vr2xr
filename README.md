# vr2xr

Samsung DeX-first Android app for stereoscopic SBS video playback with deterministic external-display behavior.

## Current Scope

- Source ingestion from `file://`, `content://`, and `http(s)://`.
- Media3/ExoPlayer playback pipeline.
- OpenGL ES SBS rendering path.
- External display detection and presentation routing.
- Runtime diagnostics overlay for display and decoder status.

Motion/orientation provider integration is intentionally external to this repository.

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

## CI Release Automation

GitHub Actions workflow: `.github/workflows/release-apk-latest.yml`

- Trigger: every push to `main`
- Action: builds `app-debug.apk`
- Release strategy: maintains a single release/tag named `latest`
- Asset strategy: deletes prior assets on that release and uploads one file:
  `vr2xr-debug.apk`

This keeps only the most recent APK artifact in GitHub Releases.
