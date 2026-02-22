<p align="center">
  <a href="https://buymeacoffee.com/skarian" target="_blank" rel="noopener noreferrer">
    <img src="https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png" alt="Buy Me A Coffee" style="height: 41px !important;width: 174px !important;box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;-webkit-box-shadow: 0px 3px 2px 0px rgba(190, 190, 190, 0.5) !important;">
  </a>
</p>

# vr2xr

vr2xr is a VR video player for **XREAL One** glasses on Android.

It plays SBS/VR video in the glasses, keeps controls on the phone, and uses XREAL One IMU data for head tracking.

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
