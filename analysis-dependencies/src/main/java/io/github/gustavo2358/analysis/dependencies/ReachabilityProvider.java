package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.AnalysisProvider;
import io.github.gustavo2358.analysis.cfg.domain.CfgNode;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.solver.Direction;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Generic forward BFS over the existing contextual graph. Does not execute a values solver. */
public final class ReachabilityProvider implements AnalysisProvider<LabelId,ReachabilityProvider.Fact> {
    public record Fact(boolean reachable,boolean sourceUnknownRemainder) { }
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
        boolean open=open(session.index().publication().coverage())||open(unit.coverage())||!context.entry().state().uncertainties().isEmpty();
        for(var sequence:unit.sequences()) {
            for(var instruction:sequence.instructions())open|=open(instruction.header());
            open|=open(sequence.terminator().header());
            if(sequence.terminator() instanceof Operations.Invoke invoke)open|=invoke.outcomes().remainder() instanceof Scopes.WithinControl;
        }
        final boolean sourceOpen=open;
        return new Prepared<>() {
            public AnalysisKey key(){return key;}
            public AnalysisOutcome refusal(){return null;}
            public Run<LabelId,Fact> execute() {
                Set<ProgramIndex.Node> seen=Collections.newSetFromMap(new IdentityHashMap<>());
                var pending=new ArrayDeque<ProgramIndex.Node>();var labels=new HashSet<LabelId>();
                seen.add(context.entryNode());pending.add(context.entryNode());long edges=0;
                while(!pending.isEmpty()) {
                    var node=pending.removeFirst();if(node.source() instanceof CfgNode.SequenceNode sequence)labels.add(sequence.source().label());
                    var cursor=context.successors(node);
                    while(cursor.advance()){edges=Math.incrementExact(edges);if(seen.add(cursor.target()))pending.addLast(cursor.target());}
                }
                var reached=Set.copyOf(labels);
                var outcome=new AnalysisOutcome(key,AnalysisOutcome.Status.STABLE,null,Map.of("nodesVisited",(long)seen.size(),"edgesVisited",edges,"reachabilityRuns",1L));
                return new Run<>() {
                    public AnalysisOutcome outcome(){return outcome;}
                    public Materialized<LabelId,Fact> observe(List<PointQuery<LabelId>> queries) {
                        var answers=new ArrayList<ObservationBatch.Observation<LabelId,Fact>>();
                        for(var q:queries) {
                            var site=session.index().site(q.point().operation());
                            if(q.point().kind()!=ProgramPoint.Kind.BEFORE||!q.point().entry().equals(key.entry())||site==null||!site.sequence().label().equals(q.subject()))
                                throw new ObservationBatch.ObservationException("unbound reachability point");
                            answers.add(new ObservationBatch.Observation<>(q,ObservationBatch.QueryStatus.VALUE,null,new Fact(reached.contains(q.subject()),sourceOpen)));
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
