#!/usr/bin/env python3
"""Offline 4D pin/fixture guard; compare real Git blobs when the upstream checkout is available."""
from __future__ import annotations
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path
from check_architecture import GateFailure, verify_snapshot_pin

AIR = "ce530a7e17ab12b23c48f29425f503ff920b09fb"
SCALAR = "cfg-adapters/src/test/resources/air/scalar-assign.canonical.json"
SHA256 = "40b9cec1bcc1c1e40cf3b9e3c48e834835e478e1d84bccafa575d63497ef3b60"
BLOB = "b37ff744819132cd0bfe115ce9a4382b19a29060"
GOBACK = "cfg-adapters/src/test/resources/air/goback.canonical.json"
GOBACK_HASH = "fa299c2e5f3fae75afe365363b9f16925f0cfea591f631768ace82f0fb9a1075"


def verify_scalar_contract(root: Path) -> None:
    pin = json.loads((root / "docs/sources/sources.lock.json").read_text())["air_java"]["commit"]
    if verify_snapshot_pin(root) != pin:
        raise GateFailure("Scalar regression requires the exact authorized air-java pin")
    data = (root / SCALAR).read_bytes()
    if len(data) != 14554 or hashlib.sha256(data).hexdigest() != SHA256:
        raise GateFailure("scalar fixture is not byte-identical to approved upstream")
    blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    if blob != BLOB:
        raise GateFailure("scalar Git blob identity differs")
    expected = dict(repository="Gustavo2358/air-java", merge_commit=AIR,
                    upstream_path="air-json/src/test/resources/scalar-assign.canonical.json",
                    upstream_blob_sha1=BLOB, sha256=SHA256, bytes=14554, local_path=SCALAR,
                    local_sha256=SHA256, local_bytes=14554)
    provenance = root / "cfg-adapters/src/test/resources/air/scalar-assign.provenance.json"
    if json.loads(provenance.read_text()) != expected:
        raise GateFailure("scalar provenance metadata disagrees with approved blob")
    if hashlib.sha256((root / GOBACK).read_bytes()).hexdigest() != GOBACK_HASH:
        raise GateFailure("GOBACK upstream fixture regression")
    # Use the same isolated source already validated by verify_snapshot_pin above.
    # A sibling's occupied branch/object database is not the dependency authority.
    upstream = Path(os.environ.get('AIR_JAVA_CHECKOUT', str(root / '.harness-results/build/air-java')))
    if (upstream / ".git").exists():
        for local in (SCALAR, GOBACK):
            path = "air-json/src/test/resources/" + Path(local).name
            result = subprocess.run(["git", "-C", str(upstream), "show", pin + ":" + path],
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
            if result.returncode or result.stdout != (root / local).read_bytes():
                raise GateFailure("historical fixture differs from pinned upstream blob: " + path)
        print("[4D] PASS: historical scalar/GOBACK equal actual pinned upstream blobs", flush=True)
    else:
        print("[4D] upstream checkout absent; verified pinned content hashes offline", flush=True)
    print("[4D] PASS: approved merge, CI/lock, scalar SHA-256/Git blob/size/provenance and GOBACK hash", flush=True)


if __name__ == "__main__":
    try:
        verify_scalar_contract(Path(__file__).resolve().parents[2])
    except (GateFailure, OSError, ValueError) as exc:
        print("[4D] FAIL: " + str(exc), file=sys.stderr)
        sys.exit(1)
