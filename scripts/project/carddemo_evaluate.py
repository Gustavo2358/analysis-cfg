#!/usr/bin/env python3
"""Focused EVALUATE run and small comparison against preserved ENTRY-wave outputs."""
import argparse
from collections import Counter
import json
from pathlib import Path
import time

import carddemo_baseline as runner
from carddemo_entry_delta import load_after, calls, totals, REMAINDERS
from carddemo_metrics import control_bound_contains, key
from carddemo_setup import check_snapshot, require_local


def evaluate_facts(sp):
    return [s for s in sp.get('statements', []) if s['variant'] == 'EVALUATE' or s.get('observedKind') == 'EVALUATE']


def annotate(path):
    raw, programs = load_after(path); root = path.parent
    for p in programs:
        sp = json.loads((root / p['artifacts']['frontend']['path']).read_text()) if 'frontend' in p['artifacts'] else {}
        air = json.loads((root / p['artifacts']['lower']['path']).read_text())['publication'] if 'lower' in p['artifacts'] else {}
        facts = evaluate_facts(sp)
        ops = {key(o['header']['id']): o for u in air.get('units', []) for seq in u['sequences'] for o in seq['instructions'] + [seq['terminator']]}
        by_source = {}
        for item in air.get('coverage', {}).get('items', []):
            by_source.setdefault(item['sourceKey'].rsplit('/', 1)[-1], []).extend(ops[key(o)] for o in item['outputs'] if key(o) in ops)
        potential = set(); kinds = Counter(); modeled = 0
        for f in facts:
            derived = by_source.get(f['header']['id'], [])
            kinds.update(o['kind'] for o in derived)
            modeled += any(o['kind'] == 'branch' for o in derived)
            potential.update(key(s['operation']) for o in derived for s in p['callSites'] if control_bound_contains(o, s))
        p['evaluate'] = {'occurrences': len(facts), 'typed': sum(s['variant'] == 'EVALUATE' for s in facts),
                         'observed': sum(s['variant'] != 'EVALUATE' for s in facts),
                         'coverage': dict(Counter(s['header']['coverage'] for s in facts)),
                         'modeledControl': modeled, 'airOperations': dict(kinds), 'potentialCallSites': len(potential)}
    return raw, programs


def evaluate_totals(programs):
    affected = [p for p in programs if p['evaluate']['occurrences']]
    return {'programs': len(affected), 'callSites': sum(len(p['callSites']) for p in affected),
            **{k: sum(p['evaluate'][k] for p in affected) for k in ('occurrences', 'typed', 'observed', 'modeledControl', 'potentialCallSites')},
            'coverage': dict(sum((Counter(p['evaluate']['coverage']) for p in affected), Counter())),
            'airOperations': dict(sum((Counter(p['evaluate']['airOperations']) for p in affected), Counter()))}


def affected(before, upstream, work, runtime, pins):
    require_local(); before_raw, programs = annotate(before)
    selected = {p['path'] for p in programs if p['evaluate']['occurrences']}
    config = json.loads(runtime.read_text()); snapshot = json.loads(pins.read_text())
    if snapshot['upstream'] != before_raw['snapshot']['upstream'] or snapshot['analysisRepositories'] != config['sources']:
        raise ValueError('same source snapshot and exact product pins required')
    check_snapshot(upstream, snapshot['upstream']['commit'])
    for name, sha in config['sources'].items(): check_snapshot(Path(config['checkouts'][name]), sha)
    work.mkdir(parents=True, exist_ok=False); runner.extract_archives(upstream, work / 'archive-members')
    sources = [s for s in runner.discover(upstream) if s['path'] in selected]
    if {s['path'] for s in sources} != selected: raise ValueError('affected population changed')
    start = time.monotonic_ns()
    def attempt(source):
        record = runner.attempt_program(source, upstream, work, config, 120, ['-Xmx2g'])
        print(source['path'] + ': ' + ', '.join(k + '=' + v['state'] for k, v in record['stages'].items()), flush=True)
        return record
    records = runner.attempt_all(sources, attempt)
    runner.dump(work / 'measurements.json', {'schemaVersion': 'carddemo-measurements-1.0.0', 'snapshot': snapshot,
        'programs': records, 'scope': 'programs with observed EVALUATE and SP in the preserved ENTRY baseline',
        'timings': {'corpusElapsedMs': runner.elapsed(start)}})
    check_snapshot(upstream, snapshot['upstream']['commit'])
    for name, sha in config['sources'].items(): check_snapshot(Path(config['checkouts'][name]), sha)


