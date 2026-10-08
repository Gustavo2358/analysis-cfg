package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.AnalysisProvider;
import io.github.gustavo2358.analysis.cfg.domain.CfgNode;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Structural reachability through the shared activation tabulator; no value-domain transfer. */
public final class ReachabilityProvider implements AnalysisProvider<LabelId,ReachabilityProvider.Fact> {
    public record Fact(boolean reachable,boolean sourceUnknownRemainder,boolean controlUnknown) {
        public Fact(boolean reachable,boolean sourceUnknownRemainder){this(reachable,sourceUnknownRemainder,false);}
    }
    public static AnalysisKey key(EntryId entry) {return new AnalysisKey("Reachability","1","known-graph@1",Direction.FORWARD,"KNOWN_GRAPH_ENTRY",Map.of(),entry);}
    public static ObservationBatchId<LabelId,Fact> batch(String id,EntryId entry) {return new ObservationBatchId<>(id,key(entry),"ReachabilityFact@1",LabelId.class,Fact.class);}
    public String implementation(){return "Reachability";}
    public String version(){return "1";}
    public String projection(){return "ReachabilityFact@1";}
    public Set<String> semanticOptionNames(){return Set.of();}
    public Class<LabelId> subjectType(){return LabelId.class;}
    public Class<Fact> factType(){return Fact.class;}
    public boolean supports(AnalysisKey key){return key.equals(key(key.entry()));}
    public Comparator<LabelId> subjectOrder(){return Comparator.comparing((LabelId id)->id.publication().localId()).thenComparing(id->id.unit().localId()).thenComparing(LabelId::localId);}
    public Prepared<LabelId,Fact> prepare(AnalysisSession session,AnalysisKey key) {
        if(!supports(key)||session.context(key.entry())==null)throw new IllegalArgumentException("reachability key/context");
        var context=session.context(key.entry());var unit=session.index().unit(key.entry().unit());
        boolean open=open(session.index().store().coverage())||open(unit.coverage())||!context.entry().state().uncertainties().isEmpty();
        for(var sequence:unit.sequences()) {
            for(var instruction:sequence.instructions())if(!(instruction instanceof Operations.HavocMust||instruction instanceof Operations.HavocMay))open|=open(instruction.header());
            if(!(sequence.terminator() instanceof Operations.Opaque))open|=open(sequence.terminator().header());
            if(sequence.terminator() instanceof Operations.Invoke invoke)open|=invoke.outcomes().remainder() instanceof Scopes.WithinControl;
        }
        final boolean sourceOpen=open || session.index().partialControl(unit.id()) || session.index().unprovedPreconditions(unit.id());
        return new Prepared<>() {
            public AnalysisKey key(){return key;}
            public AnalysisOutcome refusal(){return null;}
            public Run<LabelId,Fact> execute() {
                var selected=session.selectEntries(List.of(key.entry()));
                var selectedContext=selected.context(key.entry());
                var definition=new AnalysisDefinition<Integer>() {
                    public Direction direction(){return Direction.FORWARD;}
                    public Integer bottom(){return 0;}
                    public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(selectedContext,selectedContext.entryNode(),1));}
                    public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){int union=a|b;return new Join<>(union,union!=a);}
                    public long stateFingerprint(Integer state){return state.longValue();}
                    public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
                    public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return state;}
                    public Integer transferEdge(AnalysisPoint point,io.github.gustavo2358.analysis.cfg.domain.CfgTransition edge,Integer state,DomainWork work) {
                        if(state==0)return 0;
                        boolean unknown=point.node().source() instanceof CfgNode.SequenceNode sequence
                            && sequence.terminator() instanceof Operations.Opaque opaque
                            && opaque.envelope().control().remainder() instanceof Scopes.WithinControl
                            ||edge.kind()==io.github.gustavo2358.analysis.cfg.domain.CfgTransition.Kind.OPAQUE_UNKNOWN;
                        return unknown?state|2:state;
                    }
                };
                var solved=DataflowSolver.solve(selected,definition);
                var reached=new HashSet<LabelId>();var openLabels=new HashSet<LabelId>();
                for(var sequence:unit.sequences())for(int state:solved.states(selectedContext,session.index().sequence(sequence.label()),true)) {
                    if((state&1)!=0)reached.add(sequence.label());
                    if((state&2)!=0)openLabels.add(sequence.label());
                }
                var outcome=new AnalysisOutcome(key,AnalysisOutcome.Status.STABLE,null,Map.of("nodesVisited",solved.metrics().analysisPoints(),"edgesVisited",solved.metrics().contextualEdges(),"reachabilityRuns",1L));
                return new Run<>() {
                    public AnalysisOutcome outcome(){return outcome;}
                    public Materialized<LabelId,Fact> observe(List<PointQuery<LabelId>> queries) {
                        var answers=new ArrayList<ObservationBatch.Observation<LabelId,Fact>>();
                        for(var q:queries) {
                            var site=session.index().site(q.point().operation());
                            if(q.point().kind()!=ProgramPoint.Kind.BEFORE||!q.point().entry().equals(key.entry())||site==null||!site.sequence().label().equals(q.subject()))
                                throw new ObservationBatch.ObservationException("unbound reachability point");
                            answers.add(new ObservationBatch.Observation<>(q,ObservationBatch.QueryStatus.VALUE,null,new Fact(reached.contains(q.subject()),sourceOpen,openLabels.contains(q.subject()))));
                        }
                        return new Materialized<>(new ObservationBatch<>(ObservationBatch.Status.COMPLETE,null,answers,
                            new ObservationBatch.Metrics(queries.size(),queries.size(),0,0,0,queries.size(),0,0,0)),Map.of());
                    }
                };
            }
        };
    }
    private static boolean open(Evidence.Coverage c){return c.inventory()!=Evidence.InventoryStatus.COMPLETE||!c.uncertainties().isEmpty();}
    private static boolean open(Evidence.Claim c){return c.status()!=Evidence.PrecisionStatus.EXACT&&c.status()!=Evidence.PrecisionStatus.NOT_APPLICABLE;}
    private static boolean open(Operations.Header h){return h.coverage()!=Evidence.CoverageStatus.MODELED||open(h.precision().control())||open(h.precision().effects())||open(h.precision().storage())||open(h.precision().values());}
}
