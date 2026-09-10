#!/usr/bin/env python3
"""Check CP5 Wave 5 diff scope, fixed upstream authority and a complete tracked/untracked delivery manifest."""
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
    lines = ["# SHA-256 da entrega CP5 W5; inclui arquivos versionáveis, exclui este manifesto e artefatos ignorados."]
    lines += [hashlib.sha256((ROOT / path).read_bytes()).hexdigest() + "  " + path for path in files()]
    return ("\n".join(lines) + "\n").encode()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--update-manifest", action="store_true")
    args = parser.parse_args()
    try:
        if git("merge-base", "--is-ancestor", BASE, "HEAD") != b"":
            raise ValueError("baseline ancestry not established")
        approved = "21d65d08512f1fb8a945009c2919946a61566eed"
        if git("diff", "--name-only", approved, "--", "cfg-kernel", "cfg-adapters", "cfg-launcher/src", "docs/sources/sources.lock.json"):
            raise ValueError("unapproved product/pin changes outside W5")
        work = json.loads((ROOT / "docs/work/active/WORK-CFG-028/work-item.json").read_text())
        if work["authorization"] != "implementation" or work["id"] != "WORK-CFG-028":
            raise ValueError("wrong implementation checkpoint")
        if work["checkpoint"] != "WAVE_5":
            raise ValueError("scope checker authorizes WAVE_5 only")
        sys.path.insert(0, str(ROOT / "scripts/harness"))
        from validate_cp5 import validate_cp5
        preparation_errors = validate_cp5(ROOT)
        if preparation_errors: raise ValueError("; ".join(preparation_errors))
        scopes = work["source_scope"] + work["test_scope"]
        # The aggregate PR includes historic AGENTS edits; W3 refreshes the architecture overview.
        remediation_base = approved
        focal_paths = git("diff", "--name-only", remediation_base).decode().splitlines()
        focal_paths += git("ls-files", "--others", "--exclude-standard").decode().splitlines()
        focal_scope = ["ARCHITECTURE.md", "docs", "scripts/harness", "scripts/project", ".github/workflows/ci.yml", "MANIFEST.sha256", "pom.xml", "cfg-launcher/pom.xml", "analysis-kernel", "analysis-values", "analysis-dataflow", "analysis-adapters", "analysis-launcher"]
        if any(not any(p == s or p.startswith(s + "/") for s in focal_scope) for p in focal_paths):
            raise ValueError("path outside focal post-audit remediation scope")
        def baseline_json(path): return json.loads(git("show", remediation_base + ":" + path))
        audit_path = "docs/evals/cp5/post-audit-contracts.json"
        before = baseline_json(audit_path)
        after = json.loads((ROOT/audit_path).read_text())
        for value in [before, after]:
            for row in value["requirements"].values():
                row.pop("wave_hooks",None)
                if value is after:
                    if row.get('runtime_status')!='IMPLEMENTED_BY_WAVE_HOOKS':raise ValueError('W5 audit runtime requires complete wave hooks')
                    row['runtime_status']='NOT_AVAILABLE_UNTIL_IMPLEMENTED'
        if before != after: raise ValueError("W4 scope: approved audit semantics changed")
        for path in ["scripts/project/ci_source_receipt.py", "docs/evals/cp5/core-size-review.json"]:
            if (ROOT/path).read_bytes() != git("show", remediation_base + ":" + path):
                raise ValueError("W4 scope: future-wave contract/CI receipt changed: " + path)
        # Historical reviews/evidence are append-only, including previous await-review snapshots.
        life = json.loads((ROOT/"docs/work/cp5-lifecycle.json").read_text())
        old_life = baseline_json("docs/work/cp5-lifecycle.json")
        if life["review_history"][:len(old_life["review_history"])] != old_life["review_history"]:
            raise ValueError("historical review rewritten")
        for key in ["last_human_review", "last_human_approval", "audit_remediation", "f_correction"]:
            if life[key] != old_life[key]: raise ValueError("historical decision rewritten: " + key)
        historical = git("diff", "--name-only", remediation_base, "--", "docs/work/evidence").decode().splitlines()
        if any(not p.startswith("docs/work/evidence/WORK-CFG-028/wave-5/") for p in historical):
            raise ValueError("historical evidence changed")
        from check_w4 import verify_foundation
        verify_foundation(ROOT)
        from check_w4 import verify_focal_preservation
        verify_focal_preservation(ROOT)
        for name in ['result-contract.json','result-review.json','phase-review.json']:
            if (ROOT/'docs/evals/cp5/history'/name).read_bytes()!=git('show',approved+':docs/evals/cp5/'+name):
                raise ValueError('W5 altered historical wire design: '+name)
        old_sources = baseline_json('docs/evals/cp5/w4-source-inventory.json')['files']
        from check_w5 import original_pom
        for path,digest in old_sources.items():
            baseline_data=git('show',approved+':'+path)
            if hashlib.sha256(baseline_data).hexdigest()!=digest:raise ValueError('W4 baseline differs from immutable reviewed Git: '+path)
            data=(ROOT/path).read_bytes()
            if path=='pom.xml':data=original_pom(data)
            if hashlib.sha256(data).hexdigest()!=digest:raise ValueError('W5 changed approved W1–W4 source/test/POM: '+path)
        # Bind the offline Java/POM inventory to Git, so editing both cannot hide a change.
        names = git("ls-tree", "-r", "--name-only", BASE).decode().splitlines()
        baseline_sources = {p:hashlib.sha256(git("show", BASE + ":" + p)).hexdigest()
                            for p in names if p.endswith(".java") or p.endswith("pom.xml")}
        inventory = json.loads((ROOT / "docs/evals/cp5/preparation-source-inventory.json").read_text())
        if inventory["files"] != baseline_sources:
            raise ValueError("preparation source inventory differs from immutable Git baseline")
        from check_analysis_architecture import check_direct_air
        violations = check_direct_air(ROOT)
        if violations: raise ValueError("; ".join(violations))
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
        print("[scope/manifest] PASS: CP4 + approved W1 baseline, unchanged legacy Java/pins, W5 production scope/DRAFT, AIR fixture hash and exact delivery manifest")
        return 0
    except (GateFailure, ValueError, OSError, subprocess.CalledProcessError) as exc:
        print("[scope/manifest] FAIL: " + str(exc), file=sys.stderr); return 1

if __name__ == "__main__": sys.exit(main())
