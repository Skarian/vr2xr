# Sample Files Verification

Updated: 2026-02-12T19:18Z

## Intent

These files are for local MVP testing of:

- URL playback
- File picker playback
- 8K SBS decode/render pipeline behavior

## Android-Friendly Test Set

Use these files first in the app:

| File | Container | Video Codec/Profile | Pixel Format | FPS | Resolution |
|---|---|---|---|---:|---:|
| `FrenchAlps_EscapeVR_PREMIUM.mp4` | MP4 | HEVC Main 10 | yuv420p10le | 59.94 | 8192x4096 |
| `Murren_EscapeVR_PREVIEW_HIGH.mp4` | MP4 | HEVC Rext | yuv422p10le | 59.94 | 8192x4096 |
| `metro-10s-709.mp4` | MP4 | HEVC Main | yuv420p | 29.97 | 8192x4096 |

## Conversion Notes

- `FrenchAlps_EscapeVR_PREMIUM.mov` -> `FrenchAlps_EscapeVR_PREMIUM.mp4`
- `Murren_EscapeVR_PREVIEW_HIGH.mov` -> `Murren_EscapeVR_PREVIEW_HIGH.mp4`
- Conversion method: container remux only (no re-encode), copying video/audio streams and dropping unsupported MOV timecode data track.

Command used:

```bash
ffmpeg -i input.mov -map 0:v -map '0:a?' -c copy -movflags +faststart output.mp4
```

## Important Compatibility Caveat

- `Murren_EscapeVR_PREVIEW_HIGH.mp4` is HEVC Rext 4:2:2 10-bit (`yuv422p10le`).
- Some Android hardware decoders cannot decode this profile even though the container is MP4.
- If this file fails while others work, the likely fix is transcoding this file to HEVC Main/Main10 4:2:0.

## VR180/SBS Notes

- All files are structurally 8192x4096 SBS and suitable for MVP stereo pipeline testing.
- Explicit VR projection metadata tags are not guaranteed; inference is based on structure and split-eye differences.
- The dev server intentionally advertises `.mp4`/`.mkv` files only.
