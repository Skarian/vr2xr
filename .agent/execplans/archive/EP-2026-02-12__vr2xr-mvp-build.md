# Build vr2xr MVP End-to-End for Sideload Use

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This plan must be maintained in accordance with `.agent/PLANS.md`.

- Plan ID: EP-2026-02-12__vr2xr-mvp-build
- Status: ARCHIVED
- Created: 2026-02-12
- Last Updated: 2026-02-13
- Owner: nskaria

## Purpose / Big Picture

Superseded on 2026-02-13T14:35Z by user-directed hard reset: remove all XREAL/IMU app-source implementation and restart from a clean baseline.

This plan delivers a usable personal MVP where `vr2xr` receives stable headset-motion IMU signal in-app and feeds that signal into runtime pose fusion for VR viewport updates. Playback and rendering foundations already exist; the critical remaining blocker is reliable IMU ingest.

The current user-directed strategy is to treat the Reddit user approach as directionally correct and recover the real data path through `onGlassesAction` investigation, even if the original write-up was incomplete. The user-visible proof is: `Test XREAL` shows high-rate motion-correlated samples derived from the new mapping path, and player reprojection responds smoothly to head motion.

## Progress

- [x] (2026-02-13T14:35Z) User-directed reset completed: removed all app-source XREAL/IMU Java/Kotlin/C++ bridges, native/jar/aar payload wiring, and Test XREAL UI paths; build/lint passed.
- [x] (2026-02-12T17:00Z) ExecPlan created and aligned with MVP scope.
- [x] (2026-02-13) Verified Reddit-path asset parity: native libs, JNI bridges, and jar dependencies are present and loading.
- [x] (2026-02-13) Implemented full callback diagnostics capture (`onGlassesAction` + `onGlassesEvent`) with CSV export and probe logs.
- [x] (2026-02-13) Verification loop stable for instrumentation changes (`:app:assembleDebug`, `:app:lintDebug`, `adb install -r`).
- [x] (2026-02-13) Captured and analyzed post-expansion run artifacts (`latest.log`, `latest_action_tuples.csv`, `latest_event_tuples.csv`); callback surface remained low entropy.
- [x] (2026-02-13) User obtained direct Reddit-author clarification that hidden IMU callbacks were discovered by Java-binding interception and use two-long timestamp signatures.
- [x] (2026-02-13) Add exact hidden IMU signatures to Java bridge and callback implementation, then verify runtime hit path.
- [x] (2026-02-13) Remove non-Reddit callback overloads/probe branches and reduce active runtime to Reddit path + hidden signature evidence.
- [x] (2026-02-13T14:12Z) Reran `Test XREAL` with patched signatures and collected fresh artifacts; hidden IMU callbacks still not observed in this run.
- [x] (2026-02-13T14:22Z) Reworked callback model to Reddit-chat structure with dedicated IMU callback channels (`imuCallback`/`magCallback`) instead of embedding IMU methods in `INRControlCallback`.
- [x] (2026-02-13T14:26Z) Reran `Test XREAL` after callback-model rewrite and pulled artifacts; hidden IMU callbacks still not observed.
- [ ] (2026-02-13) Run targeted interaction capture (button/touch/motion phases) to increase callback entropy and retry hidden-callback confirmation.
- [ ] (2026-02-13) Add direct on-bridge instrumentation for `Control.onImuAccAndGyroData`/`Control.onImuMagData` invocation and IMU callback registration state.
- [ ] (2026-02-13) Perform decode/correlation pass only after hidden IMU callbacks are confirmed firing.
- [ ] (2026-02-13) Integrate validated IMU callback path into runtime pose flow and validate user-visible motion response.

## Surprises & Discoveries

- Observation: Decompiled callback contracts alone are insufficient for this path; hidden IMU entrypoints can exist outside visible interface definitions.
  Evidence: reference decompile shows 3-method callback surface, but runtime emitted IMU callback lookup warnings and direct Reddit confirmation identified hidden signatures.
- Observation: Current capture instrumentation is functioning correctly, but observed callback classes are still low entropy (`2026` triads + startup events), so decode inference is blocked on missing IMU callback hits.
  Evidence: `diagnostics/xreal_logs/latest_action_tuples.csv`, `diagnostics/xreal_logs/latest_event_tuples.csv`, `diagnostics/xreal_logs/latest.log`.
