# Phase 0 Probes

These probes de-risk the two MVP-critical unknowns before deeper implementation:

1. External display physical mode behavior during DeX/XREAL connect and mode transitions.
2. XREAL reverse-engineered IMU transport viability and reconnect behavior.

## Baseline hardware to record

Fill this with exact values from the first hardware validation session:

- Phone model: Samsung Galaxy S23+ (SM-S916*)
- Phone firmware / build: TODO
- Android version: TODO
- One UI version: TODO
- XREAL model: XREAL One or One Pro
- XREAL firmware: TODO
- Cable / adapter path: TODO

## Execution order

1. Run `display_probe.md` to capture physical mode transitions.
2. Run `tracking_probe.md` to capture IMU transport behavior.
3. Record go/no-go outcomes in each probe file.

## Go / no-go criteria

- Display probe: PASS when physical width/height and refresh can be logged before and after display changes.
- Tracking probe: PASS when IMU stream can be established and transient disconnects recover without app crash.
