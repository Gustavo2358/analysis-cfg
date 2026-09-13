#!/usr/bin/env python3
"""Conditional GO TO cohort from preserved SP/AST; small audited dependency delta."""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
import math
from pathlib import Path
import statistics
import time

import carddemo_baseline as runner
from carddemo_entry_delta import load_after, calls, totals, REMAINDERS
from carddemo_metrics import control_bound_contains, key
from carddemo_setup import check_snapshot, require_local


def conditional(fact):
    return fact['variant'] == 'GO_TO_DEPENDING_ON' or (
        fact.get('observedKind') == 'GO_TO' and fact.get('observedShape') == 'TYPED_GO_TO_DEPENDING_ON')


def population(sp, nodes):
    """Count AST reference occurrences, never split names or search COBOL text."""
    index = defaultdict(list)
    children = Counter(n['p'] for n in nodes if n['t'] == 'ProcedureReference')
    for n in nodes:
        if n['t'] == 'GoToStatement' and n['a']['goToKind'] == 'DEPENDING_ON':
            index[(n['l'], n['c'], n['e'])].append(n)
    facts = [s for s in sp.get('statements', []) if conditional(s)]
    counts = []
    for f in facts:
        loc = f['header']['provenance']['expanded']
        matches = index.get((loc['startLine'], loc['startColumn'], loc['endLine']), [])
        if len(matches) != 1:
            raise ValueError('conditional GO TO needs one preserved AST occurrence')
        n = children[matches[0]['id']]
        if f['variant'] == 'GO_TO_DEPENDING_ON' and len(f['destinations']) != n:
            raise ValueError('destination occurrence lost at SP boundary')
        counts.append(n)
    if len(facts) != sum(map(len, index.values())):
        raise ValueError('conditional GO TO occurrence lost at SP boundary')
    return facts, counts


def records(path):
    raw = json.loads(path.read_text())
    result = {}
    for p in raw['programs']:
        artifact = p['artifacts'].get('frontend')
        if not artifact:
            result[p['path']] = ([], [])
            continue
        file = path.parent / artifact['path']
        if hashlib.sha256(file.read_bytes()).hexdigest() != artifact['sha256']:
            raise ValueError('preserved SP changed')
        ast_text = (file.parent / 'ast-data.js').read_text()
        ast = json.loads(ast_text[ast_text.index('{'):].rstrip(';\n'))
        result[p['path']] = population(json.loads(file.read_text()), ast['nodes'])
    return raw, result


def cardinality(values):
    values = sorted(values)
    return dict(min=min(values) if values else None,
                median=statistics.median(values) if values else None,
                p95=values[math.ceil(.95 * len(values))-1] if values else None,
                max=max(values) if values else None)


def aggregate(records):
    facts = [f for fs, _ in records.values() for f in fs]
    counts = [n for _, ns in records.values() for n in ns]
    typed = sum(f['variant'] == 'GO_TO_DEPENDING_ON' for f in facts)
    precise = sum(f['variant'] == 'GO_TO_DEPENDING_ON' and not f['gapCodes'] for f in facts)
    return dict(programsAffected=sum(bool(fs) for fs, _ in records.values()), occurrences=len(facts),
                targetCount=cardinality(counts), totalDestinationOccurrences=sum(counts),
                typed=typed, precise=precise, structured=precise, partial=typed-precise,
                unsupported=len(facts)-typed,
                gaps=dict(Counter(g for f in facts for g in f.get('gapCodes', []))))


def affected(before, upstream, work, runtime, pins):
    require_local()
    previous, inventory = records(before)
    selected = {p for p, (facts, _) in inventory.items() if facts}
    config, snapshot = json.loads(runtime.read_text()), json.loads(pins.read_text())
    if snapshot['upstream'] != previous['snapshot']['upstream'] or snapshot['analysisRepositories'] != config['sources']:
        raise ValueError('same historical corpus and exact pipeline pins required')
    check_snapshot(upstream, snapshot['upstream']['commit'])
    for name, sha in config['sources'].items():
        check_snapshot(Path(config['checkouts'][name]), sha)
    work.mkdir(parents=True, exist_ok=False)
    runner.extract_archives(upstream, work / 'archive-members')
    sources = [s for s in runner.discover(upstream) if s['path'] in selected]
    if {s['path'] for s in sources} != selected:
        raise ValueError('affected source population differs from preserved SP')
    started = time.monotonic_ns()
    programs = []
    for source in sources:
        programs.append(runner.attempt_program(source, upstream, work, config, 120, ['-Xmx2g']))
        print(source['path'] + ': attempted', flush=True)
    runner.dump(work / 'measurements.json', dict(schemaVersion='carddemo-measurements-1.0.0',
        snapshot=snapshot, programs=programs, scope='conditional GO TO occurrences in preserved post-PERFORM SP',
        population=aggregate(inventory), timings=dict(corpusElapsedMs=runner.elapsed(started))))
    check_snapshot(upstream, snapshot['upstream']['commit'])
    for name, sha in config['sources'].items():
        check_snapshot(Path(config['checkouts'][name]), sha)
    return compare(before, work / 'measurements.json', work / 'delta.json')


