#!/usr/bin/env python3
"""W3 nominal semantic, scale, retained-object and compiled architecture checks."""
from __future__ import annotations
import argparse, json, os, re, struct, sys
from pathlib import Path
import xml.etree.ElementTree as ET
from check_w1 import ROOT, Failure, command

PREFIX='io.github.gustavo2358.analysis.values.'
QUERY_PREFIX='io.github.gustavo2358.analysis.query.'
VALUE_NAMES='Candidates SupportSet PersistentBindings PossibleValuesState ValuesWork ValueUniverse TextProfile ValueFact PossibleValuesAnalysis'.split()
QUERY_NAMES='ProgramPoint PointQuery ObservationBatch BatchReplayer'.split()
VALUE_SOURCES={f'analysis-values/src/main/java/io/github/gustavo2358/analysis/values/{n}.java' for n in VALUE_NAMES}
QUERY_SOURCES={f'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/query/{n}.java' for n in QUERY_NAMES}
INVENTORY='docs/evals/cp5/w3-inventory.json'
TESTS={
'SupportSourceTest':set('producerSurvivesBlocksAndStrongUpdateKillsOldSupport equalCandidateDiamondUnionsBothProducers entrySeedPremisesFollowValueAndAreKilledByAssignment entryUncertaintyOpensOnlyThatEntryWithoutChangingModel relevantAliasSourceGapCannotDisappearByQueryingExactAlias sourceGapDoesNotLeakFromOtherCellOrDependencyDimension candidateSupportsStayAssociatedWithTheirValue supportGrowthAtFixedValuePropagatesThroughCycle supportLatticeLawsAndStrongUpdatesRemainFinite sameCellInitialConditionsRetainBothPremises supportCardinalityPreservesAllEqualValueProducers'.split()),
'DomainTest':set('missingStrongUpdateAndJoinHaveIndependentExpected finiteLatticeLawsAndAssignmentMonotonicity persistentUpdatesShareAndRetainNoHistory allFiniteCandidatesSurviveAndUnionConverges'.split()),
'ValuesTest':set('realVerticalMixedBatchHasOneObservationPerLogicalQuery partialSourceIsSeparateFromExactModelAndUnknownWitness diamondsMissingPathsStrongUpdatesAndOtherCells sameCellAliasesAndDisjointnessAreSemanticAdmission unsupportedReadAndIndirectStorageAreNotIdentity entrySeedsContextsUnreachableAndUnicodeKeepTheirIdentity'.split()),
'ReplayTest':set('backwardReplayUsesStableOutAndReverseSuffixOnce controlledObservationFailureIsAtomicAndPreservesStableRun foreignStableRunCannotMasqueradeAsUnreachableInAnotherSession'.split()),
'ValueOracleTest':set('generatedRealAirMatchesIndependentRecompositionAndConcreteOracle concreteAndPrecisionOraclesRejectSharedWrongOrAlwaysUnknownAnswers'.split()),
'ValuesScaleTest':set('longSequenceDoesNotRetainInstructionStatesOrDeadCandidates unusedObjectInventoryDoesNotMultiplySparseState manyLiveBindingsShareAcrossWritesAndBlockBoundaries branchCandidatesAndWideFanInPreserveEveryFiniteValue denseDuplicateQueriesShareSingleReplayAndNoStateRoots unicodePoolCostFollowsBoundaryTextAndRetainsNoGlobalInterning staggeredWideFanInCountsRealDeliveriesAndBindingWork'.split())}
DENIED=('analysis.consumers','analysis.extraction','cfg.adapters','cfg.launcher','cfg.application.BuildCfg','cfg.application.CfgBuildCoordinator','java.io.','java.nio.file.','java.net.','java.lang.reflect.','ServiceLoader','String.intern','CALL','CallResolver','ProgramTarget','COBOL','CICS','GRBE','DB2')

