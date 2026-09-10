#!/usr/bin/env python3
"""Direct AIR Maven dependencies now; reusable CP5 package checks for future bytecode hooks."""
from __future__ import annotations
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



def main() -> int:
    try:
        errors = check_direct_air(ROOT)
        if errors:
            for error in errors: print('[analysis-architecture] FAIL: ' + error, file=sys.stderr)
            return 1
        print('[analysis-architecture] PASS: direct AIR dependencies; no preparation exception')
        return 0
    except (OSError, ET.ParseError) as exc:
        print('[analysis-architecture] FAIL: ' + str(exc), file=sys.stderr)
        return 1

if __name__ == '__main__':
    sys.exit(main())