def vector(site):
    return (site['classification'], site['reachability'],
            sorted((c['referenceName'], c['rawValue']) for c in site['candidates']),
            *(site[r] for r in REMAINDERS))


def frontier(path, program, facts):
    if not facts or 'lower' not in program['artifacts']:
        return set()
    air = json.loads((path.parent / program['artifacts']['lower']['path']).read_text())['publication']
    operations = {key(o['header']['id']): o for u in air['units'] for s in u['sequences']
                  for o in s['instructions'] + [s['terminator']]}
    ids = {f['header']['id'] for f in facts}
    observed = [operations[key(o)] for link in air['coverage']['items']
                if link['sourceKey'].rsplit('/', 1)[-1] in ids for o in link['outputs'] if key(o) in operations]
    return {s['sourceStatement'] for s in program['callSites'] if any(control_bound_contains(o, s) for o in observed)}


def compare(before, after, output):
    br, bi = records(before)
    ar, ai = records(after)
    if br['snapshot']['upstream'] != ar['snapshot']['upstream']:
        raise ValueError('different historical corpus')
    _, bp = load_after(before, set(ai))
    _, ap = load_after(after)
    old = {p['path']: p for p in bp}
    unexpected, changes = [], []
    preserved = outside = additions = removals = 0
    for p in ap:
        b = old[p['path']]
        if p['sourceSha256'] != b['sourceSha256'] or p['missingCopies'] != b['missingCopies']:
            raise ValueError('input/COPY evidence changed')
        if bi[p['path']][1] != ai[p['path']][1]:
            raise ValueError('conditional target cardinality changed')
        bc, ac = calls(b), calls(p)
        if set(bc) != set(ac):
            raise ValueError('CALL source inventory changed')
        f = frontier(before, b, bi[p['path']][0])
        if any(p['stages'][s]['state'] != b['stages'][s]['state'] for s in runner.STAGES):
            unexpected.append(dict(path=p['path'], reason='pipeline state changed; needs investigation'))
        for ident, left in bc.items():
            right = ac[ident]
            if len(left['sites']) != len(right['sites']):
                unexpected.append(dict(path=p['path'], reason='CALL activation inventory changed'))
            for bs, ats in zip(left['sites'], right['sites']):
                if vector(bs) == vector(ats):
                    preserved += 1
                    outside += left['statement'] not in f
                    continue
                bv = {(c['referenceName'], c['rawValue']) for c in bs['candidates']}
                av = {(c['referenceName'], c['rawValue']) for c in ats['candidates']}
                additions += len(av-bv)
                removals += len(bv-av)
                if left['statement'] not in f:
                    unexpected.append(dict(path=p['path'], reason='site changed outside conditional control frontier'))
                changes.append(dict(path=p['path'], source=left['provenance'], before=bs, after=ats,
                    added=sorted(av-bv), removed=sorted(bv-av), candidatesChanged=av != bv,
                    controlEvidence=ai[p['path']][0], structuralJustification='REQUIRES_INVESTIGATION'))
    selected_old = {p: bi[p] for p in ai}
    result = dict(schemaVersion='carddemo-after-goto-depending-1.0.0', decisionPolicy='HUMAN', rankingAuthority='ADVISORY_ONLY',
        beforeMeasurements=str(before), afterMeasurements=str(after),
        beforeMeasurementsSha256=hashlib.sha256(before.read_bytes()).hexdigest(),
        afterMeasurementsSha256=hashlib.sha256(after.read_bytes()).hexdigest(),
        beforeSnapshot=br['snapshot'], afterSnapshot=ar['snapshot'],
        pipeline=dict(before=totals(bp), after=totals(ap)),
        conditionalGoTo=dict(before=aggregate(selected_old), after=aggregate(ai)),
        cohortCalls=dict(before=totals([p for p in bp if bi[p['path']][0]]), after=totals([p for p in ap if ai[p['path']][0]])),
        candidateChanges=dict(sites=sum(c['candidatesChanged'] for c in changes), additions=additions, removals=removals),
        preservedSiteVectors=preserved, outsideFrontierPreserved=outside, changedSites=changes,
        unexpectedRegressions=unexpected,
        productResult='NO_REAL_DEPENDENCY_CHANGE' if not changes else 'REQUIRES_STRUCTURAL_JUSTIFICATION')
    with output.open('x') as f:
        json.dump(result, f, indent=2)
        f.write('\n')
    print(json.dumps({k: result[k] for k in ('pipeline', 'conditionalGoTo', 'candidateChanges', 'unexpectedRegressions')}, indent=2))
    return result


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest='command', required=True)
    a = sub.add_parser('affected')
    for name in ('before', 'upstream', 'work', 'runtime', 'pins'):
        a.add_argument('--' + name, type=Path, required=True)
    a = sub.add_parser('compare')
    for name in ('before', 'after', 'output'):
        a.add_argument('--' + name, type=Path, required=True)
    args = vars(p.parse_args())
    command = args.pop('command')
    globals()[command](**{k: v.resolve() for k, v in args.items()})
