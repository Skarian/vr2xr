# Display Probe

## Purpose

Verify that the app can reliably observe and react to external display physical mode changes.

## Instrumentation plan

Use `DisplayManager` with `DISPLAY_CATEGORY_PRESENTATION` and log on add/change/remove events:

- display id
- mode id
- physical width / height
- refresh rate
- logical display metrics

Suggested log tag: `vr2xr-display-probe`.

## Test sequence

1. Launch app with no external display connected.
2. Connect XREAL and enable DeX mode.
3. Observe first external display mode log entry.
4. Toggle any available display mode changes (if accessible) and observe callbacks.
5. Disconnect glasses and verify removal callback.

## Result template

- Run date: TODO
- Initial mode seen: TODO
- Mode change callback observed: YES/NO
- Disconnect callback observed: YES/NO
- GO/NO-GO: TODO
- Notes: TODO
