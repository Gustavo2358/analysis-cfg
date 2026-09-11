#!/usr/bin/env python3
"""W4 real shared planning, provider binding, dependency completion and scale; W5 remains unavailable."""
from __future__ import annotations
import argparse, hashlib, json, os, re, struct, sys
from pathlib import Path
import xml.etree.ElementTree as ET
from check_w1 import ROOT, Failure, command
from resource_limit_scope import allows_change

BASE='855628200fba3851493991cec869dee899e82299'
PREFIX='io.github.gustavo2358.analysis.'
TEST_PREFIX=PREFIX+'values.'
INVENTORY='docs/evals/cp6/w1d-w4-inventory.json'
PACKAGES={
'application':'AnalysisProvider AnalysisRegistry ConsumerRegistration Counts ExecutionPlan PlanningExecution PreparedAnalysisResult SitePlanner'.split(),
'plan':'AnalysisKey AnalysisOutcome ConsumerPlan ObservationBatchId ObservationRequest SiteInterest'.split(),
'consumers':'FactConsumer FactSink PreparedFacts SiteView'.split()}
KERNEL_SOURCES={f'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/{pkg}/{name}.java' for pkg,names in PACKAGES.items() for name in names}
PROVIDER='analysis-values/src/main/java/io/github/gustavo2358/analysis/values/PossibleValuesProvider.java'
SOURCES=KERNEL_SOURCES|{PROVIDER}
TESTS={
'PlanningZeroMatchTest':set('absentKindKeepsDeclaredEmptyBatch rejectingFilterKeepsDeclaredEmptyBatch zeroMatchDeclarationsValidateBindingsBeforeSelection'.split()),
'PlanningTest':set('structuralConsumerCompletesWithoutAnalysis duplicateConsumerIdsArePlanningErrors'.split()),
'PlanningRuntimeTest':set('realVerticalSharesRunQueryReplayAndProducerSupport f3FailureIsLocalAndIndependentBatchesShareStableRun failingConsumerDiscardsEveryStagedFactAndOthersComplete analysisOnlyDoesNotCreateObservationAndStructuralStartsNoAnalysis lateQueryIsNotRequestedAndNewEpochExplicitlyReusesRun cannotReadAnotherConsumersUnrequestedSubjectInSharedBatch missingDependenciesAndWrongBindingsNeverProduceSuccess registryRejectsWrongPreparedAndExecutionKey sinkIsClosedAndInfrastructureErrorsPropagateWithoutPartialResult finalFactsReflectFixedPointAfterOverwritesAndLoop preparedResultAndClosedRuntimeDetachSessionRunAndConsumer'.split()),
'PlanningContractTest':set('completeSemanticKeyIdentityAndOrderingCannotCollide distinctProfilesOptionsAndEntriesRunSeparatelyWithNoPhantomContext differentSessionsCannotShareRunsOrPlansEvenWithEqualIds registrationInterestAndQueryOrderHaveDeterministicPlansAndFacts overlappingInterestsDispatchOnlyExactPairsAndKeepStructuralPresence batchesWithSameIdCannotAliasDifferentKeys perSiteQueriesAreCompiledBeforeExecutionAndDependenciesAreExplicit modelCandidatesSupportsAndPremisesRemainAbstractAtConsumerBoundary'.split()),
'PlanningScaleTest':set('sparseOverlappingConsumersShareBucketsAndOneActualAnalysis denseQueriesAcrossConsumersReplayOnlyTheUnion consumersQueriesMatchesFactsAndBatchesHaveNoCapacitySemantics'.split())}
COMMON_DENIED=('java.io.','java.nio.file.','java.net.','java.lang.reflect.','java.util.ServiceLoader','cfg.adapters.','cfg.launcher.',
    'cfg.application.BuildCfg','cfg.application.CfgBuildCoordinator','org.antlr','cobolexplorer','lower.')
CONSUMER_DENIED=COMMON_DENIED+('analysis.solver.','analysis.structure.','analysis.application.','analysis.query.BatchReplayer',
    'analysis.values.PossibleValuesAnalysis','analysis.values.PossibleValuesProvider','air.model.Publication','air.model.Sequence','air.model.Unit','cfg.domain.CfgGraph')

def role(source:str)->str:
    if '.consumers.' in source:return 'consumers'
    if 'PossibleValuesProvider' in source:return 'provider'
    return 'application'

