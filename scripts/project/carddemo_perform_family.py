#!/usr/bin/env python3
"""Small PERFORM cohort and delta; reuses the historical CardDemo runner."""
import argparse,hashlib,json,time
from collections import Counter
from pathlib import Path
import carddemo_baseline as runner
from carddemo_entry_delta import load_after,calls,totals,REMAINDERS
from carddemo_metrics import control_bound_contains,key
from carddemo_setup import check_snapshot,require_local


def ast_performs(measurements,program):
    if 'frontend' not in program['artifacts']:return []
    path=(measurements.parent/program['artifacts']['frontend']['path']).parent/'ast-data.js'
    text=path.read_text();ast=json.loads(text[text.index('{'):].rstrip(';\n'))
    return [n for n in ast['nodes'] if n['t']=='PerformStatement']


def cohort(before,phase):
    raw=json.loads(before.read_text());selected=set()
    for p in raw['programs']:
        nodes=ast_performs(before,p)
        # Presence of the typed through-reference selects the first cohort. Later phases
        # use a conservative superset because historical SP does not type loop controls.
        if any(n['a'].get('through') if phase=='thru' else n['a'].get('performKind')=='PROCEDURE' for n in nodes):selected.add(p['path'])
    return selected


def affected(before,upstream,work,runtime,pins,phase):
    require_local();previous=json.loads(before.read_text());selected=cohort(before,phase)
    config=json.loads(runtime.read_text());snapshot=json.loads(pins.read_text())
    if snapshot['upstream']!=previous['snapshot']['upstream'] or snapshot['analysisRepositories']!=config['sources']:raise ValueError('same corpus and exact pins required')
    check_snapshot(upstream,snapshot['upstream']['commit'])
    for name,sha in config['sources'].items():check_snapshot(Path(config['checkouts'][name]),sha)
    work.mkdir(parents=True,exist_ok=False);runner.extract_archives(upstream,work/'archive-members')
    sources=[s for s in runner.discover(upstream) if s['path'] in selected]
    if {s['path'] for s in sources}!=selected:raise ValueError('affected population differs')
    started=time.monotonic_ns();records=[]
    for source in sources:
        record=runner.attempt_program(source,upstream,work,config,120,['-Xmx2g']);records.append(record)
        print(source['path']+': '+', '.join(k+'='+v['state'] for k,v in record['stages'].items()),flush=True)
    runner.dump(work/'measurements.json',{'schemaVersion':'carddemo-measurements-1.0.0','snapshot':snapshot,'programs':records,
        'scope':'THRU typed-reference cohort' if phase=='thru' else 'out-of-line PERFORM cohort; historical SP has untyped loop variants',
        'timings':{'corpusElapsedMs':runner.elapsed(started)}})
    for name,sha in config['sources'].items():check_snapshot(Path(config['checkouts'][name]),sha)
    check_snapshot(upstream,snapshot['upstream']['commit'])


def annotate(path,selected=None):
    raw,programs=load_after(path,selected)
    for p in programs:
        sp=json.loads((path.parent/p['artifacts']['frontend']['path']).read_text()) if 'frontend' in p['artifacts'] else {}
        air=json.loads((path.parent/p['artifacts']['lower']['path']).read_text())['publication'] if 'lower' in p['artifacts'] else {}
        facts=[s for s in sp.get('statements',[]) if s.get('observedKind',s['variant']) in ('PERFORM','PERFORM_PROCEDURE')]
        operations={key(o['header']['id']):o for u in air.get('units',[]) for seq in u['sequences'] for o in seq['instructions']+[seq['terminator']]}
        links={}
        for item in air.get('coverage',{}).get('items',[]):links.setdefault(item['sourceKey'].rsplit('/',1)[-1],[]).extend(operations[key(o)] for o in item['outputs'] if key(o) in operations)
        potential=set();typed=precise=0;evidence=[]
        for f in facts:
            ops=links.get(f['header']['id'],[])
            for op in ops:potential.update(s['sourceStatement'] for s in p['callSites'] if control_bound_contains(op,s))
            if f['variant']=='PERFORM_PROCEDURE':
                typed+=1;precise+=not f['gapCodes'] and any(o['kind'] in ('jump','branch') for o in ops)
                if not f['gapCodes']:evidence.append(f)
        ast=ast_performs(path,p)
        p['performFamily']={'occurrences':len(facts),'thruOccurrences':sum(bool(n['a'].get('through')) for n in ast),
            'typed':typed,'structured':precise,'partial':typed-precise,'unsupported':sum(f['variant']=='OBSERVED' for f in facts),
            'potentialCallSites':len(potential),'gaps':dict(Counter(g for f in facts for g in f.get('gapCodes',[])))}
        p['performPotentialStatements']=potential;p['performControlEvidence']=evidence
    return raw,programs


