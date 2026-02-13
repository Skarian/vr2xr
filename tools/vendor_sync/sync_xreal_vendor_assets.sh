#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC_DIR="${1:-reference/xreal_sdk}"
CONTROL_APK="${2:-reference/controlglasses/ControlGlasses.apk}"
"$REPO_ROOT/tools/vendor_sync/extract_xreal_sdk_assets.sh" "$SRC_DIR" "$CONTROL_APK"
