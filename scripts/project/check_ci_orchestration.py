#!/usr/bin/env python3
"""Guard the deliberately fixed automatic/manual workflow boundary, without path filtering."""
import re
from pathlib import Path
from run_validation import plan

FAST_STAGES = ('air-export', 'air-pin', 'air-build', 'air-provenance', 'scope-before',
               'docs-harness', 'fast-tests-and-architecture', 'dependency-wire-oracle', 'scope-after')
FULL_STAGES = ('air-export', 'air-pin', 'air-build', 'air-provenance', 'scope-before',
               'historical-repository', 'historical-producers', 'full-w1-w5', 'w1d-producers',
               'w1d', 'w1d-real-e2e-twice', 'w1d-mutations', 'scope-after')


def require(condition, message):
    if not condition:
        raise ValueError('CI orchestration: ' + message)


def verify(root):
    import json
    lock = json.loads((root / 'docs/sources/sources.lock.json').read_text())
    for name, events, entry, artifact in (
            ('ci', {'push', 'pull_request'}, 'check-pr-fast.sh', 'fast-ci-evidence'),
            ('qualification', {'workflow_dispatch'}, 'check-qualification.sh', 'full-qualification-evidence')):
        text = (root / ('.github/workflows/' + name + '.yml')).read_text()
        triggers = re.search(r'(?ms)^on:\n(.*?)(?=^\S)', text)
        require(triggers is not None, name + ' explicit triggers missing')
        require(set(re.findall(r'^  ([a-z_]+):\s*$', triggers[1], re.M)) == events, name + ' event drift')
        require(not re.search(r'paths(?:-ignore)?:|branches(?:-ignore)?:', text), 'no path/branch filtering')
        require('bash scripts/harness/' + entry + ' --work ' in text, name + ' shared entry missing')
        require('name: ' + artifact in text and 'if: always()' in text and 'actions/upload-artifact@v4' in text,
                name + ' evidence preservation missing')
        require('distribution: temurin' in text and 'java-version: "21"' in text
                and 'python-version: \'3.12\'' in text and 'fetch-depth: 0' in text, name + ' tools/history')
        require(text.count('repository: Gustavo2358/air-java') == 1 and text.count('ref: ' + lock['air_java']['commit']) == 1,
                name + ' pinned AIR')
        require('mvn ' not in text, 'build/gate logic belongs in shared scripts')
        if name == 'ci':
            for forbidden in ('proleap-poc', 'cobol-lower', 'check-full.sh', 'check-qualification.sh',
                              'prepare_w', 'e2e_w', 'challenge_', 'check_cp5_gate.py', 'performance'):
                require(forbidden not in text, 'heavy step in automatic CI: ' + forbidden)
        else:
            for project, field in (('proleap_poc', 'main_commit'), ('cobol_lower', 'commit')):
                require(text.count('ref: ' + lock[project][field]) == 1, 'full pinned producers')
    work, repo = Path('/qualification-work'), Path('/qualification-m2')
    fast = plan('FAST_CI', work, repo, root)
    local = plan('LOCAL_QUALIFICATION', work, repo, root)
    remote = plan('REMOTE_MANUAL_QUALIFICATION', work, repo, root)
    require(tuple(s['name'] for s in fast) == FAST_STAGES, 'fixed fast phase inventory')
    require(tuple(s['name'] for s in local) == FULL_STAGES, 'full phase inventory')
    require(local == remote, 'local and manual qualification must execute identical commands')
    require(local[7]['argv'] == ['bash', 'scripts/harness/check-full.sh'], 'full must execute the canonical full gate')
    for index, script in ((6, 'prepare_w5_producers.py'), (8, 'prepare_w1d_producers.py'),
                          (9, 'check_w1d.py'), (10, 'e2e_w1d.py'), (11, 'challenge_w1d.py')):
        require(local[index]['argv'][2] == 'scripts/project/' + script, 'full gate command: ' + script)
    require(local[6]['env']['MAVEN_OPTS'].endswith('/build/w5-m2'), 'separate historical Maven repository')
    require('--test-profile' in fast[6]['argv'] and fast[6]['argv'][-1] == 'fast', 'fast Maven selection')
    full = (root / 'scripts/harness/run_gate.py').read_text()
    require("('fast', 'architecture', 'semantic', 'performance', 'integration')" in full, 'full gate coverage')
    require('for wave in 1 2 3 4 5' in (root / 'scripts/project/check-performance.sh').read_text(), 'W1-W5 probes')
    print('[orchestration] PASS: automatic=FAST_CI; full=explicit, same local/manual plan', flush=True)


if __name__ == '__main__':
    verify(Path(__file__).resolve().parents[2])
