set shell := ["bash", "-eu", "-o", "pipefail", "-c"]

serve-samples HOST="0.0.0.0" PORT="8788":
    python3 tools/dev_fileserver.py --host {{HOST}} --port {{PORT}}

list-sample-urls HOST="0.0.0.0" PORT="8788":
    python3 tools/dev_fileserver.py --host {{HOST}} --port {{PORT}} --list-only
