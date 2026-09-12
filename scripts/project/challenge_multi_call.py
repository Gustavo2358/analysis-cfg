#!/usr/bin/env python3
"""Ten local focal oracle challenges; preserve all original E2E products."""
import argparse
import copy
import json
from pathlib import Path
import sys
from dependency_wire import read, require
from e2e_multi_call import dependency_oracle, program_candidates, GLOBAL
from e2e_w2d import execute, runtime
from prepare_w2d_producers import require_local


def main(work, producers, output):
    require_local(); output.mkdir(parents=True, exist_ok=False)
    cases = {}
    for n in (1, 3, 4, 6, 7):
        cwd = work / f'fixture-{n}-A'
        source = cwd / f'fixture-{n}.cbl'
        sp = json.loads((cwd / 'sp/cobol-semantic-product.json').read_text())
        air = json.loads((cwd / 'program.air.json').read_text()); result = read(cwd / 'dependencies.json')
        sites = dependency_oracle(result, air, sp, n, source)
        cases[n] = result, air, sp, source, sites
    def killed(name, action):
        try: action()
        except ValueError as error: print('KILLED ' + name + ': ' + str(error), flush=True)
        else: raise ValueError('SURVIVED ' + name)
    def corrupt(n, name, change):
        result, air, sp, source, ordered = cases[n]; bad = copy.deepcopy(result)
        sites = {s['operation']['localId']: s for s in bad['sites']}
        selected = [sites[s['operation']['localId']] for s in ordered]
        change(bad, selected)
        (output / (name + '.json')).write_text(json.dumps(bad, indent=2) + '\n')
        killed(name, lambda: dependency_oracle(bad, air, sp, n, source))
    def remove(result, site):
        result['sites'].remove(site); result['edges'] = [e for e in result['edges'] if e['site'] != site['operation']]
    corrupt(1, '01-ignore-second-call', lambda r, s: remove(r, s[1]))
    corrupt(3, '02-all-candidates-at-all-sites', lambda r, s: s[0]['candidates'].extend(copy.deepcopy(s[1]['candidates'])))
    corrupt(6, '03-final-state-at-every-point', lambda r, s: s[0].update(candidates=copy.deepcopy(s[1]['candidates'])))
    corrupt(1, '04-drop-literal-call', lambda r, s: remove(r, s[2]))
    # Fixture7 naturally contains PROGA edges at two different sites. A bag is wrong.
    r = cases[7][0]
    killed('05-duplicate-global-candidate', lambda: require(sorted(e['candidate']['referenceName'] for e in r['edges']) == sorted(GLOBAL[7]), 'global list must deduplicate across sites'))
    # Actual orphan Invoke is exercised by MultiCallModelTest. Challenge the view too,
    # so reachability is checked even if an upstream result carries an orphan edge.
    bad = copy.deepcopy(cases[1][0]); orphan = copy.deepcopy(bad['sites'][0]); orphan['operation']['localId'] = 'orphan'; orphan['reachability'] = 'UNREACHABLE_IN_MODEL'; orphan['candidates'] = []
    edge = copy.deepcopy(bad['edges'][0]); edge['site'] = orphan['operation']; edge['candidate']['referenceName'] = 'BADPROG'
    bad['sites'].append(orphan); bad['edges'].append(edge)
    require(program_candidates(bad) == sorted(GLOBAL[1]), 'actual view excludes unreachable edge')
    killed('06-include-unreachable-global', lambda: require(sorted({e['candidate']['referenceName'] for e in bad['edges']}) == sorted(GLOBAL[1]), 'unreachable BADPROG must not enter union'))
    corrupt(1, '07-values-run-per-site', lambda r, s: r['metrics'].update(possibleValuesRuns=2))
    def mixed(r, sites):
        supports = copy.deepcopy(sites[1]['candidates'][0]['supports']); sites[0]['candidates'][0]['supports'] = supports
        for edge in r['edges']:
            if edge['site'] == sites[0]['operation'] and edge['candidate']['referenceName'] == sites[0]['candidates'][0]['referenceName']: edge['candidate']['supports'] = supports
    corrupt(3, '08-cross-site-supports', mixed)
    result, air, sp, source, sites = cases[4]; bypass = copy.deepcopy(air)
    # The source PERFORM Jump is the only Jump in primary control; target body's
    # final Jump is identified by its destination, rather than physical position.
    sequences = bypass['publication']['units'][0]['sequences']; labels = {s['label']['localId']: s for s in sequences}
    body = next(s for s in sequences if s['terminator']['kind'] == 'jump' and len(s['instructions']) == 2)
    perform = next(s for s in sequences if s['terminator']['kind'] == 'jump' and s['terminator']['destination'] == body['label'])
    perform['terminator']['destination'] = body['terminator']['destination']
    path = output / '09-skipped-perform.air.json'; path.write_text(json.dumps(bypass)); dep = output / '09-skipped-perform.dependencies.json'
    execute(output, '09-skipped-perform', ['java', '-cp', runtime(producers.parent), 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies', str(path), str(dep)])
    killed('09-skip-perform-body', lambda: dependency_oracle(read(dep), air, sp, 4, source))
    corrupt(3, '10-reuse-first-diamond', lambda r, s: s[1].update(candidates=copy.deepcopy(s[0]['candidates'])))
    print('PASS 10 focal challenges; no production mutation and original products unchanged', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--work', type=Path, required=True); parser.add_argument('--producers', type=Path, required=True); parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(); main(args.work.resolve(), args.producers.resolve(), args.output.resolve())