def verify_edges(edges:dict)->None:
    for source,targets in edges.items():
        for target in targets:
            denied=CONSUMER_DENIED if role(source)=='consumers' else COMMON_DENIED
            if any(d in target for d in denied):raise Failure('W4 forbidden '+role(source)+' bytecode dependency: '+source+' -> '+target)
            if role(source)=='application' and '.analysis.values.' in target:raise Failure('generic W4 application imports concrete PossibleValues')
            if role(source)=='provider' and '.analysis.consumers.' in target:raise Failure('provider depends on consumers')

def verify_sources(root:Path)->None:
    actual={p.relative_to(root).as_posix() for pkg in PACKAGES for p in (root/'analysis-kernel/src/main/java/io/github/gustavo2358/analysis'/pkg).rglob('*.java')}
    if actual!=KERNEL_SOURCES or not (root/PROVIDER).is_file():raise Failure('W4 exact production source inventory mismatch')
    for path in SOURCES:
        source=(root/path).read_text()
        denied=CONSUMER_DENIED if '/consumers/' in path else COMMON_DENIED
        if any(d in source for d in denied):raise Failure('W4 forbidden source dependency: '+path)
        if '/application/' in path and ('PossibleValuesAnalysis' in source or 'analysis.values.' in source):raise Failure('generic application imports concrete values')
        if re.search(r'\bstatic\s+(?:final\s+)?(?:Map|HashMap|ConcurrentHashMap|List|Set)<',source):raise Failure('W4 global static cache/container')
    verify_foundation(root);verify_focal_preservation(root)

def verify_foundation(root:Path)->None:
    from check_w4_scope import preserved_digest
    baseline=json.loads((root/'docs/work/evidence/WORK-CFG-028/wave-4/baseline.json').read_text())
    for path,digest in baseline['productionAndPinSha256'].items():
        if preserved_digest(root,path)!=digest and not allows_change(root,path,digest):raise Failure('W4 changed approved foundation outside additive context selection: '+path)

def verify_focal_preservation(root:Path)->None:
    baseline=json.loads((root/'docs/work/evidence/WORK-CFG-028/wave-4/review-f1/baseline.json').read_text())
    if baseline['reviewed_head']!='330d63427c0905e8ef140924b643e9fb012c73de':raise Failure('W4-F1 reviewed baseline mismatch')
    mutable='analysis-kernel/src/main/java/io/github/gustavo2358/analysis/application/SitePlanner.java'
    if baseline['mutable_production']!=[mutable]:raise Failure('W4-F1 may change only SitePlanner production')
    for path,digest in baseline['files'].items():
        data=(root/path).read_bytes()
        if path=='pom.xml':
            from check_w5 import original_pom
            data=original_pom(data)
        if path!=mutable and hashlib.sha256(data).hexdigest()!=digest and not allows_change(root,path,digest):
            raise Failure('W4-F1 changed reviewed source: '+path)

def selected_class(name:str)->bool:
    return any(name.startswith(PREFIX+pkg+'.') for pkg in PACKAGES) or name.startswith(PREFIX+'values.PossibleValuesProvider')

def compiled_edges(root:Path)->dict:
    from check_transport_architecture import dependencies_from_jdeps
    edges={}
    for module in ['analysis-kernel','analysis-values']:
        classes=root/module/'target/classes';cp=(root/module/'target/architecture-classpath.txt').read_text().strip()
        found=dependencies_from_jdeps(command(root,['jdeps','--multi-release','21','-filter:none','-verbose:class','-cp',cp,str(classes)]))
        edges.update({k:sorted(v) for k,v in found.items() if selected_class(k)})
    if not edges:raise Failure('W4 compiled bytecode absent')
    verify_edges(edges);return edges

