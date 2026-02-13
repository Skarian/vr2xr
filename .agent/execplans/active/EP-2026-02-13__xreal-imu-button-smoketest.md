# XREAL IMU Button Smoketest (Start/Stop Toggle + Log Pull Command)

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan must be maintained in accordance with `.agent/PLANS.md`.

- Plan ID: EP-2026-02-13__xreal-imu-button-smoketest
- Status: DRAFT
- Created: 2026-02-13
- Last Updated: 2026-02-13
- Owner: nskaria

## Purpose / Big Picture

This change adds a dedicated `XREAL Test` control on the main screen so we can prove we can initialize the XREAL service stack and receive IMU callbacks before wiring tracking into the video player. A user should be able to tap `XREAL Test` to start streaming/logging IMU data, see the button change to `Stop XREAL Test`, tap again to stop safely, and then run one command that collects app crashes, manifest/package diagnostics, and IMU log output into a gitignored folder in this repo for review.

## Progress

- [x] (2026-02-13T15:01Z) Drafted dedicated ExecPlan for XREAL IMU stop-gap test with toggle and log collection requirements.
- [x] (2026-02-13T15:04Z) Added canonical source-of-truth records from the user-provided Reddit post and direct conversation transcript; marked mandatory 1:1 alignment path.
- [x] (2026-02-13T15:10Z) Added source-record binder namespace note and broad-first IMU manifest parity milestone to reduce startup blockers.
- [x] (2026-02-13T15:10Z) Switched to strict jar-first dependency plan per user direction; removed default binder-class recreation path.
- [ ] Acquire `framework.jar`, `nrcontroller.jar`, and `sparrow.jar` as first execution step.
- [ ] Integrate jars and verify binder classes resolve from jars (not handwritten source shims).
- [ ] Implement JNI bridge classes, native lib packaging, and IMU callback routing.
- [ ] Add `XREAL Test` UI toggle in `MainActivity` and wire start/stop lifecycle.
- [ ] Add one-command log collection workflow into gitignored repo folder.
- [ ] Verify assemble/lint success and produce proof logs from a real run.

## Surprises & Discoveries

- Observation: Decompiled `Control` exposes only three callback entrypoints (`onGlassesAction`, `onGlassesEvent`, `onClientCountChanged`) and does not show IMU callback shims.
  Evidence: `reference/controlglasses/apktool/smali/com/xreal/glasses/api/Control.smali` contains those methods but no `onImuAccAndGyroData`/`onImuMagData`.
- Observation: `libnr_service.so` has a dynamic dependency on `libnr_libusb.so`, so both must ship together.
  Evidence: `/opt/homebrew/opt/llvm/bin/llvm-readelf -d reference/controlglasses/apktool/lib/arm64-v8a/libnr_service.so` reports `NEEDED Shared library: [libnr_libusb.so]`.
- Observation: Native strings reference binder bridge classes under `com/xreal/framework/net/binder`, indicating class-resolution requirements beyond `Control`/`Startup`.
  Evidence: `strings reference/controlglasses/apktool/lib/arm64-v8a/libnr_service.so | rg com/xreal/framework/net/binder`.

## Decision Log

- Decision: Mirror the reverse-engineered approach 1:1 by implementing local JNI bindings in package `com.xreal.glasses.api` and include hidden IMU callback shims provided by the user’s contact.
  Rationale: This is the only verified approach available for XREAL One Pro IMU callback surfacing in this environment.
  Date/Author: 2026-02-13 / Codex + USER
- Decision: Treat the user-provided Reddit excerpt and direct conversation transcript as canonical source-of-truth records for this milestone.
  Rationale: The user explicitly required exact records in the ExecPlan and alignment to this path as the implementation baseline.
  Date/Author: 2026-02-13 / USER
- Decision: The `XREAL Test` action is a two-state toggle (`XREAL Test` -> `Stop XREAL Test`) instead of one-way start.
  Rationale: The user explicitly requested an explicit stop state and safe manual shutdown.
  Date/Author: 2026-02-13 / USER
