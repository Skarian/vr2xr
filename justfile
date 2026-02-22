set shell := ["bash", "-eu", "-o", "pipefail", "-c"]

serve-samples HOST="0.0.0.0" PORT="8788":
    python3 tools/dev_fileserver.py --host {{HOST}} --port {{PORT}}

list-sample-urls HOST="0.0.0.0" PORT="8788":
    python3 tools/dev_fileserver.py --host {{HOST}} --port {{PORT}} --list-only

readme-shots-setup:
    python3 -m venv .venv/readme-shots
    .venv/readme-shots/bin/python -m pip install --upgrade pip
    .venv/readme-shots/bin/pip install -r tools/readme_screenshots/requirements.txt

readme-shots:
    [ -x .venv/readme-shots/bin/python ] || (echo "Run 'just readme-shots-setup' first" >&2; exit 1)
    bash tools/readme_screenshots/capture_readme_shots.sh
    .venv/readme-shots/bin/python tools/readme_screenshots/frame_readme_shots.py
