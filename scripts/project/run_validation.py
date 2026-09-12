#!/usr/bin/env python3
"""One execution authority for fixed Fast CI and explicit full qualification.

All commands, exits, durations and artifacts are retained, including failed runs.
No gate can be selected away from qualification. Build exports never mutate siblings.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import time

ROOT = Path(__file__).resolve().parents[2]
BASE = 'c39a92f930b1c693857a0b30a1f5155f3f81520c'
REMEDIATION_BASE = '1ab16bdeae8d8af23e723d0b239ba191a695764a'
AUTHORITIES = ('FAST_CI', 'LOCAL_QUALIFICATION', 'REMOTE_MANUAL_QUALIFICATION')


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()


def source(root):
    return dict(commit=git(root, 'rev-parse', 'HEAD'), tree=git(root, 'rev-parse', 'HEAD^{tree}'),
                status=git(root, 'status', '--porcelain'), branch=git(root, 'branch', '--show-current'))


def plan(authority, work, repo, root=ROOT):
    """The same ordered full plan runs locally and on workflow_dispatch."""
    lock = json.loads((root / 'docs/sources/sources.lock.json').read_text())
    air = work / 'build/air-java'
    evidence = work / 'evidence'
    python = [sys.executable, '-B']
    def script(name, *args):
        return python + ['scripts/project/' + name, *map(str, args)]
    def step(name, argv, cwd=root, env=None):
        return dict(name=name, argv=list(map(str, argv)), cwd=str(cwd), env=env or {})
    stages = [
        step('air-export', ['git', 'clone', '--shared', '--no-checkout', root.parent / 'air-java', air]),
        step('air-pin', ['git', 'checkout', '--detach', lock['air_java']['commit']], air),
        step('air-build', ['mvn', '-B', '-ntp', 'clean', 'install'], air),
        step('air-provenance', script('record_air_dependency.py', '--checkout', air, '--built-source', air,
                                     '--maven-repo', repo, '--output', evidence / 'air-dependency-receipt.json')),
        step('scope-before', script('check_scope.py')),
    ]
    if authority == 'FAST_CI':
        stages += [
            step('docs-harness', ['bash', 'scripts/harness/check-fast.sh']),
            step('fast-tests-and-architecture', script('check_architecture.py', '--test-profile', 'fast')),
            step('dependency-wire-oracle', script('test_dependency_wire.py')),
        ]
    else:
        historical_repo = work / 'build/w5-m2'
        stages += [
            step('historical-repository', python + ['-c', 'import shutil,sys; shutil.copytree(sys.argv[1],sys.argv[2])', str(repo), str(historical_repo)]),
            step('historical-producers', script('prepare_w5_producers.py', '--work', work / 'build/w5-producers'),
                 env={'MAVEN_OPTS': '-Dmaven.repo.local=' + str(historical_repo)}),
            # check-full already includes docs, architecture, semantics, all W1-W5 probes and real W5 E2E.
            step('full-w1-w5', ['bash', 'scripts/harness/check-full.sh'],
                 env={'W5_PRODUCERS': str(work / 'build/w5-producers/producers.json')}),
            step('w1d-producers', script('prepare_w1d_producers.py', '--work', work / 'build/w1d-producers')),
            step('w1d', script('check_w1d.py')),
            step('w1d-real-e2e-twice', script('e2e_w1d.py', '--work', evidence / 'w1d-e2e',
                                             '--producers', work / 'build/w1d-producers/producers.json')),
            step('w1d-mutations', script('challenge_w1d.py', '--work', evidence / 'w1d-mutations')),
        ]
    return stages + [step('scope-after', script('check_scope.py'))]


def capture(root, work, name, before):
    """Preserve reports before another gate cleans them, plus outputs written by this phase."""
    evidence = work / 'evidence'
    artifacts = []
    reports = sorted(root.glob('*/target/surefire-reports/TEST-*.xml'))
    reports = [p for p in reports if before.get(str(p)) != p.stat().st_mtime_ns]
    if reports:
        target = evidence / (name + '-reports.tar.gz')
        with tarfile.open(target, 'w:gz') as archive:
            for path in reports:
                archive.add(path, arcname=str(path.relative_to(root)))
        artifacts.append(target)
    harness = root / '.harness-results'
    for path in sorted(harness.rglob('*')):
        if (not path.is_file() or path.is_symlink() or work == path or work in path.parents
                or before.get(str(path)) == path.stat().st_mtime_ns):
            continue
        target = evidence / 'gate-outputs' / name / path.relative_to(harness)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
        artifacts.append(target)
    for producer in ('w5-producers', 'w1d-producers'):
        build = work / 'build' / producer
        for path in [build / 'producers.json', *build.glob('*build.log')]:
            if path.is_file():
                target = evidence / producer / path.name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, target)
    return [str(p.relative_to(evidence)) for p in artifacts]


def mtimes(root, work):
    paths = list(root.glob('*/target/surefire-reports/TEST-*.xml'))
    paths += list((root / '.harness-results').rglob('*'))
    return {str(p): p.stat().st_mtime_ns for p in paths
            if p.is_file() and not p.is_symlink() and work not in p.parents}


def execute(authority, work, repo, root=ROOT):
    work.mkdir(parents=True, exist_ok=False)
    (work / 'build').mkdir()
    evidence = work / 'evidence'
    evidence.mkdir()
    started = time.monotonic()
    receipt = dict(schema='analysis-cfg-validation-receipt@1', authority=authority, result='IN_PROGRESS',
                   source=source(root), baseline=BASE, remediationBaseline=REMEDIATION_BASE,
                   upstreamPins=json.loads((root / 'docs/sources/sources.lock.json').read_text()),
                   github={key: os.environ.get(key) for key in
                           ('GITHUB_EVENT_NAME', 'GITHUB_SHA', 'GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT')},
                   stages=[], mavenRepository=str(repo))
    receipt['siblingsBefore'] = {p.name: source(p) for p in
                                 (root.parent / n for n in ('air-java', 'proleap-poc', 'cobol-lower', 'analysis-ir'))
                                 if (p / '.git').exists()}
    commands = plan(authority, work, repo, root)
    receipt['stages'] = [dict(**s, result='NOT_RUN', exitCode=None) for s in commands]
    path = evidence / 'receipt.json'
    def save():
        path.write_text(json.dumps(receipt, indent=2) + '\n')
    save()
    code = 1
    try:
        if authority != 'FAST_CI' and receipt['source']['status']:
            raise ValueError('full qualification requires a clean committed source tree')
        if authority == 'REMOTE_MANUAL_QUALIFICATION' and os.environ.get('GITHUB_EVENT_NAME') != 'workflow_dispatch':
            raise ValueError('remote qualification requires workflow_dispatch')
        for stage in receipt['stages']:
            print('[' + authority + '] RUN ' + stage['name'], flush=True)
            before = mtimes(root, work)
            tick = time.monotonic()
            env = dict(os.environ, MAVEN_OPTS='-Dmaven.repo.local=' + str(repo))
            env.update(stage['env'])
            log = evidence / (stage['name'] + '.log')
            stage['result'] = 'RUNNING'
            save()
            with log.open('wb') as output:
                process = subprocess.run(stage['argv'], cwd=stage['cwd'], env=env,
                                         stdout=output, stderr=subprocess.STDOUT)
            stage.update(exitCode=process.returncode, durationSeconds=round(time.monotonic()-tick, 3),
                         log=log.name, logSha256=digest(log), result='PASS' if process.returncode == 0 else 'FAIL')
            stage['artifacts'] = capture(root, work, stage['name'], before)
            save()
            print('[' + authority + '] ' + stage['result'] + ' ' + stage['name']
                  + ' (' + str(stage['durationSeconds']) + 's); log=' + str(log), flush=True)
            if process.returncode:
                raise ValueError('gate failed: ' + stage['name'] + '; exit=' + str(process.returncode))
        code = 0
    except (ValueError, OSError, subprocess.CalledProcessError, KeyboardInterrupt) as failure:
        receipt['failure'] = str(failure)
        for stage in receipt['stages']:
            if stage['result'] == 'RUNNING':
                stage.update(result='ERROR', error=str(failure))
        print('[' + authority + '] FAIL: ' + str(failure), file=sys.stderr, flush=True)
    finally:
        receipt['sourceAfter'] = source(root)
        receipt['siblingsAfter'] = {name: source(root.parent / name) for name in receipt['siblingsBefore']}
        if receipt['sourceAfter'] != receipt['source'] or receipt['siblingsAfter'] != receipt['siblingsBefore']:
            receipt['restorationFailure'] = 'source/sibling identity or status changed'
            code = 1
        receipt['result'] = 'PASS' if code == 0 else 'FAIL'
        receipt['durationSeconds'] = round(time.monotonic()-started, 3)
        receipt['artifactSha256'] = {str(p.relative_to(evidence)): digest(p) for p in sorted(evidence.rglob('*'))
                                     if p.is_file() and p != path and not p.is_symlink()}
        save()
        print('[' + authority + '] ' + receipt['result'] + ': ' + str(path), flush=True)
    return code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('authority', choices=AUTHORITIES)
    parser.add_argument('--work', type=Path, required=True, help='fresh directory; never reuse/overwrite a receipt')
    parser.add_argument('--maven-repo', type=Path, required=True, help='isolated AIR/W1D Maven repository/cache')
    args = parser.parse_args()
    return execute(args.authority, args.work.resolve(), args.maven_repo.resolve())


if __name__ == '__main__':
    sys.exit(main())
