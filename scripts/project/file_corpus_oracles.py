#!/usr/bin/env python3
"""Manual W11 CardDemo source witnesses at upstream59cc6c2; no output-derived expected values."""
import argparse,json
from collections import Counter
from pathlib import Path
from dependency_wire import read,require
from file_corpus_metrics import origin_resolver

# Transcribed from source SELECT/FDs and executable verbs, not the analyzer.
NATIVE={
'CBACT01C.cbl':({'ACCTFILE':['open','read','close'],'OUTFILE':['open','write'],
                 'ARRYFILE':['open','write'],'VBRCFILE':['open','write','write']},{'COBDATFT','CEE3ABD'}),
'CBACT02C.cbl':({'CARDFILE':['open','read','close']},{'CEE3ABD'}),
'CBTRN01C.cbl':({name:['open','close']+(['read'] if name in ('DALYTRAN','XREFFILE','ACCTFILE') else [])
                  for name in ('DALYTRAN','CUSTFILE','XREFFILE','CARDFILE','ACCTFILE','TRANFILE')},{'CEE3ABD'}),
'CBTRN02C.cbl':({'DALYTRAN':['open','read','close'],'TRANFILE':['open','write','close'],
                'XREFFILE':['open','read','close'],'DALYREJS':['open','write','close'],
                'ACCTFILE':['open','read','rewrite','close'],
                'TCATBALF':['open','read','write','rewrite','close']},{'CEE3ABD'}),
}

def legacy_read_dataset(directory):
    """C06-HUMAN-20260917; expected bindings transcribed from unmodified source."""
    d=read(directory/'dependencies.json');f=d['fileDependencies'];resolve=origin_resolver(d)
    sp=json.loads((directory/'sp/cobol-semantic-compilation.json').read_text())['units'][0]['product']
    expected={727:'LIT-CARDXREFNAME-ACCT-PATH',776:'LIT-ACCTFILENAME',826:'LIT-CUSTFILENAME'}
    facts=[s for s in sp['statements'] if s['variant']=='CICS_FILE_CONTROL' and s['header']['provenance']['original']['startLine'] in expected]
    require(len(facts)==3,'three READ DATASET facts from independent source locations')
    require({s['header']['provenance']['original']['startLine']:s['target']['reference']['binding']['candidates'][0]['canonicalName'] for s in facts}==expected,'legacy READ canonical host binding')
    for fact in facts:
        option=next(o for o in fact['options'] if o['canonicalName']=='FILE')
        require(fact['command']=='READ' and option['name']=='DATASET' and option['role']=='READ','legacy spelling and canonical READ FILE')
        require('DATASET' in fact['rawText'][option['start']:option['end']],'raw spelling/offsets retained')
    sites=[s for s in f['sites'] if any(int(o['location']['startLine']) in expected for o in resolve(s['origin']))]
    require(len(sites)==3,'three recovered FILE dependency sites')
    require(all(s['namespace']=='cics.file' and s['action']=='read' and s['targetKind']=='COMPUTED' for s in sites),'source CICS resource, not DSNAME')
    require(all(s['candidates'] or s['unknownRemainder'] and s['analysisReasons'] for s in sites),'unproved command-time values explicit')
    require(not f['declarations'],'CICS does not invent physical dataset declarations')
    return {'source':'app/cbl/COACTVWC.cbl','result':'PASS_INVENTORY_BINDING_AND_PROVENANCE','lines':sorted(expected),'fileOccurrences':len(sites),
            'knownCandidates':[c['referenceName'] for s in sites for c in s['candidates']],
            'unknownRemainders':[s['unknownRemainder'] for s in sites],'decision':'C06-HUMAN-20260917',
            'limit':'READ DATASET denotes CICS FILE; declared initial values alone are not command-time proof; no physical dataset inference.'}

def run(root,out):
    results=[]
    for filename,(expected,calls) in NATIVE.items():
        directory=root/'programs/app/cbl'/filename;d=read(directory/'dependencies.json');f=d['fileDependencies'];resolve=origin_resolver(d)
        require({x['name'] for x in f['declarations']}==set(expected),'manual native declarations '+filename)
        require(all(x['sourceKind']=='ASSIGNMENT_NAME' and x['namespace']=='cobol.external-file-name' for x in f['declarations']),'native source names')
        actual=Counter((c['referenceName'],s['action']) for s in f['sites'] for c in s['candidates'])
        require(actual==Counter((name,action) for name,actions in expected.items() for action in actions),'manual native operation/owner inventory '+filename)
        require(all(not s['unknownRemainder'] and s['targetKind']=='LITERAL' for s in f['sites']),'native literal source proof')
        require({c['referenceName'] for s in d['sites'] for c in s['candidates']}==calls,'manual CALL inventory '+filename)
        require(all(c['supports'] for s in d['sites']+f['sites'] for c in s['candidates']),'FILE/CALL support retained')
        require(all(resolve(s['origin']) for s in f['sites']),'source provenance is resolvable')
        results.append({'source':'app/cbl/'+filename,'result':'PASS','fileNames':sorted(expected),'fileOccurrences':sum(map(len,expected.values())),'callNames':sorted(calls)})
    filename='COCRDSLC.cbl';directory=root/'programs/app/cbl'/filename;d=read(directory/'dependencies.json');resolve=origin_resolver(d);f=d['fileDependencies'];sp=json.loads((directory/'sp/cobol-semantic-compilation.json').read_text())['units'][0]['product']
    expected={742:'LIT-CARDFILENAME',783:'LIT-CARDFILENAME-ACCT-PATH'}
    facts=[s for s in sp['statements'] if s['variant']=='CICS_FILE_CONTROL']
    require({s['header']['provenance']['original']['startLine']:s['target']['reference']['binding']['candidates'][0]['canonicalName'] for s in facts}==expected,'CICS host binding at manual source locations')
    require(len(f['sites'])==2 and all(s['namespace']=='cics.file' and s['action']=='read' and s['targetKind']=='COMPUTED' for s in f['sites']),'two documented READ FILE commands')
    for s in f['sites']:
        require(any(int(o['location']['startLine']) in expected for o in resolve(s['origin'])),'CICS command provenance')
        require(s['candidates'] or s['unknownRemainder'] and s['analysisReasons'],'unproved command-time value remains explicit')
    results.append({'source':'app/cbl/'+filename,'result':'PASS_INVENTORY_AND_BINDING','fileOccurrences':2,'knownCandidates':[c['referenceName'] for s in f['sites'] for c in s['candidates']],
                    'limit':'Initial PIC X(8) values CARDDAT/CARDAIX do not prove a value at the command across partial source/control/storage; report actual unknown remainder.'})
    results.append(legacy_read_dataset(root/'programs/app/cbl/COACTVWC.cbl'))
    with out.open('x') as f:json.dump(results,f,indent=2);f.write('\n')
    for r in results:print(r,flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--corpus',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();run(a.corpus.resolve(),a.output.resolve())
