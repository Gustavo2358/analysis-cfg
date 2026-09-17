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
    # This syntax is an explicit unresolved authority/coverage observation, not a passing FILE case.
    results.append({'source':'app/cbl/COACTVWC.cbl','result':'NOT_QUALIFIED','lines':[727,776,826],
                    'form':'READ DATASET','declaredValues':['CXACAIX','ACCTDAT','CUSTDAT'],
                    'limit':'Selected C-FC authority confirms DATASET alias for SET only; these READs remain OBSERVED/opaque, never claimed recognized or moved to D.'})
    with out.open('x') as f:json.dump(results,f,indent=2);f.write('\n')
    for r in results:print(r,flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--corpus',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();run(a.corpus.resolve(),a.output.resolve())
