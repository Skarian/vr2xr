#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTPUT_DIR="${REPO_ROOT}/artifacts/demo-recordings"
SERIAL="${DEVICE_SERIAL:-}"
PHONE_DISPLAY_ID="${PHONE_DISPLAY_ID:-}"
EXTERNAL_DISPLAY_ID="${EXTERNAL_DISPLAY_ID:-}"
TIME_LIMIT="${TIME_LIMIT:-170}"
BIT_RATE="${BIT_RATE:-16000000}"
SIZE="${SIZE:-}"
BASE_NAME=""
KEEP_REMOTE=0
LIST_DISPLAYS_ONLY=0

usage() {
  cat <<'EOF'
Usage: tools/capture_dual_display.sh [options]

Records phone display + external display in parallel via adb screenrecord.

Options:
  --serial <device-serial>           Target adb serial (default: auto-detect single connected device)
  --phone-display-id <display-id>    Phone display id
  --external-display-id <display-id> External display id
  --time-limit <seconds>             Recording duration per stream (1-180, default: 170)
  --bit-rate <bits-per-second>       Video bitrate (default: 16000000)
  --size <WxH>                       Optional forced recording size (example: 1920x1080)
  --output-dir <path>                Local output directory (default: artifacts/demo-recordings)
  --base-name <name>                 Output base name (default: dual-display-<timestamp>)
  --keep-remote                      Keep recordings on device (/sdcard) after pull
  --list-displays                    Print detected display ids and exit
  -h, --help                         Show this help

Env vars:
  DEVICE_SERIAL, PHONE_DISPLAY_ID, EXTERNAL_DISPLAY_ID, TIME_LIMIT, BIT_RATE, SIZE

Examples:
  tools/capture_dual_display.sh --list-displays
  tools/capture_dual_display.sh --phone-display-id 4619827677550801152 --external-display-id 4619827677550801153
EOF
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --serial)
        [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 1; }
        SERIAL="$2"
        shift 2
        ;;
      --phone-display-id)
        [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 1; }
        PHONE_DISPLAY_ID="$2"
        shift 2
        ;;
      --external-display-id)
        [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 1; }
        EXTERNAL_DISPLAY_ID="$2"
        shift 2
        ;;
      --time-limit)
        [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 1; }
        TIME_LIMIT="$2"
        shift 2
        ;;
      --bit-rate)
        [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 1; }
        BIT_RATE="$2"
        shift 2
        ;;
      --size)
        [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 1; }
        SIZE="$2"
        shift 2
        ;;
      --output-dir)
        [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 1; }
        OUTPUT_DIR="$2"
        shift 2
        ;;
      --base-name)
        [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 1; }
        BASE_NAME="$2"
        shift 2
        ;;
      --keep-remote)
        KEEP_REMOTE=1
        shift
        ;;
      --list-displays)
        LIST_DISPLAYS_ONLY=1
        shift
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        echo "Unknown option: $1" >&2
        usage >&2
        exit 1
        ;;
    esac
  done
}

validate_numeric() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    echo "${name} must be a positive integer: ${value}" >&2
    exit 1
  fi
}