- Observation: Post-cleanup rerun remains low entropy and now includes zero event rows in exported CSV for that run window.
  Evidence: `diagnostics/xreal_logs/xreal_probe_20260213T141144Z.log`, `diagnostics/xreal_logs/xreal_action_tuples_20260213T141144Z.csv`, `diagnostics/xreal_logs/xreal_event_tuples_20260213T141144Z.csv`.
- Observation: Previous app wiring diverged from Reddit-author callback skeleton by routing hidden IMU methods through `INRControlCallback`; this was corrected in the latest clean-sheet pass.
  Evidence: `app/src/main/java/com/xreal/glasses/api/Control.java`, `app/src/main/java/com/vr2xr/tracking/XrealVendorImuClient.kt`.
- Observation: Correcting callback structure alone did not activate hidden IMU callbacks in immediate retest; capture still contains sparse `2026` action tuples and no event rows.
  Evidence: `diagnostics/xreal_logs/xreal_probe_20260213T142554Z.log`, `diagnostics/xreal_logs/xreal_action_tuples_20260213T142554Z.csv`, `diagnostics/xreal_logs/xreal_event_tuples_20260213T142554Z.csv`.
- Observation: App-PID logcat reveals runtime sensor/plugin failures during the same run (`get device config failed`, repeated `GetPerceptionRuntimeConfig failed`, and missing `/data/user/0/com.vr2xr/app_jniLibs/libexternal_alg.so`), which may block IMU callback emission even with correct Java callback wiring.
  Evidence: `adb logcat -d --pid <vr2xr_pid>` capture at 2026-02-13T14:26Z.
- Observation: Direct user-to-author Reddit conversation provided the decisive missing detail and must be treated as primary evidence for next implementation step.
  Evidence: user-provided conversation details on 2026-02-13:
    Me: "I can't find onImuAccAndGyroData anywhere in ControlGlasses decompiled apk ... I see onGlassesAction/onGlassesEvent/onClientCountChanged but not onImuAccAndGyroData/onImuMagData."
    Author: "those callbacks are never mentioned anywhere ... I discovered them by intercepting the java bindings somehow."
    Author-provided callback skeleton:
      private static ImuAccAndGyroCallback imuCallback;
      private static ImuMagCallback magCallback;
      interface ImuAccAndGyroCallback {
        void onImuAccAndGyroData(long timestamp1, long timestamp2, float accX, float accY, float accZ, float gyroX, float gyroY, float gyroZ);
      }
      interface ImuMagCallback {
        void onImuMagData(long timestamp1, long timestamp2, float magX, float magY, float magZ);
      }
      static forwarders invoke the respective callback if non-null.
- Observation: Cleaning to Reddit-only runtime required removing previously-added fallback branches (reflection overload matrix, SendImuData probe, alternate SDK/bridge classes) to avoid ambiguous callback surfaces during validation.
  Evidence: `Control.java` now exposes only required callbacks; `XrealVendorServiceBootstrap` now uses only `nativeInitService/nativeStartService/nativeGlassesInit/nativeImuInit`; deleted `XrealSendImuLogcatProbe.kt`, `XrealVendorBridge.kt`, `XrealSdkBridge.kt`, and `XrealSdkNativeClient.kt`; `MainActivity` no longer starts SendImuData probe.

## Decision Log

- Decision: Supersede this plan and reset implementation baseline by removing all XREAL/IMU app-source integration.
  Rationale: User explicitly requested full deletion due accumulated tech debt and no successful validation.
  Date/Author: 2026-02-13T14:35Z / User + Codex.
- Decision: Use Reddit-path-first strategy with `onGlassesAction` semantic recovery as immediate implementation track.
  Rationale: User explicitly re-baselined direction and indicated prior write-up likely omitted crucial details rather than being wrong.
  Date/Author: 2026-02-13 / User + Codex.
- Decision: Keep non-Reddit transport branches out of active scope unless user explicitly re-enables them.
  Rationale: User directed that FD path should be ignored for now and not influence planning.
  Date/Author: 2026-02-13 / User + Codex.
- Decision: Reduce plan noise by compressing obsolete branches and preserving only the active blocker-resolution path.
  Rationale: Prevent unnecessary execution drift from stale assumptions.
  Date/Author: 2026-02-13 / User + Codex.
