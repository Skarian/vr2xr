# vr2xr — Product Requirements Document (PRD)

## 0) One-liner

**vr2xr** is an Android video player that **correctly plays VR-style stereoscopic SBS video** (e.g., **8192×4096, 4096×4096 per eye**) on **Xreal One / One Pro** by using **head tracking** to render a **viewport** and outputting **flat SBS** at the external display’s **physical mode** (e.g., **3840×1080**) so the glasses can split it cleanly.

The intended use is by samsung android users who will use DEX to output the application to the Xreal One Pro

---

## 1) Context and Problem

- Many “SBS” files are **not flat 3D movies**; they’re **VR-projected stereo frames** (often VR180 equirect).
- Xreal’s **Full/ Half SBS** modes assume **flat 16:9-ish SBS**, so VR-projected content looks warped/wrong without reprojection.
- On Samsung DeX, the _UI-reported_ resolution can differ from the _physical output_ (e.g., logical **2560×1080** vs physical **3840×1080** due to scaling). The player must render to **physical pixels**.
  - This must be validated within the application's development as it has only lightly been tested (dex resolution settings page for logical, screen resolution website for physical)

---

## 2) Goals

1. Play VR-style stereo SBS correctly on Xreal glasses:
   - Head movement changes viewport smoothly.
   - Output a clean SBS frame that Xreal can split.
2. Support **streaming from a link / Android intent** and **local file playback**.
3. Auto-detect common layouts and provide manual overrides (metadata is often missing).
4. Keep it **simple**: single-user, no accounts, no cloud, no social features.

---

## 3) Non-Goals

- No full VR platform features (controllers, room mapping beyond head pose).
- No content scraping/library management (posters, metadata, advanced history).
- No DRM / Widevine streaming (Netflix, etc.).
- No multi-user profiles.

---

## 4) Target User

- **One user** (personal app).
- Samsung phone + DeX + Xreal One / One Pro.
- Plays locally stored 8K SBS HEVC files and sometimes streams from a URL.

---

## 5) Primary Use Cases

### UC1 — Play local file

Open app → **Open File** → choose video (SAF picker) → play.

### UC2 — Play from link (copy/paste)

Open app → **Open URL** → paste `https://…` / `http://…` / LAN URL → play.

### UC3 — Play from Android intent

Tap a link or file → **Open with vr2xr** → app launches and plays.

- `ACTION_VIEW` with `http(s)://`
- `ACTION_VIEW` with `content://` (file managers)
- `ACTION_SEND` (shared single video)
- Optional: custom scheme like `vr2xr://open?url=…`

---

## 6) Functional Requirements

### 6.1 Input handling

- **FR-1** Local files via SAF picker.
- **FR-2** Streaming via URL (HTTP/HTTPS).
- **FR-3** Android intents:
  - `ACTION_VIEW` (`content://`, `http(s)://`, plus `file://` where available)
  - `ACTION_SEND` (single item)
  - Persist permissions for `content://` (takePersistableUriPermission).

### 6.2 Format support

- **FR-4** Containers: MP4, MKV (minimum).
- **FR-5** Codecs: HEVC/H.265 (must), optional H.264.
- **FR-6** Layout detection:
  - SBS vs TB inference via dimensions.
  - Per-eye dimension inference (e.g., 8192×4096 → 4096×4096 per eye).
  - Flat SBS vs VR SBS heuristic:
    - ~16:9 per eye → flat SBS
    - ~1:1 per eye → VR-style SBS (likely VR180/VR360)
- **FR-7** Manual overrides (always available):
  - Stereo layout: SBS / Top-Bottom / Mono / Swap eyes
  - Projection: VR180 equirect / VR360 equirect / Flat
  - FOV tuning / zoom

### 6.3 Head tracking + rendering

- **FR-8** Integrate Xreal head tracking API (pose: yaw/pitch/roll; timestamped).
- **FR-9** “Timewarp-like” behavior:
  - Re-render viewport at display refresh using latest pose even if video is 30/60fps.
  - Pose smoothing (low-latency; optional toggle).
