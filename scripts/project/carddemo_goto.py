#!/usr/bin/env python3
"""GO TO population and delta only; reuses the historical local CardDemo runner."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import time

import carddemo_baseline as runner
from carddemo_entry_delta import load_after, calls, totals, REMAINDERS
from carddemo_metrics import control_bound_contains, key
from carddemo_setup import check_snapshot, require_local

BASELINE_MERGES = {'proleap-poc': '04a74a00c3692e05c5ffde2b492205ccdc04d262',
    'cobol-lower': '4a72bb27d62326f26236586095eb8b2122635fea', 'analysis-cfg': 'b40c24279783a873f73b465b9c75e1ee2245d11b',
    'air-java': '96cd5e545723c6fd76d1520f431ebbc196af84f6'}


def annotate(path):
    raw, programs = load_after(path)
    for p in programs:
        sp = json.loads((path.parent / p['artifacts']['frontend']['path']).read_text()) if 'frontend' in p['artifacts'] else {}
        air = json.loads((path.parent / p['artifacts']['lower']['path']).read_text())['publication'] if 'lower' in p['artifacts'] else {}
        facts = [s for s in sp.get('statements', []) if s.get('observedKind', s['variant']) == 'GO_TO']
        ops = {key(o['header']['id']): o for u in air.get('units', []) for seq in u['sequences'] for o in seq['instructions'] + [seq['terminator']]}
        by_source = {}
        for item in air.get('coverage', {}).get('items', []):
            by_source.setdefault(item['sourceKey'].rsplit('/', 1)[-1], []).extend(ops[key(o)] for o in item['outputs'] if key(o) in ops)
        precise, potential, transfers = 0, set(), []
        for f in facts:
            derived = by_source.get(f['header']['id'], [])
            precise += f['variant'] == 'GO_TO' and any(o['kind'] == 'jump' for o in derived)
            potential.update(s['sourceStatement'] for o in derived for s in p['callSites'] if control_bound_contains(o, s))
            if f['variant'] == 'GO_TO' and any(o['kind'] == 'jump' for o in derived):
                transfers.append({'statement': f['header']['id'], 'target': f['target'], 'targetEntry': f['targetEntry'],
                    'occurrenceOrigin': f['header']['provenance'], 'referenceOrigin': f['referenceOrigin'], 'entryOrigin': f['entryOrigin']})
        typed = [f for f in facts if f['variant'] == 'GO_TO']
        p['goto'] = {'occurrences': len(facts), 'typed': len(typed), 'preciseJump': precise,
            'partial': sum(bool(f['gapCodes']) for f in typed), 'unsupported': len(facts) - len(typed),
            'gapCodes': dict(Counter(code for f in typed for code in f['gapCodes'])), 'potentialCallSites': len(potential)}
        p['gotoPotentialStatements'] = potential
        p['preciseGoToTransfers'] = transfers
    return raw, programs


def goto_totals(programs):
    selected = [p for p in programs if p['goto']['occurrences']]
    return {'programs': len(selected), 'callSites': sum(len(p['callSites']) for p in selected),
        **{k: sum(p['goto'][k] for p in selected) for k in ('occurrences', 'typed', 'preciseJump', 'partial', 'unsupported', 'potentialCallSites')},
        'gapCodes': dict(sum((Counter(p['goto']['gapCodes']) for p in selected), Counter()))}


def affected(before, upstream, work, runtime, pins):
    require_local()
    # Select from preserved SP only; no source-text classification and no Full pre-run.
    previous = json.loads(before.read_text()); selected = set()
    for p in previous['programs']:
        if 'frontend' not in p['artifacts']: continue
        path = before.parent / p['artifacts']['frontend']['path']
        if hashlib.sha256(path.read_bytes()).hexdigest() != p['artifacts']['frontend']['sha256']: raise ValueError('baseline SP changed')
        sp = json.loads(path.read_text())
        if any(s.get('observedKind', s['variant']) == 'GO_TO' for s in sp['statements']): selected.add(p['path'])
    config = json.loads(runtime.read_text()); snapshot = json.loads(pins.read_text())
    if snapshot['upstream'] != previous['snapshot']['upstream'] or snapshot['analysisRepositories'] != config['sources']: raise ValueError('same input and exact producer pins required')
    check_snapshot(upstream, snapshot['upstream']['commit'])
    for name, sha in config['sources'].items(): check_snapshot(Path(config['checkouts'][name]), sha)
    work.mkdir(parents=True, exist_ok=False); runner.extract_archives(upstream, work / 'archive-members')
    sources = [s for s in runner.discover(upstream) if s['path'] in selected]
    if {s['path'] for s in sources} != selected: raise ValueError('affected population differs')
    started = time.monotonic_ns()
    def attempt(source):
        record = runner.attempt_program(source, upstream, work, config, 120, ['-Xmx2g'])
        print(source['path'] + ': ' + ', '.join(k + '=' + v['state'] for k, v in record['stages'].items()), flush=True)
        return record
    records = runner.attempt_all(sources, attempt)
    runner.dump(work / 'measurements.json', {'schemaVersion': 'carddemo-measurements-1.0.0', 'snapshot': snapshot,
        'programs': records, 'scope': 'GO_TO occurrences in preserved post-EVALUATE SP', 'timings': {'corpusElapsedMs': runner.elapsed(started)}})
    for name, sha in config['sources'].items(): check_snapshot(Path(config['checkouts'][name]), sha)
    check_snapshot(upstream, snapshot['upstream']['commit'])


def vector(site):
    return [site['classification'], site['reachability'], sorted((c['referenceName'], c['rawValue']) for c in site['candidates']), *[site[r] for r in REMAINDERS]]


def compare(before, after, output, focal=None):
    braw, bp = annotate(before); araw, ap = annotate(after)
    if braw['snapshot']['upstream'] != araw['snapshot']['upstream']: raise ValueError('upstream changed')
    old = {p['path']: p for p in bp}; new = {p['path']: p for p in ap}
    affected_paths = {p['path'] for p in bp if p['goto']['occurrences']}
    if set(new) not in (set(old), affected_paths): raise ValueError('unexpected program population')
    before_selected = [old[path] for path in new]
    sites, programs, unexpected = [], [], []
    unchanged, outside_preserved, additions, removals = 0, 0, 0, 0
    for path, a in new.items():
        b = old[path]
        if b['sourceSha256'] != a['sourceSha256'] or b['missingCopies'] != a['missingCopies']: raise ValueError('input changed: ' + path)
        if b['goto']['occurrences'] != a['goto']['occurrences']: raise ValueError('GO TO occurrence lost: ' + path)
        bc, ac = calls(b), calls(a)
        if set(bc) != set(ac): raise ValueError('source CALL identity changed: ' + path)
        if b['goto']['occurrences']: programs.append({'path': path, 'before': b['goto'], 'after': a['goto']})
        for ident, left in bc.items():
            right = ac[ident]
            if len(left['sites']) != len(right['sites']): raise ValueError('CALL site lost: ' + path)
            for bs, ats in zip(left['sites'], right['sites']):
                changed = vector(bs) != vector(ats)
                bvalues = {(c['referenceName'], c['rawValue']) for c in bs['candidates']}; avalues = {(c['referenceName'], c['rawValue']) for c in ats['candidates']}
                outside = left['statement'] not in b['gotoPotentialStatements']
                if outside and changed: unexpected.append({'path': path, 'statement': left['statement'], 'reason': 'change outside prior GO TO control frontier'})
                if outside and not changed: outside_preserved += 1
                if not changed: unchanged += 1
                if changed:
                    additions += len(avalues - bvalues); removals += len(bvalues - avalues)
                    sites.append({'path': path, 'provenance': left['provenance'], 'candidatesChanged': avalues != bvalues,
                        'before': {'statement': left['statement'], **bs}, 'after': {'statement': right['statement'], **ats},
                        'removedCandidates': sorted(bvalues - avalues), 'addedCandidates': sorted(avalues - bvalues),
                        'controlEvidence': a['preciseGoToTransfers'],
                        'structuralJustification': 'REQUIRES_REVIEW' if avalues != bvalues else 'candidate sets unchanged; inspect remainder/reachability delta'})
    result = {'schemaVersion': 'carddemo-after-goto-1.0.0', 'decisionPolicy': 'HUMAN', 'rankingAuthority': 'ADVISORY_ONLY',
        'scope': 'FULL' if set(new) == set(old) else 'AFFECTED', 'baselineIntegratedMerges': BASELINE_MERGES,
        'baselineEquivalence': 'Preserved EVALUATE measurements; src/main and build descriptors byte-identical to these integrated merges.',
        'beforeMeasurements': str(before), 'afterMeasurements': str(after), 'beforeSnapshot': braw['snapshot'], 'afterSnapshot': araw['snapshot'],
        'pipeline': {'before': totals(before_selected), 'after': totals(ap)}, 'goto': {'before': goto_totals(before_selected), 'after': goto_totals(ap)},
        'candidateChanges': {'sites': sum(s['candidatesChanged'] for s in sites), 'additions': additions, 'removals': removals},
        'preservedSiteVectors': unchanged, 'sitesOutsideGotoFrontierPreserved': outside_preserved,
        'unexpectedRegressions': unexpected, 'programs': programs, 'changedSites': sites,
        'productResult': 'NO_REAL_CALL_CANDIDATE_GAIN' if not additions and not removals else 'REVIEW_STRUCTURAL_CAUSES',
        'impactMeaning': 'Membership in prior GO TO Opaque unit control bounds. Candidate changes require source-support and structural justification; membership alone is not causality.'}
    if focal:
        _, fp = annotate(focal)
        if {p['path'] for p in fp} != affected_paths or any(p['stages']['dependency']['state'] not in ('PASS', 'PARTIAL') for p in fp): raise ValueError('affected run must pass before Full')
        result['affected'] = {'measurements': str(focal), 'pipeline': totals(fp), 'goto': goto_totals(fp)}
    with output.open('x') as out: json.dump(result, out, ensure_ascii=False, indent=2); out.write('\n')
    print(json.dumps({k: result[k] for k in ('pipeline','goto','candidateChanges','unexpectedRegressions')}, indent=2))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); sub = parser.add_subparsers(dest='command', required=True)
    p = sub.add_parser('affected')
    for name in ('before','upstream','work','runtime','pins'): p.add_argument('--'+name, type=Path, required=True)
    p = sub.add_parser('compare')
    for name in ('before','after','output'): p.add_argument('--'+name, type=Path, required=True)
    p.add_argument('--focal', type=Path)
    args = vars(parser.parse_args()); command = args.pop('command'); globals()[command](**args)