def architecture(root:Path,update:bool=False)->None:
    from check_architecture import parse_tgf
    verify_sources(root);edges=compiled_edges(root);actual={}
    for module in ['analysis-kernel','analysis-values']:
        classes=root/module/'target/classes';cp=(root/module/'target/architecture-classpath.txt').read_text().strip()
        paths=sorted(p.relative_to(classes).as_posix() for p in classes.rglob('*.class') if selected_class(p.relative_to(classes).as_posix()[:-6].replace('/','.')))
        if not paths:raise Failure('W4 classfiles missing')
        for path in paths:
            if struct.unpack('>IHH',(classes/path).read_bytes()[:8])!=(0xcafebabe,0,65):raise Failure('W4 requires Java 21 without preview')
        descriptors={p[:-6].replace('/','.'):command(root,['javap','-classpath',str(classes)+os.pathsep+cp,'-public','-s',p[:-6].replace('/','.')]) for p in paths}
        actual[module]={'sources':sorted(KERNEL_SOURCES if module=='analysis-kernel' else {PROVIDER}),'classfiles':paths,
            'jdeps_edges':{k:v for k,v in edges.items() if (role(k)=='provider')==(module=='analysis-values')},'javap_descriptors':descriptors,
            'effective_maven':sorted(parse_tgf(root/module/'target/architecture-dependencies.tgf'))}
    if update:(root/INVENTORY).write_text(json.dumps(actual,indent=2)+'\n')
    elif json.loads((root/INVENTORY).read_text())!=actual:raise Failure('W4 compiled inventory drift')
    print('[w4-architecture] PASS: compiled consumer capabilities, generic application, bound provider; exact sources/classfiles/javap/jdeps/Maven')

def verify_reports(root:Path,names:set[str])->None:
    reports=list((root/'analysis-values/target/surefire-reports').glob('TEST-*.xml'))
    if {p.name for p in reports}!={'TEST-'+TEST_PREFIX+n+'.xml' for n in names}:raise Failure('W4 nominal report inventory mismatch')
    for path in reports:
        doc=ET.parse(path).getroot();name=doc.attrib['name'].removeprefix(TEST_PREFIX);cases=doc.findall('testcase')
        if len(cases)!=len(TESTS[name]) or {c.attrib['name'] for c in cases}!=TESTS[name]:raise Failure('W4 nominal method inventory mismatch')
        if any(c.attrib['classname']!=TEST_PREFIX+name or any(c.find(k) is not None for k in ['failure','error','skipped']) for c in cases):raise Failure('W4 failed/skipped/foreign test')
        if int(doc.attrib['tests'])!=len(cases) or any(int(doc.attrib.get(k,'0')) for k in ['failures','errors','skipped']):raise Failure('W4 report counters mismatch')

def verify_keys(output:str)->None:
    rows=[json.loads(s) for s in re.findall(r'^W4_KEYS (\{.*\})$',output,re.M)]
    if rows!=[dict(keys=8,consumers=16,analysisRuns=8,analysisCacheHits=8,pointsPerRun=3,unselectedEntries=18)]:raise Failure('W4 key/context witness missing or false')