detect_serial() {
  if [[ -n "$SERIAL" ]]; then
    if adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -Fxq "$SERIAL"; then
      return
    fi
    echo "Requested serial '${SERIAL}' is not in adb device state." >&2
    adb devices
    exit 1
  fi
  mapfile -t connected < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  if [[ ${#connected[@]} -eq 0 ]]; then
    echo "No connected Android devices found." >&2
    exit 1
  fi
  if [[ ${#connected[@]} -gt 1 ]]; then
    echo "Multiple devices found. Re-run with --serial." >&2
    adb devices
    exit 1
  fi
  SERIAL="${connected[0]}"
}

display_dump() {
  adb -s "$SERIAL" shell dumpsys SurfaceFlinger --display-id | tr -d '\r'
}

print_display_summary() {
  local dump="$1"
  awk '
    /^Display [0-9]+/ {
      id=$2
      details=$0
      sub(/^Display [0-9]+ /, "", details)
      printf "  - id=%s %s\n", id, details
    }
  ' <<<"$dump"
}

id_in_list() {
  local needle="$1"
  shift
  local id
  for id in "$@"; do
    if [[ "$id" == "$needle" ]]; then
      return 0
    fi
  done
  return 1
}

main() {
  parse_args "$@"
  require_command adb

  validate_numeric "time-limit" "$TIME_LIMIT"
  validate_numeric "bit-rate" "$BIT_RATE"
  if (( TIME_LIMIT < 1 || TIME_LIMIT > 180 )); then
    echo "time-limit must be between 1 and 180 seconds." >&2
    exit 1
  fi
  if [[ -n "$SIZE" ]] && [[ ! "$SIZE" =~ ^[0-9]+x[0-9]+$ ]]; then
    echo "size must be in WxH format, example 1920x1080." >&2
    exit 1
  fi

  detect_serial

  local dump
  dump="$(display_dump)"
  mapfile -t all_ids < <(awk '/^Display [0-9]+/ {print $2}' <<<"$dump")
  if [[ ${#all_ids[@]} -eq 0 ]]; then
    echo "No display ids found via SurfaceFlinger." >&2
    exit 1
  fi

  echo "Device: ${SERIAL}"
  echo "Detected displays:"
  print_display_summary "$dump"

  if [[ "$LIST_DISPLAYS_ONLY" -eq 1 ]]; then
    exit 0
  fi

  local default_phone
  default_phone="$(awk '/\(HWC display 0\)/ {print $2; exit}' <<<"$dump")"
  if [[ -z "$default_phone" ]]; then
    default_phone="${all_ids[0]}"
  fi
  if [[ -z "$PHONE_DISPLAY_ID" ]]; then
    PHONE_DISPLAY_ID="$default_phone"
  fi

  local default_external=""
  local id
  for id in "${all_ids[@]}"; do
    if [[ "$id" != "$PHONE_DISPLAY_ID" ]]; then
      default_external="$id"
      break
    fi
  done

  if [[ -z "$EXTERNAL_DISPLAY_ID" ]]; then
    if [[ -n "$default_external" ]]; then
      EXTERNAL_DISPLAY_ID="$default_external"
    else
      read -r -p "Enter external display id: " EXTERNAL_DISPLAY_ID
    fi
  fi

  if [[ -z "$PHONE_DISPLAY_ID" || -z "$EXTERNAL_DISPLAY_ID" ]]; then
    echo "Both phone and external display ids are required." >&2
    exit 1
  fi
  if [[ "$PHONE_DISPLAY_ID" == "$EXTERNAL_DISPLAY_ID" ]]; then
    echo "phone-display-id and external-display-id must be different." >&2
    exit 1
  fi
  if ! id_in_list "$PHONE_DISPLAY_ID" "${all_ids[@]}"; then
    echo "phone-display-id '${PHONE_DISPLAY_ID}' was not found in detected display ids." >&2
    exit 1
  fi
  if ! id_in_list "$EXTERNAL_DISPLAY_ID" "${all_ids[@]}"; then
    echo "external-display-id '${EXTERNAL_DISPLAY_ID}' was not found in detected display ids." >&2
    exit 1
  fi

  mkdir -p "$OUTPUT_DIR"
  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  if [[ -z "$BASE_NAME" ]]; then
    BASE_NAME="dual-display-${stamp}"
  fi

  local remote_phone="/sdcard/${BASE_NAME}-phone.mp4"
  local remote_external="/sdcard/${BASE_NAME}-external.mp4"
  local local_phone="${OUTPUT_DIR}/${BASE_NAME}-phone.mp4"
  local local_external="${OUTPUT_DIR}/${BASE_NAME}-external.mp4"

  adb -s "$SERIAL" shell rm -f "$remote_phone" "$remote_external" >/dev/null

  local phone_pid=""
  local external_pid=""
  cleanup_on_signal() {
    if [[ -n "$phone_pid" ]]; then
      kill -INT "$phone_pid" >/dev/null 2>&1 || true
    fi
    if [[ -n "$external_pid" ]]; then
      kill -INT "$external_pid" >/dev/null 2>&1 || true
    fi
  }
  trap cleanup_on_signal INT TERM

  local phone_cmd=(adb -s "$SERIAL" shell screenrecord --display-id "$PHONE_DISPLAY_ID" --bit-rate "$BIT_RATE" --time-limit "$TIME_LIMIT")
  local external_cmd=(adb -s "$SERIAL" shell screenrecord --display-id "$EXTERNAL_DISPLAY_ID" --bit-rate "$BIT_RATE" --time-limit "$TIME_LIMIT")
  if [[ -n "$SIZE" ]]; then
    phone_cmd+=(--size "$SIZE")
    external_cmd+=(--size "$SIZE")
  fi
  phone_cmd+=("$remote_phone")
  external_cmd+=("$remote_external")

  echo "Recording for up to ${TIME_LIMIT}s..."
  echo "Phone display id: ${PHONE_DISPLAY_ID}"
  echo "External display id: ${EXTERNAL_DISPLAY_ID}"
  echo "Output directory: ${OUTPUT_DIR}"

  "${phone_cmd[@]}" &
  phone_pid="$!"
  "${external_cmd[@]}" &
  external_pid="$!"

  set +e
  wait "$phone_pid"
  local phone_status=$?
  wait "$external_pid"
  local external_status=$?
  set -e

  trap - INT TERM

  adb -s "$SERIAL" pull "$remote_phone" "$local_phone" >/dev/null
  adb -s "$SERIAL" pull "$remote_external" "$local_external" >/dev/null

  if [[ "$KEEP_REMOTE" -eq 0 ]]; then
    adb -s "$SERIAL" shell rm -f "$remote_phone" "$remote_external" >/dev/null
  fi

  echo "Saved:"
  echo "  ${local_phone}"
  echo "  ${local_external}"

  if [[ "$phone_status" -ne 0 || "$external_status" -ne 0 ]]; then
    echo "One or both screenrecord commands exited non-zero (${phone_status}, ${external_status})." >&2
    echo "If files are short or missing, retry with explicit display ids using --list-displays output." >&2
    exit 1
  fi
}

main "$@"
