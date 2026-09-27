#!/usr/bin/env python3
"""CICS completion: real four-stage control, candidates, supports and MAY memory."""
from pathlib import Path
import json
import e2e_perform_completion as stages
from cfg_wire_contract import verify as verify_cfg

def memory_oracle(case, air):
    failures=[]
    operations=[s['terminator'] for u in air['publication']['units'] for s in u['sequences']]
    commands=[o for o in operations if o.get('observedKind','').startswith('cics-host-command/')]
    for expected in case.get('hostMemory',[]):
        found=[o for o in commands if o['observedKind']=='cics-host-command/'+expected['command']]
        if not found:failures.append('HOST_COMMAND_MISSING:'+expected['command'])
        for o in found:
            m=o['envelope']['memory']
            if len(m['knownReads'])!=expected['reads'] or len(m['knownWrites'])!=expected['writes']:failures.append('HOST_ROLES_CHANGED')
            if m['mustOverwrite']:failures.append('UNPROVED_HOST_KILL')
            if m['otherReads']['kind']!='none' or m['otherWrites']['kind']!='none':failures.append('HOST_SCOPE_ESCAPED')
    if not case.get('hostMemory') and commands and case.get('blockedHost',False):failures.append('UNAVAILABLE_HOST_MEMORY_ADMITTED')
    return failures

original_run=stages.run_case

def run_case(case,config,out,timeout,java,heap):
    row=original_run(case,config,out,timeout,java,heap)
    if 'parse_degraded' in (out/case['id']/'frontend.stderr').read_text():
        row['status']='PARSER_ERROR'
    dependency=out/case['id']/'dependencies.json'
    if dependency.exists():
        dependencies=json.loads(dependency.read_text())
        row['supportFailures']=[]
        for name in case.get('programSupports',[]):
            candidates=[c for p in dependencies['dependencies']['programs'] for c in p['candidates'] if c['referenceName']==name]
            if not candidates or not any(c.get('supports') for c in candidates):row['supportFailures'].append('EXECUTABLE_TARGET_SUPPORT_LOST:'+name)
        if row['supportFailures']:row['status']='FAIL'
    cfg=out/case['id']/'cfg.json'
    if cfg.exists():
        doc=json.loads(cfg.read_text());verify_cfg(cfg.read_bytes())
        oracle=case.get('exceptionalControl')
        if oracle:
            row['controlFailures']=[]
            if sum(t['kind']=='EXCEPTION' for t in doc['transitions'])!=oracle['localEdges']:row['controlFailures'].append('EXCEPTION_SELECTION_CHANGED')
            if len(doc['nodes'])>oracle['maximumNodes']:row['controlFailures'].append('REENTRY_GRAPH_UNBOUNDED')
            if row['controlFailures']:row['status']='FAIL'
    air=out/case['id']/'program.air.json'
    if air.exists():
        row['memoryFailures']=memory_oracle(case,json.loads(air.read_text()))
        if row['memoryFailures']:row['status']='FAIL'
        stages.dump(out/case['id']/'result.json',row)
    return row

if __name__=='__main__':
    stages.ROOT=Path(__file__).resolve().parents[2]/'analysis-adapters/src/test/resources/cp6/cics-control-completion'
    stages.run_case=run_case
    raise SystemExit(stages.main())
