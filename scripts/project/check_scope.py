#!/usr/bin/env python3
"""Check CP5 preparation diff scope, fixed upstream authority and a complete tracked/untracked delivery manifest."""
from __future__ import annotations
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path
from check_architecture import GateFailure

ROOT = Path(__file__).resolve().parents[2]
BASE = "ec525cbbad96d70c9663faa88e2672148fa8ee71"
AIR = "ce530a7e17ab12b23c48f29425f503ff920b09fb"
IR = "122ce54e1b9ef9b00646f93ece409ca8b63bc933"
FIXTURE = "cfg-adapters/src/test/resources/air/goback.canonical.json"
FIXTURE_HASH = "fa299c2e5f3fae75afe365363b9f16925f0cfea591f631768ace82f0fb9a1075"


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", *args], cwd=ROOT)


def files() -> list[str]:
    return sorted({p for p in git("ls-files", "-z", "--cached", "--others", "--exclude-standard").decode().split("\0")
                   if p and p != "MANIFEST.sha256" and (ROOT / p).is_file()})


def manifest() -> bytes:
    lines = ["# SHA-256 da entrega CP5 harness; inclui arquivos versionáveis, exclui este manifesto e artefatos ignorados."]
    lines += [hashlib.sha256((ROOT / path).read_bytes()).hexdigest() + "  " + path for path in files()]
    return ("\n".join(lines) + "\n").encode()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--update-manifest", action="store_true")
    args = parser.parse_args()
    try:
        if git("merge-base", "--is-ancestor", BASE, "HEAD") != b"":
            raise ValueError("baseline ancestry not established")
        if git("diff", "--name-only", BASE, "--", "cfg-kernel", "cfg-adapters", "cfg-launcher", "pom.xml", "docs/sources/sources.lock.json"):
            raise ValueError("production differs from authorized baseline")
        work = json.loads((ROOT / "docs/work/active/WORK-CFG-028/work-item.json").read_text())
        if work["authorization"] != "implementation" or work["id"] != "WORK-CFG-028":
            raise ValueError("wrong implementation checkpoint")
        if work["checkpoint"] != "CP5_POST_AUDIT_HARNESS_REMEDIATION":
            raise ValueError("scope checker applies only to preparation, update in authorized Wave")
        sys.path.insert(0, str(ROOT / "scripts/harness"))
        from validate_cp5 import validate_cp5
        preparation_errors = validate_cp5(ROOT)
        if preparation_errors: raise ValueError("; ".join(preparation_errors))
        scopes = work["source_scope"] + work["test_scope"]
        # The aggregate PR includes historic AGENTS/ARCHITECTURE edits. This round does not.
        remediation_base = "feb79d59cc72d7dbc6269b7cacfb74db58f90867"
        focal_paths = git("diff", "--name-only", remediation_base).decode().splitlines()
        focal_paths += git("ls-files", "--others", "--exclude-standard").decode().splitlines()
        focal_scope = ["docs", "scripts/harness", "scripts/project", ".github/workflows/ci.yml", "MANIFEST.sha256"]
        if any(not any(p == s or p.startswith(s + "/") for s in focal_scope) for p in focal_paths):
            raise ValueError("path outside focal post-audit remediation scope")
        # This human review reopens only F. Preserve the other accepted machine contracts.
        f_base = "e053f14f8f5dc7b0b80b7bbbe015fde684522835"
        def baseline_json(path): return json.loads(git("show", f_base + ":" + path))
        audit_path = "docs/evals/cp5/post-audit-contracts.json"
        before = baseline_json(audit_path)
        after = json.loads((ROOT/audit_path).read_text())
        before["requirements"].pop("F"); after["requirements"].pop("F")
        if before != after: raise ValueError("F-only scope: other approved audit contracts changed")
        for path in ["docs/evals/cp5/metrics.json", "docs/evals/cp5/architecture.json",
                     "docs/evals/cp5/gate-plan.json", ".github/workflows/ci.yml",
                     "scripts/project/ci_source_receipt.py"]:
            if (ROOT/path).read_bytes() != git("show", f_base + ":" + path):
                raise ValueError("F-only scope: approved contract changed: " + path)
        probe_path = "docs/evals/cp5/probes.json"
        before = baseline_json(probe_path)
        after = json.loads((ROOT/probe_path).read_text())
        for value in [before,after]:
            value["probes"] = [p for p in value["probes"] if p["id"] != "S14"]
        if before != after: raise ValueError("F-only scope: non-F probes changed")
        # Bind the offline Java/POM inventory to Git, so editing both cannot hide a change.
        names = git("ls-tree", "-r", "--name-only", BASE).decode().splitlines()
        baseline_sources = {p:hashlib.sha256(git("show", BASE + ":" + p)).hexdigest()
                            for p in names if p.endswith(".java") or p.endswith("pom.xml")}
        inventory = json.loads((ROOT / "docs/evals/cp5/preparation-source-inventory.json").read_text())
        if inventory["files"] != baseline_sources:
            raise ValueError("preparation source inventory differs from immutable Git baseline")
        from check_analysis_architecture import check_preparation_air
        violations, findings = check_preparation_air(ROOT)
        if violations: raise ValueError("; ".join(violations))
        for finding in findings: print("[scope] " + finding)
        changed = set(git("diff", "--name-only", "-z", BASE).decode().split("\0"))
        changed |= set(git("ls-files", "-z", "--others", "--exclude-standard").decode().split("\0"))
        outside = sorted(p for p in changed if p and not any(p == scope or p.startswith(scope + "/") for scope in scopes))
        if outside: raise ValueError("paths outside CP5 harness scope: " + repr(outside))
        lock = json.loads((ROOT / "docs/sources/sources.lock.json").read_text())
        if lock["air_java"]["commit"] != AIR or lock["analysis_ir"]["commit"] != IR:
            raise ValueError("authorized pin changed")
        binding = lock["analysis_ir"]["json_binding"]
        if (binding["version"], binding["targets_air_version"], binding["maturity"], binding["implemented_in_analysis_cfg"]) != ("1.0.0", "2.0.0", "DRAFT", False):
            raise ValueError("binding authority changed")
        if hashlib.sha256((ROOT / FIXTURE).read_bytes()).hexdigest() != FIXTURE_HASH:
            raise ValueError("upstream AIR fixture provenance drift")
        from check_scalar_contract import verify_scalar_contract
        verify_scalar_contract(ROOT)
        expected = manifest()
        if args.update_manifest: (ROOT / "MANIFEST.sha256").write_bytes(expected)
        elif (ROOT / "MANIFEST.sha256").read_bytes() != expected:
            raise ValueError("delivery manifest mismatch; review diff before --update-manifest")
        git("diff", "--check")
        print("[scope/manifest] PASS: CP4 baseline, unchanged Java/POM/product, authorized harness paths/pins/DRAFT, AIR fixture hash and exact delivery manifest")
        return 0
    except (GateFailure, ValueError, OSError, subprocess.CalledProcessError) as exc:
        print("[scope/manifest] FAIL: " + str(exc), file=sys.stderr); return 1

if __name__ == "__main__": sys.exit(main())