- **FR-10** Reprojection renderer:
  - VR180: map equirectangular to hemisphere (or a sphere with clamped longitude).
  - VR360: map to full sphere.
  - Render left/right eyes separately with a simple stereo camera model.

### 6.4 Output pipeline (SBS to glasses)

- **FR-11** Detect external display **physical** mode:
  - Query active mode from Android Display APIs (physical width/height/refresh).
  - Render to physical resolution (e.g., 3840×1080), not DeX “logical desktop”.
- **FR-12** SBS packer:
  - Compose final frame: left view in left half, right view in right half.
  - Output matches active external mode:
    - If 3840×1080 active: **Full SBS** (1920×1080 per eye)
    - If 1920×1080 active: **Half SBS** (960×1080 per eye)
- **FR-13** Fullscreen playback on external display (DeX).

### 6.5 Playback controls

- **FR-14** Standard controls:
  - Play/pause, seek, 10s skip, scrub bar
  - Resume playback (optional, local-only)
- **FR-15** View controls:
  - Recenter view
  - Lock horizon (optional)
  - Adjustable FOV / zoom
  - Projection toggle + quick preset for “8192×4096 VR SBS”

### 6.6 Debug / “make it usable”

- **FR-16** Diagnostics overlay (toggle):
  - External display physical mode (WxH@Hz)
  - Detected layout/projection
  - Decoder stats (dropped frames, decode fps)
- **FR-17** Graceful fallback:
  - If 8K decode fails, offer “Performance mode”:
    - lower render resolution while keeping SBS output correct
    - reduce sampling density / simplify shaders

---

## 7) UX Flows (minimal)

### Home

- Buttons: **Open File**, **Open URL**
- Optional: “Recent” list

### Player

- Tap: show controls
- Quick toggles: **Recenter**, **Swap Eyes**, **Projection**, **FOV**
- Advanced settings sheet: layout/projection overrides, performance mode, diagnostics

---

## 8) Technical Requirements

### 8.1 Architecture (recommended)

- Media3 / ExoPlayer for demux + MediaCodec decode.
- Decode to SurfaceTexture / ImageReader → GPU texture.
- OpenGL ES (or Vulkan) renderer:
  - Split SBS texture → two samplers
  - Apply reprojection shader → render left/right views
  - Compose SBS output frame to external Surface.

### 8.2 Performance targets

- Stable at external output **3840×1080** @ 60+ Hz (where available).
- Low perceived head-tracking latency (timewarp-style render loop).
- Detect device decode limits and auto-enter performance mode if needed.

### 8.3 External display / DeX constraints

- Always prefer external display **physical** mode for render targets.
- Handle “logical 2560×1080 vs physical 3840×1080” scaling cases.

### 8.4 Permissions

- Storage via SAF (no broad file permissions required).
- Network permission for URL streaming.
- Persist URI permissions for `content://` sources.

---

## 9) Acceptance Criteria

- Given an **8192×4096 SBS HEVC** file:
  - vr2xr detects SBS and flags it as VR-style (square per-eye).
  - With glasses in SBS mode and external output at **3840×1080**, the image:
    - has correct proportions (no stretch)
    - responds smoothly to head motion (viewport updates in real time)
- App can be launched via:
  - File manager “Open with…”
  - Browser link (http/https)
  - Share sheet (video)
- Diagnostics overlay reports external physical resolution consistent with browser pixel tests (e.g., 3840×1080).

---

## 10) Risks / Known Hard Parts

- **8K HEVC decode** may be unsupported or unstable on some phones → performance mode + clear UI.
- **Thermals / throttling**: high-refresh external rendering can throttle quickly.
- **Projection ambiguity**: lack of metadata means manual overrides must exist and be easy.
- **SDK integration**: head tracking must fail gracefully (fallback to touch/drag look).

---

## 11) MVP Scope (build first)

1. Intent + URL + file open plumbing.
2. External display physical mode detection.
3. Decode → GPU texture pipeline.
4. Basic reprojection (VR180 equirect) + SBS output.
5. Head tracking + recenter.
6. Diagnostics overlay.

---
