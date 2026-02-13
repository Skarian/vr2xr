# CONTINUITY

Facts only. No transcripts. If unknown, write UNCONFIRMED.
Add dated entries with provenance tags per AGENTS.md: [USER], [CODE], [TOOL], [ASSUMPTION].

## Snapshot

Goal: [2026-02-13T14:35Z] [USER] Remove all XREAL/IMU implementation and related app-source tech debt, then continue from a clean baseline.
Now: [2026-02-13T14:35Z] [CODE] Repo cleanup complete for reset scope: diagnostics purged, `reference/` reduced to `controlglasses` only, XREAL helper targets removed from `justfile`, and prior active ExecPlan archived.
Next: [2026-02-13] [USER] Provide new clean-slate implementation direction.
Open Questions: [2026-02-13] [ASSUMPTION] UNCONFIRMED replacement tracking architecture and acceptance criteria after reset.

## Done (recent)

- [2026-02-13T14:35Z] [CODE] Removed XREAL/IMU app-source classes: `com/xreal/glasses/api/*`, `com/vr2xr/tracking/Xreal*`, `GlassesActionTuple`, `GlassesEventTuple`, `PoseFusionEngine`, and native bridge under `app/src/main/cpp/`.
- [2026-02-13T14:35Z] [CODE] Simplified `MainActivity` by deleting Test XREAL controls/logging and related file export logic.
- [2026-02-13T14:35Z] [CODE] Simplified `PlayerActivity` to manual touch pose only; removed live IMU client dependencies.
- [2026-02-13T14:35Z] [CODE] Removed XREAL/Nreal manifest integration (metadata, binder service, receiver, and related permissions/features).
- [2026-02-13T14:35Z] [CODE] Removed XREAL/Nreal binaries and local deps from app source (`app/libs`, `app/src/main/jniLibs`) and Gradle dependency wiring.
- [2026-02-13T14:43Z] [TOOL] Post-cleanup verification re-run passed: `:app:assembleDebug` and `:app:lintDebug` (AGP compileSdk warning only).
- [2026-02-13T14:35Z] [CODE] Removed diagnostics log trees, pruned `reference/` to `reference/controlglasses` only, cleaned XREAL tasks from `justfile`, and archived the prior active ExecPlan.
- [2026-02-13T14:22Z] [CODE] Reworked `Control.java` to Reddit-chat callback shape: `INRControlCallback` (3 methods) + dedicated `ImuAccAndGyroCallback`/`ImuMagCallback` static hooks.
- [2026-02-13T14:22Z] [CODE] Updated `XrealVendorImuClient` to register/clear all three callback channels (`setINRControlCallback`, `setImuAccAndGyroCallback`, `setImuMagCallback`).
- [2026-02-13] [CODE] Simplified `XrealVendorServiceBootstrap` to Reddit init order only (`nativeInitService`, `nativeStartService`, `nativeGlassesInit`, `nativeImuInit`).
- [2026-02-13] [CODE] Simplified `XrealVendorImuClient` callback implementation to required callbacks only while preserving action/event tuple capture/export.
- [2026-02-13] [CODE] Removed non-Reddit diagnostics and alternate bridge classes (`XrealSendImuLogcatProbe`, `XrealVendorBridge`, `XrealSdkBridge`, `XrealSdkNativeClient`).
- [2026-02-13] [CODE] Removed SendImuData/logcat branch from `Test XREAL`; probe now focuses on callback path evidence.
- [2026-02-13T14:22Z] [TOOL] Verification and deploy after clean-sheet rewrite succeeded: `:app:assembleDebug`, `:app:lintDebug`, `adb install -r`.
- [2026-02-13T14:26Z] [TOOL] Post-rewire device run pulled/analyzed (`xreal_probe_20260213T142554Z.log`): no hidden IMU callbacks, 5 action tuples (`2026` only), event CSV header-only.

## Decisions

- D001 SUPERSEDED: [2026-02-13] [USER] Reddit-path-first investigation baseline replaced by clean-slate reset.
- D002 SUPERSEDED: [2026-02-13] [USER] `onGlassesAction` semantic-analysis track replaced by clean-slate reset.
- D003 SUPERSEDED: [2026-02-13] [USER] Hidden callback-contract pursuit replaced by clean-slate reset.
- D004 SUPERSEDED: [2026-02-13] [USER] FD-path scope decision no longer active after subsystem purge.
- D005 SUPERSEDED: [2026-02-13] [USER] Hidden two-timestamp callback patch strategy replaced by clean-slate reset.
- D006 ACTIVE: [2026-02-13T14:35Z] [USER] Hard reset direction: remove all current XREAL/IMU app-source implementation and restart from scratch.

## State

Done: [2026-02-13T14:35Z] [TOOL] Completed XREAL/IMU app-source purge and verified successful build/lint.
Now: [2026-02-13T14:35Z] [CODE] Baseline app and repo are reset and cleaned for fresh implementation work.
Next: [2026-02-13] [USER] Provide new clean-slate direction for replacement implementation.

## Working set

