#!/usr/bin/env python3
"""W2 executable nominal gates, measured work/retention and compiled solver/SPI boundary."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import re
import struct
import sys
import xml.etree.ElementTree as ET
from check_w1 import ROOT, Failure, command

PREFIX='io.github.gustavo2358.analysis.solver.'
INVENTORY='docs/evals/cp5/w2-inventory.json'
NAMES=['Direction','AnalysisPoint','AnalysisDefinition','DomainWork','SolverMetrics','DataflowResult','SolverTopology','DataflowSolver','IntWorklist']
SOURCES={f'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/solver/{name}.java' for name in NAMES}
TESTS={
 'SolverTest':set('forwardBoundaryBlockAndEdgeHaveProgramOrderMeaning'.split()),
 'OracleTest':set('generatedForwardBackwardCorpusMatchesIndependentOraclesAndSchedules backwardAnchorsAndReverseOperationOrderAreObservable monotoneButWrongSharedTransferIsRejectedByConcreteInclusion alwaysTopPassesInclusionButFailsMinimumPrecision validForwardTransferPassesConcreteAndPrecisionOracle distinctContextsRemainIsolatedAndUnselectedEntriesCreateNoPoints finiteDomainLawsAndNonIdempotentBlockTransfer'.split()),
 'SolverPropertiesTest':set('firstBottomPublicationAndExitlessSccAreProcessed selfLoopReenqueuesAfterMembershipIsCleared multipleBoundariesJoinOnceOnCorrectSideInBothDirections coalescedArrivalsSuppressDuplicateEnqueueAndSubsumedJoins equivalentFreshRootsStopPropagationAndKeepPublishedRoot failuresExposeNoStableResultAndForeignBoundaryIsRejected emptySelectionIsStableWithoutPhantomStates'.split()),
 'SolverScaleTest':set('s4bStaggeredFanInHasLinearDeliveriesAndQuadraticRecompositionReads s4AndS16LinearAndCycleSizesRemainStableInBothDirections s8ResultRetainsOnlyEffectivePointsAndTwoRootArrays'.split())}
METRICS=set('analysisPoints contextualEdges boundaryJoins initializationAttempts worklistAttempts worklistPushes nodesPopped duplicatePushesSuppressed maxWorklistSize nodesTransferred operationsTransferred firstPublications publishedStatesChanged publishedStatesUnchanged edgeTransferInvocations edgeContributionJoins accumulatorStatesChanged accumulatorStatesUnchanged predecessorContributionReads successorContributionReads joinEntriesVisited stateCompareEntries'.split())
DENIED=('io.github.gustavo2358.air.','analysis.values','analysis.consumers','analysis.extraction','cfg.adapters','cfg.launcher','cfg.application.BuildCfg','java.io.','java.nio.file.','java.net.','java.lang.reflect.','ServiceLoader','lower.','cobolexplorer','org.antlr')

def verify_sources(root:Path)->None:
    from check_w1 import SOURCES as STRUCTURAL, verify_sources as structural
    structural(root)
    actual={p.relative_to(root).as_posix() for p in (root/'analysis-kernel/src/main/java').rglob('*.java')}
    from check_w3 import QUERY_SOURCES
    from check_w4 import KERNEL_SOURCES
    if actual!=STRUCTURAL|SOURCES|QUERY_SOURCES|KERNEL_SOURCES: raise Failure('W2 exact source inventory mismatch')
    for path in SOURCES:
        source=(root/path).read_text()
        if any(d in source for d in DENIED): raise Failure('W2 forbidden solver/SPI dependency: '+path)

def verify_reports(root:Path,names:set[str])->None:
    reports=list((root/'analysis-kernel/target/surefire-reports').glob('TEST-*.xml'))
    if {p.name for p in reports}!={'TEST-'+PREFIX+n+'.xml' for n in names}: raise Failure('W2 nominal report inventory mismatch')
    for path in reports:
        report=ET.parse(path).getroot(); name=report.attrib['name'].removeprefix(PREFIX); cases=report.findall('testcase')
        actual=[c.attrib['name'] for c in cases]
        if len(actual)!=len(TESTS[name]) or set(actual)!=TESTS[name]: raise Failure('W2 nominal method inventory mismatch: '+name)
        if any(c.attrib.get('classname')!=PREFIX+name or any(c.find(k) is not None for k in ['failure','error','skipped']) for c in cases): raise Failure('W2 failed/skipped/foreign nominal test')
        if int(report.attrib['tests'])!=len(actual) or any(int(report.attrib.get(k,'0')) for k in ['failures','errors','skipped']): raise Failure('W2 report counters mismatch')

def verify_corpus(output:str)->dict:
    rows=[json.loads(s) for s in re.findall(r'^W2_CORPUS (\{.*\})$',output,re.M)]
    if len(rows)!=1 or rows[0]!={'graphs':80,'directions':2,'schedules':4,'comparisons':640,'contextualPoints':1284,'concreteChecks':640,'seedBase':19073}:
        raise Failure('W2 generated corpus nominal evidence missing/changed: '+repr(rows))
    return rows[0]

def verify_metrics(output:str)->list[dict]:
    rows=[json.loads(s) for s in re.findall(r'^W2_METRICS (\{.*\})$',output,re.M)]
    expected={('S4b',n,None) for n in [1000,2000,4000,10000]}
    expected|={(probe,n,direction) for probe in ['S4-S16-linear','S4-S16-cycle'] for n in [1000,2000,4000,10000] for direction in ['FORWARD','BACKWARD']}
    expected|={('S8',n,None) for n in [1,2,20]}
    keys=[(r['probe'],r['N'],r.get('direction')) for r in rows]
    if len(keys)!=len(expected) or set(keys)!=expected: raise Failure('W2 missing/duplicate/foreign scale measurements')
    for r in rows:
        if not METRICS<=set(r) or any(type(r[k])!=int or r[k]<0 for k in METRICS): raise Failure('W2 missing/invalid runtime counter')
        checks=[r['status']=='STABLE',r['firstPublications']==r['analysisPoints'],r['nodesTransferred']==r['nodesPopped'],r['worklistPushes']==r['nodesPopped'],r['edgeContributionJoins']==r['edgeTransferInvocations'],r['predecessorContributionReads']==0,r['successorContributionReads']==0,r['worklistAttempts']==r['initializationAttempts']+r['accumulatorStatesChanged'],r['worklistPushes']+r['duplicatePushesSuppressed']==r['worklistAttempts'],r['maxWorklistSize']<=r['analysisPoints'],r['retainedRootArrays']==2,r['retainedRootSlots']==2*r['analysisPoints'],r['retainedPointHandles']==r['analysisPoints'],type(r['elapsedNanosObservation'])==int,r['elapsedNanosObservation']>=0]
        if r['probe']=='S4b':
            n=r['N']; checks += [r['joinPointDeliveries']==n,r['edgeContributionJoins']==4*n,r['worklistPushes']==4*n+2,r['slowJoinPredecessorReads']==n*(n+1),r['slowPredecessorReads']==r['slowJoinIntoCalls'],r['slowWorklistPushes']==r['slowWorklistPops']]
        if not all(checks): raise Failure('W2 work/retention ledger mismatch: '+repr(r))
    return rows

def architecture(root:Path,update:bool=False)->None:
    from check_transport_architecture import dependencies_from_jdeps
    from check_architecture import parse_tgf
    verify_sources(root)
    classes=root/'analysis-kernel/target/classes'; cp=(root/'analysis-kernel/target/architecture-classpath.txt').read_text().strip()
    paths=sorted(p.relative_to(classes).as_posix() for p in (classes/Path(PREFIX.replace('.','/'))).rglob('*.class'))
    if not paths: raise Failure('W2 classfiles absent')
    for path in paths:
        if struct.unpack('>IHH',(classes/path).read_bytes()[:8])!=(0xcafebabe,0,65): raise Failure('W2 requires Java 21 without preview')
    output=command(root,['jdeps','--multi-release','21','-filter:none','-verbose:class','-cp',cp,str(classes)])
    edges={k:v for k,v in dependencies_from_jdeps(output).items() if k.startswith(PREFIX)}
    for source,targets in edges.items():
        for target in targets:
            if any(d in target for d in DENIED): raise Failure('W2 forbidden solver/SPI bytecode dependency: '+source+' -> '+target)
            if not target.startswith(('java.',PREFIX,'io.github.gustavo2358.analysis.structure.','io.github.gustavo2358.analysis.cfg.domain.CfgTransition')): raise Failure('W2 dependency outside solver DAG: '+target)
    descriptors={}
    for path in paths:
        cls=path[:-6].replace('/','.'); descriptors[cls]=command(root,['javap','-classpath',str(classes)+os.pathsep+cp,'-public','-s',cls])
    actual={'sources':sorted(SOURCES),'classfiles':paths,'jdeps_edges':{k:sorted(v) for k,v in sorted(edges.items())},'javap_descriptors':descriptors,'effective_maven':sorted(parse_tgf(root/'analysis-kernel/target/architecture-dependencies.tgf'))}
    if update: (root/INVENTORY).write_text(json.dumps(actual,indent=2)+'\n')
    elif json.loads((root/INVENTORY).read_text())!=actual: raise Failure('W2 compiled architecture inventory drift')
    print('[w2-architecture] PASS: opaque solver/SPI, exact sources/classfiles/javap/jdeps, W1 DAG preserved')

def run(root:Path,category:str)->None:
    verify_sources(root)
    maven=['mvn','-B','-ntp']
    if category=='architecture':
        command(root,maven+['-DskipTests','package','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree','-Dscope=compile','-DoutputType=tgf','-DoutputFile=target/architecture-dependencies.tgf','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=compile','-Dmdep.outputFile=target/architecture-classpath.txt']); architecture(root)
    else:
        names=set(TESTS) if category=='performance' else set(TESTS)-{'SolverScaleTest'}
        output=command(root,maven+['-pl','analysis-kernel','-am','clean','test','-Dtest=BuildCfgContractTest,'+','.join(sorted(names)),'-Dsurefire.failIfNoSpecifiedTests=false'])
        verify_reports(root,names); corpus=verify_corpus(output)
        if category=='performance':
            rows=verify_metrics(output); path=root/'.harness-results/w2-performance.json'; path.parent.mkdir(exist_ok=True)
            path.write_text(json.dumps({'scope':'W2 synthetic domains; W3-W5 unavailable','role':'OBSERVATION_ONLY','corpus':corpus,'measurements':rows,'retention':'identity walk of result-owned roots/maps/points; no physical heap byte claim'},indent=2)+'\n')
        print('[w2-'+category+'] PASS: '+str(sum(len(TESTS[n]) for n in names))+' nominal tests, no skips; actual product solver executed')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('category',choices=['architecture','semantic','performance']); parser.add_argument('--root',type=Path,default=ROOT); args=parser.parse_args()
    try: run(args.root.resolve(),args.category)
    except (Failure,ValueError,OSError,KeyError,ET.ParseError) as e: print('[w2] FAIL: '+str(e),file=sys.stderr);sys.exit(1)
