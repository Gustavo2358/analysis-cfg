"""FD-W11 projection of immutable corpus evidence; no source parsing or accuracy claims."""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
from carddemo_metrics import timing_stats, validate_enumeration
from dependency_wire import read


def key(value):return json.dumps(value,sort_keys=True,separators=(',',':'))


def origin_resolver(wire):
    origins={key(o['id']):o for o in wire['origins']};artifacts={key(a['id']):a['logicalName'] for a in wire['artifacts']}
    def resolve(ref):
        seen=set();locations=[];pending=[ref]
        while pending:
            r=pending.pop();k=key(r)
            if k in seen:continue
            seen.add(k)
            if k not in origins:raise ValueError('unresolved provenance reference '+k)
            o=origins[k]
            if o['kind']=='WRITTEN':
                name=artifacts[key(o['artifact'])]
                if name!='<preprocessed>':locations.append({'file':name,'location':o['location'],'exact':o['exact']})
            else:pending.extend(o.get('inputs',[]))
        return sorted({key(x):x for x in locations}.values(),key=key)
    return resolve


def source_locations(wire,ref):return origin_resolver(wire)(ref)


def call_vectors(wire):
    result=[];resolve=origin_resolver(wire)
    for s in wire['sites']:
        source=resolve(s['siteOrigin']);target=resolve(s['targetOrigin'])
        candidates=[]
        for c in s['candidates']:
            supports=[{'kind':q['kind'],'source':resolve(q['origin']),
                       'premiseCount':len(q['premises'])} for q in c['supports']]
            candidates.append({'referenceName':c['referenceName'],'rawValue':c['rawValue'],'supports':sorted(supports,key=key)})
        result.append({'source':source,'command':s.get('command','CALL'),'targetSource':target,
                       'targetKind':s['targetKind'],'reachability':s['reachability'],'candidates':sorted(candidates,key=key),
                       **{k:s[k] for k in ('modelValueRemainder','sourceValueRemainder','interpretationUnknownRemainder','effectiveUnknownRemainder','openControlRemainder')}})
    return sorted(result,key=key)


def compare_vectors(before,after):
    groups=[]
    for rows in (before,after):
        g=defaultdict(list)
        for r in rows:g[key([r['source'],r['command']])].append(r)
        groups.append(g)
    result={'unchanged':0,'changed':[],'added':[],'removed':[]}
    for ident in sorted(set(groups[0])|set(groups[1])):
        a=list(groups[0][ident]);b=list(groups[1][ident])
        for row in a[:]:
            if row in b:a.remove(row);b.remove(row);result['unchanged']+=1
        while a and b:
            x=a.pop(0);y=b.pop(0);result['changed'].append({'dimensions':sorted(k for k in x if x[k]!=y[k]),'before':x,'after':y})
        result['removed'].extend(a);result['added'].extend(b)
    return result


