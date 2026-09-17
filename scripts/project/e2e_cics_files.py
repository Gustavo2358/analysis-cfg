#!/usr/bin/env python3
"""FD-W8 independent source oracles: C-FC targets/context, output direction, CALL and memory."""
import argparse,sys
from pathlib import Path
from e2e_file_dependencies import run,ROOT,source_spans
from e2e_file_memory import PROFILE
from dependency_wire import require
FIXTURES=ROOT/'analysis-adapters/src/test/resources/file-dependencies/w8'
BASIC=('read','write','rewrite','delete','startbr','readnext','readprev','resetbr','endbr','unlock','inquire','set')
MIXED={'read-call':'read','write-call':'write','length-open':'read','response':'read','set-pointer':'read','browse-id':'startbr','system-two':'endbr','inquire-next':'inquire','inquire-start':'inquire','inquire-end':'inquire','set-alias':'set','handler-open':'read','sysid-short':'endbr','file-slice':'endbr'}
COMPUTED={'computed-alias':(['ACCOUNTS'],False,['R001']), 'computed-cycle':(['ACCOUNTS','CUSTOMER'],False,['R001']), 'computed-exact':(['ACCOUNTS'],False,['R001']), 'computed-closed':(['ACCOUNTS','CUSTOMER'],False,['R001']), 'computed-partial':(['ACCOUNTS'],True,['R001']), 'computed-unknown':([],True,['R001']), 'computed-systems':(['ACCOUNTS'],False,['R001','R002']), 'computed-timing':([],False,[])}
EXPECTED={name:None for name in (*BASIC,*MIXED,*COMPUTED,'host-and-output','resp-call','not-files','literal-systems','read-dataset-literal','read-dataset-computed','read-file-computed')}
def names(site):return sorted(c['referenceName'] for c in site['candidates'])
def operation(site,air):return next(s['terminator'] for u in air['publication']['units'] for s in u['sequences'] if s['terminator']['header']['id']==site['operation'])
def oracle(name,sp,air,result,source):
    require(sp['contractVersion']=='2.28.0' and sp['fileInventory']['version']=='1.6.0','bilateral SP2.28 contract')
    f=result['fileDependencies'];sites=f['sites'];require(not f['declarations'],'CICS needs no SELECT/FD')
    expected_count=0 if name=='not-files' else 3 if name=='literal-systems' else 2 if name in ('computed-timing','host-and-output') else 1
    require(len(sites)==expected_count,'source FILE occurrence inventory '+name)
    expected_calls=1 if name in BASIC or name in ('host-and-output','resp-call') else 2 if name in MIXED or (name.startswith('read-dataset-') or name=='read-file-computed') else 0
    require(len(result['sites'])==expected_calls,'CALL/LINK inventory '+name)
    if name=='not-files':return
    for s in sites:
        require(s['namespace']=='cics.file' and not s['bindings'],'C-FC source namespace independent of native declarations')
        require(s['context'] is not None,'source system selection context')
        for c in s['candidates']:source_spans(result,c['supports'][0],source)
    if name.startswith('read-dataset-') or name=='read-file-computed':
        site=sites[0];fact=next(s for s in sp['statements'] if s['variant']=='CICS_FILE_CONTROL')
        option=next(o for o in fact['options'] if o['canonicalName']=='FILE')
        spelling='FILE' if name=='read-file-computed' else 'DATASET'
        require(option['name']==spelling and option['role']=='READ','original spelling/canonical direction')
        require(fact['rawText'][option['start']:option['end']].startswith(spelling+'('),'exact source spelling offsets')
        require(site['action']=='read' and names(site)==['ACCOUNTS'],'independent READ DATASET target oracle')
        require(site['targetKind']==('LITERAL' if name.endswith('literal') else 'COMPUTED'),'same literal/computed FILE contract')
        if name.endswith('literal'):require(not site['unknownRemainder'],'literal source name does not need solver')
        else:
            require(site['valuePoint']['position']=='BEFORE','computed FILE sampled at command')
            # This witness contains local open CALLs: W9's independent control oracle
            # requires model remainder even when the MOVE candidate is retained.
            require('FILE_MODEL_VALUE_REMAINDER' in site['analysisReasons'] and 'FILE_SOURCE_VALUE_REMAINDER' in site['analysisReasons'],'local open CALL and partial source remain explicit')
        require(result['sites'][0]['modelValueRemainder'],'READ invalidates old buffer singleton')
        require('KEEPNAME' in names(result['sites'][1]),'disjoint CALL evidence survives READ')
        require(not f['declarations'] and all(x['namespace']=='cics.file' for x in f['sites']),'no physical or native declaration inferred')
        forbidden={'bindingMechanism','physicalResource','physicalResolution','DSNAME','dsname'}
        def guard(value):
            if isinstance(value,dict):
                require(not(set(value)&forbidden),'no external resolution field')
                for v in value.values():guard(v)
            elif isinstance(value,list):
                for v in value:guard(v)
        guard(result)
    if name in BASIC:
        require(sites[0]['action']==name and names(sites[0])==['ACCOUNTS'] and not sites[0]['unknownRemainder'],'literal C-FC '+name)
        require(names(result['sites'][0])==['AFTER'],'CALL remains independent')
    if name in MIXED:
        require(sites[0]['action']==MIXED[name],'typed command action')
        if name.startswith('inquire-'):require(not sites[0]['candidates'] and sites[0]['unknownRemainder'],'SPI output/start/end does not read old name')
        elif name=='file-slice':require('OLDNAME' in names(sites[0]),'computed source slice value')
        else:require(names(sites[0])==['ACCOUNTS'] and not sites[0]['unknownRemainder'],'literal FILE independent of memory gaps')
        if name in ('browse-id','system-two'):require(names(sites[0]['context'])==(['R001'] if name=='browse-id' else ['R002']),'distinct explicit systems')
        elif name=='sysid-short':require(sites[0]['context']['selection']=='EXPLICIT' and sites[0]['context']['unknownRemainder'] and not sites[0]['context']['candidates'],'unproved system width never default')
        elif name!='file-slice':require(sites[0]['context']['selection']=='DEFAULT','absence is default selection, not local system proof')
        if name=='write-call':require('OLDNAME' in names(result['sites'][0]) and 'KEEPNAME' in names(result['sites'][1]),'FROM/input effects preserve CALL evidence')
        if name in ('read-call','length-open'):require(result['sites'][0]['modelValueRemainder'],'READ/unknown footprint never old exact singleton')
        if name=='set-alias':require(all(c['referenceName']!='ADMIN.ONLY' for s in sites for c in s['candidates']),'DSNAME administrative argument never FILE target')
        if name=='browse-id':require(operation(sites[0],air)['arguments'][2]['value']['value']=={'kind':'int','value':'5'},'browse REQID input preserved')
        if name=='response':require(operation(sites[0],air)['effectBound']['perOutcome'][0]['outcome']=={'kind':'normal'},'response MUST only on return')
    if name in COMPUTED:
        if name=='computed-timing':
            pairs={(tuple(names(s)),tuple(names(s['context']))) for s in sites}
            require(pairs=={(('ACCOUNTS',),('R001',)),(('CUSTOMER',),('R002',))},'FILE and SYSID sampled before each source command')
        else:
            expected,remainder,systems=COMPUTED[name];s=sites[0]
            require(names(s)==expected and ('FILE_MODEL_VALUE_REMAINDER' in s['analysisReasons'])==remainder,'independent four-state model FILE oracle '+name)
            require(names(s['context'])==systems and 'CICS_SYSID_MODEL_VALUE_REMAINDER' not in s['context']['analysisReasons'],'independent system model projection '+name)
        # Independent AIR law, tested by the closed/partial coverage counterproof:
        # a partial source inventory cannot certify source closure even when the model set is closed.
        require(sp['entryInventory']['scope']=='PRIMARY_ONLY' and sp['entryInventory']['status']=='PARTIAL' and 'ALTERNATE_ENTRIES_NOT_PROJECTED' in sp['entryInventory']['gapCodes'],'producer primary-entry coverage limit retained')
        require(air['publication']['coverage']['inventory']=='PARTIAL' and air['publication']['coverage']['uncertainties'],'AIR source coverage remains open')
        require(all(s['unknownRemainder'] and 'FILE_SOURCE_VALUE_REMAINDER' in s['analysisReasons'] for s in sites),'source remainder cannot be hidden by known model candidates')
        require(all(s['context']['unknownRemainder'] and 'CICS_SYSID_SOURCE_VALUE_REMAINDER' in s['context']['analysisReasons'] for s in sites),'system source remainder is independent of model names')
        require(f['metrics']['possibleValuesPreparations']==1,'one shared general FILE/SYSID provider')
        require(all(s['valuePoint']['position']=='BEFORE' and s['context']['valuePoint']['position']=='BEFORE' for s in sites),'command-point timing')
    if name=='host-and-output':
        read=next(s for s in sites if s['action']=='read');out=next(s for s in sites if s['action']=='inquire')
        require(names(read)==['ACCOUNTS'] and names(read['context'])==['R001'],'computed source input independent of subsequent effects')
        require(not out['candidates'] and out['unknownRemainder'],'NEXT output never old FILE input')
        require(names(result['sites'][0])==['PGM'],'LINK remains program category')
    if name=='literal-systems':
        require(all(names(s)==['ACCOUNTS'] for s in sites),'same source FILE remains known')
        require({(s['context']['selection'],tuple(names(s['context']))) for s in sites}=={('DEFAULT',()),('EXPLICIT',('R001',)),('EXPLICIT',('R002',))},'default and explicit systems never fused')
        require(f['metrics']['possibleValuesPreparations']==0,'literal context needs no values solver')
    if name=='resp-call':require('OLD1' not in names(result['sites'][0]) and result['sites'][0]['modelValueRemainder'],'response write on return kills old response value')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work',type=Path,required=True);p.add_argument('--producers',type=Path,required=True);p.add_argument('--runtime',type=Path);a=p.parse_args()
    try:run(a.work.resolve(),a.producers.resolve(),runtime_config=a.runtime,fixtures=FIXTURES,expected=EXPECTED,check=oracle,label='FD-W8 C-FC',frontend_args=PROFILE)
    except (ValueError,RuntimeError,OSError) as error:print('FAIL: '+str(error),file=sys.stderr);sys.exit(1)