- Decision: Prioritize patching exact hidden IMU callback signatures with two long timestamps before additional decode analysis.
  Rationale: Direct author confirmation identifies likely signature mismatch as primary reason IMU callbacks are not firing despite correct asset/bootstrap setup.
  Date/Author: 2026-02-13 / User + Codex.
- Decision: Constrain active runtime/test harness strictly to Reddit setup + hidden callback signature evidence and remove side branches.
  Rationale: User explicitly requested cleanup to eliminate non-essential paths that could mask whether the Reddit path is working.
  Date/Author: 2026-02-13 / User + Codex.

## Outcomes & Retrospective

Current milestone status: SUPERSEDED.

What is complete:

- Playback/rendering baseline and diagnostic harness exist.
- Decompilation-first contract check is complete enough to avoid further blind callback-signature churn.
- Action-tuple capture/export plumbing is implemented and integrated into the current test workflow (`just pull-xreal-log`).

What remains:

- Capture richer callback IDs than the currently observed startup/heartbeat set.
- Prove or disprove that callback payloads (`onGlassesAction` and/or `onGlassesEvent`) carry enough structure to reconstruct usable IMU.
- Ship a successful callback-path decode that produces usable runtime IMU signal.
- Re-plan from clean baseline under a new ExecPlan if IMU/XREAL work is resumed.

## Context and Orientation

This repository already contains the Android app shell, player pipeline, and current XREAL tracking integration points. The active code paths relevant to this plan are:

- `app/src/main/java/com/vr2xr/tracking/XrealVendorImuClient.kt`: current callback-based integration point for Control/Startup path.
- `app/src/main/java/com/vr2xr/app/MainActivity.kt`: `Test XREAL` diagnostics harness and log surface.
- `app/src/main/java/com/xreal/glasses/api/Control.java`: local callback bridge contract currently used by app code.
- `app/src/main/java/com/xreal/glasses/api/Startup.java`: service/init JNI bridge declarations.
- `reference/controlglasses/apktool/smali/...`: reverse-engineered reference source used as evidence anchor.

Term definition for this plan: `action tuple` means one `onGlassesAction(type, param, param2, timeNanos)` callback instance.

## Plan of Work

Milestone 1: Instrument capture.

Add precise capture in the active `onGlassesAction` callback path and emit structured logs with monotonic receive time, callback time, tuple values, and sequence index. Keep this logging isolated to diagnostics mode so runtime overhead can be controlled.

Milestone 2: Collect evidence.

Run controlled motion scripts (still, isolated axis movement, quick shake) plus explicit interaction stimuli (button/touch/menu) and export logs. Pair action/event tuple logs with existing IMU-related diagnostics to compute cadence and correlation windows.

Milestone 3: Derive decode rules.

Build a small repo-local analyzer script that ingests the exported logs and reports candidate tuple-group mappings to accel/gyro/mag. Accept only mappings that are repeatable across multiple capture sessions and motion scripts.

Milestone 4: Runtime decode integration.

Implement the best mapping candidate in `XrealVendorImuClient` and emit `ImuSample` at practical cadence. Wire to existing fusion path and confirm user-visible head-motion response.

Milestone 5: Hardening and repeatability.

Confirm decode behavior is stable across multiple runs/devices in the current test matrix and keep exported artifacts sufficient for later refinement.

## Concrete Steps

Working directory: repository root.

1. Build and verify baseline before edits:

    JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew :app:assembleDebug

2. Implement action-tuple capture and export plumbing in app code. (DONE 2026-02-13)

3. Rebuild and deploy test build: (DONE 2026-02-13)

    JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew :app:assembleDebug
    /Users/nskaria/Library/Android/sdk/platform-tools/adb -t 1 install -r app/build/outputs/apk/debug/app-debug.apk

4. Run `Test XREAL` and collect logs via existing diagnostics workflow.

5. Run analyzer script over captured logs and produce candidate mappings. (ATTEMPTED 2026-02-13, insufficient signal in first and second captures)

6. Implement decode candidate, rebuild, deploy, and rerun motion tests.

## Validation and Acceptance

Acceptance criteria for this milestone:

