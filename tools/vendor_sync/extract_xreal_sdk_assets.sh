#!/usr/bin/env bash
set -euo pipefail

SRC_ROOT="${1:-reference/xreal_sdk}"
CONTROL_APK_PATH="${2:-reference/controlglasses/ControlGlasses.apk}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SDK_ROOT="$SRC_ROOT"
if [[ ! "$SDK_ROOT" = /* ]]; then
  SDK_ROOT="$REPO_ROOT/$SDK_ROOT"
fi

CONTROL_APK="$CONTROL_APK_PATH"
if [[ ! "$CONTROL_APK" = /* ]]; then
  CONTROL_APK="$REPO_ROOT/$CONTROL_APK"
fi

ANDROID_PLUGINS_DIR="$SDK_ROOT/Runtime/Plugins/Android"
ARM64_DIR="$ANDROID_PLUGINS_DIR/arm64-v8a"
LIBS_DIR="$REPO_ROOT/app/libs"
JNI_DIR="$REPO_ROOT/app/src/main/jniLibs/arm64-v8a"
MANIFEST="$REPO_ROOT/tools/vendor_sync/LOCAL_MANIFEST.txt"

if [[ ! -d "$ANDROID_PLUGINS_DIR" ]]; then
  echo "XREAL SDK Android plugins not found at: $ANDROID_PLUGINS_DIR"
  echo "Usage: $0 [path/to/reference/xreal_sdk]"
  exit 1
fi

mkdir -p "$LIBS_DIR" "$JNI_DIR"

copy_file() {
  local src="$1"
  local dst="$2"
  if [[ ! -f "$src" ]]; then
    echo "Missing required file: $src"
    exit 1
  fi
  cp -f "$src" "$dst"
}

extract_zip_entry() {
  local archive="$1"
  local entry="$2"
  local dst="$3"
  if ! unzip -l "$archive" "$entry" >/dev/null 2>&1; then
    echo "Missing required entry in $archive: $entry"
    exit 1
  fi
  unzip -p "$archive" "$entry" > "$dst"
}

required_aars=(
  nr_api.aar
  nr_common.aar
  nr_loader.aar
  nractivitylife_6-release.aar
  xreal-auto-log-1.2.aar
  GlassesDisplayPlugEvent-2.4.2.aar
  Log-Control-1.2.aar
)

top_level_sos=(
  libXREALXRPlugin.so
  libXREALNativeSessionManager.so
  libVulkanSupport.so
)

echo "Copying required AARs into app/libs ..."
for aar in "${required_aars[@]}"; do
  copy_file "$ANDROID_PLUGINS_DIR/$aar" "$LIBS_DIR/$aar"
done

echo "Copying top-level SDK native libraries into app/src/main/jniLibs/arm64-v8a ..."
for so in "${top_level_sos[@]}"; do
  copy_file "$ARM64_DIR/$so" "$JNI_DIR/$so"
done

controlglasses_sos=(
  libnr_service.so
  libnr_glasses_api.so
)

if [[ -f "$CONTROL_APK" ]]; then
  echo "Extracting ControlGlasses native libraries from $CONTROL_APK ..."
  for so in "${controlglasses_sos[@]}"; do
    extract_zip_entry "$CONTROL_APK" "lib/arm64-v8a/$so" "$JNI_DIR/$so"
  done
else
  echo "ControlGlasses APK not found at $CONTROL_APK"
  echo "Skipping libnr_service/libnr_glasses_api extraction."
fi

{
  echo "# Local vendor asset manifest"
  echo "# Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo "# Source: $SDK_ROOT"
  echo
  echo "[aar]"
  for aar in "${required_aars[@]}"; do
    shasum -a 256 "$LIBS_DIR/$aar"
  done
  echo
  echo "[native]"
  for so in "${top_level_sos[@]}"; do
    shasum -a 256 "$JNI_DIR/$so"
  done
  if [[ -f "$CONTROL_APK" ]]; then
    for so in "${controlglasses_sos[@]}"; do
      shasum -a 256 "$JNI_DIR/$so"
    done
  fi
} > "$MANIFEST"

echo "XREAL SDK assets extracted."
echo "- AARs:  $LIBS_DIR"
echo "- SOs:   $JNI_DIR"
echo "- Hash:  $MANIFEST"
