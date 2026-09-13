#!/usr/bin/env python3
"""ST-W3 file vertical with independent physical goldens and existing CFG/RD/values engines."""
import argparse,base64,hashlib,json,os,shutil,subprocess,time
from pathlib import Path
from storage_group_fixtures import fixtures,ibm_octets
from cfg_wire_contract import verify as verify_cfg_wire
from dependency_wire import read,require
from e2e_evaluate import execute,control_oracle
ROOT=Path(__file__).resolve().parents[2]
PROFILE='ibm-enterprise-6.4-fixed-display-1047@1'

def write_json(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n')
def local(identifier):return identifier['localId']
def number(measure):return None if measure['value'] is None else int(measure['value'])
def ordered_nodes(storage):
    children={};nodes=storage['nodes'];seen=set();output=[]
    for node in nodes:children.setdefault(node['parent'],[]).append(node)
    def visit(parent):
        for node in sorted(children.get(parent,[]),key=lambda n:n['order']):
            require(node['id'] not in seen,'layout node cycle/duplicate');seen.add(node['id']);output.append(node);visit(node['id'])
    visit(None);require(len(output)==len(nodes),'every physical node belongs to the forest');return output

def oracle(name,case,sp,air,cfg,dependency,probe):
    require(sp['contractVersion']=='2.7.0','SP 2.7 explicit contract');storage=sp['storage'];require(storage['version']=='1.0.0' and storage['profileId']==PROFILE,'explicit storage profile')
    publication=air['publication'];units=publication['units'];require(len(units)==1,'fixture one-unit scope');unit=units[0]
    statements={s['header']['id']:s for s in sp['statements']};operations={local(op['header']['id']):op for seq in unit['sequences'] for op in seq['instructions']+[seq['terminator']]}
    coverage=publication['coverage']['items']
    def outputs(suffix,domain):
        return sorted({local(output) for item in coverage if item['sourceKey'].endswith('/'+suffix) for output in item['outputs'] if output['domain']==domain})
    def operation(index):
        result=outputs('statement:'+str(index),'operation');require(len(result)==1,'one operation per fixture statement');return result[0]
    sites={local(s['operation']):s for s in dependency['sites']};queries={q['operation']:q for q in probe['queries']}
    if case.get('unknown'):
        require(len(sites)==1,'one unknown call');site=next(iter(sites.values()));require(not site['candidates'] and site['effectiveUnknownRemainder'],'unknown remains open')
        target=next(s for s in statements.values() if s['variant']=='CALL')['target']['reference'];require(target['regionalAccess'] is None,'unproved offset has no precise access')
        node=next(n for n in storage['nodes'] if n['data']==target['binding']['selected']);view=next(v for v in storage['views'] if v['node']==node['id']);require(view['offset']['value'] is None and view['offset']['gapCodes'],'unknown offset is not zero')
        require(any(s['kind']=='region' and s['extent']['kind']=='unknown' for s in publication['storage']),'unknown physical allocation retained');require(not queries,'no invented readable object')
        return dict(calls=1,candidates=0,unknownTargets=1,sourceOpen=1,modelOpen=0,qualifiedPhysicalQueries=0)
    nodes=ordered_nodes(storage);node_ord={n['id']:i for i,n in enumerate(nodes)};views={v['node']:v for v in storage['views']};base_ord={}
    for n in nodes:base_ord.setdefault(views[n['id']]['base'],len(base_ord))
    actual=[dict(parent=None if n['parent'] is None else node_ord[n['parent']],base=base_ord[views[n['id']]['base']],offset=number(views[n['id']]['offset']),extent=number(n['extent']),kind=n['kind'],filler=n['filler']) for n in nodes]
    require(actual==case['nodes'],name+': independent SP physical golden '+str(actual))
    extents=[None]*len(base_ord)
    for base in storage['bases']:
        require(base['allocation']=='INDEPENDENT_LOCAL_WORKING_STORAGE','source allocation proof is explicit');extents[base_ord[base['id']]]=number(base['extent'])
    require(extents==case['extents'],'base extent golden')
    base_ids={};air_bases={local(b['header']['id']):b for b in publication['storage']};objects={local(o['id']):o for o in unit['objects']}
    for base,ordinal in base_ord.items():
        ids=outputs('storage-base/'+base,'storage')
        if not ids and case['extents'][ordinal] is None:
            # Legacy scalar coverage names its explicit SP data identity. Follow the
            # published singleton root relation, never a display name or guessed ID.
            members=[n for n in nodes if views[n['id']]['base']==base]
            require(len(members)==1 and members[0]['parent'] is None and members[0]['data'] is not None,'scalar base is a proved singleton root')
            ids=outputs('data/'+members[0]['data'],'storage')
            require(len(ids)==1 and air_bases[ids[0]]['kind']=='cell','legacy scalar coverage identifies its logical Cell')
        require(len(ids)==1,'one AIR representative per physical base');base_ids[ordinal]=ids[0]
        model=air_bases[ids[0]]
        if case['extents'][ordinal] is not None:require(model['kind']=='region' and model['extent']==dict(kind='known',value=str(case['extents'][ordinal])),'known physical base is a Region with exact extent')
    require(len(air_bases)==len(base_ord),'no cell per name or duplicate base');require(sum(b['kind']=='cell' for b in air_bases.values())==case['cells'],'explicit scalar/regional mixture')
    node_objects={}
    for index,node in enumerate(nodes):
        if node['data'] is None:require(node['filler'],'anonymous physical item is FILLER');continue
        ids=outputs('data/'+node['data'],'object');require(len(ids)==1,'one correlated object for named fixture data');node_objects[index]=ids[0]
        expected=case['nodes'][index];binding=objects[ids[0]]['storage']
        if expected['extent'] is not None:
            require(binding['kind']=='view','textual declaration is a view');require(local(binding['region'])==base_ids[expected['base']],'shared physical region')
            require((int(binding['offset']),int(binding['extent']))==(expected['offset'],expected['extent']),'SP→AIR offset/extent golden');require(binding['codec']['name']=='text.ebcdic.ibm1047' and binding['codec']['version']=='1','explicit interpreted codec')
        else:require(binding['kind']=='cell','legacy integer represented as a logical Cell, never byte zero')
    if len(base_ids)>1:
        proofs=[{local(s) for s in p['assertion']['storage']} for p in publication['premises'] if p['assertion']['kind']=='disjoint_storage']
        require(any(set(base_ids.values())<=proof for proof in proofs),'all independent source allocations have AIR separation evidence')
    def place(p):
        if p['kind']=='object':binding=objects[local(p['object'])]['storage'];return local(binding['region']),int(binding['offset']),int(binding['extent'])
        require(p['kind']=='region_slice','precise physical destination');return local(p['region']),int(p['offset']['value']['value']),int(p['length']['value']['value'])
    for expected in case['writes']:
        op=operations[operation(expected['statement'])];wanted=(base_ids[expected['base']],expected['offset'],expected['length'])
        if expected.get('copy'):
            require(op['kind']=='copy_bytes','explicit byte capture operation');require(int(op['length'])==expected['length'],'copy length')
            for key,base,start in [('source',expected['sourceBase'],expected['sourceOffset']),('destination',expected['base'],expected['offset'])]:
                r=op[key];require(local(r['region'])==base_ids[base] and int(r['offset']['value']['value'])==start,'copy physical correlation')
        else:
            require(op['kind']=='assign' and place(op['destination'])==wanted,'exact write footprint')
            literal=op['value']['value'];require(op['value']['kind']=='literal','literal effect witness')
            if literal['kind']=='bytes':require(list(base64.b64decode(literal['base64'],validate=True))==ibm_octets(expected['text']),'independent IBM1047 octet golden')
            else:require(literal['kind']=='text' and literal['value']==expected['text'],'exact logical literal before explicit encoding')
    require(len(sites)==len(case['calls'])==len(queries),'every CALL and physical query retained')
    for expected in case['calls']:
        op_id=operation(expected['statement']);site=sites[op_id];query=queries[op_id];target=case['nodes'][expected['node']];object_id=node_objects[expected['node']]
        require(local(site['subject'])==object_id==query['object'],'CALL uses exact published object identity')
        require([c['referenceName'] for c in site['candidates']]==[expected['text']],'independent dependency golden');require(site['modelValueRemainder']==expected['modelOpen'],'MAY timing/model remainder');require(site['sourceValueRemainder'],'source gaps are retained independently')
        require(query['values']==[expected['text']] and query['valueModelRemainder']==expected['modelOpen'],'typed regional values agree with independent golden')
        producer=operation(expected['producer']);require(query['producers']==[dict(value=expected['text'],operations=[producer])],'literal producer survives physical capture')
        require({local(s['producer']) for c in site['candidates'] for s in c['supports']}=={producer},'dependency supports keep the literal producer')
        rd_expected={operation(n) for n in expected['rd']};require({d['operation'] for d in query['definitions']}==rd_expected,'RD producer operations golden')
        require(query['rdModelRemainder']==expected['modelOpen'],'RD unknown/MAY remainder')
        wanted=dict(base=base_ids[target['base']],start=str(target['offset']),end=str(target['offset']+target['extent']),kind='BYTE_RANGE',activation=None)
        require(query['interpretations']==[wanted],'query physical interpretation')
        writes_by_statement={w['statement']:w for w in case['writes']}
        kinds={operation(i):('COPY' if writes_by_statement[i].get('copy') else 'ASSIGN') if i in writes_by_statement else 'UNKNOWN_WRITE' for i in expected['rd']}
        for definition in query['definitions']:
            require(definition['ranges']==[wanted],'RD surviving physical range')
            kind=kinds[definition['operation']];require(definition['kind']==kind and definition['unknown']==(kind=='UNKNOWN_WRITE'),'RD event kind/unknown golden')
        require(query['reachability']=='REACHABLE' and query['rdSourceRemainder'] and query['valueSourceRemainder'],'reached physical values retain source remainder')
    if case['books']:
        require(any(n['provenance']['includeChain'] for n in nodes),'COPY include chain survives layout');require(any(a['logicalName'].endswith('AREADEF.cpy') for a in publication['artifacts']),'copybook identity survives AIR')
    verify_cfg_wire(json.dumps(cfg).encode());control_oracle(air,cfg)
    return dict(calls=len(sites),candidates=sum(len(s['candidates']) for s in sites.values()),unknownTargets=0,sourceOpen=sum(s['sourceValueRemainder'] for s in sites.values()),modelOpen=sum(s['modelValueRemainder'] for s in sites.values()),qualifiedPhysicalQueries=len(queries),bases=len(base_ids),nodes=len(nodes),cells=case['cells'])

def run(work,runtime,names=None,attempts=2,permutations=True):
    require(not os.environ.get('CI'),'local qualification only');work.mkdir(parents=True,exist_ok=False);config=json.loads(runtime.read_text());require(config['semanticProductVersion']=='2.7.0','pinned SP runtime')
    source=ROOT/'analysis-adapters/src/test/java/io/github/gustavo2358/analysis/adapters/StorageE2eProbe.java';probe=work/'probe';probe.mkdir();shutil.copyfile(source,probe/source.name);classes=probe/'classes';classes.mkdir()
    command=['javac','-cp',os.pathsep.join(config['cfg']['classpath']),'-d',str(classes),str(probe/source.name)];write_json(probe/'compile.command.json',command)
    with (probe/'compile.stdout.log').open('w') as out,(probe/'compile.stderr.log').open('w') as err:require(subprocess.run(command,stdout=out,stderr=err).returncode==0,'test probe compilation')
    config['probe']=dict(main='io.github.gustavo2358.analysis.adapters.StorageE2eProbe',classpath=[str(classes)]+config['cfg']['classpath']);results={}
    selected={k:v for k,v in fixtures().items() if not names or k in names};require(bool(selected),'nonempty selected fixture set')
    for name,case in selected.items():
        cwd=work/name;cwd.mkdir();source=cwd/(name+'.cbl');source.write_text(case['source']);write_json(cwd/'independent-golden.json',{k:v for k,v in case.items() if k not in ('source','books')})
        for filename,content in case['books'].items():(cwd/filename).write_text(content)
        web=cwd/'src/main/resources';web.mkdir(parents=True);(web/'web').symlink_to(Path(config['checkouts']['proleap-poc'])/'src/main/resources/web',target_is_directory=True)
        snapshots=[];started=time.monotonic()
        for attempt in range(attempts):
            label=chr(ord('a')+attempt);out=cwd/label;out.mkdir()
            execute(cwd,'frontend-'+label,config,'frontend',['--source',source.name,'--copybooks',cwd,'--output',out/'sp','--storage-profile',PROFILE])
            sp=out/'sp/cobol-semantic-product.json';air=out/'air.json';cfg=out/'cfg.json';dependency=out/'dependency.json';probe=out/'probe.json'
            for stage,paths in [('lower',[sp,air]),('cfg',[air,cfg]),('dependency',[air,dependency]),('probe',[air,probe])]:execute(cwd,stage+'-'+label,config,stage,paths)
            results[name]=oracle(name,case,json.loads(sp.read_text()),json.loads(air.read_text()),json.loads(cfg.read_text()),read(dependency),json.loads(probe.read_text()))
            snapshots.append([p.read_bytes() for p in (sp,air,cfg,dependency,probe)])
        require(all(s==snapshots[0] for s in snapshots),'A/B byte identity across five boundaries '+name)
        if permutations:
            spdoc=json.loads(snapshots[0][0]);spdoc['statements'].reverse();spdoc['dataDeclarations'].reverse()
            for field in ['nodes','bases','views']:spdoc['storage'][field].reverse()
            perm=cwd/'permuted.sp.json';write_json(perm,spdoc);ap=cwd/'permuted.lower.air.json';execute(cwd,'sp-permutation',config,'lower',[perm,ap]);require(ap.read_bytes()==snapshots[0][1],'SP inventory permutation preserves canonical AIR '+name)
            a=json.loads(ap.read_text());a['publication']['storage'].reverse()
            for unit in a['publication']['units']:unit['objects'].reverse();unit['sequences'].reverse()
            ap=cwd/'permuted.air.json';write_json(ap,a)
            for stage,index in [('cfg',2),('dependency',3),('probe',4)]:
                dest=cwd/('permuted.'+stage+'.json');execute(cwd,stage+'-permutation',config,stage,[ap,dest]);require(dest.read_bytes()==snapshots[0][index],'AIR inventory permutation '+stage+' '+name)
        results[name]['elapsedSeconds']=round(time.monotonic()-started,3);print(name+': PASS '+json.dumps(results[name]),flush=True)
    write_json(work/'results.json',dict(status='W3_FOCAL_VERTICAL',runtime=str(runtime),sources=config['sources'],results=results))
    return results
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--work',type=Path,required=True);parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--cases',nargs='*');parser.add_argument('--attempts',type=int,default=2);parser.add_argument('--no-permutations',action='store_true');a=parser.parse_args();run(a.work.resolve(),a.runtime.resolve(),a.cases,a.attempts,not a.no_permutations)