def verify_sources(root:Path)->None:
    from check_analysis_architecture import check_direct_air,direct_dependencies
    if check_direct_air(root):raise Failure('W3 requires direct air-java dependency')
    actual={p.relative_to(root).as_posix() for p in (root/'analysis-values/src/main').rglob('*.java')}
    query={p.relative_to(root).as_posix() for p in (root/'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/query').rglob('*.java')}
    if actual!=VALUE_SOURCES or query!=QUERY_SOURCES:raise Failure('W3 exact source inventory mismatch')
    expected={('io.github.gustavo2358.analysis',n,'compile') for n in ['analysis-kernel','cfg-kernel']}|{('io.github.gustavo2358','air-java','compile'),('org.junit.jupiter','junit-jupiter','test')}
    if direct_dependencies(root/'analysis-values/pom.xml')!=expected:raise Failure('W3 direct Maven DAG mismatch')
    for path in VALUE_SOURCES|QUERY_SOURCES:
        source=(root/path).read_text()
        if any(token in source for token in DENIED):raise Failure('W3 forbidden dependency: '+path)
        if path in QUERY_SOURCES and 'analysis.values' in source:raise Failure('generic query cannot depend on values')
        if path in QUERY_SOURCES and 'DataflowSolver' in source:raise Failure('W3 replay cannot rerun solver')
        if path.endswith('PossibleValuesAnalysis.java') and 'DataflowSolver' in source[source.index('public static final class Execution'):]:raise Failure('W3 observation cannot rerun solver')
    import hashlib
    baseline=json.loads((root/'docs/work/evidence/WORK-CFG-028/wave-3/baseline.json').read_text())
    for path,digest in baseline['w2_production_sha256'].items():
        if hashlib.sha256((root/path).read_bytes()).hexdigest()!=digest:raise Failure('W3 modified approved W1/W2 production: '+path)

    reviewed=json.loads((root/'docs/work/evidence/WORK-CFG-028/wave-3/review-f1-f2/baseline.json').read_text())
    for path,digest in reviewed['frozen_sha256'].items():
        if hashlib.sha256((root/path).read_bytes()).hexdigest()!=digest:raise Failure('W3 F1/F2 changed reviewed foundation: '+path)

def verify_reports(root:Path,names:set[str])->None:
    reports=list((root/'analysis-values/target/surefire-reports').glob('TEST-*.xml'))
    if {p.name for p in reports}!={'TEST-'+PREFIX+n+'.xml' for n in names}:raise Failure('W3 nominal report inventory mismatch')
    for path in reports:
        report=ET.parse(path).getroot();name=report.attrib['name'].removeprefix(PREFIX);cases=report.findall('testcase')
        if {c.attrib['name'] for c in cases}!=TESTS[name] or len(cases)!=len(TESTS[name]):raise Failure('W3 nominal method inventory mismatch')
        if any(c.attrib['classname']!=PREFIX+name or any(c.find(k) is not None for k in ['failure','error','skipped']) for c in cases):raise Failure('W3 failed/skipped nominal test')
        if int(report.attrib['tests'])!=len(cases) or any(int(report.attrib.get(k,'0')) for k in ['failures','errors','skipped']):raise Failure('W3 report counters mismatch')

def verify_corpus(output:str)->dict:
    rows=[json.loads(s) for s in re.findall(r'^W3_CORPUS (\{.*\})$',output,re.M)]
    if rows!=[dict(graphs=48,points=213,seedBase=27001,independentOracles=2)]:raise Failure('W3 independent corpus missing or changed')
    return rows[0]

