# Tracking Probe

## Purpose

Validate that reverse-engineered XREAL IMU transport can be reached and samples can be consumed safely.

## Transport baseline

- Host: `169.254.2.1`
- Port: `52998`
- Protocol: framed binary IMU packets (as documented in `reference/xreal_one_driver`).

## Probe behavior

1. Open TCP socket with timeout.
2. Read and parse packets.
3. Validate payload sanity (reject NaN/inf/outlier values).
4. Simulate transient disconnect and verify reconnect attempts.

## Result template

- Run date: TODO
- Connected successfully: YES/NO
- Valid sample count (30s): TODO
- Disconnect/reconnect behavior: TODO
- GO/NO-GO: TODO
- Notes: TODO
