#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <tag>" >&2
  exit 1
fi

tag="$1"

if [[ ! "$tag" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Invalid tag '$tag'. Expected vMAJOR.MINOR.PATCH with non-negative integers." >&2
  exit 1
fi

major="${BASH_REMATCH[1]}"
minor="${BASH_REMATCH[2]}"
patch="${BASH_REMATCH[3]}"

version_name="${major}.${minor}.${patch}"
version_code=$((major * 1000000 + minor * 1000 + patch))

if ((version_code <= 0 || version_code > 2100000000)); then
  echo "Computed version code '$version_code' is outside Android's supported range." >&2
  exit 1
fi

printf 'VR2XR_VERSION_NAME=%s\n' "$version_name"
printf 'VR2XR_VERSION_CODE=%s\n' "$version_code"
