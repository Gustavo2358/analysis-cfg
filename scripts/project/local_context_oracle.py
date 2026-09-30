"""Independent finite scalar AIR interpreter for local-call E2E oracles.

This test helper rejects effects/expressions outside its admitted subset. It does
not change products or synthesize dependency sites. Source lines identify manifest
expectations only; execution follows full AIR label/operation/object identities.
"""
from collections import deque
import json

def key(value):return json.dumps(value,sort_keys=True,separators=(',',':'))

def evaluate(air,source_name):
    p=air['publication'];origins={key(o['id']):o for o in p['origins']};artifacts={key(a['id']):a for a in p['artifacts']}
    def lines(ref):
        todo=[key(ref)];seen=set();found=set()
        while todo:
            ident=todo.pop()
            if ident in seen:continue
            seen.add(ident);origin=origins[ident]
            if origin['kind']=='derived':todo.extend(key(i) for i in origin['inputs'])
            elif origin['kind']=='written' and artifacts[key(origin['artifact'])]['logicalName']==source_name:
                loc=origin.get('location') or {}
                if loc.get('kind')=='line_columns':
                    span=loc['span']
                    if span['start']['line']==span['end']['line']:found.add(int(span['start']['line'])+1-int(span['lineBase']))
        return sorted(found)
    def expression(expr,state):
        kind=expr['kind']
        if kind=='literal':return expr['value']['value']
        if kind=='fit_text':
            text=expression(expr['value'],state);length=int(expr['length']);return (text+expr['pad']*length)[:length]
        if kind=='read' and expr['place']['kind']=='object':return state[key(expr['place']['object'])]
        raise ValueError('local oracle expression outside admitted subset: '+kind)
    results={}
    for unit in p['units']:
        seqs={key(s['label']):s for s in unit['sequences']}
        for entry in unit['entries']:
            if entry['state']['conditions']:raise ValueError('local oracle requires explicit assignment before reads')
            todo=deque([(key(entry['initialLabel']),(),())]);seen=set()
            while todo:
                at,stack,env=todo.popleft();point=(at,stack,env)
                if point in seen:continue
                seen.add(point);state=dict(env);seq=seqs[at]
                for op in seq['instructions']:
                    if op['kind']=='nop':continue
                    if op['kind']!='assign' or op['destination']['kind']!='object':raise ValueError('local oracle instruction outside admitted subset')
                    state[key(op['destination']['object'])]=expression(op['value'],state)
                term=seq['terminator'];kind=term['kind'];destinations=[];next_stack=stack
                if kind=='local.invoke':
                    op=key(term['header']['id'])
                    if any(f[0]==op for f in stack):raise ValueError('recursive local oracle activation')
                    next_stack=stack+((op,key(term['resume']),tuple(key(x) for x in term['completionPorts']),tuple(lines(term['header']['origin']))),)
                    destinations=[key(term['entry'])]
                elif kind=='local.resume':
                    if not stack:raise ValueError('invalid local return in oracle')
                    destinations=[stack[-1][1]];next_stack=stack[:-1]
                elif kind=='jump':destinations=[key(term['destination'])]
                elif kind=='branch':destinations=[key(term['trueDestination']),key(term['falseDestination'])]
                elif kind in {'return','halt'}:pass
                elif kind in {'opaque','invoke'}:
                    if kind=='opaque':
                        memory=term['envelope']['memory'];control=term['envelope']['control']
                        if memory['knownWrites'] or memory['otherWrites']['kind']!='none' or memory['mustOverwrite']:raise ValueError('opaque write in oracle')
                    else:
                        effect=term['effectBound']
                        if effect['perOutcome'] or effect['otherwise']['writes']['kind']!='none' or effect['otherwise']['mustOverwrite']:raise ValueError('invoke write in oracle')
                        target=term['target'];control=term['outcomes']
                        if target['kind']!='computed' or not stack:raise ValueError('oracle requires a computed call inside a local body')
                        value=expression(target['name'],state);ident=(key(entry['id']),key(term['header']['id']),stack[-1][0])
                        record=results.setdefault(ident,{'entry':entry['id'],'operation':term['header']['id'],'frame':json.loads(stack[-1][0]),'performLines':list(stack[-1][3]),'values':set()});record['values'].add(value.rstrip(' '))
                    if control['remainder']['kind']!='none':raise ValueError('open control in local scalar oracle')
                    for outcome in control['known']:
                        if outcome['kind'] in {'normal','jump'}:destinations.append(key(outcome['label']))
                        elif outcome['kind'] not in {'return','halt','diverge'}:raise ValueError('unsupported oracle outcome')
                else:raise ValueError('local oracle terminator outside admitted subset: '+kind)
                frozen=tuple(sorted(state.items()))
                for dest in destinations:todo.append((dest,next_stack,frozen))
    return [dict(r,values=sorted(r['values'])) for r in results.values()]