def verify_metrics(output:str)->list[dict]:
    rows=[json.loads(s) for s in re.findall(r'^W4_METRICS (\{.*\})$',output,re.M)]
    expected={('S5',100000,k) for k in [1,2,20]}|{(p,n,k) for p in ['S6-half','S6-all'] for n in [1000,2000,4000] for k in [1,2,20]}|{(p,n,n) for p in ['S16-shared','S16-batches'] for n in [32,64,128]}
    if len(rows)!=len(expected) or {(r['probe'],r['N'],r['K']) for r in rows}!=expected:raise Failure('W4 scale rows missing/duplicate/foreign')
    required=set('index_operationsIndexed index_structuralVisits planning_candidateSites planning_structuralVisits planning_siteMatches consumer_consumerInvocations analysis_analysisRuns analysis_analysisCacheHits planning_observationBatchesPlanned observation_observationBatchesExecuted observation_queryRequests observation_uniqueQueries observation_sequencesReplayed observation_operationsReplayed observation_queriesAnswered observation_unsupportedQueries observation_queriesNotMaterialized observation_observationFailures observation_modelOpenResults observation_sourceOpenResults observation_effectiveOpenResults observation_closedInModelResults consumer_factsStaged consumer_factsCommitted consumer_factsDiscarded consumer_consumerFailures consumer_consumersComplete consumer_consumersNotStarted planning_planningEpochs consumer_notRequested retained_ValueFact retained_DataflowResult retained_AnalysisSession retained_ProgramIndex retained_ConsumerRegistration retained_PossibleValuesState retained_ValueUniverse elapsedNanos'.split())
    for row in rows:
        if not required<=set(row) or any(type(row[k])!=int or row[k]<0 for k in required):raise Failure('W4 missing/invalid phase metric')
        def eq(name,value):
            if row[name]!=value:raise Failure('W4 work/quality mismatch: '+name)
        n,k,p=row['N'],row['K'],row['probe'];eq('analysis_analysisRuns',1);eq('analysis_analysisCacheHits',k-1)
        eq('planning_planningEpochs',1);eq('consumer_consumersComplete',k)
        for zero in ['consumer_factsDiscarded','consumer_consumerFailures','consumer_consumersNotStarted','observation_unsupportedQueries','observation_observationFailures','consumer_notRequested']:eq(zero,0)
        for name in ['DataflowResult','AnalysisSession','ProgramIndex','ConsumerRegistration','PossibleValuesState','ValueUniverse']:eq('retained_'+name,0)
        eq('observation_uniqueQueries',row['observation_queriesAnswered']+row['observation_unsupportedQueries']+row['observation_queriesNotMaterialized'])
        eq('consumer_factsStaged',row['consumer_factsCommitted'])
        if p=='S5':
            eq('index_operationsIndexed',100000);eq('planning_candidateSites',60);eq('planning_structuralVisits',60);eq('planning_siteMatches',60*k);eq('consumer_consumerInvocations',60*k)
            eq('observation_queryRequests',20*k);eq('observation_uniqueQueries',20);eq('observation_sequencesReplayed',11)
        else:
            q=n//2 if p=='S6-half' else n
            eq('planning_candidateSites',1);eq('planning_structuralVisits',1);eq('planning_siteMatches',k);eq('consumer_consumerInvocations',k)
            eq('observation_uniqueQueries',q);eq('observation_queriesAnswered',q);eq('observation_closedInModelResults',q);eq('retained_ValueFact',q)
            eq('observation_operationsReplayed',n*(n+1)//2 if p=='S16-batches' else q)
            eq('observation_sequencesReplayed',n if p=='S16-batches' else 1)
            eq('observation_queryRequests',q*k if p.startswith('S6') else n);eq('consumer_factsCommitted',q*k if p.startswith('S6') else n)
        eq('planning_observationBatchesPlanned',n if p=='S16-batches' else 1);eq('observation_observationBatchesExecuted',n if p=='S16-batches' else 1)
    if len({r['index_structuralVisits'] for r in rows if r['probe']=='S5'})!=1:raise Failure('W4 global index multiplied by K')
    return rows

def run(root:Path,category:str,update:bool=False)->None:
    verify_sources(root);maven=['mvn','-B','-ntp']
    if category=='architecture':
        command(root,maven+['-DskipTests','package','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree','-Dscope=compile','-DoutputType=tgf','-DoutputFile=target/architecture-dependencies.tgf','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=compile','-Dmdep.outputFile=target/architecture-classpath.txt']);architecture(root,update)
    else:
        names=set(TESTS) if category=='performance' else set(TESTS)-{'PlanningScaleTest'}
        output=command(root,maven+['-pl','analysis-values','-am','clean','test','-Dtest=BuildCfgContractTest,StructureTest,'+','.join(sorted(names))]);verify_reports(root,names);verify_keys(output)
        if category=='performance':
            rows=verify_metrics(output);path=root/'.harness-results/w4-performance.json';path.parent.mkdir(exist_ok=True)
            path.write_text(json.dumps({'scope':'W4 shared planning with real W3; W5 composition executes separately','role':'OBSERVATION_ONLY','measurements':rows,'retention':'identity-deduplicated reachable logical objects, not physical heap'},indent=2)+'\n')
        print('[w4-'+category+'] PASS: '+str(sum(len(TESTS[n]) for n in names))+' nominal tests; real AIR/CFG/W1/W2/W3/W4; no skipped methods')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('category',choices=['architecture','semantic','performance']);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--update-inventory',action='store_true');p.add_argument('--compiled-boundary-only',action='store_true');a=p.parse_args()
    try:
        if a.compiled_boundary_only:compiled_edges(a.root.resolve());print('[w4-boundary] PASS')
        else:run(a.root.resolve(),a.category,a.update_inventory)
    except (Failure,ValueError,OSError,KeyError,ET.ParseError) as e:print('[w4] FAIL: '+str(e),file=sys.stderr);sys.exit(1)