def aggregate(programs):
    fields=('occurrences','thruOccurrences','typed','structured','partial','unsupported','potentialCallSites')
    return {**{k:sum(p['performFamily'][k] for p in programs) for k in fields},
        'gaps':dict(sum((Counter(p['performFamily']['gaps']) for p in programs),Counter()))}


def vector(site):
    return [site['classification'],site['reachability'],sorted((c['referenceName'],c['rawValue']) for c in site['candidates']),*[site[r] for r in REMAINDERS]]


def compare(before,after,output):
    selected={p['path'] for p in json.loads(after.read_text())['programs']}
    braw,bp=annotate(before,selected);araw,ap=annotate(after)
    if braw['snapshot']['upstream']!=araw['snapshot']['upstream']:raise ValueError('different corpus')
    old={p['path']:p for p in bp};changes=[];unexpected=[];preserved=outside=additions=removals=0
    for a in ap:
        b=old[a['path']]
        if a['sourceSha256']!=b['sourceSha256'] or a['missingCopies']!=b['missingCopies']:raise ValueError('input changed')
        if a['performFamily']['occurrences']!=b['performFamily']['occurrences']:raise ValueError('PERFORM occurrence lost')
        bc,ac=calls(b),calls(a)
        if set(bc)!=set(ac):raise ValueError('CALL source inventory changed')
        if a['stages']['dependency']['state'] not in ('PASS','PARTIAL') and b['stages']['dependency']['state'] in ('PASS','PARTIAL'):
            unexpected.append({'path':a['path'],'reason':'dependency no longer reached'})
        for ident,left in bc.items():
            right=ac[ident]
            if len(left['sites'])!=len(right['sites']):unexpected.append({'path':a['path'],'reason':'CALL activation count changed; inspect control evidence'})
            for bs,ats in zip(left['sites'],right['sites']):
                changed=vector(bs)!=vector(ats);frontier=left['statement'] in b['performPotentialStatements']
                if not changed:preserved+=1;outside+=not frontier;continue
                bv={(c['referenceName'],c['rawValue']) for c in bs['candidates']};av={(c['referenceName'],c['rawValue']) for c in ats['candidates']}
                additions+=len(av-bv);removals+=len(bv-av)
                if not frontier:unexpected.append({'path':a['path'],'statement':left['statement'],'reason':'outside PERFORM frontier'})
                changes.append({'path':a['path'],'source':left['provenance'],'before':bs,'after':ats,'added':sorted(av-bv),'removed':sorted(bv-av),
                    'candidatesChanged':av!=bv,'controlEvidence':a['performControlEvidence'],'structuralJustification':'REQUIRES_INVESTIGATION'})
    result={'schemaVersion':'carddemo-after-perform-family-1.0.0','decisionPolicy':'HUMAN','rankingAuthority':'ADVISORY_ONLY',
        'beforeMeasurements':str(before),'afterMeasurements':str(after),'beforeSnapshot':braw['snapshot'],'afterSnapshot':araw['snapshot'],
        'pipeline':{'before':totals([old[p['path']] for p in ap]),'after':totals(ap)},
        'performFamily':{'before':aggregate([old[p['path']] for p in ap]),'after':aggregate(ap)},
        'candidateChanges':{'sites':sum(c['candidatesChanged'] for c in changes),'additions':additions,'removals':removals},
        'preservedSiteVectors':preserved,'outsideFrontierPreserved':outside,'changedSites':changes,'unexpectedRegressions':unexpected,
        'programs':[{'path':p['path'],'before':old[p['path']]['performFamily'],'after':p['performFamily']} for p in ap],
        'productResult':'NO_REAL_CALL_CANDIDATE_GAIN' if additions==removals==0 else 'REQUIRES_STRUCTURAL_JUSTIFICATION'}
    with output.open('x') as out:json.dump(result,out,indent=2);out.write('\n')
    print(json.dumps({k:result[k] for k in ('pipeline','performFamily','candidateChanges','unexpectedRegressions')},indent=2))
    return result


if __name__=='__main__':
    p=argparse.ArgumentParser();sub=p.add_subparsers(dest='command',required=True)
    a=sub.add_parser('affected')
    for n in ('before','upstream','work','runtime','pins'):a.add_argument('--'+n,type=Path,required=True)
    a.add_argument('--phase',choices=('thru','until','all'),required=True)
    a=sub.add_parser('compare')
    for n in ('before','after','output'):a.add_argument('--'+n,type=Path,required=True)
    args=vars(p.parse_args());command=args.pop('command');globals()[command](**{k:v.resolve() if isinstance(v,Path) else v for k,v in args.items()})
