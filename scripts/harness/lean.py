#!/usr/bin/env python3
"""Lean lifecycle and CI routing. Git/PR/tests are the development record."""
import argparse
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import time

import yaml

ROOT = Path(__file__).resolve().parents[2]
STATUSES = {'TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE'}


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()


def require_local():
    if any(os.environ.get(key, '').lower() not in ('', 'false', '0')
           for key in ('CI', 'GITHUB_ACTIONS', 'GITHUB_RUN_ID', 'GITLAB_CI', 'JENKINS_URL')):
        raise RuntimeError('REMOTE_QUALIFICATION_PROHIBITED: full is LOCAL / ON-DEMAND')


def work_errors(record):
    """Only new minimal records use this schema; legacy records stay read-only."""
    if not isinstance(record, dict):
        return ['work item must be an object']
    errors = []
    if not re.fullmatch(r'WORK-[A-Z0-9-]+', str(record.get('id', ''))):
        errors.append('work id missing/invalid')
    if not isinstance(record.get('title'), str) or not record['title'].strip():
        errors.append('title required')
    if record.get('status') not in STATUSES:
        errors.append('status must be TODO | IN_PROGRESS | BLOCKED | DONE')
    scope = record.get('scope')
    if not isinstance(scope, list) or not scope or not all(isinstance(x, str) and x.strip() for x in scope):
        errors.append('nonempty scope required')
    return errors


def close_work(record, *, merged, tests_passed):
    if work_errors(record):
        raise ValueError('; '.join(work_errors(record)))
    if merged is not True or tests_passed is not True:
        raise ValueError('DONE requires PR merged AND required technical tests passed')
    return dict(record, status='DONE')


def navigation_errors(root):
    path = root / 'docs/work/registry.json'
    if not path.exists():
        return []  # A registry is optional navigation.
    try:
        registry = json.loads(path.read_text())
        active = registry.get('active', [])
        if not isinstance(active, list):
            raise ValueError('active must be a list')
        ids, errors = set(), []
        for item in active:
            ident, path = item['id'], item['path']
            if ident in ids:
                errors.append('duplicate active ID: ' + ident)
            ids.add(ident)
            if not isinstance(path, str) or not (root / path).resolve().is_relative_to(root.resolve()) or not (root / path).exists():
                errors.append('active path missing/unsafe: ' + str(path))
        return errors
    except (ValueError, TypeError, KeyError, AttributeError) as error:
        return ['registry JSON/navigation: ' + str(error)]


def documentary(path):
    p = PurePosixPath(path)
    if p.is_absolute() or '..' in p.parts or '\\' in path:
        return False
    if path in ('README', 'README.md', 'ARCHITECTURE.md', 'AGENTS.md'):
        return True
    if path.startswith('docs/') and p.suffix == '.md':
        return True
    # Work records are documentary data; test/contract data elsewhere is code.
    return (path == 'docs/work/registry.json' or path.startswith(('docs/work/active/', 'docs/work/history/'))) and p.suffix in ('.json', '.yaml', '.yml')


def classify(paths):
    return 'DOCS_ONLY' if paths and all(map(documentary, paths)) else 'CODE_CHANGE'


def changed_paths(root):
    event_name = os.environ.get('GITHUB_EVENT_NAME')
    if not event_name:
        return []  # Local fast always exercises the technical profile.
    event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    if event_name == 'pull_request':
        pr = event['pull_request']
        head = pr['head']['sha']
        base = git(root, 'merge-base', pr['base']['sha'], head)
    elif event_name == 'push':
        head, base = event['after'], event['before']
        if base == '0' * 40:
            base = git(root, 'merge-base', 'origin/main', head)
    else:
        raise ValueError('remote CI supports push and pull_request only')
    if git(root, 'rev-parse', 'HEAD') != head:
        raise ValueError('CI checkout must match event head')
    return git(root, 'diff', '--no-renames', '--name-only', base, head).splitlines()


