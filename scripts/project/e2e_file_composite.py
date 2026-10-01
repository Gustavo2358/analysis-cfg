#!/usr/bin/env python3
"""Current-contract FILE composition through the four production CLIs.
Hand-written oracles inspect graph paths and dependency supports, not graph sizes.
Use an immutable runtime.json from a qualified producer build; --inspect can also
challenge already generated artifacts (e.g. the historical RED witness).
"""
from cfg_local_paths import Paths
from cfg_wire_contract import verify as verify_cfg_wire
import argparse,collections,hashlib,json,os,shutil,subprocess,time
from pathlib import Path
from dependency_wire import read,require
from e2e_w2d import source_spans,runtime
from carddemo_setup import check_snapshot
ROOT=Path(__file__).resolve().parents[2]
FIXTURES=ROOT/'analysis-adapters/src/test/resources/file-dependencies/composite'
PAIR=['open CLIENTDD','open OTHERDD','close CLIENTDD','close OTHERDD']
EXPECTED={
 'original':[PAIR], 'single':[['open CLIENTDD','close CLIENTDD']], 'split':[PAIR],
 'reverse':[['open OTHERDD','open CLIENTDD','close OTHERDD','close CLIENTDD']],
 'same-mode':[PAIR], 'three':[['open CLIENTDD','open OTHERDD','open THIRDDD','close CLIENTDD','close OTHERDD','close THIRDDD']],
 'open-multi-close-split':[PAIR], 'open-split-close-multi':[PAIR], 'status':[PAIR],
 'perform-twice':[PAIR+['call BETWEEN']+PAIR+['call AFTER']],
 'perform-nested':[PAIR+['call BETWEEN']+PAIR+['call AFTER']],
 'branch':[PAIR,['open OTHERDD','open CLIENTDD','close CLIENTDD','close OTHERDD']],
 'status-may-alias':[['open CLIENTDD','open OTHERDD','call KEEP0001','close CLIENTDD','close OTHERDD']],
 'status-alias':[['open CLIENTDD','open OTHERDD','call <unknown>','close CLIENTDD','close OTHERDD']],
 'use-error':[PAIR], 'cics-handler':[['open CLIENTDD','open OTHERDD','call RECOVER']],
 'native-single':[['open CLIENTDD','read CLIENTDD','write CLIENTDD','rewrite CLIENTDD','delete-record CLIENTDD','start CLIENTDD','close CLIENTDD']],
 'sort-multi':None,'sort-output':None,'merge-multi':None,'sort-use-error':None,'sort-callback':None,
}
def sha(p):
 if p.is_file():return hashlib.sha256(p.read_bytes()).hexdigest()
 return hashlib.sha256(b''.join(str(f.relative_to(p)).encode()+b'\0'+f.read_bytes() for f in sorted(p.rglob('*')) if f.is_file())).hexdigest()
def producer_runtime(path):
 config=json.loads(path.read_text());base=path.parent;lock=json.loads((ROOT/'docs/sources/sources.lock.json').read_text())
 checkouts={}
 for repo,key in [('proleap-poc','proleap_poc'),('cobol-lower','cobol_lower'),('air-java','air_java')]:
  require(config['sources'][repo]==lock[key]['commit'],'producer pin '+repo);check_snapshot(base/repo,config['sources'][repo]);checkouts[repo]=str(base/repo)
 cp=runtime(base).split(os.pathsep)
 result={'frontend':config['frontend'],'lower':dict(config['lower'],main='io.github.gustavo2358.lower.adapters.cli.CobolDependencyInput'),
  'cfg':{'main':'io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg','classpath':cp},
  'dependency':{'main':'io.github.gustavo2358.analysis.launcher.AnalysisDependencies','classpath':cp},'sources':config['sources'],'checkouts':checkouts}
 result['artifactHashes']={p:sha(Path(p)) for stage in ['frontend','lower','cfg','dependency'] for p in result[stage]['classpath']}
 return result
def entity(ref,domain='operation'):return (ref.get('domain',domain),ref['publication'],ref.get('unit'),ref['localId'])
def vertex(ref):return (ref['publication'],str(ref['ordinal']))
def labelrefs(value):
 if isinstance(value,dict):
  if value.get('domain')=='label':yield entity(value)
  else:
   for v in value.values():yield from labelrefs(v)
 elif isinstance(value,list):
  for v in value:yield from labelrefs(v)
def reachable(start,adj,blocked=frozenset()):
 if isinstance(adj,Paths):return adj.reachable(start,blocked)
 seen=set();todo=collections.deque(start)
 while todo:
  at=todo.popleft()
  if at in blocked or at in seen:continue
  seen.add(at);todo.extend(adj[at])
 return seen

