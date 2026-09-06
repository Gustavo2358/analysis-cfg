#!/usr/bin/env python3
"""Offline structural checks for this harness. Not an IR/CFG semantic validator."""
from __future__ import annotations
import argparse
import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[2]
IGNORED = {'.git', '.cache', '.harness-results', '__pycache__', 'target', 'node_modules'}
ID_PATTERN = re.compile(r'\b(?:INV-CFG-\d{3}|EVAL-CFG-\d{3}|BACKLOG-CFG-\d{3}|ADR-\d{4}|WORK-CFG-\d{3})\b')
GATES = {'docs', 'harness', 'fast', 'architecture', 'semantic', 'performance', 'integration', 'full'}
PRODUCT_GATES = {'architecture', 'semantic', 'performance', 'integration'}
WORK_FILES = {'work-item.json', 'spec.md', 'plan.md', 'eval.md', 'state.md'}
FIELDS = {'id', 'backlog_id', 'title', 'status', 'risk', 'goal', 'authorization',
          'authorization_evidence', 'checkpoint', 'must_read', 'related_decisions',
          'related_invariants', 'evals', 'source_scope', 'test_scope', 'must_not_change',
          'gates', 'stop_condition'}
STRUCT_UPSTREAM_ORACLE_NUMBERS = (tuple(range(1, 11)) + tuple(range(18, 23)) +
                                   tuple(range(29, 35)) + tuple(range(41, 49)) +
                                   tuple(range(69, 92)))
SCALAR_UPSTREAM_ORACLE_NUMBERS = (tuple(range(69, 71)) + tuple(range(72, 81)) +
                                   tuple(range(82, 85)) + (88,))
REGION_UPSTREAM_ORACLE_NUMBERS = (81, 88)
VALID_UPSTREAM_ORACLES = (
    {'O-%02d' % n for n in range(1, 92)} |
    {'O-%02d-STRUCT' % n for n in STRUCT_UPSTREAM_ORACLE_NUMBERS} |
    {'O-%02d-SCALAR' % n for n in SCALAR_UPSTREAM_ORACLE_NUMBERS} |
    {'O-%02d-REGION' % n for n in REGION_UPSTREAM_ORACLE_NUMBERS}
)


def no_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate JSON key: ' + key)
        result[key] = value
    return result


def load_json(path: Path):
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=no_duplicate_keys)


def files_under(root: Path):
    for p in sorted(root.rglob('*')):
        if p.is_file() and not any(x in IGNORED for x in p.relative_to(root).parts):
            yield p


def inside(root: Path, p: Path) -> bool:
    try:
        p.resolve().relative_to(root.resolve())
        return True
    except ValueError:
        return False


def existing(root: Path, value: str, planned: bool = False) -> bool:
    if not isinstance(value, str) or not value:
        return False
    if value.startswith('planned:'):
        if not planned:
            return False
        value = value[8:]
        path = root / value.split('#', 1)[0]
        return inside(root, path) and not path.exists() and not Path(value).is_absolute()
    path = root / value.split('#', 1)[0]
    return inside(root, path) and path.exists() and not Path(value).is_absolute()


def stripped_markdown(text: str) -> str:
    # This parses documentation syntax only. It never infers program semantics.
    return re.sub(r'^```[^\n]*\n.*?^```\s*$', '', text, flags=re.M | re.S)


def anchor_names(text: str):
    names = set(re.findall(r'<a\s+id=[\'\"]([^\'\"]+)', text))
    counts = {}
    for heading in re.findall(r'^#{1,6}\s+(.+?)\s*#*$', text, re.M):
        heading = re.sub(r'[^\w\- ]', '', heading.replace('`', '').lower(), flags=re.U).replace(' ', '-')
        n = counts.get(heading, 0)
        counts[heading] = n + 1
        names.add(heading if not n else heading + '-' + str(n))
    return names


