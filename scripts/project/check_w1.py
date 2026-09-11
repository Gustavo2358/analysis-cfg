#!/usr/bin/env python3
"""Execute CP5 W1 product gates with nominal reports, measured work and compiled boundary inventory."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
INVENTORY='docs/evals/cp6/w1d-w1-inventory.json'
PREFIX='io.github.gustavo2358.analysis.structure.'
TESTS={
 'AdmissionTest':set('realBuildIsAccepted missingBranchEdgeIsInvalid missingOrphanIsInvalid equalLookingReplacementSequenceIsInvalid equalLookingForeignSnapshotIsInvalid equalLookingForeignEntrySelectionIsInvalid duplicateAndWrongContextEdgesAreInvalid missingEntryExitHaltAndReplacedHaltAreInvalid foreignEntryNodeAndDuplicateSequenceRoleAreInvalid unsupportedProfileAndPolicyMismatchHaveDistinctTaxonomy incompleteUpstreamValidationDoesNotBecomeSemanticSizeOutcome'.split()),
 'StructureTest':set('offsetsBucketsAndPayloadAreCanonical multipleEntriesKeepEntryReturnAndContextSeparate sameTargetBranchKeepsTwoOutcomesAndBackwardEdges orphanIsIndexedWithoutInventedReachability sparseNodeIdsRoundTripWithoutDensePublicAssumption fullOwnersAndDisplayRenamingDoNotCollide physicalOrderDoesNotDefineControlAndSameSnapshotIsDeterministic emptySelectionCreatesNoContextsAndForeignHandleIsRejected cycleAndSelfLoopKeepLiteralControl'.split()),
 'ScaleTest':set('s1LongSequence s2WideDeclarations s4AdjacencyIsLinear s8DemandedContextsOnly s16EverySizeOfSameProfileIsAdmitted primitiveDirectoryRetainsSparseKeysAcrossGrowth'.split())}
SOURCES={f'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/structure/{name}.java' for name in ['AnalysisSession','ProgramIndex','IndexBuilder','ContextView','IndexMetrics','LongIntDirectory']}
DENIED=('java.io.','java.nio.file.','java.net.','java.lang.reflect.','io.github.gustavo2358.air.json.',
        'io.github.gustavo2358.air.validation.AirValidator','analysis.cfg.application.BuildCfg',
        'analysis.cfg.application.CfgBuildCoordinator','analysis.cfg.domain.CoreCfgProjection',
        'analysis.values.','analysis.solver.','analysis.consumers.','analysis.extraction.','cfg.adapters.','cfg.launcher.')

from check_architecture import GateFailure
class Failure(GateFailure): pass

def source_inventory(root:Path)->set[str]: return SOURCES

def verify_sources(root:Path)->None:
    from check_analysis_architecture import check_direct_air, direct_dependencies
    errors=check_direct_air(root)
    if errors: raise Failure('; '.join(errors))
    actual={p.relative_to(root).as_posix() for p in (root/'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/structure').rglob('*.java')}
    if actual!=SOURCES: raise Failure('W1 exact source inventory mismatch')
    deps=direct_dependencies(root/'analysis-kernel/pom.xml')
    if deps!={('io.github.gustavo2358.analysis','cfg-kernel','compile'),('io.github.gustavo2358','air-java','compile'),('org.junit.jupiter','junit-jupiter','test')}:
        raise Failure('W1 direct Maven DAG mismatch')
    for path in SOURCES:
        text=(root/path).read_text()
        if any(d in text for d in DENIED): raise Failure('W1 forbidden structural dependency: '+path)

def command(root:Path,args:list[str])->str:
    p=subprocess.run(args,cwd=root,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    print(p.stdout,end='',flush=True)
    if p.returncode: raise Failure('command failed exit '+str(p.returncode)+': '+repr(args))
    return p.stdout

def verify_reports(root:Path,names:set[str])->None:
    reports=list((root/'analysis-kernel/target/surefire-reports').glob('TEST-*.xml'))
    if {p.name for p in reports}!={'TEST-'+PREFIX+n+'.xml' for n in names}: raise Failure('W1 nominal report inventory mismatch')
    for path in reports:
        doc=ET.parse(path).getroot(); name=doc.attrib['name'].removeprefix(PREFIX)
        cases=doc.findall('testcase'); actual=[c.attrib['name'] for c in cases]
        if len(actual)!=len(TESTS[name]) or set(actual)!=TESTS[name]: raise Failure('W1 nominal method inventory mismatch: '+name)
        if any(c.attrib.get('classname')!=PREFIX+name or c.find('failure') is not None or c.find('error') is not None or c.find('skipped') is not None for c in cases):
            raise Failure('W1 missing/failed/skipped nominal test: '+name)
        if int(doc.attrib['tests'])!=len(actual) or any(int(doc.attrib.get(k,'0'))!=0 for k in ('failures','errors','skipped')): raise Failure('W1 report counters mismatch')

def verify_metrics(output:str)->list[dict]:
    rows=[json.loads(s) for s in re.findall(r'^W1_METRICS (\{.*\})$',output,re.M)]
    expected=set()
    for n in [10000,20000,100000,200000]: expected.add(('S1',1,n,1,1,1))
    for n in [1000,2000,10000]: expected.update([('S2',1,8,n,1,1),('S4',n,0,0,1,1)])
    for k in [1,2,20]: expected.add(('S8',100,8,8,k,min(k,2)))
    for n in [1000,2000,4000]:
        expected.update([('S16-nodes-edges',n,0,0,1,1),('S16-operations',1,n,1,1,1),('S16-objects',1,8,n,1,1),('S16-references',1,n,n,1,1)])
    keys=[tuple(r[k] for k in ['probe','sequences','instructions','objects','entries','selected']) for r in rows]
    if len(keys)!=len(expected) or set(keys)!=expected: raise Failure('W1 missing/duplicate/foreign scale measurements')
    for r in rows:
        s,i,d,k,selected=(r[x] for x in ['sequences','instructions','objects','entries','selected'])
        c=int(d>0); v=s+2*k; e=k*(s+1); buckets=(2 if s>1 else 1)+int(i>0)
        checks={'nodesIndexed':v,'edgesIndexed':e,'operationsIndexed':s+i,'objectsIndexed':d,'locationsIndexed':c,
                'referencesResolved':c+d+k+(i if d else 0),'structuralVisits':3+3*d+2*c+2*s+(4 if d else 1)*i+2*k+v+2*e+buckets,
                'queryEdgeReads':2*selected*(s+1),'retainedNodeHandles':v,'retainedSiteHandles':s+i,'additionalAirCfgPayloads':0}
        for field,value in checks.items():
            if r.get(field)!=value: raise Failure('W1 ledger/retention mismatch: '+field)
        if type(r.get('elapsedNanosObservation'))!=int or r['elapsedNanosObservation']<0: raise Failure('W1 missing elapsed observation')
        if type(r.get('retainedArraySlots'))!=int or r['retainedArraySlots']<=0: raise Failure('W1 missing retained graph walk')
    return rows

def architecture(root:Path, update:bool=False)->None:
    from check_transport_architecture import dependencies_from_jdeps
    verify_sources(root)
    classes=root/'analysis-kernel/target/classes'
    cpfile=root/'analysis-kernel/target/architecture-classpath.txt'
    if not cpfile.exists(): raise Failure('W1 compiled dependency/classpath evidence missing')
    cp=cpfile.read_text().strip()
    paths=sorted(p.relative_to(classes).as_posix() for p in (classes/'io/github/gustavo2358/analysis/structure').rglob('*.class'))
    if not paths: raise Failure('W1 classfiles absent')
    for path in paths:
        if struct.unpack('>IHH',(classes/path).read_bytes()[:8])!=(0xcafebabe,0,65): raise Failure('W1 requires Java 21 without preview')
    output=command(root,['jdeps','--multi-release','21','-filter:none','-verbose:class','-cp',cp,str(classes)])
    edges={k:v for k,v in dependencies_from_jdeps(output).items() if k.startswith(PREFIX)}
    for source,targets in edges.items():
        for target in targets:
            if any(d in target for d in DENIED): raise Failure('W1 forbidden bytecode dependency: '+source+' -> '+target)
            if not target.startswith(('java.',PREFIX,'io.github.gustavo2358.air.model.','io.github.gustavo2358.air.validation.ValidationOptions','io.github.gustavo2358.analysis.cfg.')):
                raise Failure('W1 dependency outside approved DAG: '+target)
    descriptors={}
    for path in paths:
        cls=path[:-6].replace('/','.')
        descriptors[cls]=command(root,['javap','-classpath',str(classes)+os.pathsep+cp,'-public','-s',cls])
    tree=(root/'analysis-kernel/target/architecture-dependencies.tgf').read_text()
    from check_architecture import parse_tgf
    coordinates=set(parse_tgf(root/'analysis-kernel/target/architecture-dependencies.tgf'))
    expected={'io.github.gustavo2358.analysis:analysis-kernel:jar:0.1.0-SNAPSHOT','io.github.gustavo2358.analysis:cfg-kernel:jar:0.1.0-SNAPSHOT:compile','io.github.gustavo2358:air-java:jar:0.1.0-SNAPSHOT:compile'}
    if coordinates!=expected: raise Failure('W1 effective Maven graph mismatch: '+repr(coordinates))
    actual={'sources':sorted(SOURCES),'classfiles':paths,'jdeps_edges':{k:sorted(v) for k,v in sorted(edges.items())},'javap_descriptors':descriptors,'effective_maven':sorted(coordinates)}
    inv=root/INVENTORY
    if update: inv.write_text(json.dumps(actual,indent=2)+'\n')
    elif json.loads(inv.read_text())!=actual: raise Failure('W1 compiled architecture inventory drift')
    print('[w1-architecture] PASS: direct dependencies, sources, classfiles, javap/jdeps and effective Maven DAG')

def run(root:Path,category:str)->None:
    verify_sources(root)
    maven=['mvn','-B','-ntp']
    if category=='architecture':
        command(root,maven+['-DskipTests','package','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree','-Dscope=compile','-DoutputType=tgf','-DoutputFile=target/architecture-dependencies.tgf','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=compile','-Dmdep.outputFile=target/architecture-classpath.txt'])
        architecture(root)
    else:
        names=set(TESTS) if category=='performance' else {'AdmissionTest','StructureTest'}
        out=command(root,maven+['-pl','analysis-kernel','-am','clean','test','-Dtest=BuildCfgContractTest,'+','.join(sorted(names)),'-Dsurefire.failIfNoSpecifiedTests=false'])
        verify_reports(root,names)
        if category=='performance':
            rows=verify_metrics(out); path=root/'.harness-results/w1-performance.json';path.parent.mkdir(exist_ok=True)
            path.write_text(json.dumps({'scope':'W1 structural dimension; later Waves execute separately','role':'OBSERVATION_ONLY','memory_method':'identity-deduplicated logical retained containers and arrays; not JVM bytes/backing map nodes','measurements':rows},indent=2)+'\n')
        print('[w1-'+category+'] PASS: '+str(sum(len(TESTS[n]) for n in names))+' nominal tests, zero skips; runtime product executed')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('category',choices=['architecture','semantic','performance']);parser.add_argument('--root',type=Path,default=ROOT)
    args=parser.parse_args()
    try: run(args.root.resolve(),args.category)
    except (Failure,ValueError,OSError,ET.ParseError) as e: print('[w1] FAIL: '+str(e),file=sys.stderr);sys.exit(1)
