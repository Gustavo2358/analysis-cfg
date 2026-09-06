#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "$HERE/../.." && pwd)"

exec "${PYTHON_BIN:-python3}" "$HERE/check_architecture.py" --root "$ROOT" "$@"
