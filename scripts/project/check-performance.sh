#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
for wave in 1 2 3 4 5; do
  "${PYTHON_BIN:-python3}" "$HERE/check_cp5_gate.py" performance --wave "$wave"
done
