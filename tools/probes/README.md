# Phase 0 Probes

These probes validate display behavior before adding deeper runtime integrations.

## Baseline hardware to record

Fill this with exact values from the first hardware validation session:

- Phone model: Samsung Galaxy S23+ (SM-S916*)
- Phone firmware / build: TODO
- Android version: TODO
- One UI version: TODO
- External display model: TODO
- External display firmware: TODO
- Cable / adapter path: TODO

## Execution order

1. Run `display_probe.md` to capture physical mode transitions.
2. Record go/no-go outcomes in each probe file.

## Go / no-go criteria

- Display probe: PASS when physical width/height and refresh can be logged before and after display changes.
