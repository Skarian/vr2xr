#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RAW_DIR="${REPO_ROOT}/assets/screenshots/raw"
CAPTURE_DISPLAY_ID="${CAPTURE_DISPLAY_ID:-}"
STATUS_BAR_TARGET_PACKAGE="${STATUS_BAR_TARGET_PACKAGE:-com.vr2xr}"
DEVICE_SERIAL=""
PREVIOUS_POLICY_CONTROL="null"
RESTORE_POLICY_CONTROL=0

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

detect_device() {
  mapfile -t connected < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [ "${#connected[@]}" -eq 0 ]; then
    echo "No connected Android devices found. Connect one device and retry." >&2
    exit 1
  fi
  if [ "${#connected[@]}" -gt 1 ]; then
    echo "Multiple connected Android devices found. Leave only one connected and retry." >&2
    exit 1
  fi
  echo "${connected[0]}"
}

capture_checkpoint() {
  local device="$1"
  local file_name="$2"
  local guidance="$3"
  local output_path="${RAW_DIR}/${file_name}.png"

  echo
  echo "Checkpoint: ${file_name}"
  echo "${guidance}"
  read -r -p "Press Enter to capture this screen..." _
  if ! adb -s "${device}" shell screencap -d "${CAPTURE_DISPLAY_ID}" -p >"${output_path}"; then
    echo "Display id ${CAPTURE_DISPLAY_ID} capture failed. Retrying without explicit display id."
    adb -s "${device}" shell screencap -p >"${output_path}"
  fi
  if [ ! -s "${output_path}" ]; then
    echo "Capture failed for ${file_name}" >&2
    exit 1
  fi
  echo "Saved ${output_path}"
}

detect_capture_display_id() {
  local device="$1"
  local detected
  if [ -n "${CAPTURE_DISPLAY_ID}" ]; then
    echo "${CAPTURE_DISPLAY_ID}"
    return
  fi
  detected="$(
    adb -s "${device}" shell dumpsys SurfaceFlinger --display-id | tr -d '\r' | \
      awk '/\(HWC display 0\)/ {print $2; exit}'
  )"
  if [ -z "${detected}" ]; then
    detected="$(
      adb -s "${device}" shell dumpsys SurfaceFlinger --display-id | tr -d '\r' | \
        awk '/^Display [0-9]+/ {print $2; exit}'
    )"
  fi
  if [ -z "${detected}" ]; then
    echo "Unable to detect valid display id from SurfaceFlinger output" >&2
    exit 1
  fi
  echo "${detected}"
}

get_policy_control() {
  local device="$1"
  local value
  value="$(adb -s "${device}" shell settings get global policy_control | tr -d '\r')"
  if [ -z "${value}" ] || [ "${value}" = "null" ]; then
    echo "null"
    return
  fi
  echo "${value}"
}

hide_status_bar() {
  local device="$1"
  PREVIOUS_POLICY_CONTROL="$(get_policy_control "${device}")"
  adb -s "${device}" shell settings put global policy_control "immersive.status=${STATUS_BAR_TARGET_PACKAGE}" >/dev/null
  RESTORE_POLICY_CONTROL=1
  echo "Temporarily hiding status bar for package: ${STATUS_BAR_TARGET_PACKAGE}"
}

restore_policy_control() {
  if [ "${RESTORE_POLICY_CONTROL}" -ne 1 ] || [ -z "${DEVICE_SERIAL}" ]; then
    return
  fi
  adb -s "${DEVICE_SERIAL}" shell settings put global policy_control "${PREVIOUS_POLICY_CONTROL}" >/dev/null 2>&1 || true
  echo
  echo "Restored status bar policy_control to: ${PREVIOUS_POLICY_CONTROL}"
}

main() {
  require_command adb
  mkdir -p "${RAW_DIR}"
  local device
  device="$(detect_device)"
  DEVICE_SERIAL="${device}"
  CAPTURE_DISPLAY_ID="$(detect_capture_display_id "${device}")"
  trap restore_policy_control EXIT
  echo "Using device: ${device}"
  echo "Capture display id: ${CAPTURE_DISPLAY_ID}"
  hide_status_bar "${device}"
  echo "Capture contract:"
  echo "1) 01-home: launcher connected idle"
  echo "2) 02-calibration: setup step 1 idle before Run Calibration"
  echo "3) 03-sbs-mode: setup step 2 on-head SBS mode guidance screen"
  echo "4) 04-player: player with controls visible"

  capture_checkpoint "${device}" "01-home" "Navigate to launcher with glasses connected. Keep idle default state visible."
  capture_checkpoint "${device}" "02-calibration" "Open a source and stop at setup step 1 before tapping Run Calibration."
  capture_checkpoint "${device}" "03-sbs-mode" "Run calibration and stop at setup step 2 where Full SBS mode instructions are shown."
  capture_checkpoint "${device}" "04-player" "Continue to player and tap once so controls are visible before capture."
  echo
  echo "Done. Raw screenshots are in ${RAW_DIR}"
}

main "$@"