def verify_metrics(output:str)->list[dict]:
    rows=[json.loads(s) for s in re.findall(r'^W3_METRICS (\{.*\})$',output,re.M)]
    expected={('S1',n) for n in [10000,20000,100000,200000]}|{('S2-S16',n) for n in [1000,2000,4000,10000]}|{('S7-S4b',n) for n in [8,9,100,1000,2000,4000,10000]}|{(p,n) for p in ['S3-sequence','S3-chain','S4b-wide','S6-S16','S9'] for n in [1000,2000,4000]}
    if len(rows)!=len(expected) or {(r['probe'],r['N']) for r in rows}!=expected:raise Failure('W3 scale rows missing/duplicate/foreign')
    required=set('analysisPoints operationsTransferred predecessorContributionReads edgeContributionJoins queryRequests uniqueQueries sequencesReplayed operationsReplayed queriesAnswered unsupportedQueries queriesNotMaterialized observationFailures modelOpenResults sourceOpenResults effectiveOpenResults closedInModelResults retained_PossibleValuesState retained_Node retained_Object[] retained_arraySlots solve_stateAllocations solve_persistentNodesAllocated solve_maxSparseBindings solve_joinEntriesVisited solve_stateBytesAllocatedEstimate prepare_valuesInterned prepare_poolHits prepare_unicodeScalarsHashed replay_bindingLookups'.split())
    for r in rows:
        if not required<=set(r) or any(type(r[k])!=int or r[k]<0 for k in required):raise Failure('W3 missing/invalid metric')
        if r['uniqueQueries']!=r['queriesAnswered']+r['unsupportedQueries']+r['queriesNotMaterialized'] or r['predecessorContributionReads']!=0 or r['retained_Object[]']!=2 or r['retained_arraySlots']<2*r['analysisPoints']:raise Failure('W3 runtime ledger mismatch')
        if any(r.get('batchRetained_'+name,0) for name in ['PossibleValuesState','Node','ValueUniverse','SupportSet']):raise Failure('W3 observation retains state/history')
        n=r['N'];probe=r['probe']
        if probe=='S2-S16' and (r['solve_maxSparseBindings']!=1 or r['retained_Node']!=1):raise Failure('W3 dense inventory state')
        if probe=='S6-S16' and (r['operationsReplayed']!=n or r['sequencesReplayed']!=1 or r['uniqueQueries']!=n or r['queryRequests']!=2*n):raise Failure('W3 replay-per-query or lost query')
        if probe.startswith('S3') and r['solve_persistentNodesAllocated']>=40*n:raise Failure('W3 full-state clone allocation')
        if probe=='S7-S4b' and (r['prepare_valuesInterned']!=n or r['solve_candidateCardinality']!=n or r['modelOpenResults']!=0):raise Failure('W3 candidate truncation/remainder')
    staggered=[json.loads(s) for s in re.findall(r'^W3_STAGGERED (\{.*\})$',output,re.M)]
    if {r['N'] for r in staggered}!={1000,2000,4000} or len(staggered)!=3:raise Failure('W3 wide staggered probe missing')
    for r in staggered:
        if r['deliveries']!=r['N'] or r['joinTransfers']!=r['N']+1 or r['bindings']!=r['N']//125 or r['predecessorContributionReads']!=0 or r['joinEntriesVisited']<=r['N']*r['bindings']:raise Failure('W3 staggered work mismatch')
    support_rows=verify_support_metrics(output)
    return rows

def verify_support_metrics(output:str)->list[dict]:
    rows=[json.loads(s) for s in re.findall(r'^W3_SUPPORT_METRICS (\{.*\})$',output,re.M)]
    if len(rows)!=4 or {r['N'] for r in rows}!={1000,2000,4000,10000}:raise Failure('W3 support scale missing')
    for r in rows:
        if r['candidates']!=1 or r['supports']!=r['N'] or r['supportUnionEntriesVisited']<=0 or r['supportBytesAllocatedEstimate']<=0:raise Failure('W3 support loss or unmeasured union')
    return rows

