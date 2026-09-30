#!/usr/bin/env python3
"""Real producer regressions for record continuation and unnamed character roots.
Hand-written candidate oracles include proven overwrites and unknown fragments.
The runtime must expose the full dependency-input producer, not AIR alone.
"""
import argparse, json, os, shutil, subprocess
from pathlib import Path
from e2e_file_composite import sha, entity
from cfg_wire_contract import verify as verify_cfg_wire
from dependency_wire import require
ROOT=Path(__file__).resolve().parents[2]
FIXTURES=ROOT/'analysis-adapters/src/test/resources/normalization-logical-roots'
def walk(value):
 if isinstance(value,dict):
  yield value
  for v in value.values():yield from walk(v)
 elif isinstance(value,list):
  for v in value:yield from walk(v)
def oracle(name,case,expected):
 dep=json.loads((case/'dependencies.json').read_text());air=json.loads((case/'program.air.json').read_text())['publication']
 sp=json.loads((case/'sp/cobol-semantic-product.json').read_text());verify_cfg_wire((case/'cfg.json').read_bytes())
 candidates=[c for p in dep['dependencies']['programs'] for c in p['candidates']]
 actual=sorted({c['referenceName'] for c in candidates});require(actual==expected['targets'],name+' targets '+str(actual))
 require(all(c['supports'] for c in candidates),'candidate supports preserved')
 require(all(s['siteOrigin'] and s['targetOrigin'] and s['provenance'] for s in dep['sites']),'site provenance preserved')
 roots=[o for u in air['units'] for o in u['objects'] if not o.get('displayName')]
 require(len(roots)==expected['roots'],name+' anonymous root count')
 require(len({entity(o['id'],'object') for o in roots})==len(roots),'distinct structural identities')
 require(all(o['storage']['kind']=='cell' for o in roots),'character root uses an abstract cell')
 require(all(o.get('displayName')!='FILLER' for u in air['units'] for o in u['objects']),'no fabricated nominal FILLER')
 if expected.get('unknownRootRead'):
  ids={entity(o['id'],'object') for o in roots}
  # Initialization must retain a read of the unknown root for unnamed payload;
  # replacing it with spaces would falsely initialize otherwise unknown bytes.
  boot=[s for u in air['units'] for s in u['sequences'] if any(e.get('initialLabel')==s['label'] for e in u['entries'])]
  require(any(v.get('domain')=='object' and entity(v,'object') in ids for s in boot for i in s['instructions'] for v in walk(i.get('value',{}))), 'unknown root fragment preserved')
 if expected.get('remainder'):require(dep['sites'] and all(s['effectiveUnknownRemainder'] for s in dep['sites']),'unknown target remains open')
 return dict(case=name,status='PASS',targets=actual,anonymousRoots=len(roots),spVersion=sp['contractVersion'])
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--work',type=Path,required=True);p.add_argument('--cases',nargs='*');a=p.parse_args()
 config=json.loads(a.runtime.read_text());out=a.work.resolve();out.mkdir(parents=True,exist_ok=False)
 for f,d in config['artifactHashes'].items():require(sha(Path(f))==d,'immutable runtime')
 (out/'runtime.json').write_text(json.dumps(config,indent=2)+'\n');results=[]
 for name,expected in json.loads((FIXTURES/'expected.json').read_text()).items():
  if a.cases and name not in a.cases:continue
  case=out/name;case.mkdir();(case/'copybooks').mkdir();web=case/'src/main/resources/web';web.parent.mkdir(parents=True);web.symlink_to(Path(config['checkouts']['proleap-poc'])/'src/main/resources/web')
  source=FIXTURES/(name+'.cbl')
  physical=source.read_bytes()
  if name.startswith('literals-'):
   ending={'lf':b'\n','crlf':b'\r\n','cr':b'\r'}[name.rsplit('-',1)[1]]
   require(ending in physical and b'\r' not in physical.replace(ending,b'') and b'\n' not in physical.replace(ending,b''),'physical record endings preserved')
  args={'frontend':['--source',source,'--output',case/'sp','--copybooks',case/'copybooks',*expected.get('flags',[])],
        'lower':[case/'sp/cobol-semantic-product.json',case/'dependency-input.json'],'cfg':[case/'program.air.json',case/'cfg.json'],'dependency':[case/'dependency-input.json',case/'dependencies.json',*expected.get('dependencyFlags',[])]}
  execution={'sourceSha256':sha(source),'stages':{}}
  for stage,arguments in args.items():
   command=['java','-Xmx1g','-cp',os.pathsep.join(config[stage]['classpath']),config[stage]['main'],*map(str,arguments)]
   with (case/(stage+'.log')).open('w') as log:run=subprocess.run(command,cwd=case,stdout=log,stderr=log,timeout=180)
   execution['stages'][stage]={'command':command,'exitCode':run.returncode};(case/'execution.json').write_text(json.dumps(execution,indent=2)+'\n')
   require(run.returncode==0,name+' '+stage)
   if stage=='lower':
    bundle=json.loads((case/'dependency-input.json').read_text())
    for role in ['air','qualifiedSource']:require(sha(case/bundle[role]['path'])==bundle[role]['sha256'],'bound product hash '+role)
    shutil.copyfile(case/bundle['air']['path'],case/'program.air.json')
  results.append(oracle(name,case,expected));(out/'results.json').write_text(json.dumps(results,indent=2)+'\n');print('PASS',name,flush=True)
 for f,d in config['artifactHashes'].items():require(sha(Path(f))==d,'runtime unchanged')
 print('PASS NORMALIZATION LOGICAL ROOTS',len(results),flush=True)
if __name__=='__main__':main()
