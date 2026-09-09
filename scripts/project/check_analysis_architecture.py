#!/usr/bin/env python3
"""Direct AIR Maven dependencies now; reusable CP5 package checks for future bytecode hooks."""
from __future__ import annotations
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}


def direct_dependencies(pom: Path) -> set[tuple[str, str, str]]:
    tree = ET.parse(pom).getroot()
    return {(d.findtext('m:groupId', '', NS), d.findtext('m:artifactId', '', NS),
             d.findtext('m:scope', 'compile', NS))
            for d in tree.findall('m:dependencies/m:dependency', NS)}


def check_direct_air(root: Path) -> list[str]:
    """Import/qualified-name check complements, never replaces, effective Maven/jdeps."""
    errors = []
    for pom in sorted(root.rglob('pom.xml')):
        if any(p in {'target','.git','.cache','.harness-results'} for p in pom.relative_to(root).parts):
            continue
        source = pom.parent / 'src/main/java'
        uses_air = any(re.search(r'\bio\.github\.gustavo2358\.air\.', f.read_text())
                       for f in source.rglob('*.java')) if source.exists() else False
        if uses_air and ('io.github.gustavo2358','air-java','compile') not in direct_dependencies(pom):
            errors.append('AIR imports require direct compile air-java dependency: ' + str(pom.relative_to(root)))
    return errors


def forbidden_dependencies(role: str, dependencies: list[str], rules: dict) -> list[str]:
    """Input must come from javap descriptors/jdeps, not a hand-written PASS receipt."""
    denied = rules['package_rules'][role]['forbidden']
    return [dep for dep in dependencies if any(token in dep.replace('/', '.') for token in denied)]


def check_preparation_air(root: Path) -> tuple[list[str], list[str]]:
    errors = check_direct_air(root)
    baseline_issue = 'AIR imports require direct compile air-java dependency: cfg-launcher/pom.xml'
    findings = []
    inventory = json.loads((root / 'docs/evals/cp5/preparation-source-inventory.json').read_text())
    launcher = {p:h for p,h in inventory['files'].items() if p.startswith('cfg-launcher/')}
    actual = {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest()
              for p in (root / 'cfg-launcher').rglob('*')
              if p.is_file() and (p.suffix == '.java' or p.name == 'pom.xml') and 'target' not in p.parts}
    if baseline_issue in errors and launcher and launcher == actual:
        errors.remove(baseline_issue)
        findings.append('CP5-F01 OPEN_FOR_HUMAN_REVIEW: unchanged baseline cfg-launcher uses transitive AIR; '
                        'direct-dependency compliance is NOT claimed; POM correction requires Wave scope')
    return errors, findings


def main() -> int:
    try:
        errors, findings = check_preparation_air(ROOT)
        for finding in findings: print('[analysis-architecture] ' + finding)
        if errors:
            for error in errors: print('[analysis-architecture] FAIL: ' + error, file=sys.stderr)
            return 1
        print('[analysis-architecture] PASS: preparation dependency guard, except explicit baseline finding; '
              'future CP5 bytecode gates NOT_AVAILABLE_UNTIL_IMPLEMENTED')
        return 0
    except (OSError, ET.ParseError) as exc:
        print('[analysis-architecture] FAIL: ' + str(exc), file=sys.stderr)
        return 1

if __name__ == '__main__':
    sys.exit(main())
