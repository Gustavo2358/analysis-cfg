#!/usr/bin/env python3
"""F2 synthetic dependency CLI/wire oracle; run after LeadingDollarProductTest."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import time
from dependency_wire import read

ROOT = Path(__file__).resolve().parents[2]
EXPECTED = {
    'literal-dollar': '$PROGA', 'computed-dollar': '$PROGA',
    'regional-dollar': '$PROGA', 'eight-characters': '$ABCDEFG',
    'computed-control': 'PROGA', 'literal-control': 'PROGA',
    'middle-dollar': 'A$PROG', 'underscore': '_PROGA',
    **{'negative-' + str(i): None for i in range(6)}, 'literal-padding': None,
}
CHANGED = {'literal-dollar', 'computed-dollar', 'eight-characters'}


def require(condition, reason):
    if not condition:
        raise AssertionError(reason)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixtures', type=Path, default=ROOT / 'analysis-adapters/target/ep-r2-f2')
    parser.add_argument('--before', type=Path, help='preserved W0 synthetic-before directory; never real input')
    parser.add_argument('--maven-repo', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    modules = ['cfg-kernel', 'cfg-adapters', 'analysis-kernel', 'analysis-values',
               'analysis-dataflow', 'analysis-dependencies', 'analysis-adapters', 'analysis-launcher']
    jars = [args.maven_repo / p for p in [
        'io/github/gustavo2358/air-java/0.1.0-SNAPSHOT/air-java-0.1.0-SNAPSHOT.jar',
        'io/github/gustavo2358/air-json/0.1.0-SNAPSHOT/air-json-0.1.0-SNAPSHOT.jar',
        'com/fasterxml/jackson/core/jackson-core/2.22.2/jackson-core-2.22.2.jar',
        'com/fasterxml/jackson/core/jackson-databind/2.22.2/jackson-databind-2.22.2.jar',
        'com/fasterxml/jackson/core/jackson-annotations/2.22/jackson-annotations-2.22.jar']]
    require(all(p.is_file() for p in jars), 'missing pinned runtime prerequisite')
    cp = ':'.join(map(str, [*[ROOT / m / 'target/classes' for m in modules], *jars]))
    require({p.name.removesuffix('.air.json') for p in args.fixtures.glob('*.air.json')} == set(EXPECTED),
            'exact synthetic fixture inventory')
    records = []
    for case, expected in EXPECTED.items():
        air = args.fixtures / (case + '.air.json')
        memory_path = args.fixtures / (case + '.dependencies.json')
        memory = read(memory_path)
        site = memory['sites'][0]
        require(len(memory['sites']) == 1, case + ': one site')
        require(site['nameProfile'] == 'cobol-zos-dynamic-call-minimal@1', case + ': profile drift')
        require([c['referenceName'] for c in site['candidates']] == ([] if expected is None else [expected]), case + ': candidates')
        require([e['candidate']['referenceName'] for e in memory['edges']] == ([] if expected is None else [expected]), case + ': edges')
        require(site['rawCandidates'][0]['supports'] and site['provenance'], case + ': raw support/provenance')
        for candidate in site['candidates']:
            require(candidate['rawValue'] == site['rawCandidates'][0]['rawValue'], case + ': raw spelling')
            require(candidate['supports'] == site['rawCandidates'][0]['supports'], case + ': support association')
        require(site['interpretationUnknownRemainder'] and site['effectiveUnknownRemainder'], case + ': UnknownName stays open')
        target = args.output / (case + '.dependencies.json')
        command = ['java', '-Xmx256m', '-cp', cp, 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies', str(air), str(target)]
        start = time.monotonic()
        with (args.output / (case + '.log')).open('x') as log:
            subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=60)
        read(target)
        require(target.read_bytes() == memory_path.read_bytes(), case + ': CLI/memory bytes differ')
        baseline = 'NOT_REQUESTED'
        if args.before and case != 'regional-dollar':
            before_path = args.before / memory_path.name
            before = read(before_path)
            require((args.before / air.name).read_bytes() == air.read_bytes(), case + ': input AIR changed')
            if case in CHANGED:
                old, new = copy.deepcopy(before), copy.deepcopy(memory)
                for document in (old, new):
                    document.pop('edges')
                    document['sites'][0].pop('candidates')
                    document['sites'][0].pop('targetStatus')
                require(old == new, case + ': changed more than candidate/status/edges')
                require(before['sites'][0]['candidates'] == [] and before['sites'][0]['targetStatus'] == 'OPEN_TARGET', case + ': baseline RED')
                require(site['targetStatus'] == 'RESOLVED_CANDIDATES', case + ': GREEN status')
                baseline = 'ONLY_CANDIDATE_STATUS_EDGES_CHANGED'
            else:
                require(before_path.read_bytes() == memory_path.read_bytes(), case + ': unchanged control differs')
                baseline = 'BYTE_IDENTICAL'
        records.append(dict(case=case, expected=expected, baselineComparison=baseline, cliMemory='BYTE_IDENTICAL',
                            profile=site['nameProfile'], seconds=time.monotonic()-start,
                            airSha256=hashlib.sha256(air.read_bytes()).hexdigest(), command=command))
        print(case, 'PASS', baseline, flush=True)
    source = ROOT / 'analysis-dependencies/src/main/java/io/github/gustavo2358/analysis/dependencies/CallNameInterpreter.java'
    result = dict(status='PASS', profile='cobol-zos-dynamic-call-minimal@1',
                  revision=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                  productionSha256=hashlib.sha256(source.read_bytes()).hexdigest(), cases=records, realCase='NOT_RUN')
    (args.output / 'results.json').write_text(json.dumps(result, indent=2) + '\n')


if __name__ == '__main__':
    main()
