# XREAL SDK Asset Sync (Local Only)

This project uses a local-only XREAL SDK workflow for native tracking integration.

Source expected:

- `reference/xreal_sdk` (official SDK package dropped into repo-local `reference/`)

Proprietary blobs are local-only and must not be committed.

## Usage

1. Run:

   ```sh
   just sync-xreal-vendor
   ```

2. Optional alternate source path and ControlGlasses APK path:

   ```sh
   just sync-xreal-vendor SRC=/absolute/path/to/xreal_sdk CONTROL_APK=/absolute/path/to/ControlGlasses.apk
   ```

3. The scripts copy required assets into:

- `app/libs/` (`.aar` runtime packages, including `Log-Control-1.2.aar`)
- `app/src/main/jniLibs/arm64-v8a/` (native `.so` blobs, including `libnr_service.so` and `libnr_glasses_api.so` when ControlGlasses APK is available)

4. Rebuild and install:

   ```sh
   ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Notes

- `just sync-xreal-vendor` delegates to `tools/vendor_sync/extract_xreal_sdk_assets.sh`.
- If `reference/controlglasses/ControlGlasses.apk` exists, the sync step also extracts `libnr_service.so` and `libnr_glasses_api.so` from it.
- If assets are missing, `Test XREAL` reports native-load failure and suggests rerunning sync.
