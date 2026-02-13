# CONTINUITY

Facts only. No transcripts. If unknown, write UNCONFIRMED.
Add dated entries with provenance tags per AGENTS.md: [USER], [CODE], [TOOL], [ASSUMPTION].

## Snapshot

Goal: [2026-02-13T15:33Z] [USER] Execute `EP-2026-02-13__xreal-imu-button-smoketest` to prove XREAL IMU bring-up with strict alignment to Reddit source-of-truth records.
Now: [2026-02-13] [TOOL] `ControlGlasses.apk` contains binder/sparrow/controller classes in `classes*.dex` (no embedded `.jar` files found) and no reusable packaged unit/instrumentation test suite was identified in decompiled app structure.
Next: [2026-02-13] [USER] Confirm reconstructed-jar path and validation flow (compile + runtime smoke) to execute Milestone 1 immediately.
Open Questions: [2026-02-13] [ASSUMPTION] UNCONFIRMED official distribution point for exact `framework.jar`/`nrcontroller.jar`/`sparrow.jar` binaries.

## Done (recent)

- [2026-02-13T15:04Z] [CODE] Embedded exact Reddit excerpt and direct callback-discovery conversation as canonical source records.
- [2026-02-13T15:10Z] [CODE] Replaced binder-shim-first approach with strict jar-first sequencing.
- [2026-02-13T15:20Z] [CODE] Added explicit no-fabrication rule for binder classes without user exception.
- [2026-02-13T15:22Z] [CODE] Removed non-evidence binder namespace references from plan narrative sections.
- [2026-02-13T15:24Z] [CODE] Hardened Milestone 2 to explicitly reject minimal/guessed binder implementations.
- [2026-02-13T15:24Z] [TOOL] Latest known baseline verification: `:app:assembleDebug` and `:app:lintDebug` previously passed (AGP compileSdk warning only).
- [2026-02-13] [TOOL] Completed jar-source research: local APK has binder namespaces in dex/smali and no `.jar` archive entries.

## Decisions

- D002 ACTIVE: [2026-02-13T14:35Z] [USER] Hard-reset baseline stands: no reuse of prior app-source vendor implementation.
- D003 ACTIVE: [2026-02-13T15:01Z] [USER] Stop-gap requires toggle `XREAL Test`/`Stop XREAL Test` plus repo-local log pull command.
- D004 ACTIVE: [2026-02-13T15:04Z] [USER] Reddit post + direct conversation are canonical source-of-truth records.
- D005 ACTIVE: [2026-02-13T15:20Z] [USER] Keep `com.framework.net.binder.*` explicitly as source-record text; resolve real package names from acquired jars.
- D006 ACTIVE: [2026-02-13T15:10Z] [USER] Keep broad-first manifest parity for IMU bring-up, prune after proof.
- D007 ACTIVE: [2026-02-13T15:10Z] [USER] `framework.jar`, `nrcontroller.jar`, `sparrow.jar` acquisition is mandatory first step.
- D008 ACTIVE: [2026-02-13T15:20Z] [USER] No binder class recreation unless explicitly approved after jar verification shows blocker.
- D009 ACTIVE: [2026-02-13T15:24Z] [USER] Milestone 2 must not use minimal/guessed binder implementations.
- D010 ACTIVE: [2026-02-13T15:33Z] [USER] Execution discipline: if implementation path conflicts with source records, mark `BLOCKED` and request user decision instead of inferring.

## State

Done: [2026-02-13] [TOOL] Gathered local + web evidence for jar acquisition options and constraints.
Now: [2026-02-13] [CODE] Awaiting user selection of acquisition route before Milestone 1 implementation.
Next: [2026-02-13] [USER] Approve route and proceed with jar acquisition workflow.

## Working set

- `.agent/CONTINUITY.md`
- `.agent/execplans/INDEX.md`
- `.agent/execplans/active/EP-2026-02-13__xreal-imu-button-smoketest.md`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/vr2xr/app/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/libs/`
- `app/src/main/jniLibs/arm64-v8a/`
- `tools/collect_xreal_logs.sh`
- `justfile`

## Receipts (recent)

- [2026-02-13T15:01Z] [USER] Requested plan-first start with `XREAL Test` and log pull command.
- [2026-02-13T15:04Z] [USER] Required full Reddit + conversation records as canonical source-of-truth.
- [2026-02-13T15:10Z] [USER] Required jar-first path and rejected default file recreation.
- [2026-02-13T15:20Z] [USER] Required drift audit and removal of permissive binder language.
- [2026-02-13T15:24Z] [USER] Clarified Milestone 2 must not be minimal/guess-based.
- [2026-02-13T15:01Z] [CODE] Created/indexed active ExecPlan.
- [2026-02-13T15:10Z] [CODE] Rewrote plan to strict jar-first.
- [2026-02-13T15:22Z] [CODE] Removed non-evidence binder namespace references.
- [2026-02-13T15:24Z] [CODE] Updated Milestone 2 to no-minimal/no-guess binder rule.
- [2026-02-13T15:33Z] [CODE] Rewrote continuity into execution-ready anti-drift state.
- [2026-02-13] [TOOL] Local evidence: `ControlGlasses.apk` has `classes*.dex` + `.so`; no `.jar` entries; binder classes present under `com/xreal/framework/net/binder` and `ai/nreal/framework/net/binder`.
- [2026-02-13] [TOOL] Official Android docs confirm APKs package compiled code as DEX and AARs package `classes.jar` + optional `libs/*.jar`.
- [2026-02-13] [TOOL] XREAL release notes place direct IMU/camera/event data under Enterprise SDK access path.
- [2026-02-13] [TOOL] Decompiled `ControlGlasses` manifest/activity scan found runtime app flows but no explicit packaged test runner or instrumentation harness for jar validation.

## Superseded

- [2026-02-13T15:10Z] [CODE] Superseded binder-shim-first execution path.
- [2026-02-13T15:22Z] [CODE] Superseded non-evidence binder namespace usage in plan narrative.