def architecture(root:Path,update:bool=False)->None:
    from check_transport_architecture import dependencies_from_jdeps
    from check_architecture import parse_tgf
    verify_sources(root);actual={}
    for module,prefix,sources in [('analysis-kernel',QUERY_PREFIX,QUERY_SOURCES),('analysis-values',PREFIX,VALUE_SOURCES)]:
        classes=root/module/'target/classes';cp=(root/module/'target/architecture-classpath.txt').read_text().strip()
        paths=sorted(p.relative_to(classes).as_posix() for p in (classes/Path(prefix.replace('.','/'))).rglob('*.class'))
        if not paths:raise Failure('W3 classfiles missing')
        for path in paths:
            if struct.unpack('>IHH',(classes/path).read_bytes()[:8])!=(0xcafebabe,0,65):raise Failure('W3 requires Java 21 without preview')
        edges={k:sorted(v) for k,v in dependencies_from_jdeps(command(root,['jdeps','--multi-release','21','-filter:none','-verbose:class','-cp',cp,str(classes)])).items() if k.startswith(prefix)}
        for source,targets in edges.items():
            for target in targets:
                if ('BatchReplayer' in source or 'PossibleValuesAnalysis$Execution' in source) and 'DataflowSolver' in target:raise Failure('W3 observation cannot rerun solver')
                if any(d in target for d in DENIED) or (module=='analysis-kernel' and target.startswith(PREFIX)):raise Failure('W3 forbidden bytecode dependency: '+target)
                if not target.startswith(('java.',prefix,'io.github.gustavo2358.air.model.','io.github.gustavo2358.analysis.structure.','io.github.gustavo2358.analysis.solver.','io.github.gustavo2358.analysis.query.','io.github.gustavo2358.analysis.cfg.domain.')):raise Failure('W3 DAG: '+target)
        descriptors={p[:-6].replace('/','.'):command(root,['javap','-classpath',str(classes)+os.pathsep+cp,'-public','-s',p[:-6].replace('/','.')]) for p in paths}
        actual[module]={'sources':sorted(sources),'classfiles':paths,'jdeps_edges':dict(sorted(edges.items())),'javap_descriptors':descriptors,'effective_maven':sorted(parse_tgf(root/module/'target/architecture-dependencies.tgf'))}
    if update:(root/INVENTORY).write_text(json.dumps(actual,indent=2)+'\n')
    elif json.loads((root/INVENTORY).read_text())!=actual:raise Failure('W3 compiled inventory drift')
    print('[w3-architecture] PASS: exact sources/classfiles/javap/jdeps/direct Maven; W1/W2 production byte-exact')

def run(root:Path,category:str,update:bool=False)->None:
    verify_sources(root);maven=['mvn','-B','-ntp']
    if category=='architecture':
        command(root,maven+['-DskipTests','package','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree','-Dscope=compile','-DoutputType=tgf','-DoutputFile=target/architecture-dependencies.tgf','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=compile','-Dmdep.outputFile=target/architecture-classpath.txt']);architecture(root,update)
    else:
        names=set(TESTS) if category=='performance' else set(TESTS)-{'ValuesScaleTest'}
        output=command(root,maven+['-pl','analysis-values','-am','clean','test','-Dtest=BuildCfgContractTest,StructureTest,'+','.join(sorted(names))])
        verify_reports(root,names);corpus=verify_corpus(output);verify_support_metrics(output)
        if category=='performance':
            rows=verify_metrics(output);path=root/'.harness-results/w3-performance.json';path.parent.mkdir(exist_ok=True)
            path.write_text(json.dumps({'scope':'W3 real AIR/CFG/PossibleValues; W4/W5 unavailable','role':'OBSERVATION_ONLY','corpus':corpus,'measurements':rows,'supportMeasurements':verify_support_metrics(output),'retention':'identity walk of actual reachable objects; logical counts, allocated byte estimates explicitly labelled'},indent=2)+'\n')
        print('[w3-'+category+'] PASS: '+str(sum(len(TESTS[n]) for n in names))+' nominal tests; real AIR/CFG/W1/W2/W3, no skips')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('category',choices=['architecture','semantic','performance']);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--update-inventory',action='store_true');args=parser.parse_args()
    try:run(args.root.resolve(),args.category,args.update_inventory)
    except (Failure,ValueError,OSError,KeyError,ET.ParseError) as e:print('[w3] FAIL: '+str(e),file=sys.stderr);sys.exit(1)
