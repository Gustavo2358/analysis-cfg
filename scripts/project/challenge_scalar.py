#!/usr/bin/env python3
"""Checkpoint 4D falsifications; compile product mutants and restore every original byte."""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADAPTER = 'cfg-adapters/src/main/java/io/github/gustavo2358/analysis/cfg/adapters/'
TEST = 'cfg-adapters/src/test/java/io/github/gustavo2358/analysis/cfg/adapters/'
CORE = 'cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/domain/CoreCfgProjection.java'
OPTIONS = 'cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/application/BuildOptions.java'
NEW = 'ce530a7e17ab12b23c48f29425f503ff920b09fb'
OLD = 'b78f4068d8a479f48eb048b8d76fa60a0997dc4a'


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence', type=Path, required=True)
    args = parser.parse_args()
    evidence = args.evidence.resolve()
    if evidence.is_relative_to(ROOT): parser.error('write raw challenge evidence outside tracked source')
    evidence.mkdir(parents=True, exist_ok=True)
    guard = [sys.executable, '-B', 'scripts/project/check_scalar_contract.py']
    integration = ['bash', 'scripts/harness/check-integration.sh']
    tests = ['mvn', '-B', '-ntp', '-pl', 'cfg-adapters', '-am',
             '-Dtest=ScalarAssignTest,TransportTest,SemanticInterpreterRegistryTest', 'clean', 'test']
    shape = [sys.executable, '-B', '-c', "import sys;from pathlib import Path;sys.path.insert(0,'scripts/project');from check_architecture import verify_project_shape;verify_project_shape(Path.cwd())"]
    cases = [
        ('01-ci-old', '.github/workflows/ci.yml', NEW, OLD, guard, 'source-lock SHA'),
        ('02-lock-ci-drift', 'docs/sources/sources.lock.json', '"commit": "' + NEW + '"', '"commit": "' + OLD + '"', guard, 'source-lock SHA'),
        ('03-fixture-drift', 'cfg-adapters/src/test/resources/air/scalar-assign.canonical.json', '"PROGA"', '"PROGB"', guard, 'not byte-identical'),
        ('04-integration-ignores-payload', TEST + 'ScalarAssignTest.java',
         '@Test void sequencePayloadAndObjectCellIdentitySurviveRealReaderAndBuild()',
         'void sequencePayloadAndObjectCellIdentitySurviveRealReaderAndBuild()', integration, 'method inventory missing'),
        ('05-sequence-loses-assign', CORE, 'new CfgNodeId(publication.id(), nodes.size()), sequence);',
         'new CfgNodeId(publication.id(), nodes.size()), new Sequence(sequence.label(), List.of(), sequence.terminator(), sequence.origin()));', tests, 'Failures:'),
        ('06-node-per-assign', CORE, 'nodes.add(node);',
         'nodes.add(node);\n                for (var instruction : sequence.instructions()) if (instruction instanceof Operations.Assign) nodes.add(new CfgNode.SequenceNode(new CfgNodeId(publication.id(), nodes.size()), sequence));', tests, 'Failures:'),
        ('07-edge-depends-on-assign', CORE,
         'transitions.add(new CfgTransition(from, exit.id(), CfgTransition.Kind.RETURN, entry.id()));',
         'if (sequence.instructions().stream().anyMatch(Operations.Assign.class::isInstance)) transitions.add(new CfgTransition(from, exit.id(), CfgTransition.Kind.RETURN, entry.id()));', tests, 'Failures:'),
        ('08-no-return-edge', CORE,
         'transitions.add(new CfgTransition(from, exit.id(), CfgTransition.Kind.RETURN, entry.id()));',
         '// mutant: Return emits no transition', tests, 'Failures:'),
        ('09-default-strict', OPTIONS, 'this(validation, ProjectionPolicy.KNOWN_SUBSET);',
         'this(validation, ProjectionPolicy.STRICT);', tests, 'Failures:'),
        ('10-partial-promoted', ADAPTER + 'CfgJsonWriter.java', 'case PARTIAL -> "PARTIAL";',
         'case PARTIAL -> "COMPLETE";', tests, 'Failures:'),
        ('11-local-reader', ADAPTER + 'AirJsonFileReader.java', 'return codec.decode(bytes);',
         'return java.util.regex.Pattern.compile("publication").matcher(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).find() ? null : null;', shape, 'shared AirJson.decode'),
        ('12-wire-duplicates-air', ADAPTER + 'CfgJsonWriter.java', 'out.raw("]}");\n        return out.bytes();',
         'out.raw("],\\\"instructions\\\":"); out.string(graph.publication().units().getFirst().sequences().getFirst().instructions().toString()); out.raw("}");\n        return out.bytes();', tests, 'Failures:'),
        ('13-goback-suite-removed', TEST + 'TransportTest.java', None, None, integration, 'integration reports missing'),
        ('14-coordinated-old-pin', None, None, None, guard, 'approved 4B merge'),
    ]
    paths = {c[1] for c in cases if c[1]} | {'.github/workflows/ci.yml', 'docs/sources/sources.lock.json'}
    originals = {path: (ROOT / path).read_bytes() for path in paths}
    results = []
    try:
        for name, relative, old, new, command, expected in cases:
            try:
                if name == '14-coordinated-old-pin':
                    for path in ('.github/workflows/ci.yml', 'docs/sources/sources.lock.json'):
                        (ROOT / path).write_bytes(originals[path].replace(NEW.encode(), OLD.encode()))
                elif old is None: (ROOT / relative).unlink()
                else:
                    data = originals[relative].decode()
                    if old not in data: raise RuntimeError('missing mutation anchor: ' + name)
                    (ROOT / relative).write_text(data.replace(old, new))
                # The source-shape guard is intentionally fast, but first prove this replacement compiles.
                compile_exit = None
                if command == shape:
                    with (evidence / (name + '-compile.log')).open('w') as log:
                        compile_exit = subprocess.run(['mvn', '-B', '-ntp', '-DskipTests', 'clean', 'test'],
                                                      cwd=ROOT, stdout=log, stderr=subprocess.STDOUT).returncode
                    if compile_exit != 0: raise RuntimeError('mutant does not compile: ' + name)
                with (evidence / (name + '.log')).open('w') as log:
                    result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
                output = (evidence / (name + '.log')).read_text()
                detected = result.returncode == 1 and expected in output and 'COMPILATION ERROR' not in output
                if command == tests:
                    detected = detected and bool(re.search(r'Tests run: \d+, Failures: [1-9]\d*, Errors: 0, Skipped: 0', output))
                if command == integration:
                    detected = detected and 'BUILD SUCCESS' in output
                results.append(dict(challenge=name, command=command, exit=result.returncode,
                                    compile_exit=compile_exit, expected_detector=expected, detected=detected,
                                    log_sha256=hashlib.sha256((evidence/(name+'.log')).read_bytes()).hexdigest()))
                print(name + ': ' + ('RED' if detected else 'NOT PROVED'), flush=True)
                if not detected: raise RuntimeError('intended falsification not observed: ' + name)
            finally:
                for path, data in originals.items():
                    (ROOT / path).write_bytes(data)
                    if (ROOT / path).read_bytes() != data: raise RuntimeError('restore failed: ' + path)
        return 0
    finally:
        (evidence / 'results.json').write_text(json.dumps(dict(challenges=results,
            restored_sha256={p:hashlib.sha256(b).hexdigest() for p,b in sorted(originals.items())},
            restored_byte_identical=all((ROOT/p).read_bytes()==b for p,b in originals.items())), indent=2)+'\n')
        print('Restored all original source/fixture bytes; second GREEN required.', flush=True)

if __name__ == '__main__': sys.exit(main())