1. `Test XREAL` exports structured `onGlassesAction` logs with stable ordering and timestamps.
2. `Test XREAL` exports structured `onGlassesEvent` logs with stable ordering and payload preservation.
3. Analyzer produces at least one repeatable candidate mapping hypothesis from captured sessions.
4. Integrated decode path emits non-sparse `ImuSample` stream that correlates with deliberate head motion.
5. Build succeeds (`:app:assembleDebug`) and app remains stable during `Test XREAL` start/stop cycles.

## Idempotence and Recovery

- Logging/instrumentation changes are additive and safe to rerun.
- Capture sessions are repeatable and should be timestamped in `diagnostics/xreal_logs/`.
- If a decode attempt regresses runtime behavior, revert to last known stable callback capture commit and continue analysis offline.

## Artifacts and Notes

Required artifacts per capture cycle:

- Structured action-tuple log export from `Test XREAL`.
- Structured event-tuple log export from `Test XREAL`.
- Companion runtime log extract for the same test window.
- Analyzer output summary showing top candidate mappings and confidence scores.

## Interfaces and Dependencies

Preserve existing interfaces unless required for decode output integration:

- `XrealVendorImuClient.Listener.onImuSample(sample: ImuSample)` remains the runtime handoff.
- `XrealImuClient` remains the façade consumed by activities.
- No new host-level dependencies are allowed; tooling must remain repo-local.

## Plan Revision Notes (bottom-of-file change notes)

- (2026-02-13T14:35Z) Retired and archived plan under `.agent/execplans/archive/`; index updated to mark `Status:ARCHIVED` with reset outcome.
- (2026-02-13T14:35Z) Plan superseded by explicit user request to delete all XREAL/IMU app-source code and restart from scratch; implementation removed and build/lint verified.
- (2026-02-13) Re-baselined and compacted plan to remove stale branch history; set Reddit-path-first `onGlassesAction` semantic recovery as the sole active blocker-resolution track.
- (2026-02-13) Completed instrumentation milestone: action tuple CSV export + callback plumbing + assemble/lint verification.
- (2026-02-13) First analyzed capture was valid but too sparse for decode inference; next pass requires higher-entropy event generation during capture.
- (2026-02-13) Removed SendImuData UI/log throttle gate to collect full-rate comparison artifacts for path viability decision.
- (2026-02-13) Unthrottled rerun confirmed SendImuData logcat output remains decimated upstream; continue with `onGlassesAction`-centric path.
- (2026-02-13) Added definitive all-line SendImuData probe mode (no parser-side dropping) to close logcat-path viability question with one final capture.
- (2026-02-13) Expanded callback instrumentation to include full `onGlassesEvent` stream capture and removed `onGlassesAction` probe-log sampling gates; diagnostics pull now exports both tuple streams.
- (2026-02-13) First post-expansion rerun still lacked diverse callback IDs; next iteration must inject interaction stimuli to generate richer action/event classes before decode inference.
- (2026-02-13) Added external clarification that hidden IMU callback signatures include two long timestamps; prioritize bridge-signature patch before further inference from sparse action/event IDs.
- (2026-02-13) Compacted plan sections and recorded detailed user-to-Reddit-author conversation evidence (exact hidden signatures + callback skeleton) as primary blocker-resolution input.
- (2026-02-13) Completed strict Reddit-path cleanup: trimmed callback contract/startup sequence to required surface, removed SendImuData and alternate bridge branches, and re-verified with `:app:assembleDebug` + `:app:lintDebug`.
- (2026-02-13T14:12Z) Fresh cleaned-build device run captured and analyzed; hidden IMU callbacks still absent, action tuples remained `2026`-only, and event CSV was empty for this window.
- (2026-02-13T14:22Z) Applied clean-sheet callback correction per user direction: separate static IMU callback channels (`ImuAccAndGyroCallback`, `ImuMagCallback`) now registered independently from `INRControlCallback`; rebuild/lint/install succeeded.
- (2026-02-13T14:26Z) Post-rewire rerun still showed no hidden IMU callback hits; next step is direct bridge-entry instrumentation to determine if native invokes hidden methods at all.
- (2026-02-13T14:26Z) Added runtime evidence that native external-sensor path is degraded (`libexternal_alg.so` load failure + perception config failures), introducing a new blocker candidate beyond Java callback shape.
