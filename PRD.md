# vr2xr Product Requirements

Status: Active
Last updated: 2026-02-18
Owner: nskaria

## One-liner

`vr2xr` is a Samsung DeX-first Android player for stereoscopic video playback with deterministic external-display behavior and low-friction source ingestion (file or URL).

## Product outcome

A single user can:

1. Open local files or HTTPS links.
2. Play stereoscopic SBS media without stretch/warp.
3. Route rendering to an external display when present.
4. Use manual touch look controls and recenter controls in player mode.
5. See diagnostics for display mode and decoder behavior.

## Scope

In scope:

1. Source ingestion (`file://`, `content://`, `http(s)://`).
2. Playback via Media3/ExoPlayer.
3. OpenGL ES render path for SBS presentation.
4. External display lifecycle and mode handling.
5. Diagnostics overlay and runtime error surfacing.

Out of scope:

1. Vendor-specific motion sensor extraction.
2. Reverse-engineered device SDK bring-up.
3. Sensor protocol research and validation.

## Architecture

1. `app`: activities, lifecycle, error UI.
2. `source`: URI normalization and intent ingestion.
3. `player`: decode lifecycle and codec capability probing.
4. `render`: SBS render pipeline and pose application.
5. `display`: external presentation discovery and routing.
6. `diag`: runtime diagnostics overlay.

## Integration boundary

Motion/orientation data is expected to come from an external library maintained outside this repository.

Repository responsibilities:

1. Accept pose updates in app/render layers.
2. Keep manual touch controls as fallback.
3. Avoid embedding vendor SDK extraction logic.

## Acceptance criteria

1. `:app:assembleDebug` succeeds.
2. `:app:lintDebug` succeeds.
3. Main screen opens URL/file and launches player.
4. Player renders on internal surface and external display surface when available.
5. Recenter and touch look controls remain functional.

## Risks

1. Device-specific decoder capability differences for high-resolution files.
2. External display mode shifts during DeX transitions.
3. Future pose-provider API mismatch during external library integration.

## Next milestones

1. Stabilize display-mode diagnostics and recovery behavior.
2. Add integration adapter for external pose-provider library once API contract is shared.
3. Add automated tests around source ingest + playback intent paths.
