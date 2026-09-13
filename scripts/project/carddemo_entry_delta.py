#!/usr/bin/env python3
"""Small after-vs-before entry-wave comparison; reads existing runner outputs only."""
import argparse
import hashlib
import json
from pathlib import Path

from carddemo_metrics import CLASSES, bind_sites, diagnostic_gaps, key, timing_stats
from dependency_wire import read

REMAINDERS = ('modelValueRemainder', 'sourceValueRemainder', 'interpretationUnknownRemainder',
              'effectiveUnknownRemainder', 'openControlRemainder')


def call_key(call):
    p = call['provenance']
    return key([p['original'], p['includeChain']])


def calls(program):
    result = {}
    for call in program['sourceCallInventory']:
        identity = call_key(call)
        if identity in result: raise ValueError('ambiguous source CALL identity: ' + program['path'])
        sites = [s for s in program['callSites'] if s['sourceStatement'] == call['statement']]
        result[identity] = {'statement': call['statement'], 'provenance': call['provenance'],
                            'sites': [{'operation': s['operation'], 'classification': s['classification'],
                                       'reachability': s['reachability'], 'candidates': s['candidates'],
                                       **{r: s[r] for r in REMAINDERS}} for s in sites]}
    return result


def totals(programs):
    sites = [s for p in programs for s in p['callSites']]
    return {'attempted': len(programs),
            **{stage: sum(p['stages'][stage]['state'] in ('PASS', 'PARTIAL') for p in programs)
               for stage in ('frontend', 'lower', 'cfg', 'dependency')},
            'callsObserved': sum(len(p['sourceCallInventory']) for p in programs),
            'callsAnalyzed': len(sites), 'callsReachable': sum(s['reachability'] == 'REACHABLE' for s in sites),
            'withKnownCandidates': sum(bool(s['candidates']) for s in sites),
            'categories': {c: sum(s['classification'] == c for s in sites) for c in CLASSES},
            'remainders': {r: sum(s[r] is True for s in sites) for r in REMAINDERS}}


def load_after(path):
    raw = json.loads(path.read_text()); root = path.parent
    programs = []
    for p in raw['programs']:
        data = {}
        for stage, artifact in p['artifacts'].items():
            file = root / artifact['path']
            if hashlib.sha256(file.read_bytes()).hexdigest() != artifact['sha256']:
                raise ValueError('raw artifact changed: ' + str(file))
            data[stage] = json.loads(file.read_text())
        sp = data.get('frontend', {})
        source_calls = [{'statement': s['header']['id'], 'provenance': s['header']['provenance']}
                        for s in sp.get('statements', []) if s['variant'] == 'CALL']
        sites = []
        if p['stages']['dependency']['state'] in ('PASS', 'PARTIAL'):
            dep = read(root / p['artifacts']['dependency']['path'])
            sites = bind_sites(sp, data['lower']['publication'], dep, p['path'])
            if {s['sourceStatement'] for s in sites} != {s['statement'] for s in source_calls}:
                raise ValueError('CALL lost at dependency boundary: ' + p['path'])
        programs.append({**p, 'sourceCallInventory': source_calls, 'callSites': sites,
                         'entryInventory': sp.get('entryInventory'),
                         'inputInventory': sp.get('coverage', {}).get('inventoryStatus'),
                         'missingCopies': [g['requestedDependency'] for g in diagnostic_gaps(root / p['rawDirectory'], p)
                                           if g.get('requestedDependency')]})
    return raw, programs


def compare(baseline, measurements):
    before = json.loads(baseline.read_text()); raw, after = load_after(measurements)
    old = {p['path']: p for p in before['programs']}
    if before['snapshot']['upstream'] != raw['snapshot']['upstream'] or set(old) != {p['path'] for p in after}:
        raise ValueError('different upstream or program population')
    changes, recovered, regression_checks = [], [], 0
    for p in after:
        b = old[p['path']]
        if b['sourceSha256'] != p['sourceSha256']: raise ValueError('source changed: ' + p['path'])
        missing_before = sorted(g['requestedDependency'] for g in b['gaps'] if g.get('requestedDependency'))
        if missing_before != sorted(p['missingCopies']): raise ValueError('COPY gap changed: ' + p['path'])
        if p['missingCopies'] and p['inputInventory'] != 'INPUT_MISSING':
            raise ValueError('missing COPY incorrectly became complete input: ' + p['path'])
        previous, current = calls(b), calls(p)
        if set(previous) != set(current): raise ValueError('CALL source population changed: ' + p['path'])
        is_recovered = b['stages']['lower']['reasonCode'] == 'ENTRY_START_UNAVAILABLE'
        for identity in previous:
            left, right = previous[identity]['sites'], current[identity]['sites']
            if left:
                # Ignore publication IDs, retain actual candidate values and every remainder.
                signature = lambda ss: [(s['classification'], s['reachability'],
                                         [(c['referenceName'], c['rawValue']) for c in s['candidates']],
                                         [s[r] for r in REMAINDERS]) for s in ss]
                if signature(left) != signature(right): raise ValueError('existing CALL changed: ' + p['path'])
                regression_checks += len(left)
        if is_recovered:
            cls = 'A' if Path(p['path']).name in ('DBUNLDGS.CBL', 'PAUDBLOD.CBL', 'PAUDBUNL.CBL') else 'C' if '/app-vsam-mq/' in p['path'] else 'B'
            recovered.append((cls, p))
            changes.append({'path': p['path'], 'class': cls, 'sourceSha256': p['sourceSha256'],
                            'beforeStages': {s: v['state'] for s, v in b['stages'].items()},
                            'afterStages': {s: v['state'] for s, v in p['stages'].items()},
                            'entryStart': p['entryInventory']['entries'][0]['start'],
                            'inputInventory': p['inputInventory'], 'missingCopies': p['missingCopies'],
                            'calls': list(current.values()),
                            'timingMs': {'before': b['timings'], 'after': p['timings']}})
    blockers = [{'path': p['path'], 'stage': stage, **outcome} for p in after for stage, outcome in p['stages'].items()
                if outcome['state'] in ('BLOCKED', 'FAILED', 'TIMEOUT')]
    return {'schemaVersion': 'carddemo-entry-localization-delta-1.0.0', 'baselineReference': baseline.name,
            'baselineSnapshot': before['snapshot'], 'snapshot': raw['snapshot'],
            'measurementDirectory': str(measurements.parent), 'before': totals(before['programs']), 'after': totals(after),
            'classes': {c: totals([p for cls, p in recovered if cls == c]) for c in ('A', 'B', 'C')},
            'previouslyStrandedCallsNowAnalyzed': sum(len(p['callSites']) for _, p in recovered),
            'existingCallSitesPreserved': regression_checks,
            'entryBlockersRemaining': sum(p['entryInventory']['entries'][0]['start']['availability'] != 'KNOWN' for _, p in recovered),
            'newBlockers': [b for b in blockers if b['path'] in {p['path'] for _, p in recovered}],
            'allBlockers': blockers, 'changedPrograms': changes,
            'timing': {'beforeCorpusMs': before['timings']['corpusElapsedMs'], 'afterCorpusMs': raw['timings']['corpusElapsedMs'],
                       'beforePrograms': before['aggregate']['timing']['allAttempted'],
                       'afterPrograms': timing_stats([(p['path'], p['timings']['programElapsedMs']) for p in after])}}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--measurements', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = compare(args.baseline, args.measurements)
    with args.output.open('x') as output: json.dump(result, output, indent=2); output.write('\n')
