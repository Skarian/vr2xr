#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="${PACKAGE_NAME:-com.vr2xr}"
APP_LOG_FILE="${APP_LOG_FILE:-xreal_probe_latest.log}"
APP_ACTION_FILE="${APP_ACTION_FILE:-xreal_action_tuples_latest.csv}"
APP_EVENT_FILE="${APP_EVENT_FILE:-xreal_event_tuples_latest.csv}"
OUT_DIR="${1:-diagnostics/xreal_logs}"

mkdir -p "${OUT_DIR}"

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device available." >&2
  exit 1
fi

if ! adb shell run-as "${PACKAGE_NAME}" test -f "files/${APP_LOG_FILE}" >/dev/null 2>&1; then
  echo "No probe log found at files/${APP_LOG_FILE}. Run Test XREAL first." >&2
  exit 1
fi

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dest_file="${OUT_DIR}/xreal_probe_${stamp}.log"

adb exec-out run-as "${PACKAGE_NAME}" cat "files/${APP_LOG_FILE}" > "${dest_file}"

if [[ ! -s "${dest_file}" ]]; then
  rm -f "${dest_file}"
  echo "Pulled log is empty. Run Test XREAL first." >&2
  exit 1
fi

cp "${dest_file}" "${OUT_DIR}/latest.log"
echo "Saved ${dest_file}"
echo "Updated ${OUT_DIR}/latest.log"

if adb shell run-as "${PACKAGE_NAME}" test -f "files/${APP_ACTION_FILE}" >/dev/null 2>&1; then
  action_dest_file="${OUT_DIR}/xreal_action_tuples_${stamp}.csv"
  adb exec-out run-as "${PACKAGE_NAME}" cat "files/${APP_ACTION_FILE}" > "${action_dest_file}"
  if [[ -s "${action_dest_file}" ]]; then
    cp "${action_dest_file}" "${OUT_DIR}/latest_action_tuples.csv"
    echo "Saved ${action_dest_file}"
    echo "Updated ${OUT_DIR}/latest_action_tuples.csv"
  else
    rm -f "${action_dest_file}"
    echo "Action tuple file was empty: files/${APP_ACTION_FILE}" >&2
  fi
else
  echo "No action tuple file found at files/${APP_ACTION_FILE}. Run Test XREAL first." >&2
fi

if adb shell run-as "${PACKAGE_NAME}" test -f "files/${APP_EVENT_FILE}" >/dev/null 2>&1; then
  event_dest_file="${OUT_DIR}/xreal_event_tuples_${stamp}.csv"
  adb exec-out run-as "${PACKAGE_NAME}" cat "files/${APP_EVENT_FILE}" > "${event_dest_file}"
  if [[ -s "${event_dest_file}" ]]; then
    cp "${event_dest_file}" "${OUT_DIR}/latest_event_tuples.csv"
    echo "Saved ${event_dest_file}"
    echo "Updated ${OUT_DIR}/latest_event_tuples.csv"
  else
    rm -f "${event_dest_file}"
    echo "Event tuple file was empty: files/${APP_EVENT_FILE}" >&2
  fi
else
  echo "No event tuple file found at files/${APP_EVENT_FILE}. Run Test XREAL first." >&2
fi