- Decision: Add a single repo-local log collection command that writes to a gitignored diagnostics folder.
  Rationale: We need deterministic evidence capture (IMU stream, crashes, manifest/package state) that can be reviewed after device runs.
  Date/Author: 2026-02-13 / USER
- Decision (REJECTED): Implement minimal/guessed binder compatibility classes as an initial approach.
  Rationale: Rejected by user direction; binder layer must come from acquired jars first, not guessed source stubs.
  Date/Author: 2026-02-13 / Codex + USER
- Decision: For IMU bring-up, use a broad-first manifest parity set (camera/audio/high-rate sensors/connected-device service and USB host feature), then remove unnecessary entries after successful proof.
  Rationale: User requested a reasonable non-blocking guess now and accepted trimming later if not needed.
  Date/Author: 2026-02-13 / USER + Codex
- Decision: `framework.jar`, `nrcontroller.jar`, and `sparrow.jar` acquisition is mandatory first step; do not fabricate binder classes as a default solution.
  Rationale: User explicitly required non-deviation from the Reddit path and rejected "making files up" as baseline behavior.
  Date/Author: 2026-02-13 / USER
- Decision: Preserve the source-of-truth wording `com.framework.net.binder.*` exactly as mentioned, and treat namespace resolution as verification work against acquired jars rather than assumption.
  Rationale: User indicated this is probably a typo but asked to keep it noted while avoiding speculative drift.
  Date/Author: 2026-02-13 / USER + Codex

## Outcomes & Retrospective

At draft stage, no code has shipped yet. Expected outcome is a validated IMU callback stream in app logs and reproducible diagnostics capture from one command. This section will be updated after implementation and device verification.

## Context and Orientation

The app currently has no active XREAL native integration after the clean reset. The main entrypoint is `app/src/main/java/com/vr2xr/app/MainActivity.kt` with layout in `app/src/main/res/layout/activity_main.xml`. Existing tracking code has been reduced to touch-only pose behavior in player code, which is intentionally out of scope for this stop-gap.

The reference materials for this task are local, under `reference/controlglasses/apktool/`. We rely on:

- Native libs: `reference/controlglasses/apktool/lib/arm64-v8a/libnr_service.so` and `reference/controlglasses/apktool/lib/arm64-v8a/libnr_libusb.so`
- Decompiled API classes: `reference/controlglasses/apktool/smali/com/xreal/glasses/api/Control.smali` and `reference/controlglasses/apktool/smali/com/xreal/glasses/api/Startup.smali`
- Evidence of additional binder class coupling exists in native strings (see `Surprises & Discoveries` for concrete evidence pointers).

The implementation target is a smoketest layer only. It is not the full head-tracking renderer integration from the PRD and must not modify player rendering behavior in this milestone.

## Canonical Source-of-Truth Records

This section is authoritative for this milestone. The implementation must align 1:1 with this path unless the user explicitly updates this record.

Exact record: Reddit user original post excerpt (user-provided).

    1. Included their libnr_service.so and libnr_libusb.so libs

    2. Created the JNI binding classes: com.xreal.glasses.api.Control, com.xreal.glasses.api.Startup

    3. Used their framework.jar, nrcontroller.jar and sparrow.jar files in order to have the com.framework.net.binder.* JNI binding classes (or you can create them)

    4. Set the callbacks needed inside Control: onGlassesAction, onGlassesEvent, onClientCountChanged, onImuAccAndGyroData, onImuMagData

    5. Called the correct sequence of init methods: Startup.nativeInitService(), Startup.nativeStartService(), Startup.nativeGlassesInit(), Startup.nativeImuInit()

    6. Success

