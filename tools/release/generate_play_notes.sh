#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <current-tag> <output-file>" >&2
  exit 1
fi

current_tag="$1"
output_file="$2"
max_chars=500

if [[ ! "$current_tag" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Invalid tag '$current_tag'. Expected vMAJOR.MINOR.PATCH." >&2
  exit 1
fi

if ! git rev-parse --verify "refs/tags/$current_tag" >/dev/null 2>&1; then
  echo "Tag '$current_tag' is not present in the local repository." >&2
  exit 1
fi

tags=()
while IFS= read -r tag; do
  tags+=("$tag")
done < <(git tag --list 'v*.*.*' --sort=version:refname)

previous_tag=""
found_current=0
for tag in "${tags[@]}"; do
  if [[ "$tag" == "$current_tag" ]]; then
    found_current=1
    break
  fi
  previous_tag="$tag"
done

if ((found_current == 0)); then
  echo "Tag '$current_tag' was not found in semantic tag ordering." >&2
  exit 1
fi

range="$current_tag"
if [[ -n "$previous_tag" ]]; then
  range="${previous_tag}..${current_tag}"
fi

subjects=()
while IFS= read -r subject; do
  normalized="${subject//$'\r'/ }"
  normalized="${normalized//$'\n'/ }"
  normalized="${normalized#"${normalized%%[![:space:]]*}"}"
  normalized="${normalized%"${normalized##*[![:space:]]}"}"
  while [[ "$normalized" == *"  "* ]]; do
    normalized="${normalized//  / }"
  done
  if [[ -n "$normalized" ]]; then
    subjects+=("$normalized")
  fi
done < <(git log --no-merges --pretty=format:%s "$range")

notes="Release ${current_tag#v}"
added_any=0
truncated=0

for subject in "${subjects[@]}"; do
  candidate="${notes}"$'\n'"- ${subject}"
  if ((${#candidate} > max_chars)); then
    truncated=1
    break
  fi
  notes="$candidate"
  added_any=1
done

if ((added_any == 0)); then
  fallback="${notes}"$'\n'"- Internal testing release"
  if ((${#fallback} <= max_chars)); then
    notes="$fallback"
  fi
fi

if ((truncated == 1)); then
  ellipsis_line="${notes}"$'\n''- ...'
  if ((${#ellipsis_line} <= max_chars)); then
    notes="$ellipsis_line"
  fi
fi

mkdir -p "$(dirname "$output_file")"
printf '%s\n' "$notes" > "$output_file"
