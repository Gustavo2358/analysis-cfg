#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "$HERE/../.." && pwd)"
"${PYTHON_BIN:-python3}" "$HERE/check_integration.py" --root "$ROOT" "$@"

exec "${PYTHON_BIN:-python3}" "$HERE/check_w5.py" integration --root "$ROOT"