def compare(before, after, output, focal=None):
    braw, previous = annotate(before); araw, current = annotate(after)
    if braw['snapshot']['upstream'] != araw['snapshot']['upstream']: raise ValueError('upstream changed')
    old = {p['path']: p for p in previous}; new = {p['path']: p for p in current}
    if set(old) != set(new): raise ValueError('Full program population changed')
    site_deltas = []; program_deltas = []
    for path, b in old.items():
        a = new[path]
        if b['sourceSha256'] != a['sourceSha256'] or b['missingCopies'] != a['missingCopies']:
            raise ValueError('source bytes or missing COPY inventory changed: ' + path)
        bc, ac = calls(b), calls(a)
        if set(bc) != set(ac): raise ValueError('source CALL inventory changed: ' + path)
        if b['evaluate']['occurrences'] != a['evaluate']['occurrences']: raise ValueError('EVALUATE silently disappeared: ' + path)
        if b['evaluate']['occurrences']:
            program_deltas.append({'path': path, 'sourceSha256': a['sourceSha256'], 'before': b['evaluate'], 'after': a['evaluate']})
        for ident, left in bc.items():
            right = ac[ident]
            if len(left['sites']) != len(right['sites']): raise ValueError('CALL analysis coverage regressed: ' + path)
            for bs, ats in zip(left['sites'], right['sites']):
                if {(c['referenceName'], c['rawValue']) for c in bs['candidates']} != {(c['referenceName'], c['rawValue']) for c in ats['candidates']}:
                    # An intentional precision change is recorded, not silently claimed as equivalence.
                    change = True
                else: change = False
                if b['evaluate']['occurrences'] or change:
                    site_deltas.append({'path': path, 'provenance': left['provenance'], 'candidatesChanged': change,
                        'before': {'statement': left['statement'], **bs}, 'after': {'statement': right['statement'], **ats}})
    summary = {'schemaVersion': 'carddemo-after-evaluate-1.0.0', 'decisionPolicy': 'HUMAN', 'rankingAuthority': 'ADVISORY_ONLY',
        'beforeMeasurements': str(before), 'afterMeasurements': str(after), 'beforeSnapshot': braw['snapshot'], 'afterSnapshot': araw['snapshot'],
        'full': {'before': totals(previous), 'after': totals(current)},
        'evaluate': {'before': evaluate_totals(previous), 'after': evaluate_totals(current)},
        'knownCandidateCounts': {'before': sum(len(s['candidates']) for p in previous for s in p['callSites']),
                                 'after': sum(len(s['candidates']) for p in current for s in p['callSites'])},
        'programs': program_deltas, 'sites': site_deltas,
        'potentialImpactMeaning': 'Unique sites contained by an EVALUATE-derived Opaque control bound, per program. Potential membership, not causal attribution.'}
    if focal:
        _, fp = annotate(focal)
        if {p['path'] for p in fp} != {p['path'] for p in previous if p['evaluate']['occurrences']}:
            raise ValueError('focused population differs from EVALUATE inventory')
        if any(p['stages']['dependency']['state'] not in ('PASS', 'PARTIAL') for p in fp): raise ValueError('focused pipeline is not green')
        summary['focused'] = {'measurements': str(focal), 'totals': totals(fp), 'evaluate': evaluate_totals(fp)}
    destination = Path(str(output) + '.json')
    if destination.exists(): raise ValueError('comparison output already exists')
    destination.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); sub = parser.add_subparsers(dest='command', required=True)
    p = sub.add_parser('affected')
    for name in ('before', 'upstream', 'work', 'runtime', 'pins'): p.add_argument('--' + name, type=Path, required=True)
    p = sub.add_parser('compare')
    for name in ('before', 'after', 'output'): p.add_argument('--' + name, type=Path, required=True)
    p.add_argument('--focal', type=Path)
    args = vars(parser.parse_args()); command = args.pop('command')
    value = globals()[command](**args)
    if value: print(json.dumps({k: value[k] for k in ('full', 'evaluate', 'knownCandidateCounts')}, indent=2))
