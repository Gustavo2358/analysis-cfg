#!/usr/bin/env python3
"""Compiled W4 mutations: actual nominal RED, byte-exact restore and second GREEN; preserves failed attempts."""
from __future__ import annotations
import argparse, difflib, gzip, hashlib, json, os, subprocess, sys, time
from pathlib import Path
import xml.etree.ElementTree as ET
from check_w4 import ROOT, TESTS, PROVIDER

APP='analysis-kernel/src/main/java/io/github/gustavo2358/analysis/application/'
RUNTIME=APP+'PlanningExecution.java'; PLANNER=APP+'SitePlanner.java'
CONSUMER='analysis-kernel/src/main/java/io/github/gustavo2358/analysis/consumers/FactConsumer.java'
PREFIX='io.github.gustavo2358.analysis.values.'
ALL_TESTS='BuildCfgContractTest,StructureTest,'+','.join(sorted(TESTS))
MAVEN=['mvn','-B','-ntp','-pl','analysis-values','-am']

def change(path,before,after):return (path,before,after)
def cases():
    c={}
    def add(name,test,diagnostic,*patches):c[name]=dict(test=test,diagnostic=diagnostic,patches=patches)
    vertical='PlanningRuntimeTest#realVerticalSharesRunQueryReplayAndProducerSupport'
    f3='PlanningRuntimeTest#f3FailureIsLocalAndIndependentBatchesShareStableRun'
    failure='PlanningRuntimeTest#failingConsumerDiscardsEveryStagedFactAndOthersComplete'
    scale='PlanningScaleTest#consumersQueriesMatchesFactsAndBatchesHaveNoCapacitySemantics'
    add('consumer-starts-solver','architecture','W4 forbidden consumers bytecode dependency',change(CONSUMER,'    void consume(SiteView site, PreparedFacts facts, FactSink<F> sink);','''    void consume(SiteView site, PreparedFacts facts, FactSink<F> sink);
    static <S> Object startsSolver(io.github.gustavo2358.analysis.structure.AnalysisSession session,
            io.github.gustavo2358.analysis.solver.AnalysisDefinition<S> definition) {
        return io.github.gustavo2358.analysis.solver.DataflowSolver.solve(session,definition);
    }'''))
    add('full-traversal-per-consumer','PlanningScaleTest#sparseOverlappingConsumersShareBucketsAndOneActualAnalysis','bucket scan once',change(PLANNER,'        for (var bucket : byKind.entrySet()) {','''        for (var registration : registrations) for (var unit : session.index().publication().units()) for (var sequence : unit.sequences()) {
            for (var operation : sequence.instructions()) { Objects.requireNonNull(operation); counts.add("structuralVisits",1); }
            Objects.requireNonNull(sequence.terminator()); counts.add("structuralVisits",1);
        }
        for (var bucket : byKind.entrySet()) {'''))
    add('broadcast-i-times-k','PlanningContractTest#overlappingInterestsDispatchOnlyExactPairsAndKeepStructuralPresence','expected:',change(PLANNER,'session.index().sites(bucket.getKey())','List.<Class<? extends Operation>>of(Operations.Nop.class,Operations.Assign.class,Operations.Return.class,Operations.Branch.class,Operations.Jump.class).stream().flatMap(kind -> session.index().sites(kind).stream()).toList()'))
    add('cache-ignores-options-entry','PlanningContractTest#distinctProfilesOptionsAndEntriesRunSeparatelyWithNoPhantomContext','wrong batch bound to analysis key',change(RUNTIME,'var cached = runs.get(key);','var cached = runs.entrySet().stream().filter(e -> e.getKey().implementation().equals(key.implementation())).map(Map.Entry::getValue).findFirst().orElse(null);'))
    add('solver-per-consumer',vertical,'one actual provider solve',change(RUNTIME,'var cached = runs.get(key);','CachedRun cached = null;'))
    add('solver-per-observation-batch',f3,'batches reuse stable run',change(RUNTIME,'var run = selectedRuns.get(batch.id.analysisKey()); BatchResult result;','var run = start(registry.require(batch.id.analysisKey()),batch.id.analysisKey(),analysis); BatchResult result;'))
    add('replay-per-consumer',vertical,'one actual observation batch',change(RUNTIME,'observations.add("observationBatchesExecuted",1); result = run.observe(batch);','observations.add("observationBatchesExecuted",1); result = run.observe(batch);\n                for (int i=1;i<plan.registrations.size();i++) result = run.observe(batch);'))
    add('observation-failure-blocks-independent-consumer',f3,'independent structural consumer completes',change(RUNTIME,'            if (!available) {','            available &= batches.values().stream().allMatch(b -> b.status() == BatchStatus.COMPLETE);\n            if (!available) {'))
    add('analysis-only-consumer-forced-to-depend-on-batch',f3,'analysis-only does not depend on batch',change(RUNTIME,'            if (!available) {','            if (!dependencies.requiredAnalysisKeys().isEmpty()) available &= batches.values().stream().allMatch(b -> b.status() == BatchStatus.COMPLETE);\n            if (!available) {'))
    add('structural-consumer-forced-to-start-analysis','PlanningRuntimeTest#analysisOnlyDoesNotCreateObservationAndStructuralStartsNoAnalysis','expected:',change(RUNTIME,'for (var key : registration.dependencies().requiredAnalysisKeys()) {','for (var key : registration.dependencies().requiredAnalysisKeys().isEmpty() ? List.of(new AnalysisKey("PossibleValues","1","scalar-text-direct@1",io.github.gustavo2358.analysis.solver.Direction.FORWARD,"FINITE_PROGRAM_TEXT_VALUES",Map.of(),plan.sites.get(registration.dependencies().consumerId()).getFirst().entry())) : registration.dependencies().requiredAnalysisKeys()) {'))
    add('consumer-failure-reported-as-complete-output',failure,'expected: <FAILED>',change(RUNTIME,'new ConsumerOutcome<>(id,ConsumerStatus.FAILED,"CONSUMER_ERROR",List.of())','new ConsumerOutcome<>(id,ConsumerStatus.COMPLETE,null,List.of())'))
    add('consumer-partial-facts-committed-on-failure',failure,'failed consumer commits no partial bundle',change(RUNTIME,'staging.discard(); consumers.add("consumerFailures",1);','var leaked = List.copyOf(staging.facts); staging.discard(); consumers.add("consumerFailures",1);'),change(RUNTIME,'new ConsumerOutcome<>(id,ConsumerStatus.FAILED,"CONSUMER_ERROR",List.of())','new ConsumerOutcome<>(id,ConsumerStatus.FAILED,"CONSUMER_ERROR",leaked)'),change(APP+'PreparedAnalysisResult.java','reason == null || !facts.isEmpty()','reason == null'))
    add('missing-consumer-dependency-treated-as-success','PlanningRuntimeTest#missingDependenciesAndWrongBindingsNeverProduceSuccess','missing batch dependency',change(PLANNER,'throw new IllegalArgumentException("missing or mismatched consumer batch dependency");','{ /* mutant accepts missing dependency */ }'))
    add('wrong-batch-bound-to-analysis-key','PlanningContractTest#batchesWithSameIdCannotAliasDifferentKeys','batch ID binds exact key',change(PLANNER,'if (!pending.id.equals(batch)) throw new IllegalArgumentException("incompatible batch ID binding");','/* mutant ignores batch identity collision */'))
    add('wrong-provider-execution-binding','PlanningRuntimeTest#registryRejectsWrongPreparedAndExecutionKey','provider binding mismatch rejected',change(RUNTIME,'if (!key.equals(outcome.key()) || outcome.status() != AnalysisOutcome.Status.STABLE) throw new IllegalArgumentException("provider execution binding mismatch");','if (outcome.status() != AnalysisOutcome.Status.STABLE) throw new IllegalArgumentException("provider execution binding mismatch");'))
    add('unplanned-query-triggers-hidden-replay','PlanningRuntimeTest#lateQueryIsNotRequestedAndNewEpochExplicitlyReusesRun','late access cannot replay',
        change(RUNTIME,'new ResolvedFacts(dependencies,plan.requests.get(id),outcomes,batches,answers,consumers)','new ResolvedFacts(dependencies,plan.requests.get(id),outcomes,batches,answers,consumers,() -> { for (var b : plan.batches.values()) selectedRuns.get(b.id.analysisKey()).observe(b); })'),
        change(RUNTIME,'private final Map<AnalysisKey,AnalysisOutcome> analyses;','private final Map<AnalysisKey,AnalysisOutcome> analyses;\n        private final Runnable lateReplay;'),
        change(RUNTIME,'observations, Counts counts) {','observations, Counts counts, Runnable lateReplay) {\n            this.lateReplay = lateReplay;'),
        change(RUNTIME,'counts.add("notRequested",1); return new Lookup<>','lateReplay.run(); counts.add("notRequested",1); return new Lookup<>'))
    add('global-static-run-cache','PlanningContractTest#differentSessionsCannotShareRunsOrPlansEvenWithEqualIds','cache owner is session lifetime',change(RUNTIME,'private final Map<AnalysisKey,CachedRun> runs','private static final Map<AnalysisKey,CachedRun> runs'))
    add('max-consumers-admission',scale,'mutant consumer capacity',change(PLANNER,'        var consumers = new TreeMap','        if (registrations.size()>64) throw new IllegalArgumentException("mutant consumer capacity");\n        var consumers = new TreeMap'))
    add('max-queries-admission',scale,'mutant query capacity',change(PLANNER,'pending.queries.add(query); local.add(query);','pending.queries.add(query); local.add(query);\n                    if (pending.queries.size()>64) throw new IllegalArgumentException("mutant query capacity");'))
    add('resource-cap-on-sites',scale,'mutant site capacity',change(PLANNER,'        var localRequests = new TreeMap','        if (counts.snapshot().get("siteMatches")>64) throw new IllegalArgumentException("mutant site capacity");\n        var localRequests = new TreeMap'))
    add('resource-cap-on-facts',scale,'size never changes completion',change(RUNTIME,'facts.add(Objects.requireNonNull(fact)); counts.add("factsStaged",1);','if (counts.snapshot().get("factsStaged")>=64) throw new FactConsumer.ConsumerException("mutant fact capacity");\n            facts.add(Objects.requireNonNull(fact)); counts.add("factsStaged",1);'))
    add('resource-cap-on-batches',scale,'mutant batch capacity',change(PLANNER,'        var selected = new TreeMap','        if (frozen.size()>64) throw new IllegalArgumentException("mutant batch capacity");\n        var selected = new TreeMap'))
    normal='return new Materialized<>(observations.batch(),work);'
    def mapped(expression):return '''var batch = observations.batch();
                        var mapped = batch.observations().stream().map(o -> '''+expression+''').toList();
                        return new Materialized<>(new io.github.gustavo2358.analysis.query.ObservationBatch<>(batch.status(),batch.reason(),mapped,batch.metrics()),work);'''
    def fact(fields):return 'new io.github.gustavo2358.analysis.query.ObservationBatch.Observation<>(o.query(),o.status(),o.reason(),o.value()==null?null:new ValueFact('+fields+'))'
    original='o.value().cell(),o.value().reachability(),o.value().candidates(),o.value().modelValueRemainder(),o.value().sourceUnknownRemainder(),o.value().effectiveUnknownRemainder(),o.value().premises(),o.value().evidence(),o.value().provenance(),o.value().candidateSupports()'
    add('drop-W3-support-or-remainder-before-consumer',vertical,'candidate support preserved at consumer boundary',change(PROVIDER,normal,mapped(fact(original.replace('o.value().candidateSupports()','List.of()')))))
    add('w4-drop-candidates',vertical,'all real candidates reach sink',change(PROVIDER,normal,mapped(fact('o.value().cell(),o.value().reachability(),List.of(),true,o.value().sourceUnknownRemainder(),true,o.value().premises(),List.of(),List.of(),List.of()'))))
    add('w4-unsupported-everything',vertical,'query consumer receives supported value',change(PROVIDER,normal,mapped('new io.github.gustavo2358.analysis.query.ObservationBatch.Observation<ObjectId,ValueFact>(o.query(),io.github.gustavo2358.analysis.query.ObservationBatch.QueryStatus.UNSUPPORTED_POINT,io.github.gustavo2358.analysis.query.ObservationBatch.PointReason.UNSUPPORTED_SUBJECT,null)')))
    add('emit-provisional-facts','PlanningRuntimeTest#finalFactsReflectFixedPointAfterOverwritesAndLoop','only final stable overwrite reaches sink',change(PROVIDER,'var observations = execution.observe(queries);','''var early = execution.observe(queries.stream().map(q -> new PointQuery<>(io.github.gustavo2358.analysis.query.ProgramPoint.entry(q.point().entry()),q.subject())).toList());
                        var provisional = new ArrayList<io.github.gustavo2358.analysis.query.ObservationBatch.Observation<ObjectId,ValueFact>>();
                        for (var q : queries) provisional.add(new io.github.gustavo2358.analysis.query.ObservationBatch.Observation<>(q,io.github.gustavo2358.analysis.query.ObservationBatch.QueryStatus.VALUE,null,early.batch().observations().getFirst().value()));
                        var observations = new PossibleValuesAnalysis.Execution.Observations(new io.github.gustavo2358.analysis.query.ObservationBatch<>(early.batch().status(),early.batch().reason(),provisional,early.batch().metrics()),early.stateMetrics(),early.quality());'''))
    add('site-query-zero-match-drops-required-batch','PlanningZeroMatchTest#absentKindKeepsDeclaredEmptyBatch','declared SiteQuery batch survives zero matches',change(PLANNER,'                for (var query : interest.queries()) bindBatch(batches,registry,dependency,query.batch());','                /* mutant binds only after first matching site */'))
    return c