def workflow_errors(root):
    """Small command/action allowlist, reviewed in Git; no hashes or certificates."""
    errors = []
    files = list((root / '.github/workflows').glob('*.y*ml'))
    if len(files) != 1:
        errors.append('remote CI must have one Fast workflow')
    for path in files:
        try:
            doc = yaml.safe_load(path.read_text())
            events = doc.get('on', doc.get(True))  # YAML 1.1 also parses on as True.
            if not isinstance(events, dict) or set(events) != {'push', 'pull_request'}:
                errors.append('remote events must be push/PR; workflow_dispatch is prohibited')
            jobs = doc['jobs']
            if len(jobs) != 1:
                errors.append('only one Fast job is allowed')
            for job in jobs.values():
                if any(key in job for key in ('uses', 'strategy', 'container', 'services', 'env', 'if', 'continue-on-error')):
                    errors.append('unreviewed remote job orchestration')
                commands = []
                for step in job['steps']:
                    if any(key in step for key in ('env', 'if', 'continue-on-error', 'shell', 'working-directory')):
                        errors.append('Fast cannot be skipped or overridden')
                    if 'run' in step:
                        commands.append(step['run'].strip())
                    if 'uses' in step and not re.fullmatch(r'actions/(checkout|setup-java|setup-python|setup-node)@(?:v[0-9]+|[0-9a-f]{40})', step['uses']):
                        errors.append('unreviewed remote action')
                if commands != ['python3 -m pip install PyYAML==6.0.3', 'python3 -B scripts/harness/lean.py ci']:
                    errors.append('remote commands must run Fast only; full/performance/mutation/qualification prohibited')
        except (OSError, ValueError, TypeError, KeyError, AttributeError, yaml.YAMLError) as error:
            errors.append('invalid workflow: ' + str(error))
    return errors


def check(root):
    from lean_project import pin_errors
    errors = navigation_errors(root) + pin_errors(root) + workflow_errors(root)
    if errors:
        raise RuntimeError('\n'.join(errors))
    subprocess.run(['git', 'diff', '--check'], cwd=root, check=True)
    subprocess.run(['git', 'diff', '--cached', '--check'], cwd=root, check=True)


def validate_pins(root, lock_path, required):
    """Required repository entries and immutable revisions cannot disappear."""
    try:
        lock = json.loads((root / lock_path).read_text())
        def visit(value):
            if isinstance(value, dict):
                if 'repository' in value:
                    yield value
                for child in value.values():
                    yield from visit(child)
            elif isinstance(value, list):
                for child in value:
                    yield from visit(child)
        sources = list(visit(lock))
        errors = []
        for repository in required:
            entries = [s for s in sources if s['repository'] == repository]
            if not entries or any(not re.fullmatch('[0-9a-f]{40}', str(s.get('commit', s.get('ref', s.get('main_commit', ''))))) for s in entries):
                errors.append('missing/invalid immutable cross-repo pin: ' + repository)
        for repository, fields in {
                'Gustavo2358/analysis-ir': ('semantic_version',),
                'Gustavo2358/air-java': ('maven',),
                'Gustavo2358/proleap-poc': ('contract_version', 'semantic_product_version')}.items():
            if repository not in required:
                continue
            versions = [entry.get(field) for entry in sources if entry['repository'] == repository for field in fields]
            if not any(isinstance(value, str) and re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', value)
                       or isinstance(value, dict) and value.get('version') for value in versions):
                errors.append('missing cross-repo contract/version: ' + repository)
        return errors
    except (OSError, ValueError, TypeError) as error:
        return ['source lock: ' + str(error)]


def execute(profile, root=ROOT):
    from lean_project import technical_fast
    if profile not in ('DOCS_ONLY', 'CODE_CHANGE'):
        raise ValueError('unknown Fast profile')
    started = time.monotonic()
    check(root)
    subprocess.run([sys.executable, '-B', str(root / 'scripts/harness/test_lean.py')], cwd=root, check=True)
    if profile == 'CODE_CHANGE':
        technical_fast(root)
    print(f'PASS {profile} elapsed_seconds={time.monotonic()-started:.3f}', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('gate', choices=['fast', 'ci', 'docs', 'policy', 'qualification-local', 'full', 'close'])
    parser.add_argument('--work', type=Path)
    parser.add_argument('--merged', action='store_true', help='attest the PR is merged, as recorded in Git/GitHub')
    parser.add_argument('--tests-passed', action='store_true', help='attest required technical tests passed')
    args = parser.parse_args()
    try:
        if args.gate == 'close':
            if not args.work:
                parser.error('close requires --work')
            record = yaml.safe_load(args.work.read_text())
            result = close_work(record, merged=args.merged, tests_passed=args.tests_passed)
            args.work.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n')
            print('DONE')
        elif args.gate in ('qualification-local', 'full'):
            require_local()
            from lean_project import full_local
            full_local(ROOT)
            print('PASS')
        else:
            profile = classify(changed_paths(ROOT)) if args.gate == 'ci' else 'CODE_CHANGE' if args.gate == 'fast' else 'DOCS_ONLY'
            execute(profile)
        return 0
    except (ValueError, RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print('FAIL: ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