def validate(root: Path) -> list[str]:
    root = root.resolve()
    errors: list[str] = []
    def error(message):
        errors.append(message)
    required = ['README.md', 'START_HERE.md', 'AGENTS.md', 'ARCHITECTURE.md',
                'docs/index.md', 'docs/work/registry.json', 'docs/work/backlog.json',
                'docs/evals/catalog.json', 'docs/evals/profile-obligations.json',
                'docs/sources/sources.lock.json', 'docs/engineering/gate-state.json']
    for value in required:
        if not existing(root, value):
            error('Missing required file: ' + value)
    if errors:
        return errors
    try:
        registry = load_json(root / 'docs/work/registry.json')
        backlog = load_json(root / 'docs/work/backlog.json')['items']
        evals = load_json(root / 'docs/evals/catalog.json')['evals']
        profiles = load_json(root / 'docs/evals/profile-obligations.json')
        sources = load_json(root / 'docs/sources/sources.lock.json')
        state = load_json(root / 'docs/engineering/gate-state.json')
    except (OSError, ValueError, KeyError, TypeError) as exc:
        return ['Invalid control metadata: ' + str(exc)]

    for key, value in [('registry', registry), ('profiles', profiles), ('sources', sources), ('gates', state)]:
        if value.get('schema_version') != 1:
            error('Unsupported harness schema: ' + key)
    phase = state.get('phase')
    if phase not in ('docs_only', 'implementation'):
        error('Invalid harness phase')
    runtime_auth = state.get('runtime_authorization')
    if phase == 'docs_only' and runtime_auth is not None:
        error('docs_only cannot claim runtime authorization')

    all_files = list(files_under(root))
    if phase == 'docs_only':
        for p in all_files:
            if p.suffix == '.java' or p.name == 'pom.xml':
                error('Java/POM forbidden in docs_only: ' + str(p.relative_to(root)))
        if profiles.get('implemented_profiles'):
            error('Implemented profile claimed without product in docs_only')
        if any(e.get('status') != 'planned' for e in evals):
            error('Product eval not planned in docs_only')

    groups = state.get('product_gates', {})
    if set(groups) != PRODUCT_GATES:
        error('Product gate inventory mismatch')
    for name, gate in groups.items():
        if gate.get('status') not in ('unavailable', 'implemented'):
            error('Invalid gate status: ' + name)
        if gate.get('status') == 'unavailable' and gate.get('hook') is not None:
            error('Unavailable gate cannot have active hook: ' + name)
        if gate.get('status') == 'implemented':
            hook = gate.get('hook')
            if phase != 'implementation':
                error('Implemented product gate in docs_only: ' + name)
            if not isinstance(hook, str) or not hook.startswith('scripts/project/') or not existing(root, hook):
                error('Invalid/missing product gate hook: ' + name)

    def unique_ids(entries, label):
        ids = [x.get('id') for x in entries]
        if any(not isinstance(x, str) for x in ids) or len(ids) != len(set(ids)):
            error('Duplicate/invalid IDs in ' + label)
        return {x for x in ids if isinstance(x, str)}
    back_ids = unique_ids(backlog, 'backlog')
    eval_ids = unique_ids(evals, 'evals')
    inv_text = (root / 'docs/architecture/invariants.md').read_text(encoding='utf-8')
    inv_defs = re.findall(r'^## (INV-CFG-\d{3})\b', inv_text, re.M)
    inv_ids = set(inv_defs)
    if len(inv_ids) != len(inv_defs):
        error('Duplicate invariant definition')
    adr_ids = {p.stem for p in (root / 'docs/architecture/decisions').glob('ADR-*.md')}
    for a in adr_ids:
        text = (root / 'docs/architecture/decisions' / (a + '.md')).read_text(encoding='utf-8')
        if not text.startswith('# ' + a + ' '):
            error('ADR file/heading mismatch: ' + a)
        if a not in (root / 'docs/architecture/decisions/index.md').read_text(encoding='utf-8'):
            error('ADR missing from index: ' + a)

    dependencies = {}
    for item in backlog:
        ident = item.get('id', '?')
        if not existing(root, item.get('path', '')):
            error('Backlog detail missing: ' + ident)
        elif not (root / item['path']).read_text(encoding='utf-8').startswith('# ' + ident + ' '):
            error('Backlog file/heading mismatch: ' + ident)
        allowed_backlog_states = {'ready_for_authorization', 'planned', 'deferred', 'active', 'blocked', 'completed'}
        if item.get('status') not in allowed_backlog_states:
            error('Invalid backlog status: ' + ident)
        if item.get('status') == 'completed' and not item.get('completion_evidence'):
            error('Completed backlog without evidence: ' + ident)
        dependencies[ident] = item.get('dependencies', [])
        if not set(item.get('dependencies', [])) <= back_ids:
            error('Missing backlog dependency: ' + ident)
        if not set(item.get('evals', [])) <= eval_ids:
            error('Missing eval reference: ' + ident)
        if ident not in (root / 'docs/work/backlog.md').read_text(encoding='utf-8'):
            error('Backlog missing from index: ' + ident)
    visiting, visited = set(), set()
    def visit(ident):
        if ident in visiting:
            error('Backlog dependency cycle at ' + ident)
            return
        if ident in visited:
            return
        visiting.add(ident)
        for dep in dependencies.get(ident, []):
            if dep in dependencies:
                visit(dep)
        visiting.remove(ident)
        visited.add(ident)
    for ident in dependencies:
        visit(ident)
    for e in evals:
        if not set(e.get('invariants', [])) <= inv_ids:
            error('Unknown invariant in eval: ' + e.get('id', '?'))
        for o in e.get('upstream_oracles', []):
            if o not in VALID_UPSTREAM_ORACLES:
                error('Invalid upstream oracle: ' + str(o))
        if e.get('status') == 'implemented' and not e.get('implementation_evidence'):
            error('Implemented eval without evidence: ' + e.get('id', '?'))

    active = registry.get('active', [])
    history = registry.get('history', [])
    work_ids = unique_ids(active + history, 'work registry')
    active_root = root / 'docs/work/active'
    actual_dirs = ({p.name for p in active_root.iterdir() if p.is_dir()}
                   if active_root.exists() else set())
    if actual_dirs != {a['id'] for a in active}:
        error('Active directories differ from registry')
    for item in backlog:
        linked = item.get('work_item')
        if linked is not None and linked not in work_ids:
            error('Backlog points to unknown work item: ' + item['id'])
    known_impl_auth = set()
    for entry in active:
        ident = entry['id']
        folder = root / entry.get('path', '')
        if not inside(root, folder) or folder.name != ident or not folder.is_dir():
            error('Invalid active work path: ' + ident)
            continue
        if {p.name for p in folder.iterdir()} != WORK_FILES:
            error('Active work must contain exactly five files: ' + ident)
        try:
            work = load_json(folder / 'work-item.json')
        except (ValueError, OSError) as exc:
            error('Invalid work manifest: ' + ident + ': ' + str(exc))
            continue
        if set(work) != FIELDS:
            error('Work manifest fields mismatch: ' + ident)
        for k in ('id', 'status', 'authorization'):
            if work.get(k) != entry.get(k):
                error('Work registry mismatch ' + k + ': ' + ident)
        if work.get('status') not in ('active', 'blocked'):
            error('Completed/invalid work in active: ' + ident)
        if work.get('authorization') not in ('none', 'discovery', 'implementation'):
            error('Invalid authorization: ' + ident)
        if work.get('status') == 'active' and work.get('authorization') == 'none':
            error('Active work without authorization: ' + ident)
        if work.get('authorization') == 'implementation':
            known_impl_auth.add(ident)
        if work.get('risk') not in ('low', 'medium', 'high'):
            error('Invalid work risk: ' + ident)
        if not work.get('authorization_evidence') or not work.get('stop_condition'):
            error('Missing authorization/stop evidence: ' + ident)
        if work.get('backlog_id') not in back_ids:
            error('Work points to missing backlog: ' + ident)
        for field in ('must_read', 'source_scope', 'test_scope'):
            for path in work.get(field, []):
                if not existing(root, path, planned=(field != 'must_read')):
                    error('Missing/invalid work path: ' + ident + ' ' + str(path))
        for field, choices in [('related_decisions', adr_ids), ('related_invariants', inv_ids), ('evals', eval_ids), ('gates', GATES)]:
            if not set(work.get(field, [])) <= choices:
                error('Unresolved ' + field + ': ' + ident)
        for fn, heads in [('state.md', ['Onde estamos','Verde conhecido','Restante','Descobertas que afetam o plano']),
                          ('spec.md', ['Problema','Objetivo','Fora de escopo']),
                          ('plan.md', ['Fatiamento','Dependências']), ('eval.md', ['O que prova corretude','Casos adversariais'])]:
            if (folder / fn).exists():
                text = (folder / fn).read_text(encoding='utf-8')
                for h in heads:
                    if '## ' + h not in text:
                        error('Missing work section: ' + ident + '/' + fn + ': ' + h)
        if ident not in (root / 'docs/work/index.md').read_text(encoding='utf-8'):
            error('Active work missing from index: ' + ident)
    for entry in history:
        if not existing(root, entry.get('path', '')):
            error('Missing history record: ' + entry.get('id', '?'))
        if entry.get('authorization') == 'implementation':
            known_impl_auth.add(entry['id'])
    if phase == 'implementation' and runtime_auth not in known_impl_auth:
        error('Implementation phase lacks linked implementation authorization')

    all_ids = back_ids | eval_ids | inv_ids | adr_ids | work_ids
    for p in all_files:
        if not inside(root, p):
            error('File escapes root: ' + str(p))
            continue
        if p.suffix == '.json':
            try:
                load_json(p)
            except (ValueError, OSError) as exc:
                error('Invalid JSON ' + str(p.relative_to(root)) + ': ' + str(exc))
        if p.suffix != '.md':
            continue
        text = p.read_text(encoding='utf-8')
        body = stripped_markdown(text)
        for ident in ID_PATTERN.findall(body):
            if ident not in all_ids:
                error('Unknown ID ' + ident + ' in ' + str(p.relative_to(root)))
        for target in re.findall(r'(?<!!)\[[^\]\n]+\]\(([^\s)]+)\)', body):
            url = urlsplit(target)
            if url.scheme or target.startswith('//'):
                continue
            q = p.parent / unquote(url.path) if url.path else p
            if not inside(root, q) or not q.exists():
                error('Broken local link in ' + str(p.relative_to(root)) + ': ' + target)
            elif url.fragment and q.is_file() and q.suffix == '.md':
                if unquote(url.fragment) not in anchor_names(q.read_text(encoding='utf-8')):
                    error('Missing local anchor in ' + str(p.relative_to(root)) + ': ' + target)

    air = sources.get('analysis_ir', {})
    if not re.fullmatch('[0-9a-f]{40}', air.get('commit', '')):
        error('IR commit must be immutable 40-character SHA')
    if air.get('semantic_version') != '2.0.0':
        error('Harness must target Analysis IR 2.0.0')
    if profiles.get('ir_commit') != air.get('commit'):
        error('Profile matrix and IR lock disagree')
    paths = [x.get('path') for x in air.get('files', [])]
    if not paths or len(paths) != len(set(paths)):
        error('Empty/duplicate IR source paths')
    if 'exemplos/04-conhecimento-de-tipo.md' not in paths:
        error('Analysis IR V2 type-knowledge example missing from lock')
    for f in air.get('files', []):
        p = f.get('path', '')
        if Path(p).is_absolute() or '..' in Path(p).parts or not p.endswith('.md'):
            error('Unsafe IR source path: ' + p)
        if not re.fullmatch('[0-9a-f]{40}', f.get('git_blob_sha1', '')):
            error('Invalid IR blob hash: ' + p)
    binding = air.get('json_binding', {})
    if binding.get('owner') != air.get('repository'):
        error('Analysis IR JSON binding must be owned by analysis-ir')
    if binding.get('status') != 'present':
        error('Pinned Analysis IR JSON binding must be present')
    if (binding.get('version') != '1.0.0' or binding.get('maturity') != 'DRAFT'
            or binding.get('targets_air_version') != air.get('semantic_version')):
        error('Analysis IR JSON binding must remain DRAFT 1.0.0 targeting AIR 2.0.0')
    if binding.get('path') not in paths or binding.get('review_path') not in paths:
        error('Analysis IR JSON binding documents must be pinned by blob hash')
    if binding.get('implemented_in_analysis_cfg') is not False:
        error('Analysis IR JSON binding must remain outside analysis-cfg code')
    if binding.get('checked_commit') != air.get('commit'):
        error('Analysis IR JSON binding check must use normative commit')

    air_java = sources.get('air_java', {})
    if not re.fullmatch('[0-9a-f]{40}', air_java.get('commit', '')):
        error('air-java commit must be immutable 40-character SHA')
    if air_java.get('role') != 'shared_java_model_and_validator':
        error('air-java role must own shared model and validator')
    coordinates = air_java.get('maven', {})
    expected_coordinates = {
        'group_id': 'io.github.gustavo2358',
        'artifact_id': 'air-java',
        'version': air_java.get('library_version')
    }
    if coordinates != expected_coordinates or not air_java.get('library_version'):
        error('air-java Maven coordinates/version mismatch')
    air_java_ir = air_java.get('analysis_ir', {})
    if (air_java_ir.get('semantic_version') != air.get('semantic_version') or
            air_java_ir.get('commit') != air.get('commit')):
        error('air-java and normative Analysis IR baseline disagree')
    java = air_java.get('java', {})
    if java.get('release') != 21 or java.get('preview') is not False:
        error('analysis-cfg/air-java baseline must use Java 21 without preview')
    if air_java.get('ci', {}).get('checked_commit') != air_java.get('commit'):
        error('air-java CI evidence must match pinned commit')

    proleap = sources.get('proleap_poc', {})
    if not re.fullmatch('[0-9a-f]{40}', proleap.get('main_commit', '')):
        error('proleap-poc main commit must be immutable 40-character SHA')
    if (proleap.get('public_boundary') != 'cobol_semantic_product' or
            proleap.get('json_product') != 'cobol-semantic-product'):
        error('proleap-poc boundary must remain COBOL Semantic Product')
    merged_reviews = proleap.get('merged_reviews', [])
    if (not isinstance(merged_reviews, list) or not merged_reviews or
            merged_reviews[-1].get('merge_commit') != proleap.get('main_commit')):
        error('proleap-poc main evidence must match latest merged review')

    lower = sources.get('cobol_lower', {})
    if lower.get('status') == 'planned_upstream_component':
        if lower.get('commit') is not None or lower.get('api') is not None:
            error('planned cobol-lower cannot claim commit or API')
    elif lower.get('status') != 'verified':
        error('Invalid cobol-lower status')
    expected = {
        'AIR-STRUCTURE@2': {
            'requires': [],
            'oracles': ['O-%02d-STRUCT' % n for n in STRUCT_UPSTREAM_ORACLE_NUMBERS]
        },
        'AIR-LOCAL-CONTROL@2': {
            'requires': ['AIR-STRUCTURE@2'],
            'oracles': ['O-%02d' % n for n in range(56, 61)]
        },
        'AIR-INDIRECT-CONTROL@2': {
            'requires': ['AIR-STRUCTURE@2'],
            'oracles': ['O-%02d' % n for n in range(61, 64)]
        }
    }
    for profile in profiles.get('profiles', []):
        ident = profile.get('id')
        if (ident not in expected or profile.get('requires') != expected[ident]['requires'] or
                profile.get('oracles') != expected[ident]['oracles']):
            error('Profile obligations drift: ' + str(ident))
    if {p.get('id') for p in profiles.get('profiles', [])} != set(expected):
        error('Profile inventory incomplete')
    return sorted(set(errors))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=ROOT)
    args = parser.parse_args()
    try:
        errors = validate(args.root)
    except (OSError, ValueError, KeyError, TypeError) as exc:
        print('[docs] FAIL: invalid/missing metadata: ' + str(exc), file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print('[docs] FAIL: ' + error, file=sys.stderr)
        return 1
    print('[docs] PASS: local harness structure; NOT Java/IR/CFG semantic validation')
    return 0

if __name__ == '__main__':
    sys.exit(main())
