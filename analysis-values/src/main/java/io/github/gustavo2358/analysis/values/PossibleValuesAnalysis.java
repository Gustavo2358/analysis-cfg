package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Generic scalar text analysis, using the approved W2 solver and a shared operation transfer. */
public final class PossibleValuesAnalysis implements AnalysisDefinition<PossibleValuesState>,AutoCloseable {
    public static final String PROFILE="scalar-text-direct@1";
    public static final String EFFECTS_PROFILE="scalar-text-effects@1";
    private TextProfile profile;
    private final ValuesWork work=new ValuesWork();
    private PossibleValuesAnalysis(TextProfile profile){this.profile=profile;}
    public enum Status { ACCEPTED, UNSUPPORTED, INVALID_INPUT }
    public record Admission(Status status,String reason,Optional<PossibleValuesAnalysis> analysis,
                            long unsupportedStorageProfiles,long unsupportedEffectProfiles) { }
    public static Admission prepare(AnalysisSession session) {
        return prepare(session,PROFILE);
    }
    public static Admission prepare(AnalysisSession session,String profile) {
        return prepare(session,profile,null);
    }
    public static Admission prepare(AnalysisSession session,String profile,Set<ObjectId> demand) {
        if(!PROFILE.equals(profile)&&!EFFECTS_PROFILE.equals(profile))
            return new Admission(Status.UNSUPPORTED,"UNSUPPORTED_EFFECT_PROFILE",Optional.empty(),0,1);
        var universe=new ValueUniverse(session.index().store());
        try { return new Admission(Status.ACCEPTED,null,Optional.of(new PossibleValuesAnalysis(new TextProfile(session,EFFECTS_PROFILE.equals(profile),demand,universe))),0,0); }
        catch(TextProfile.Refusal refusal) {
            try{universe.close();}catch(RuntimeException|Error cleanup){if(cleanup!=refusal)refusal.addSuppressed(cleanup);throw refusal;}
            return new Admission(refusal.invalid?Status.INVALID_INPUT:Status.UNSUPPORTED,refusal.getMessage(),Optional.empty(),
                refusal.getMessage().contains("STORAGE")?1:0,refusal.getMessage().contains("EFFECT")?1:0);
        }
        catch(RuntimeException|Error failure){try{universe.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    /** Retire preparation ownership; already started executions keep their own lease. */
    @Override public void close(){var owned=profile;profile=null;if(owned!=null)owned.universe.release();}
    @Override public long stateFingerprint(PossibleValuesState state){return state.fingerprint();}
    @Override public Direction direction(){return Direction.FORWARD;}
    @Override public PossibleValuesState bottom(){return PossibleValuesState.unreachable();}
    @Override public Iterable<Boundary<PossibleValuesState>> boundaries(AnalysisSession session) {
        if(session!=profile.session)throw new IllegalArgumentException("different analysis session");
        var result=new ArrayList<Boundary<PossibleValuesState>>();
        for(var context:session.contexts())result.add(new Boundary<>(context,context.entryNode(),profile.boundaries.get(context)));
        return result;
    }
    @Override public Join<PossibleValuesState> joinInto(PossibleValuesState a,PossibleValuesState b,DomainWork domainWork) {
        long before=work.joinEntries;var result=a.join(b,work);
        for(long i=before;i<work.joinEntries;i++)domainWork.joinEntryVisited();
        return new Join<>(result,result!=a);
    }
    @Override public boolean equivalent(PossibleValuesState a,PossibleValuesState b,DomainWork domainWork) {
        long before=work.compareEntries;boolean result=a.equivalent(b,work);
        for(long i=before;i<work.compareEntries;i++)domainWork.stateCompareEntry();
        return result;
    }
    @Override public PossibleValuesState transferBlock(AnalysisPoint point,PossibleValuesState anchor,DomainWork domainWork) {
        if(!(point.node().source() instanceof CfgNode.SequenceNode node))return anchor;
        var sequence=profile.session.index().sequence(point.node());
        var state=anchor;
        for(var operation:sequence.instructions()){domainWork.operationTransferred();state=profile.transferOperation(state,operation,work);}
        domainWork.operationTransferred();return profile.transferOperation(state,sequence.terminator(),work);
    }
    @Override public PossibleValuesState transferEdge(AnalysisPoint point,CfgTransition edge,PossibleValuesState state,DomainWork domainWork){
        if(!state.isReached()||!(point.node().source() instanceof CfgNode.SequenceNode))return state;
        var terminator=profile.session.index().sequence(point.node()).terminator();
        if(!(terminator instanceof Operations.Branch branch))return state;
        int requested=edge.kind()==CfgTransition.Kind.BRANCH_TRUE?TextPredicate.TRUE:edge.kind()==CfgTransition.Kind.BRANCH_FALSE?TextPredicate.FALSE:TextPredicate.BOTH;
        int possible=TextPredicate.truth(branch.predicate(),place->{
            if(!(place instanceof Places.ObjectPlace named))return TextPredicate.Text.unknown();
            var location=profile.subjects.get(named.object());if(location==null||!profile.selected(location))return TextPredicate.Text.unknown();
            var value=state.value(location.ordinal(),work);var texts=new HashSet<LogicalText>();
            for(int i=0;i<value.size();i++)texts.add(profile.universe.value(value.at(i)));
            return new TextPredicate.Text(texts,value.open());
        });
        return (possible&requested)==0?bottom():state;
    }
    /** Each explicit execution solves once. Batches reuse the returned stable execution. */
    public Execution execute() {
        var profile=this.profile;if(profile==null)throw new IllegalStateException("closed scalar preparation");
        profile.universe.retain();DataflowResult<PossibleValuesState> result=null;
        try {
        var definition=new PossibleValuesAnalysis(profile);
        result=DataflowSolver.solve(profile.session,definition);
        profile.relations.retain(result);
        var metrics=new LinkedHashMap<>(definition.work.snapshot());
        metrics.putAll(profile.relations.metrics());
        return new Execution(profile,result,Map.copyOf(metrics));
        } catch(RuntimeException|Error failure){
            if(result!=null)try{result.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            try{profile.universe.release();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;
        }
    }
    public static final class Execution implements AutoCloseable {
        private TextProfile profile;
        private final DataflowResult<PossibleValuesState> dataflow;
        private final Map<String,Long> solveMetrics;
        private Map<String,Long> preparationMetrics;
        private Execution(TextProfile profile,DataflowResult<PossibleValuesState> dataflow,Map<String,Long> metrics){this.profile=profile;this.dataflow=dataflow;this.solveMetrics=metrics;preparationMetrics=snapshotPreparation(profile);}
        public DataflowResult<PossibleValuesState> dataflow(){return dataflow;}
        public Map<String,Long> solveMetrics(){return solveMetrics;}
        public Map<String,Long> preparationMetrics(){var active=profile;if(active!=null&&!active.universe.closed())preparationMetrics=snapshotPreparation(active);return preparationMetrics;}
        private static Map<String,Long> snapshotPreparation(TextProfile profile){var m=new HashMap<>(profile.preparation.snapshot());m.put("demandCellsPrepared",(long)profile.preparedCellCount());m.put("demandWritesPrepared",(long)profile.writes.size());m.put("demandObjectsRequested",profile.requestedObjects);m.put("valuesInterned",(long)profile.universe.size());m.put("producersPrepared",(long)profile.universe.producerCount());m.put("poolHits",profile.universe.poolHits);m.put("textRunsHashed",profile.universe.runsHashed);return Map.copyOf(m);}
        @Override public void close(){
            var owned=profile;if(owned==null)return;if(!owned.universe.closed())preparationMetrics=snapshotPreparation(owned);profile=null;
            Throwable failure=null;try{dataflow.close();}catch(RuntimeException|Error error){failure=error;}
            try{owned.universe.release();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
        }
        public record Observations(ObservationBatch<ObjectId,ValueFact> batch,Map<String,Long> stateMetrics,Map<String,Long> quality) { }
        /** One replay union of immutable points in this exact selected run. Project
         * after replay, never join different points or contexts. Controlled failure
         * falls back to the original atomic per-batch path; operational failures
         * still abort and are never converted into incomplete semantic answers. */
        public List<Observations> observeBatches(List<List<PointQuery<ObjectId>>> requests) {
            var active=profile;if(active==null)throw new IllegalStateException("closed scalar execution");
            active.universe.available();
            long bytes=Math.addExact(256L,Math.multiplyExact(128L,requests.size()));
            for(var queries:requests)bytes=Math.addExact(bytes,Math.multiplyExact(192L,queries.size()));
            // Union/index/list reference controls, not an admission claim about
            // the existing resident detached ValueFact payloads.
            var budget=active.session.index().store().executionBudget();
            var lease=budget.isPresent()?budget.orElseThrow().reserve(ProgramStore.CapacityPool.SCRATCH,bytes,ProgramStore.ExecutionPhase.REPLAY):null;
            try(lease) {
                var union=new LinkedHashSet<PointQuery<ObjectId>>();
                for(var queries:requests)for(var query:queries) {
                    active.session.index().store().progress(ProgramStore.ExecutionPhase.REPLAY);union.add(Objects.requireNonNull(query));
                }
                var shared=observe(union);
                var output=new ArrayList<Observations>();
                if(shared.batch().status()!=ObservationBatch.Status.COMPLETE) {
                    for(var queries:requests)output.add(observe(queries));
                    if(!output.isEmpty()) {
                        var first=output.getFirst();var local=first.batch();var m=local.metrics();var attempted=shared.batch().metrics();
                        var work=new TreeMap<>(first.stateMetrics());
                        shared.stateMetrics().forEach((name,value)->work.merge(name,value,Math::addExact));
                        output.set(0,new Observations(new ObservationBatch<>(local.status(),local.reason(),local.observations(),
                            new ObservationBatch.Metrics(m.queryRequests(),m.uniqueQueries(),Math.addExact(m.sequencesReplayed(),attempted.sequencesReplayed()),
                                Math.addExact(m.operationsReplayed(),attempted.operationsReplayed()),m.maxRequestedOffsetObserved(),
                                m.queriesAnswered(),m.unsupportedQueries(),m.queriesNotMaterialized(),m.observationFailures())),Map.copyOf(work),first.quality()));
                    }
                    return List.copyOf(output);
                }
                var answers=new HashMap<PointQuery<ObjectId>,ObservationBatch.Observation<ObjectId,ValueFact>>();
                for(var observation:shared.batch().observations())answers.put(observation.query(),observation);
                var order=Comparator.<PointQuery<ObjectId>,ProgramPoint>comparing(PointQuery::point,ProgramPoint.ORDER)
                    .thenComparing(PointQuery::subject,Comparator.comparing((ObjectId id)->id.unit().publication().localId())
                        .thenComparing(id->id.unit().localId()).thenComparing(ObjectId::localId));
                for(int at=0;at<requests.size();at++) {
                    var queries=requests.get(at);var selected=new ArrayList<>(new LinkedHashSet<>(queries));selected.sort(order);
                    var observations=new ArrayList<ObservationBatch.Observation<ObjectId,ValueFact>>();
                    long answered=0,unsupported=0,maxOffset=0;
                    for(var query:selected) {
                        active.session.index().store().progress(ProgramStore.ExecutionPhase.REPLAY);
                        var observation=Objects.requireNonNull(answers.get(query));observations.add(observation);
                        if(observation.status()==ObservationBatch.QueryStatus.VALUE) {
                            answered=Math.incrementExact(answered);
                            if(query.point().kind()!=ProgramPoint.Kind.ENTRY)
                                maxOffset=Math.max(maxOffset,active.session.index().site(query.point().operation()).offset());
                        } else unsupported=Math.incrementExact(unsupported);
                    }
                    var counters=shared.batch().metrics();
                    var batch=new ObservationBatch<>(ObservationBatch.Status.COMPLETE,null,observations,
                        new ObservationBatch.Metrics(queries.size(),selected.size(),at==0?counters.sequencesReplayed():0,
                            at==0?counters.operationsReplayed():0,maxOffset,answered,unsupported,0,0));
                    output.add(new Observations(batch,at==0?shared.stateMetrics():Map.of(),quality(batch)));
                }
                return List.copyOf(output);
            }
        }
        public Observations observe(Iterable<PointQuery<ObjectId>> queries) {
            var profile=this.profile;if(profile==null)throw new IllegalStateException("closed scalar execution");
            profile.universe.available();
            var replayWork=new ValuesWork();
            var comparator=Comparator.comparing((ObjectId id)->id.unit().publication().localId()).thenComparing(id->id.unit().localId()).thenComparing(ObjectId::localId);
            var batch=BatchReplayer.materialize(profile.session,dataflow,Direction.FORWARD,PossibleValuesState.unreachable(),queries,comparator,
                (state,operation)->profile.transferOperation(state,operation,replayWork),new BatchReplayer.Projection<PossibleValuesState,ObjectId,ValueFact>() {
                    @Override public PossibleValuesState mergeStates(PossibleValuesState a,PossibleValuesState b){return a.join(b,replayWork);}
                    @Override public boolean supports(PointQuery<ObjectId> query){return profile.supports(query.subject(),query.point().entry());}
                    @Override public ValueFact project(PointQuery<ObjectId> query,PossibleValuesState state) {
                        var cell=profile.subjects.get(query.subject());boolean source=profile.sourceOpen(query.subject(),query.point().entry());
                        var supports=state.isReached()?profile.universe.materializeSupports(state.value(cell.ordinal(),replayWork)):List.<ValueFact.CandidateSupport>of();
                        var evidence=new LinkedHashSet<Id>();var provenance=new LinkedHashSet<OriginId>();
                        var premises=new LinkedHashSet<PremiseId>(profile.premises);
                        for(var candidate:supports)for(var producer:candidate.producers()) {
                            evidence.add(producer.evidence());provenance.add(producer.origin());premises.addAll(producer.premises());
                        }
                        boolean model=state.isReached()&&(state.value(cell.ordinal(),replayWork).open()||profile.universe.partial(state.value(cell.ordinal(),replayWork)));
                        return new ValueFact(cell.cell().header().id(),state.isReached()?ValueFact.Reachability.REACHABLE:ValueFact.Reachability.UNREACHABLE_IN_MODEL,
                            state.isReached()?profile.universe.materialize(state.value(cell.ordinal(),replayWork)):null,state.isReached()?model:null,
                            source,model,List.copyOf(premises),List.copyOf(evidence),List.copyOf(provenance),supports);
                    }
                });
            preparationMetrics=snapshotPreparation(profile);
            return new Observations(batch,replayWork.snapshot(),quality(batch));
        }
        private static Map<String,Long> quality(ObservationBatch<ObjectId,ValueFact> batch) {
            var quality=new HashMap<String,Long>();
            for(String name:List.of("modelOpenResults","sourceOpenResults","effectiveOpenResults","closedInModelResults"))quality.put(name,0L);
            for(var observation:batch.observations())if(observation.value()!=null) {
                var fact=observation.value();
                if(Boolean.TRUE.equals(fact.modelValueRemainder()))increment(quality,"modelOpenResults");
                if(fact.sourceUnknownRemainder())increment(quality,"sourceOpenResults");
                if(fact.effectiveUnknownRemainder())increment(quality,"effectiveOpenResults");
                if(Boolean.FALSE.equals(fact.modelValueRemainder()))increment(quality,"closedInModelResults");
            }
            return Map.copyOf(quality);
        }
        private static void increment(Map<String,Long> metrics,String key){metrics.put(key,Math.incrementExact(metrics.get(key)));}
    }
}