def oracle(name,folder):
 load=lambda n:json.loads((folder/n).read_text())
 air=load('program.air.json')['publication'];cfg=load('cfg.json');dep=read(folder/'dependencies.json');sp=load('sp/cobol-semantic-product.json')
 seqs=[s for u in air['units'] for s in u['sequences']];terms={entity(s['terminator']['header']['id']):s['terminator'] for s in seqs}
 byop={entity(n['terminator']['operation']):vertex(n['id']) for n in cfg['nodes'] if 'terminator' in n}
 bylabel={entity(n['label'],'label'):vertex(n['id']) for n in cfg['nodes'] if 'label' in n}
 starts=[vertex(n['id']) for n in cfg['nodes'] if n['kind']=='ENTRY'];require(len(starts)==1,'one source entry')
 adj=Paths(cfg);verify_cfg_wire((folder/'cfg.json').read_bytes())
 local={oid:t for oid,t in terms.items() if t['kind'].startswith('local.')}
 rules={entity(r['operation']):r for r in cfg.get('localControl',[])}
 require(set(local)==set(rules),'all AIR local rules have exactly one CFG rule')
 for oid,t in local.items():
  r=rules[oid];require(vertex(r['source'])==byop[oid],'local rule source identity')
  for field in {'local.invoke':['entry','resume'],'local.boundary':['defaultDestination'],'local.resume':[],'local.unwind':['destination']}[t['kind']]:
   require(vertex(r[field])==bylabel[entity(t[field],'label')],'exact AIR local destination '+field)
 air_edges={(bylabel[entity(s['label'],'label')],bylabel[l]) for s in seqs if not s['terminator']['kind'].startswith('local.') for l in labelrefs(s['terminator'])}
 cfg_edges={(vertex(e['from']),vertex(e['to'])) for e in cfg['transitions'] if vertex(e['from']) in bylabel.values() and vertex(e['to']) in bylabel.values()}
 require(air_edges==cfg_edges,'CFG preserves every explicit AIR destination, adds no FILE edge')
 if name=='sort-callback':
  require(not sp['controlTopology'].get('fileFlows'),'no invented callback flow')
  owner='statement:'+str(sp['fileInventory']['sortPlans'][0]['statement'].split(':')[-1])
  outcomes=[o for o in sp['controlTopology']['outcomes'] if o['statement']==owner]
  require(outcomes and all(o['kind']=='UNKNOWN_LOCAL' for o in outcomes),'callback control remains unknown')
  require(not dep['fileDependencies']['sites'],'no unsupported callback FILE operations materialized')
  return {'name':name,'status':'PASS','callbackControl':'UNKNOWN_LOCAL','cfgMatchesAir':True}
 seen=reachable(starts,adj);files=dep['fileDependencies'];declarations={entity(d['id'],'resource'):d for d in files['declarations']};actions={};roles=collections.defaultdict(set)
 for site in files['sites']:
  at=byop[entity(site['operation'])];require((site['reachability']=='REACHABLE')==(at in seen),'independent reachability')
  require(at in seen,'every emitted operand is reachable in these witnesses')
  require(len(site['bindings'])==1,'one use binding');binding=site['bindings'][0];declaration=declarations[entity(binding['declaration'],'resource')]
  roles[binding['role']].add(at)
  if site['targetKind']=='LOCAL':require(not site['candidates'] and declaration['targetKind']=='LOCAL','local work is not an external dependency');continue
  require([c['referenceName'] for c in site['candidates']]==[declaration['name']],'candidate follows its declaration')
  require(site['effects']=='OPEN','I/O effects remain partial')
  for candidate in site['candidates']:
   require(candidate['supports'],'dependency keeps support');source_spans(dep,candidate['supports'][0],FIXTURES/(name+'.cbl'))
  actions[at]=site['action']+' '+declaration['name']
 for site in dep['sites']:
  at=byop.get(entity(site['operation']))
  if at in seen:
   names=[c['referenceName'] for c in site['candidates']]
   if name=='status-alias':require(not names and site['modelValueRemainder'],'proved status overwrite removes stale literal, keeps unknown remainder');actions[at]='call <unknown>'
   else:require(len(names)==1,'one expected CALL target in focal witness');actions[at]='call '+names[0]
 if name in {'status-alias','status-may-alias'}:
  second=next(u for u in sp['fileInventory']['operations']['uses'] if u['command']=='OPEN' and u['ordinal']==1)
  steps=[s for o in second['effects']['outcomes'] for s in o['steps'] if s['role']=='FILE_STATUS']
  expected_strength='MUST_UNKNOWN' if name=='status-alias' else 'MAY_UNKNOWN'
  require(steps and all(s['kind']==expected_strength for s in steps),'status kill follows published write proof')
 if name=='status-may-alias':
  require(any(c['referenceName']=='KEEP0001' for s in dep['sites'] for c in s['candidates']),'unknown status write cannot prove a kill')
 critical={entity(g['id']) for g in air['uncertainties'] if g['code']=='FILE_CRITICAL_ERROR_EXIT_NOT_PROVEN'}
 blocked={byop[oid] for oid,t in terms.items() if any(entity(g) in critical for g in t['header']['uncertainties'])}
 for oid,t in terms.items():
  if byop[oid] in blocked:require(t['envelope']['control']['remainder']['kind']!='none','critical error remains open')
 normal=reachable(starts,adj,blocked)
 expected=EXPECTED[name]
 if expected is not None:
  # Check all normal paths using (node, trace) states, so merged branch histories
  # and distinct PERFORM returns cannot hide a skipped operand.
  exits={vertex(n['id']) for n in cfg['nodes'] if n['kind']=='NORMAL_EXIT'};todo=collections.deque((s,(),()) for s in starts);visited=set();traces=set()
  while todo:
   at,stack,trace=todo.popleft()
   if at in blocked or (at,stack,trace) in visited:continue
   visited.add((at,stack,trace));trace=trace+((actions[at],) if at in actions else ())
   require(any(tuple(path[:len(trace)])==trace for path in expected),name+' forbidden success trace '+str(trace))
   if at in exits:require(list(trace) in expected,name+' early completion');traces.add(trace)
   for dst,frames in adj.successors(at,stack):todo.append((dst,frames,trace))
  require(traces=={tuple(x) for x in expected},name+' all source alternatives survive')
 else:
  require(len(files['sites'])==4 and len(files['edges'])==3,'three external participants and one local SD')
  require(len(roles['work'])==1,'one work use');work=next(iter(roles['work']))
  require(not (reachable(starts,adj,blocked|{work}) & roles['output']),'output cannot precede work')
  require(not (reachable([work],adj,blocked) & roles['input']),'input cannot restart after work')
  require(roles['input']|roles['output']<=normal,'all aggregate participants reachable normally')
  for at in roles['input']|roles['output']:require(at in reachable(adj.ordinary[at],adj,blocked),'aggregate count remains unbounded')
  require(any(g['code']=='FILE_AGGREGATE_ORDER_COUNT_NOT_PROVEN' for g in air['uncertainties']),'unknown aggregate order/count explicit')
 if name=='use-error':require('IOERROR' not in {c['referenceName'] for s in dep['sites'] for c in s['candidates']},'unknown USE callback not invented')
 return {'name':name,'fileSites':len(files['sites']),'fileEdges':len(files['edges']),'spVersion':sp['contractVersion'],'cfgMatchesAir':True,'status':'PASS'}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',type=Path);p.add_argument('--producers',type=Path);p.add_argument('--work',type=Path);p.add_argument('--inspect',type=Path);p.add_argument('--case',choices=list(EXPECTED));args=p.parse_args()
 if args.inspect:print(json.dumps(oracle(args.case or 'original',args.inspect),indent=2));return
 require((args.runtime is not None) != (args.producers is not None) and args.work is not None,'one runtime/producers and work required');config=producer_runtime(args.producers.resolve()) if args.producers else json.loads(args.runtime.read_text());out=args.work.resolve();out.mkdir(parents=True,exist_ok=False)
 for path,digest in config['artifactHashes'].items():require(sha(Path(path))==digest,'immutable runtime artifact')
 (out/'runtime.json').write_text(json.dumps(config,indent=2)+'\n');results=[]
 for name in ([args.case] if args.case else EXPECTED):
  case=out/name;case.mkdir();source=FIXTURES/(name+'.cbl');(case/'empty-copybooks').mkdir();stages={}
  web=case/'src/main/resources/web';web.parent.mkdir(parents=True);web.symlink_to(Path(config['checkouts']['proleap-poc'])/'src/main/resources/web',target_is_directory=True)
  arguments={'frontend':['--source',source,'--copybooks',case/'empty-copybooks','--output',case/'sp','--storage-profile','ibm-enterprise-6.4-fixed-display-1047@1','--entry-storage-state','initial','--cics-entry-mode','new-logical-level','--logical-text','disabled'],
   'lower':[case/'sp/cobol-semantic-product.json',case/'dependency-input.json'],'cfg':[case/'program.air.json',case/'cfg.json'],'dependency':[case/'dependency-input.json',case/'dependencies.json']}
  for stage in arguments:
   command=[os.environ.get('JAVA','java'),'-Xmx2g','-cp',os.pathsep.join(config[stage]['classpath']),config[stage]['main'],*map(str,arguments[stage])];start=time.monotonic()
   with (case/(stage+'.log')).open('w') as log:r=subprocess.run(command,cwd=case,stdout=log,stderr=log,timeout=300)
   stages[stage]={'command':command,'exitCode':r.returncode,'seconds':round(time.monotonic()-start,3)};(case/'execution.json').write_text(json.dumps(stages,indent=2)+'\n');require(r.returncode==0,name+' '+stage)
   if stage=='lower':
    bundle=json.loads((case/'dependency-input.json').read_text());artifact=case/bundle['air']['path'];require(sha(artifact)==bundle['air']['sha256'],'AIR hash');shutil.copyfile(artifact,case/'program.air.json')
  result=oracle(name,case);results.append(result);(out/'results.json').write_text(json.dumps(results,indent=2)+'\n');print('PASS',name,flush=True)
 for path,digest in config['artifactHashes'].items():require(sha(Path(path))==digest,'runtime unchanged')
 print('PASS FILE COMPOSITE',len(results),flush=True)
if __name__=='__main__':main()