- `.agent/CONTINUITY.md`
- `.agent/execplans/INDEX.md`
- `.agent/execplans/archive/EP-2026-02-12__vr2xr-mvp-build.md`
- `justfile`
- `reference/controlglasses/`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/vr2xr/app/MainActivity.kt`
- `app/src/main/java/com/vr2xr/app/PlayerActivity.kt`
- `app/src/main/java/com/vr2xr/app/Vr2xrApplication.kt`
- `app/src/main/java/com/vr2xr/tracking/PoseState.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`

## Receipts (recent)

- [2026-02-13] [TOOL] Decompile evidence: reference Control callback contract exposes 3 methods only (`onClientCountChanged`, `onGlassesAction`, `onGlassesEvent`).
- [2026-02-13] [USER] Direction reset: Reddit path remains authoritative; FD branch explicitly out of scope.
- [2026-02-13] [CODE] Implemented all-action and all-event callback persistence in `Test XREAL` with CSV exports (`latest_action_tuples.csv`, `latest_event_tuples.csv`) and probe logs.
- [2026-02-13] [CODE] Removed probe-side throttling/dropping for callback and SendImuData diagnostics; all observed lines now retained.
- [2026-02-13] [TOOL] Build/deploy baseline repeatedly green after instrumentation (`assembleDebug`, `lintDebug`, `adb install -r`).
- [2026-02-13] [TOOL] Latest capture (`xreal_probe_20260213T132910Z.log`) contains only 9 action callbacks and 2 event callbacks in run window.
- [2026-02-13] [TOOL] Action stream remains low entropy: only `(2026,0,0)`, `(2026,1,0)`, `(2026,2,0)` in ~10.014s bursts, with `callback_time_nanos_raw=0`.
- [2026-02-13] [TOOL] Event stream remains startup-only: `category=5,event=114` and `category=5,event=89`, payloads decode to firmware/config strings.
- [2026-02-13] [TOOL] Asset parity verified: app copies of `libnr_service.so`, `libnr_libusb.so`, `libnr_loader.so`, `libnr_api.so` are byte-identical to ControlGlasses reference.
- [2026-02-13] [TOOL] Wiring audit verified Reddit setup steps exist in codebase: native libs, JNI bridges, jar deps, callback registration, and startup init sequence.
- [2026-02-13] [USER] External confirmation from Reddit author: IMU callbacks were found by intercepting Java bindings and are undocumented in decompiled contract.
- [2026-02-13] [USER] External signatures provided verbatim by Reddit author: `onImuAccAndGyroData(long,long,float,float,float,float,float,float)` and `onImuMagData(long,long,float,float,float)`.
- [2026-02-13] [CODE] Cleanup aligned active runtime to Reddit path only; removed reflection-based optional callback overload matrix and non-Reddit probe/bridge branches.
- [2026-02-13] [TOOL] Post-cleanup verification passed with Gradle tasks `:app:assembleDebug` and `:app:lintDebug` (AGP compileSdk warning only).
- [2026-02-13T14:09Z] [TOOL] Host ADB connectivity verified; device detected as `SM_S916U` (`adb-R5CW522YY0W-TIhAEd._adb-tls-connect._tcp`).
- [2026-02-13T14:09Z] [TOOL] Installed `app/build/outputs/apk/debug/app-debug.apk` with `adb install -r` and launched via `adb shell monkey -p com.vr2xr -c android.intent.category.LAUNCHER 1`.
- [2026-02-13T14:11Z] [TOOL] Pulled fresh probe artifacts from device with `tools/pull_xreal_probe_log.sh`; files saved under `diagnostics/xreal_logs/` with stamp `20260213T141144Z`.
- [2026-02-13T14:12Z] [TOOL] Fresh capture summary: no `onImuAccAndGyroData`/`onImuMagData` hits, `latest_action_tuples.csv` has 11 rows (`2026` only), `latest_event_tuples.csv` has header only.
- [2026-02-13T14:22Z] [USER] Directed clean-sheet alignment to Reddit conversation, prioritizing exact callback wiring over prior abstractions.
- [2026-02-13T14:22Z] [CODE] Applied callback wiring correction: hidden IMU methods now forward through dedicated IMU callback fields instead of `INRControlCallback`.
- [2026-02-13T14:22Z] [TOOL] Rebuilt, reinstalled, and relaunched app on `SM_S916U` for immediate validation of corrected callback model.
- [2026-02-13T14:25Z] [TOOL] Pulled new post-rewire artifacts with `tools/pull_xreal_probe_log.sh` (stamp `20260213T142554Z`).
- [2026-02-13T14:26Z] [TOOL] New run summary: `latest.log` contains startup + 5 `ACTION` lines only (`2026` with params `0/1/2`), `latest_event_tuples.csv` remains header-only, and no `IMU t=` lines were emitted.
- [2026-02-13T14:26Z] [TOOL] App-PID logcat during latest run shows `NRExternalSensor` errors: `get device config failed`, repeated `GetPerceptionRuntimeConfig failed`, and `Load lib failed from path: /data/user/0/com.vr2xr/app_jniLibs/libexternal_alg.so`.
- [2026-02-13T14:26Z] [TOOL] Decompiled built APK confirms corrected callback model shipped: `Control` contains separate `ImuAccAndGyroCallback`/`ImuMagCallback` plus static forwarders, and `INRControlCallback` is client/action/event only.
- [2026-02-13T14:35Z] [USER] Requested repo cleanup: remove diagnostics artifacts, prune `reference/` to `controlglasses`, clean `justfile`, and retire current ExecPlan.
- [2026-02-13T14:35Z] [CODE] Executed repo cleanup request and archived `EP-2026-02-12__vr2xr-mvp-build` to `.agent/execplans/archive/` with index updated.
- [2026-02-13T14:43Z] [TOOL] Re-validated baseline after cleanup with Gradle: `BUILD SUCCESSFUL` for `:app:assembleDebug` and `:app:lintDebug`.

## Superseded

- [2026-02-13] [CODE] Superseded assumption: missing IMU methods in decompiled `INRControlCallback` is sufficient to reject Reddit callback-based path.
- [2026-02-13] [CODE] Superseded execution bias: introducing non-Reddit transport branches before exhausting `onGlassesAction` semantic analysis.
