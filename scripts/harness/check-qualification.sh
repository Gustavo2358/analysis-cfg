#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
AUTHORITY=LOCAL_QUALIFICATION
if [[ "${GITHUB_ACTIONS:-}" == true ]]; then
  AUTHORITY=REMOTE_MANUAL_QUALIFICATION
fi
exec "${PYTHON_BIN:-python3}" -B "$HERE/../project/run_validation.py" "$AUTHORITY" "$@"