def summarize(root):
    raw=json.loads((root/'measurements.json').read_text());validate_enumeration(raw['corpus']['discoveredSources'],raw['programs'])
    stage_counts={s:Counter() for s in ('frontend','lower','cfg','dependency')};timings={s:[] for s in stage_counts};rss={s:[] for s in stage_counts}
    programs=[];totals=Counter();namespaces=defaultdict(Counter);reasons=Counter();call_states=Counter();blockers=[]
    for p in raw['programs']:
        row={'path':p['path'],'sourceSha256':p['sourceSha256'],'programNames':p.get('programNames',[]),'stages':{s:v['state'] for s,v in p['stages'].items()},'calls':[]}
        for s,v in p['stages'].items():
            stage_counts[s][v['state']]+=1
            if v.get('elapsedMs') is not None:timings[s].append((p['path'],v['elapsedMs']))
            resource=v.get('resources') or v.get('resourceUsage') or {}
            # The runner stores GNU time under resourceMeasurement, depending on its version.
            if not resource:
                rp=root/p['rawDirectory']/(s+'.resources.json')
                if rp.is_file() and rp.stat().st_size:resource=json.loads(rp.read_text())
            if resource.get('maximumResidentSetKiB') is not None:rss[s].append((p['path'],resource['maximumResidentSetKiB']))
            if v['state'] in ('FAILED','BLOCKED','TIMEOUT'):blockers.append({'path':p['path'],'stage':s,'reasonCode':v['reasonCode'],'diagnostic':v['diagnostic']})
        if p['stages']['frontend']['state'] in ('PASS','PARTIAL'):
            sp=json.loads((root/p['artifacts']['frontend']['path']).read_text());units=sp['units'];totals['sourceUnits']+=len(units)
            row['sourceInventory']=[{'program':u['product']['unit']['canonicalProgramName'],'entryInventory':u['product']['entryInventory'],
                                     'coverage':u['product']['coverage']} for u in units]
            for u in units:
                product=u['product'];totals['sourceStatements']+=len(product['statements']);totals['sourceFileDeclarations']+=len(product['fileInventory']['declarations'])
        if p['stages']['dependency']['state'] in ('PASS','PARTIAL'):
            d=read(root/p['artifacts']['dependency']['path']);row['calls']=call_vectors(d);row['fileDependencies']=d['fileDependencies'];row['publicationInventory']=d['publicationInventory']
            totals['programsWithDependency']+=1;totals['callSites']+=len(d['sites']);totals['fileSites']+=len(d['fileDependencies']['sites']);totals['fileDeclarations']+=len(d['fileDependencies']['declarations'])
            for s in d['sites']:call_states['knownWithRemainder' if s['candidates'] and s['effectiveUnknownRemainder'] else 'knownClosed' if s['candidates'] else 'unknown']+=1
            for s in d['fileDependencies']['sites']:
                ns=s['namespace'] or 'LOCAL';namespaces[ns]['sites']+=1;namespaces[ns]['knownCandidates']+=len(s['candidates']);namespaces[ns]['withUnknownRemainder']+=bool(s['unknownRemainder']);namespaces[ns]['withoutCandidate']+=not bool(s['candidates']);reasons.update(s['analysisReasons'])
        programs.append(row)
    return {'schemaVersion':'fd-corpus-observation-1.0','pins':raw['snapshot'],'environment':raw['environment'],'population':raw['corpus']['programsAttempted'],
            'stageStates':stage_counts,'totals':totals,'fileNamespaces':namespaces,'fileAnalysisReasons':reasons,'callStates':call_states,'blockers':blockers,
            'stageTimings':{s:timing_stats(v) for s,v in timings.items()},
            'stageMemoryKiB':{s:{'count':len(v),'max':max((n for _,n in v),default=None),'perProgram':dict(v)} for s,v in rss.items()},
            'programs':programs,'limits':['Observed per-process startup-inclusive time and peak RSS; no SLA.','PRIMARY_ONLY and source/model/control remainders remain explicit.','No precision/recall without exhaustive ground truth.','DSNAME/JCL/runtime allocation are outside the product.']}


def compare_reports(before,after):
    old={p['path']:p for p in before['programs']};out=[]
    for p in after['programs']:
        if p['path'] not in old:continue
        b=old[p['path']]
        if p['sourceSha256']!=b['sourceSha256']:raise ValueError('different source bytes '+p['path'])
        if p['stages']['dependency'] not in ('PASS','PARTIAL') or b['stages']['dependency'] not in ('PASS','PARTIAL'):
            out.append({'path':p['path'],'comparison':'NOT_COMPARABLE','before':b['stages']['dependency'],'after':p['stages']['dependency']});continue
        out.append({'path':p['path'],'comparison':'COMPARED',**compare_vectors(b['calls'],p['calls'])})
    return out


def historical(path):
    x=json.loads(path.read_text());programs=[]
    for p in x['programs']:
        wire={**p.get('dependencyProvenance',{}),'sites':p.get('callSites',[])}
        programs.append({'path':p['path'],'sourceSha256':p['sourceSha256'],'stages':{k:v['state'] for k,v in p['stages'].items()},'calls':call_vectors(wire) if wire['sites'] else []})
    return {'programs':programs,'pins':x['snapshot']}


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--corpus',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--before',type=Path);p.add_argument('--historical',type=Path);a=p.parse_args()
    result=summarize(a.corpus)
    if a.before:result['callDeltaPreviousAttempt']=compare_reports(summarize(a.before),result)
    if a.historical:result['callDeltaHistorical']=compare_reports(historical(a.historical),result)
    with a.output.open('x') as f:json.dump(result,f,indent=2);f.write('\n')
    print(json.dumps({k:result[k] for k in ('population','stageStates','totals','fileNamespaces','callStates')},indent=2))
