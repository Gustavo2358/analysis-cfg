package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Generic scalar text analysis, using the approved W2 solver and a shared operation transfer. */
public final class PossibleValuesAnalysis implements AnalysisDefinition<PossibleValuesState> {
    public static final String PROFILE="scalar-text-direct@1";
    private final TextProfile profile;
    private final ValuesWork work=new ValuesWork();
    private PossibleValuesAnalysis(TextProfile profile){this.profile=profile;}
    public enum Status { ACCEPTED, UNSUPPORTED, INVALID_INPUT }
    public record Admission(Status status,String reason,Optional<PossibleValuesAnalysis> analysis,
                            long unsupportedStorageProfiles,long unsupportedEffectProfiles) { }
    public static Admission prepare(AnalysisSession session) {
        try { return new Admission(Status.ACCEPTED,null,Optional.of(new PossibleValuesAnalysis(new TextProfile(session))),0,0); }
        catch(TextProfile.Refusal refusal) {
            return new Admission(refusal.invalid?Status.INVALID_INPUT:Status.UNSUPPORTED,refusal.getMessage(),Optional.empty(),
                refusal.getMessage().contains("STORAGE")?1:0,refusal.getMessage().contains("EFFECT")?1:0);
        }
    }
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
        var state=anchor;
        for(var operation:node.source().instructions()){domainWork.operationTransferred();state=profile.transferOperation(state,operation,work);}
        domainWork.operationTransferred();return profile.transferOperation(state,node.source().terminator(),work);
    }
    @Override public PossibleValuesState transferEdge(AnalysisPoint point,CfgTransition edge,PossibleValuesState state,DomainWork domainWork){return state;}
    /** Each explicit execution solves once. Batches reuse the returned stable execution. */
    public Execution execute() {
        var definition=new PossibleValuesAnalysis(profile);
        var result=DataflowSolver.solve(profile.session,definition);
        return new Execution(profile,result,definition.work.snapshot());
    }
    public static final class Execution {
        private final TextProfile profile;
        private final DataflowResult<PossibleValuesState> dataflow;
        private final Map<String,Long> solveMetrics;
        private Execution(TextProfile profile,DataflowResult<PossibleValuesState> dataflow,Map<String,Long> metrics){this.profile=profile;this.dataflow=dataflow;this.solveMetrics=metrics;}
        public DataflowResult<PossibleValuesState> dataflow(){return dataflow;}
        public Map<String,Long> solveMetrics(){return solveMetrics;}
        public Map<String,Long> preparationMetrics(){var m=new HashMap<>(profile.preparation.snapshot());m.put("valuesInterned",(long)profile.universe.size());m.put("poolHits",profile.universe.poolHits);m.put("unicodeScalarsHashed",profile.universe.scalarsHashed);return Map.copyOf(m);}
        public record Observations(ObservationBatch<ObjectId,ValueFact> batch,Map<String,Long> stateMetrics,Map<String,Long> quality) { }
        public Observations observe(Iterable<PointQuery<ObjectId>> queries) {
            var replayWork=new ValuesWork();
            var comparator=Comparator.comparing((ObjectId id)->id.unit().publication().localId()).thenComparing(id->id.unit().localId()).thenComparing(ObjectId::localId);
            var batch=BatchReplayer.materialize(profile.session,dataflow,Direction.FORWARD,PossibleValuesState.unreachable(),queries,comparator,
                (state,operation)->profile.transferOperation(state,operation,replayWork),new BatchReplayer.Projection<PossibleValuesState,ObjectId,ValueFact>() {
                    @Override public boolean supports(PointQuery<ObjectId> query){return profile.supports(query.subject(),query.point().entry());}
                    @Override public ValueFact project(PointQuery<ObjectId> query,PossibleValuesState state) {
                        var cell=profile.subjects.get(query.subject());boolean source=profile.sourceOpen(query.subject(),query.point().entry());
                        var evidence=new ArrayList<Id>();evidence.add(query.point().entry());evidence.add(query.subject());evidence.add(cell.cell().header().id());
                        if(query.point().operation()!=null)evidence.add(query.point().operation());
                        boolean model=state.isReached()&&state.value(cell.ordinal(),replayWork).open();
                        return new ValueFact(cell.cell().header().id(),state.isReached()?ValueFact.Reachability.REACHABLE:ValueFact.Reachability.UNREACHABLE_IN_MODEL,
                            state.isReached()?profile.universe.materialize(state.value(cell.ordinal(),replayWork)):null,state.isReached()?model:null,
                            source,model||source,profile.premises,evidence);
                    }
                });
            var quality=new HashMap<String,Long>();
            for(String name:List.of("modelOpenResults","sourceOpenResults","effectiveOpenResults","closedInModelResults"))quality.put(name,0L);
            for(var observation:batch.observations())if(observation.value()!=null) {
                var fact=observation.value();
                if(Boolean.TRUE.equals(fact.modelValueRemainder()))increment(quality,"modelOpenResults");
                if(fact.sourceUnknownRemainder())increment(quality,"sourceOpenResults");
                if(fact.effectiveUnknownRemainder())increment(quality,"effectiveOpenResults");
                if(Boolean.FALSE.equals(fact.modelValueRemainder()))increment(quality,"closedInModelResults");
            }
            return new Observations(batch,replayWork.snapshot(),Map.copyOf(quality));
        }
        private static void increment(Map<String,Long> metrics,String key){metrics.put(key,Math.incrementExact(metrics.get(key)));}
    }
}
