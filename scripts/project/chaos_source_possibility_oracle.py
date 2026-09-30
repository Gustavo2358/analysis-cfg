#!/usr/bin/env python3
"""Chaos contract 2.7 oracle. Original executable manifest remains authoritative.

Source-only uncertainty has its own exact oracle, reviewed against assignments.
No change to executable names, context expectations, kills, edges or physical supports.
Shared bodies require an independent per-frame AIR oracle for context expectations.
The legacy evaluator/helpers are loaded from --corpus; original files are untouched.
"""
import argparse,importlib.util,json,copy,sys
from pathlib import Path
from typing import Any
SOURCE_POSSIBILITIES={
    '09_alter_ignored_textual_goto':{'PROGB001':21},
    '11_alter_inside_evaluate':{'PROGB001':26},
    '12_multiple_alter_same_dispatch':{'PROGB001':22,'PROGC001':25},
    '37_mixed_alter_initialize_comp3':{'PROGB001':23},
}
def configure(corpus):
    spec=importlib.util.spec_from_file_location('legacy_chaos',corpus/'run_e2e.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    module.WIRE_ROOT=Path(__file__).resolve().parent
    for name in ('wire_validator','key','site_source_line','written_files','exact_source_lines','written_spans'):globals()[name]=getattr(module,name)
    return module
def evaluate(case:dict[str,Any],doc:dict[str,Any],local_contexts=None) -> dict[str,Any]:
    """Check executable activations and the canonical source occurrence inventory.

    Dead CALLs may have no executable site, but remain in source inventory. Every
    observed CALL/site/edge must be accounted for; missing or foreign data fail.
    """
    errors=[];per_call={}; extra=SOURCE_POSSIBILITIES.get(case["id"],{})
    try:
        wire_validator().validate(doc)
    except (ValueError, KeyError, TypeError, StopIteration) as exc:
        return {'status':'FAIL','contractErrors':[str(exc)],'sourceMappingErrors':[], 'calls':{}}
    origins={key(o['id']):o for o in doc['origins']}
    artifacts={key(a['id']):a for a in doc['artifacts']}
    source_name=Path(case['source']).name
    expected_lines={v['line'] for v in case['calls'].values()}
    sites_by_line={}
    for site in doc['sites']:
        line=site_source_line(site,origins)
        names=written_files(site['targetOrigin'],origins,artifacts)
        if source_name not in names or names-{source_name,'<preprocessed>'}:
            errors.append('Foreign or absent source artifact for executable CALL')
        if site.get('command')!='CALL' or line not in expected_lines:
            errors.append(f'Unexpected or unmapped executable site: {site.get("operation")}')
        else:
            sites_by_line.setdefault(line,[]).append(site)
    edges_by_activation={}
    for edge in doc['edges']:
        activation=(key(edge['entry']),key(edge['site']))
        edges_by_activation.setdefault(activation,[]).append(edge)

    source_lines={};source_by_line={}
    for unit in doc.get('sourceQualifiedDependencies',{}).get('evidence',{}).get('units',[]):
        for occurrence in unit['occurrences']:
            operands=occurrence.get('operands',[])
            loc=operands[0].get('provenance',{}).get('original',{}) if operands else {}
            line=loc.get('startLine')
            if (occurrence.get('command')!='CALL' or line not in expected_lines
                    or loc.get('endLine')!=line or loc.get('file')!=Path(case['source']).name
                    or occurrence['id']['unit']['canonicalProgramName']!=case['program']):
                errors.append('Unexpected or unmapped source occurrence: '+key(occurrence['id']))
                continue
            source_lines[key(occurrence['id'])]=line
            source_by_line.setdefault(line,[]).append(occurrence)
    programs_by_line={}
    for program in doc.get('dependencies',{}).get('programs',[]):
        line=source_lines.get(key(program.get('sourceOccurrence')))
        if line is None or program['caller']!=case['program']:
            errors.append('Unexpected or unmapped canonical program occurrence')
        else:
            programs_by_line.setdefault(line,[]).append(program)

    for cid,expect in case['calls'].items():
        line=expect['line'];sites=sites_by_line.get(line,[])
        reachable_sites=[s for s in sites if s['reachability']=='REACHABLE']
        programs=programs_by_line.get(line,[])
        failures=[]
        if len(source_by_line.get(line,[]))!=1 or len(programs)!=1:
            failures.append('SOURCE_OCCURRENCE_MISSING_OR_DUPLICATE')
        reachable=bool(expect.get('reachable',True))
        if reachable and not reachable_sites:failures.append('NO_REACHABLE_ACTIVATION')
        if not reachable and any(s['reachability']!='UNREACHABLE_IN_MODEL' for s in sites):
            failures.append('DEAD_CALL_NOT_PROVEN_UNREACHABLE')
        if any(s['reachability']=='UNKNOWN' for s in sites):failures.append('UNKNOWN_ACTIVATION')
        for site in reachable_sites:
            point=site.get('valuePoint') or {}
            if (site.get('targetKind')!='COMPUTED' or point.get('position')!='BEFORE'
                    or point.get('entryId')!=site['entry'] or point.get('operationId')!=site['operation']):
                failures.append('WRONG_VALUE_QUERY_POINT')
        context_sites=reachable_sites
        if local_contexts is not None and 'activationContexts' in expect:
            # Shared operations deliberately aggregate public candidates. Preserve
            # the original per-caller oracle using independent AIR execution.
            context_sites=[]
            for site in reachable_sites:
                observations=[r for r in local_contexts if r['operation']==site['operation'] and r['entry']==site['entry']]
                union={v for r in observations for v in r['values']}
                if union!={c['referenceName'] for c in site['candidates']}:
                    failures.append('SHARED_CONTEXT_CANDIDATES_DIFFER')
                for observation in observations:
                    context_sites.append({'contextLines':observation['performLines'],
                        'candidates':[{'referenceName':v} for v in observation['values']]})
        if 'activationContexts' in expect:
            contexts=expect['activationContexts'];matched=[]
            for site in context_sites:
                lines=site['contextLines'] if 'contextLines' in site else exact_source_lines(site['siteOrigin'],origins,artifacts,source_name)
                contexts_here=[c for c in contexts if c['performLine'] in lines]
                if len(contexts_here)!=1:
                    failures.append('ACTIVATION_CONTEXT_MISSING_OR_AMBIGUOUS')
                    continue
                context=contexts_here[0];matched.append(context['performLine'])
                if sorted(c['referenceName'] for c in site['candidates'])!=sorted(context['required']):
                    failures.append('WRONG_CANDIDATES_FOR_PERFORM_CONTEXT')
            if sorted(matched)!=sorted(c['performLine'] for c in contexts):failures.append('ACTIVATION_CONTEXT_COVERAGE')
        if 'activationCandidateSets' in expect:
            actual_sets=sorted(sorted(c['referenceName'] for c in site['candidates']) for site in context_sites)
            if actual_sets!=sorted(sorted(v) for v in expect['activationCandidateSets']):
                failures.append('WRONG_ACTIVATION_CANDIDATE_SETS')
        actual_activations={(key(site['entry']),key(site['operation'])) for site in sites}
        inventory_activations={(key(site['entry']),key(site['operation'])) for program in programs for site in program['executableSites']}
        inventory_operations={key(op) for program in programs for op in program['executableOperations']}
        if actual_activations!=inventory_activations or {b for a,b in actual_activations}!=inventory_operations:
            failures.append('CANONICAL_EXECUTABLE_SITES_DIFFER')
        candidates=[c for site in sites for c in site['candidates']]
        edges=[e for site in sites for e in edges_by_activation.get((key(site['entry']),key(site['operation'])),[])]
        unified=[c for program in programs for c in program['candidates']]
        actual={c['referenceName'] for c in candidates};edge_names={e['candidate']['referenceName'] for e in edges}
        program_names={c['referenceName'] for c in unified}
        if not extra and {(c['referenceName'],c['rawValue']) for c in candidates}!={(c['referenceName'],c['rawValue']) for c in unified}:
            failures.append('CANONICAL_CANDIDATES_DIFFER')
        required=set(expect['required']);allowed=set(expect['allowed']);forbidden=set(expect.get('forbidden',[]))
        missing=sorted(required-actual);missing_edges=sorted(required-edge_names)
        unexpected=sorted(actual-allowed);unexpected_edges=sorted(edge_names-allowed)
        for label,names in [('CANDIDATES',actual),('EDGES',edge_names),('PROGRAMS',program_names)]:
            wanted=required|set(extra) if label=='PROGRAMS' else required
            permitted=allowed|set(extra) if label=='PROGRAMS' else allowed
            excluded=forbidden-set(extra) if label=='PROGRAMS' else forbidden
            if wanted-names:failures.append('MISSING_'+label)
            if names-permitted:failures.append('UNEXPECTED_'+label)
            if names&excluded:failures.append('FORBIDDEN_'+label)
        # All this corpus's permitted names are literal, uppercase, eight-byte
        # TEXT values. A correct interpreted name cannot hide a different raw value.
        for candidate in candidates+unified+[e['candidate'] for e in edges]:
            if candidate['rawValue']!=candidate['referenceName']:failures.append('WRONG_RAW_VALUE')
            if not candidate['supports']:
                if candidate not in unified or candidate['referenceName'] not in extra:failures.append('CANDIDATE_WITHOUT_SUPPORT')
                else:
                    conditional=candidate.get('conditionalSupports',[])
                    if not conditional:failures.append('SOURCE_CANDIDATE_WITHOUT_SUPPORT')
                    for support in conditional:
                        if 'UNKNOWN_CONTROL_CAN_COMPLETE' not in support['assumptions']:failures.append('SOURCE_CONTROL_ASSUMPTION_MISSING')
                        assignments=[v['provenance']['original'] for v in support['evidence'] if v['kind']=='ASSIGNMENT']
                        if not any(v['file']==source_name and v['startLine']==v['endLine']==extra[candidate['referenceName']] for v in assignments):failures.append('SOURCE_SUPPORT_MISSES_PRODUCER')
            for support in candidate['supports']:
                spans=written_spans(support['origin'],origins)
                if not spans:failures.append('SUPPORT_WITHOUT_SOURCE')
                names=written_files(support['origin'],origins,artifacts)
                if source_name not in names or names-{source_name,'<preprocessed>'}:failures.append('SUPPORT_FROM_FOREIGN_SOURCE')
                lines=expect.get('producerLines',{}).get(candidate['referenceName'],[])
                if not set(lines)&exact_source_lines(support['origin'],origins,artifacts,source_name):
                    failures.append('SUPPORT_MISSES_EXPECTED_PRODUCER')
        if not reachable and (actual or edge_names or program_names or any(s['rawCandidates'] for s in sites)):
            failures.append('UNREACHABLE_CALL_HAS_PRODUCT')
        if expect.get('requireOpenRemainder') and not any(s['effectiveUnknownRemainder'] for s in reachable_sites):
            failures.append('EXPECTED_OPEN_REMAINDER')
        for field,value in expect.get('remainderRequirements',{}).items():
            if not any(s.get(field) is value for s in reachable_sites):failures.append('WRONG_REMAINDER_'+field)
        if extra and (len(programs)!=1 or not programs[0].get('controlRemainder') or 'SOURCE_CONTROL_POSSIBLE' not in programs[0]['authorities']):failures.append('SOURCE_CONTROL_REMAINDER_MISSING')
        per_call[cid]={'sourceLine':line,'targetIdentifier':expect.get('targetIdentifier'),'status':'FAIL' if failures else 'PASS',
            'required':sorted(required),'allowed':sorted(allowed),'actual':sorted(actual),'actualEdges':sorted(edge_names),
            'actualPrograms':sorted(program_names),'missing':missing,'missingEdges':missing_edges,
            'unexpected':unexpected,'unexpectedEdges':unexpected_edges,'failures':sorted(set(failures)),
            'basis':expect['basis'],'oracleClass':case.get('oracleClass','SOURCE_VALUE_AND_CONTROL'),
            'activations':[{k:s.get(k) for k in ('operation','entry','reachability','targetStatus','analysisStatus',
                'valuePoint','modelValueRemainder','sourceValueRemainder','interpretationUnknownRemainder',
                'effectiveUnknownRemainder','openControlRemainder')} for s in sites]}
    return {'status':'FAIL' if errors or any(c['status']=='FAIL' for c in per_call.values()) else 'PASS',
            'analysisStatus':doc.get('analysisStatus'),'sourceMappingErrors':errors,'calls':per_call}

def main():
    p=argparse.ArgumentParser();p.add_argument('--corpus',type=Path,required=True);p.add_argument('--results',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--mutation-tests',action='store_true');a=p.parse_args();configure(a.corpus)
    rows=[];mutations=0
    for case in json.loads((a.corpus/'manifest.json').read_text())['cases']:
        doc=json.loads((a.results/case['id']/'dependencies.json').read_text());local_contexts=None
        if any('activationContexts' in expect for expect in case['calls'].values()):
            air=json.loads((a.results/case['id']/'program.air.json').read_text())
            if any(s['terminator']['kind']=='local.invoke' for u in air['publication']['units'] for s in u['sequences']):
                from local_context_oracle import evaluate as execute_local
                local_contexts=execute_local(air,Path(case['source']).name)
        result=evaluate(case,doc,local_contexts);rows.append({'id':case['id'],'oracle':result});print(case['id'],result['status'],flush=True)
        if a.mutation_tests and local_contexts is not None:
            for mutation in ('drop-context','swap-values','fake-value'):
                bad=copy.deepcopy(local_contexts)
                if mutation=='drop-context':bad.pop()
                elif mutation=='swap-values':bad[0]['values'],bad[1]['values']=bad[1]['values'],bad[0]['values']
                else:bad[0]['values'].append('INVENTED')
                assert evaluate(case,doc,bad)['status']=='FAIL',(case['id'],mutation);mutations+=1
        if a.mutation_tests and case['id'] in SOURCE_POSSIBILITIES:
            for mutation in ('drop-original','drop-possible','fake-name','drop-assumption','false-producer','drop-physical','fake-executable'):
                bad=copy.deepcopy(doc);row=bad['dependencies']['programs'][0];old=next(c for c in row['candidates'] if c['referenceName']=='PROGA001');new=next(c for c in row['candidates'] if c['referenceName']=='PROGB001')
                if mutation=='drop-original':row['candidates'].remove(old)
                elif mutation=='drop-possible':row['candidates'].remove(new)
                elif mutation=='fake-name':new['referenceName']='INVENTED'
                elif mutation=='drop-assumption':new['conditionalSupports'][0]['assumptions'].remove('UNKNOWN_CONTROL_CAN_COMPLETE')
                elif mutation=='false-producer':new['conditionalSupports'][0]['evidence'][0]['provenance']['original']['startLine']=1
                elif mutation=='drop-physical':old['supports']=[]
                elif mutation=='fake-executable':bad['sites'][0]['candidates'].append(copy.deepcopy(new))
                assert evaluate(case,bad)['status']=='FAIL',(case['id'],mutation);mutations+=1
    report={'runs':rows,'passed':sum(r['oracle']['status']=='PASS' for r in rows),'total':len(rows),'rejectedMutations':mutations}
    with a.out.open('x') as f:json.dump(report,f,indent=2);f.write('\n')
    print('PASS',report['passed'],'/',report['total'],'negative mutations',mutations)
    return 0 if report['passed']==report['total'] else 1
if __name__=='__main__':raise SystemExit(main())
