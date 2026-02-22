<p align="center">
  <a href="https://buymeacoffee.com/skarian" target="_blank" rel="noopener noreferrer">
    <img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" alt="Buy Me A Coffee" style="height: 41px !important;width: 174px !important;box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;-webkit-box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;">
  </a>
</p>

<p align="center">
  <img src="assets/banner.png" alt="vr2xr" width="100%">
</p>

**vr2xr is an Android app for watching VR SBS video on XREAL One glasses. It focuses on simple, reliable playback with phone-based controls and IMU head tracking.**

---

<p align="center">
  <img src="./assets/screenshots/framed/01-home-framed.png" alt="vr2xr home" width="22%">
  &nbsp;
  <img src="./assets/screenshots/framed/02-calibration-framed.png" alt="vr2xr calibration setup" width="22%">
  &nbsp;
  <img src="./assets/screenshots/framed/03-sbs-mode-framed.png" alt="vr2xr sbs mode setup" width="22%">
  &nbsp;
  <img src="./assets/screenshots/framed/04-player-framed.png" alt="vr2xr player controls" width="22%">
</p>

## Requirements

- XREAL One or XREAL One Pro glasses
- Android 13+ phone (`minSdk = 33`)
- Samsung DeX desktop mode is not supported (use screen mirroring instead)

## App Guide

1. Connect your XREAL One glasses.
2. Open a video from file, URL, or share.
3. In setup step 1, place the glasses on a flat surface and run calibration.
4. In setup step 2, put the glasses back on and switch `Display > 3D Mode` to `Full SBS`.
5. Tap `Continue to VR Player` (the app applies Zero View automatically).
6. Start playback and use phone controls to play, pause, seek, rewind, and fast-forward.

Samsung DeX warning: DeX desktop mode is not supported for playback. If DeX is on, turn it off and use screen mirroring.

If glasses are disconnected, playback pauses and waits for glasses output to return.

Phone-only playback is intentionally not supported. The app also does not change SBS mode on your glasses; SBS mode stays user/device controlled.

## Video Sources

- Local video files
- `http(s)` video URLs
- Android share targets (video links/files)

## Install

Debug APKs are published to the rolling `latest` GitHub release as `vr2xr.apk`.

## For Developers

Development setup, build/test commands, diagnostics, and project structure are in `CONTRIBUTING.md`.