def hashes(root):
    paths=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard'],cwd=root).decode().splitlines()
    return {p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in sorted(set(paths)) if p.endswith('.java') or p.endswith('pom.xml')}

def campaign(root:Path,destination:Path,only:list[str]):
    destination.mkdir(parents=True,exist_ok=False); inventory=hashes(root)
    receipt={'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),'tree':subprocess.check_output(['git','rev-parse','HEAD^{tree}'],cwd=root,text=True).strip(),'source_sha256':inventory,'mutants':[],'status':'RUNNING','environment':{'JAVA_HOME':os.environ.get('JAVA_HOME'),'MAVEN_OPTS':os.environ.get('MAVEN_OPTS')}}
    def save(): (destination/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    def run(label,args):
        start=time.monotonic();p=subprocess.run(args,cwd=root,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        path=destination/(label+'.log.gz');path.write_bytes(gzip.compress(p.stdout,mtime=0));data={'command':args,'exit':p.returncode,'elapsedSeconds':time.monotonic()-start,'log':path.name,'sha256':hashlib.sha256(p.stdout).hexdigest()};return data,p.stdout.decode(errors='replace')
    baseline,_=run('baseline-green',MAVEN+['test','-Dtest='+ALL_TESTS]);receipt['baseline']=baseline;save()
    if baseline['exit']:raise RuntimeError('baseline not GREEN')
    for name,spec in cases().items():
        if only and name not in only:continue
        originals={p:(root/p).read_bytes() for p,_,_ in spec['patches']};row={'id':name,'test':spec['test'],'expectedDiagnostic':spec['diagnostic'],'status':'RUNNING'};receipt['mutants'].append(row);save()
        try:
            for p,before,after in spec['patches']:
                data=(root/p).read_text()
                if data.count(before)!=1:raise RuntimeError('mutation anchor not unique: '+p+' '+before)
                (root/p).write_text(data.replace(before,after,1))
            diff=''.join(''.join(difflib.unified_diff(data.decode().splitlines(True),(root/p).read_text().splitlines(True),fromfile=p,tofile=p)) for p,data in originals.items())
            (destination/(name+'.diff')).write_text(diff);row['diff']=name+'.diff';row['mutantSourceSha256']=hashes(root)
            compile_log,_=run(name+'-compile',MAVEN+['-DskipTests','test-compile']);row['compile']=compile_log;save()
            if compile_log['exit']:raise RuntimeError('INVALID compilation attempt: '+name)
            if spec['test']=='architecture':
                args=[sys.executable,'scripts/project/check_w4.py','architecture','--compiled-boundary-only']
            else:args=MAVEN+['test','-Dtest=BuildCfgContractTest,StructureTest,'+spec['test']]
            red,output=run(name+'-red',args);row['red']=red
            if red['exit']==0 or spec['diagnostic'] not in output:raise RuntimeError('INVALID RED/survivor: '+name)
            if spec['test']!='architecture':
                suite,method=spec['test'].split('#');report=ET.parse(root/'analysis-values/target/surefire-reports'/('TEST-'+PREFIX+suite+'.xml')).getroot()
                failed=[c for c in report.findall('testcase') if c.attrib['name']==method and (c.find('failure') is not None or c.find('error') is not None)]
                if len(failed)!=1:raise RuntimeError('nominal method did not fail: '+name)
                row['nominalFailure']=ET.tostring(failed[0],encoding='unicode')
            row['status']='RED_NOMINAL';save()
        except BaseException as exc:
            row['status']='INVALID_OR_SURVIVED';row['error']=str(exc);receipt['status']='FAILED';save();raise
        finally:
            for p,data in originals.items():(root/p).write_bytes(data)
            row['byteExactRestore']=hashes(root)==inventory;save()
            if not row['byteExactRestore']:raise RuntimeError('restoration is not byte exact')
            second,_=run(name+'-second-green',MAVEN+['test','-Dtest='+ALL_TESTS]);row['secondGreen']=second;save()
            if second['exit']:raise RuntimeError('restored source did not return GREEN')
        print('[w4-challenge] '+name+' compiled / RED / restored / GREEN',flush=True)
    receipt['status']='PASS';receipt['finalSourceSha256']=hashes(root);save()

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--output',type=Path,required=True);p.add_argument('--only',action='append',default=[]);a=p.parse_args()
    try:campaign(a.root.resolve(),a.output.resolve(),a.only)
    except (OSError,ValueError,RuntimeError) as exc:print('[w4-challenge] FAIL: '+str(exc),file=sys.stderr);sys.exit(1)