Exact record: User-to-contact callback discovery conversation (user-provided).

    me: "I can't find onImuAccAndGyroData" anywhere in controlglasses decompiled apk (used jadx and apktool on multipler versions of the apk.

    Just not seeing it at all, I see onGlassesAction, onGlassesEvent and onClientCountChanged like you mentioned in the post but I don't see onImuAccAndGyroData or onImuMagData"

    him: "let me check where I found those
    oh yea, I remember
    that was the hard part about all of htis
    2:44 PM
    those callbacks are never mentioned anywhere but I discovered them by intercepting the java bindings somehow"

    him: "
    private static ImuAccAndGyroCallback imuCallback;
    private static ImuMagCallback magCallback;

    public interface ImuAccAndGyroCallback {
    void onImuAccAndGyroData(long timestamp1, long timestamp2, float accX, float accY, float accZ, float gyroX, float gyroY, float gyroZ);
    }

    public interface ImuMagCallback {
    void onImuMagData(long timestamp1, long timestamp2, float magX, float magY, float magZ);
    }

    public static void onImuAccAndGyroData(long timestamp1, long timestamp2, float accX, float accY, float accZ, float gyroX, float gyroY, float gyroZ) {
    if (imuCallback != null) {
    imuCallback.onImuAccAndGyroData(timestamp1, timestamp2, accX, accY, accZ, gyroX, gyroY, gyroZ);
    }
    }

    public static void onImuMagData(long timestamp1, long timestamp2, float magX, float magY, float magZ) {
    if (magCallback != null) {
    magCallback.onImuMagData(timestamp1, timestamp2, magX, magY, magZ);
    }
    }

    "

Interpretation rule for this plan:

1. The above records are mandatory baseline behavior and method naming.
2. Where records are incomplete (for example jar packaging details), use local evidence only for verification; do not invent or guess binder JNI binding class surfaces.
3. Any deviation from these records must be logged in `Decision Log` and approved by the user before implementation continues.
4. The source record string `com.framework.net.binder.*` must remain documented exactly as written in the canonical section. Actual runtime package/class names must be confirmed from the acquired jars before coding against them.
5. Do not create handwritten binder compatibility classes unless the user explicitly approves that exception after jar verification shows a hard blocker.
6. For Milestone 2 specifically, no minimal/guessed binder implementation is permitted; binder JNI binding classes must come from the acquired jar artifacts for this path.

## Plan of Work

Milestone 1 acquires the dependency jars as the first required step: `framework.jar`, `nrcontroller.jar`, and `sparrow.jar`. These are required for the binder class surface from the Reddit path. Execution must pause in `BLOCKED` state if these jars cannot be acquired; handwritten binder class recreation is not the default recovery path.

Milestone 2 integrates the acquired jars into the app module and verifies binder class resolution from jar artifacts. Keep verification explicit by checking package/class names directly from jar contents and the compiled app classpath, including the source-record binder namespace expectation (`com.framework.net.binder.*`). No minimal/guessed binder implementation is permitted in this milestone; handwritten binder shim files are not allowed unless the user explicitly approves that exception after a documented blocker.

Milestone 3 establishes native/JNI boundaries using the source-of-truth sequence. Add vendor libraries into `app/src/main/jniLibs/arm64-v8a/`. Create JNI bridge classes `com.xreal.glasses.api.Startup` and `com.xreal.glasses.api.Control` with callback relays including hidden IMU callbacks (`onImuAccAndGyroData`, `onImuMagData`) exactly per canonical records.

Milestone 4 wires the app test surface. Update `activity_main.xml` and `MainActivity.kt` to add a single `XREAL Test` button that toggles between start and stop states. On start, execute the required sequence in order: `Startup.nativeInitService(context)`, `Startup.nativeStartService()`, `Startup.nativeGlassesInit()`, `Startup.nativeImuInit()`. Register callback handlers before init so IMU events are captured from first packet. Log callback data with a stable tag (for example `VR2XR_XREAL_IMU`) and reflect test state plus latest sample in `statusText`. On stop (and on activity teardown), deinitialize cleanly using available native methods (for example `nativeImuDeInit`, `nativeGlassesDeInit`, `nativeStopService`, `nativeDestroyService`) and restore button label/state.

Milestone 5 adds manifest parity for IMU bring-up using a broad-first set to avoid startup blockers; unused entries can be removed after proof.

1. Add permissions in `app/src/main/AndroidManifest.xml`:
   - `android.permission.HIGH_SAMPLING_RATE_SENSORS`
   - `android.permission.FOREGROUND_SERVICE`
   - `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` (with `android:minSdkVersion="34"`)
   - `android.permission.CAMERA`
   - `android.permission.RECORD_AUDIO`
2. Add hardware features:
   - `android.hardware.usb.host`
   - `android.hardware.camera` with `android:required="false"` to avoid filtering install targets.
3. Add binder acceptor service declaration aligned to reference behavior.
4. Explicitly mark these entries as provisional in plan notes and diagnostics output so we can prune after successful IMU validation.

Milestone 6 adds deterministic diagnostics capture. Add a repo-local command (via `justfile` target plus script in `tools/`) that:

1. Creates/rotates a gitignored output directory under `diagnostics/xreal_logs/`.
2. Captures `adb logcat -d` output filtered to app and XREAL tags.
3. Captures crash and ANR related context (`AndroidRuntime`, `DEBUG`, tombstone hints where accessible).
4. Captures package/manifest runtime state via `adb shell dumpsys package com.vr2xr`.
5. Clears logcat after capture (optional flag) to keep subsequent runs clean.

The command should print output file paths so logs can be reviewed immediately in-repo.

## Concrete Steps

Run these commands from repository root (`/Users/nskaria/projects/vr2xr`):

1. Acquire or place jar dependencies:
   - `app/libs/framework.jar`
   - `app/libs/nrcontroller.jar`
   - `app/libs/sparrow.jar`
2. Add jar dependencies in `app/build.gradle.kts` and verify classes resolve from these jars.
3. `mkdir -p app/src/main/jniLibs/arm64-v8a`
4. `cp reference/controlglasses/apktool/lib/arm64-v8a/libnr_libusb.so app/src/main/jniLibs/arm64-v8a/`
5. `cp reference/controlglasses/apktool/lib/arm64-v8a/libnr_service.so app/src/main/jniLibs/arm64-v8a/`
6. Add new source files for:
   - `app/src/main/java/com/xreal/glasses/api/Startup.kt`
   - `app/src/main/java/com/xreal/glasses/api/Control.kt`
   - `app/src/main/java/com/vr2xr/tracking/xreal/XrealImuTestController.kt`
7. Update:
   - `app/build.gradle.kts`
   - `app/src/main/AndroidManifest.xml`
   - `app/src/main/res/layout/activity_main.xml`
   - `app/src/main/java/com/vr2xr/app/MainActivity.kt`
   - `app/src/main/res/values/strings.xml`
   - `justfile`
   - `.gitignore` (if needed for new diagnostics path pattern)
8. Add log collector script:
   - `tools/collect_xreal_logs.sh`
9. Verify builds:
   - `./gradlew :app:assembleDebug`
   - `./gradlew :app:lintDebug`
10. Device-run proof:
   - Install app and run test.
   - Trigger start/stop on `XREAL Test`.
   - Run log capture command (for example `just collect-xreal-logs`).

Expected transcript snippets:

    VR2XR_XREAL_IMU: start requested
    VR2XR_XREAL_IMU: startup sequence success glassesInit=true imuInit=true
    VR2XR_XREAL_IMU: accGyro ts1=... ts2=... acc=(...) gyro=(...)
    VR2XR_XREAL_IMU: mag ts1=... ts2=... mag=(...)
    VR2XR_XREAL_IMU: stop requested
    collect-xreal-logs: wrote diagnostics/xreal_logs/<timestamp>/logcat.txt
    collect-xreal-logs: wrote diagnostics/xreal_logs/<timestamp>/dumpsys_package.txt

## Validation and Acceptance

Acceptance is behavior-based and must be demonstrated on device:

1. `MainActivity` shows a new `XREAL Test` button in addition to existing controls.
2. First tap changes label from `XREAL Test` to `Stop XREAL Test` and updates status text to running state.
3. While running, Logcat contains repeated IMU lines from hidden callbacks (`acc/gyro` and `mag`) or explicit diagnostic errors if unavailable.
4. Second tap stops callbacks, restores label to `XREAL Test`, and updates status text to stopped state.
5. App compiles against `framework.jar`, `nrcontroller.jar`, and `sparrow.jar`, and no handwritten binder compatibility source package is introduced by default.
6. Manifest contains provisional IMU bring-up parity items (permissions/features/service) and app still installs/runs on target test device.
7. Running the log collection command creates timestamped files under gitignored diagnostics directory and includes:
   - IMU callback lines (or failure traces),
   - crash-related logs if present,
   - runtime manifest/package diagnostics from `dumpsys package`.
8. `:app:assembleDebug` and `:app:lintDebug` succeed with no newly introduced errors.

## Idempotence and Recovery

The library-copy step is idempotent because overwriting the same `.so` files is safe. The button toggle start/stop path must be idempotent by guarding against duplicate starts and duplicate stops. If startup fails midway, the controller must execute the same cleanup used by manual stop to return to a known idle state.

If runtime errors report missing JNI classes or methods, recovery is to capture evidence, mark the plan `BLOCKED`, and request user direction; do not add handwritten binder compatibility classes unless the user explicitly approves that exception. Log capture command should always create a new timestamped directory so prior evidence is preserved.

## Artifacts and Notes

Store final evidence in:

- `diagnostics/xreal_logs/<timestamp>/logcat.txt`
- `diagnostics/xreal_logs/<timestamp>/logcat_xreal_imu.txt` (if split filtering is added)
- `diagnostics/xreal_logs/<timestamp>/dumpsys_package.txt`
- `diagnostics/xreal_logs/<timestamp>/meta.txt` (device serial, app version, command timestamp)

Include a short run note in plan progress when first successful IMU stream is observed.

## Interfaces and Dependencies

The following interface shapes must exist at the end of implementation:

- `com.xreal.glasses.api.Startup`
  - Static native methods including at least:
    - `nativeInitService(Context)`
    - `nativeStartService()`
    - `nativeGlassesInit(): Boolean`
    - `nativeImuInit(): Boolean`
    - `nativeImuDeInit()`
    - `nativeGlassesDeInit()`
    - `nativeStopService()`
    - `nativeDestroyService()`
- `com.xreal.glasses.api.Control`
  - Callback relays:
    - `onGlassesAction(int, int, int, long)`
    - `onGlassesEvent(int, int, int, int, int, byte[])`
    - `onClientCountChanged(int, int)`
    - `onImuAccAndGyroData(long, long, float, float, float, float, float, float)`
    - `onImuMagData(long, long, float, float, float)`
  - Callback registration methods for each callback type used by app layer.
- Jar dependencies required:
  - `app/libs/framework.jar`
  - `app/libs/nrcontroller.jar`
  - `app/libs/sparrow.jar`

Dependencies are intentionally limited to existing project dependencies, these local jar artifacts, and local `.so` binaries from the reference repo. No host-level package installation is required.

## Plan Revision Notes (bottom-of-file change notes)

- (2026-02-13) Initial draft created for dedicated XREAL IMU stop-gap test, including requested start/stop toggle behavior and one-command repo-local log collection workflow.
- (2026-02-13) Added canonical source-of-truth records with exact user-provided Reddit steps and callback discovery conversation; marked mandatory 1:1 alignment rule.
- (2026-02-13) Added source-record binder namespace note (`com.framework.net.binder.*` kept explicit) and broad-first IMU manifest parity milestone to avoid permission/service blockers during bring-up.
- (2026-02-13) Revised plan to strict jar-first execution per user direction: acquiring `framework.jar`, `nrcontroller.jar`, and `sparrow.jar` is now mandatory before implementation; default handwritten binder recreation path removed.
