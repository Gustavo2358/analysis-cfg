package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.AnalysisProvider;
import io.github.gustavo2358.analysis.cfg.domain.CfgNode;
import io.github.gustavo2358.analysis.cfg.domain.CfgControl;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Structural reachability; ordinary roots share an exact closure, local control uses the activation tabulator. */
public final class ReachabilityProvider implements AnalysisProvider<LabelId,ReachabilityProvider.Fact> {
    // Index identity includes admitted snapshot/options; node identity is its full nominal label.
    private record InitialRoot(ProgramIndex index,ProgramIndex.Node label) { }
    private final Map<InitialRoot,OrdinaryClosure> ordinaryRuns=new HashMap<>();
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
        if(!session.hasLocalControl())return ordinary(session,key);
        var context=session.context(key.entry());var unit=session.index().unit(key.entry().unit());
        boolean open=open(session.index().store().coverage())||open(unit.coverage())||!context.entryView().state().uncertainties().isEmpty();
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
                            && sequence.control() instanceof CfgControl.Opaque opaque
                            && opaque.remainder() instanceof Scopes.WithinControl
                            ||edge.kind()==io.github.gustavo2358.analysis.cfg.domain.CfgTransition.Kind.OPAQUE_UNKNOWN;
                        return unknown?state|2:state;
                    }
                };
                var reached=new HashSet<LabelId>();var openLabels=new HashSet<LabelId>();
                final AnalysisOutcome outcome;
                try(var solved=DataflowSolver.solve(selected,definition)) {
                    for(var sequence:unit.sequences())for(int state:solved.states(selectedContext,session.index().sequence(sequence.label()),true)) {
                        if((state&1)!=0)reached.add(sequence.label());
                        if((state&2)!=0)openLabels.add(sequence.label());
                    }
                    outcome=new AnalysisOutcome(key,AnalysisOutcome.Status.STABLE,null,Map.of("nodesVisited",solved.metrics().analysisPoints(),"edgesVisited",solved.metrics().contextualEdges(),"reachabilityRuns",1L));
                }
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
    private Prepared<LabelId,Fact> ordinary(AnalysisSession session,AnalysisKey key) {
        var context=session.context(key.entry());
        var root=new InitialRoot(session.index(),session.index().sequence(context.entryView().initialLabel().orElseThrow()));
        return new Prepared<>() {
            public AnalysisKey key(){return key;}
            public AnalysisOutcome refusal(){return null;}
            public Run<LabelId,Fact> execute() {
                final boolean entryOpen=!context.entryView().state().uncertainties().isEmpty();
                var closure=ordinaryRuns.get(root);boolean created=closure==null;
                if(created){
                    closure=new OrdinaryClosure(session,context);
                    try{ordinaryRuns.put(root,closure);}
                    catch(RuntimeException|Error failure){closure.close();throw failure;}
                }
                final OrdinaryClosure shared=closure;
                AnalysisResources.Reservation binding;
                // Run, detached outcome/map, binding lease and owner-expiry observer.
                try{binding=shared.resources.reserve(AnalysisResources.Pool.RESIDENT,1024,AnalysisResources.Phase.CONTROL);}
                catch(RuntimeException|Error failure){if(created){ordinaryRuns.remove(root);shared.close();}throw failure;}
                shared.references++;
                try {
                final boolean sourceOpen=shared.sourceOpen||entryOpen;
                var outcome=new AnalysisOutcome(key,AnalysisOutcome.Status.STABLE,null,Map.of(
                    "nodesVisited",(long)shared.flags.size(),"edgesVisited",shared.edges,"reachabilityRuns",1L,
                    "reachabilityPhysicalRuns",created?1L:0L,"reachabilityOpenGroups",created?(long)shared.groups.size():0L));
                var run=new OrdinaryRun(ReachabilityProvider.this,session,root,shared,binding,key,sourceOpen,outcome);
                var observer=new java.lang.ref.WeakReference<Run<LabelId,Fact>>(run);
                binding.onOwnerClose(()->{var live=observer.get();if(live!=null)live.close();else retire(root,shared,binding);});
                return run;
                }catch(RuntimeException|Error failure){
                    retire(root,shared,binding);
                    throw failure;
                }
            }
        };
    }

    private void retire(InitialRoot root,OrdinaryClosure shared,AnalysisResources.Reservation binding) {
        try{binding.close();}finally{if(--shared.references==0){ordinaryRuns.remove(root,shared);shared.close();}}
    }

    /** No anonymous captures: a closed handle cannot pin the retired execution/index. */
    private static final class OrdinaryRun implements Run<LabelId,Fact> {
        private ReachabilityProvider provider;
        private AnalysisSession session;
        private InitialRoot root;
        private OrdinaryClosure shared;
        private AnalysisResources.Reservation binding;
        private final AnalysisKey key;
        private final boolean sourceOpen;
        private final AnalysisOutcome outcome;
        private boolean closed;
        OrdinaryRun(ReachabilityProvider provider,AnalysisSession session,InitialRoot root,OrdinaryClosure shared,
                AnalysisResources.Reservation binding,AnalysisKey key,boolean sourceOpen,AnalysisOutcome outcome) {
            this.provider=provider;this.session=session;this.root=root;this.shared=shared;this.binding=binding;
            this.key=key;this.sourceOpen=sourceOpen;this.outcome=outcome;
        }
        public AnalysisOutcome outcome(){requireOpen();return outcome;}
        private void requireOpen(){if(closed)throw new IllegalStateException("reachability run closed");}
        public Materialized<LabelId,Fact> observe(List<PointQuery<LabelId>> queries) {
            requireOpen();var answers=new ArrayList<ObservationBatch.Observation<LabelId,Fact>>();
            for(var query:queries) {
                session.index().store().progress(ProgramStore.ExecutionPhase.REPLAY);
                var site=session.index().site(query.point().operation());
                if(query.point().kind()!=ProgramPoint.Kind.BEFORE||!query.point().entry().equals(key.entry())||site==null||!site.sequence().label().equals(query.subject()))
                    throw new ObservationBatch.ObservationException("unbound reachability point");
                int state=shared.flags.getOrDefault(session.index().sequence(query.subject()),0);
                answers.add(new ObservationBatch.Observation<>(query,ObservationBatch.QueryStatus.VALUE,null,new Fact((state&1)!=0,sourceOpen,(state&2)!=0)));
            }
            return new Materialized<>(new ObservationBatch<>(ObservationBatch.Status.COMPLETE,null,answers,
                new ObservationBatch.Metrics(queries.size(),queries.size(),0,0,0,queries.size(),0,0,0)),Map.of());
        }
        public void close() {
            if(closed)return;closed=true;
            var owner=provider;var identity=root;var state=shared;var lease=binding;
            provider=null;session=null;root=null;shared=null;binding=null;
            owner.retire(identity,state,lease);
        }
    }

    /** Two-bit reachability, not an aggregation rule for an arbitrary value transfer. */
    private static final class OrdinaryClosure implements AutoCloseable {
        final AnalysisResources resources;
        IdentityHashMap<ProgramIndex.Node,Integer> flags;
        Set<Scopes.ControlScope> groups;
        private final AnalysisResources.Reservation retained;
        final boolean sourceOpen;
        int references;
        long edges;
        OrdinaryClosure(AnalysisSession session,ContextView context) {
            resources=AnalysisResources.executionFor(session.index().store());
            retained=resources.reserve(AnalysisResources.Pool.RESIDENT,1024,AnalysisResources.Phase.CONTROL);
            AnalysisResources.Reservation scratch=null;
            try {
                scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,1024,AnalysisResources.Phase.CONTROL);
                flags=new IdentityHashMap<>();groups=new HashSet<>();
                var queue=new ArrayDeque<ProgramIndex.Node>();
                boolean open=open(session.index().store().coverage())||open(context.entryNode().owner().coverage());
                for(var sequence:context.entryNode().owner().sequences()) {
                    session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
                    for(var instruction:sequence.instructions())if(!(instruction instanceof Operations.HavocMust||instruction instanceof Operations.HavocMay))open|=open(instruction.header());
                    if(!(sequence.terminator() instanceof Operations.Opaque))open|=open(sequence.terminator().header());
                    if(sequence.terminator() instanceof Operations.Invoke invoke)open|=invoke.outcomes().remainder() instanceof Scopes.WithinControl;
                }
                sourceOpen=open||session.index().partialControl(context.entryView().id().unit())||session.index().unprovedPreconditions(context.entryView().id().unit());
                add(context.entryNode(),1,queue,scratch);
                while(!queue.isEmpty()) {
                    session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
                    var node=queue.removeFirst();int state=flags.get(node);
                    boolean opaque=node.source() instanceof CfgNode.SequenceNode sequence
                        &&sequence.control() instanceof CfgControl.Opaque control&&control.remainder() instanceof Scopes.WithinControl;
                    var known=context.knownSuccessors(node);
                    while(known.advance()) {
                        session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);edges=Math.incrementExact(edges);
                        boolean unknown=opaque||known.transition().kind()==io.github.gustavo2358.analysis.cfg.domain.CfgTransition.Kind.OPAQUE_UNKNOWN;
                        add(known.target(),unknown?state|2:state,queue,scratch);
                    }
                    if(context.unknownControl(node) instanceof Scopes.WithinControl within&&!groups.contains(within.scope())) {
                        retained.grow(scopeBytes(within.scope(),scratch),AnalysisResources.Phase.CONTROL);
                        groups.add(within.scope());
                        var unknown=context.unknownSuccessors(node);
                        while(unknown.advance()) {
                            session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);edges=Math.incrementExact(edges);
                            add(unknown.target(),state|2,queue,scratch);
                        }
                    }
                }
            }catch(RuntimeException|Error failure){retained.close();throw failure;}
            finally{if(scratch!=null)scratch.close();}
        }
        private void add(ProgramIndex.Node node,int contribution,ArrayDeque<ProgramIndex.Node> queue,AnalysisResources.Reservation scratch) {
            int prior=flags.getOrDefault(node,0),joined=prior|contribution;
            if(joined==prior)return;
            if(prior==0) {
                // Map resize old+new and at most two pending queue slots per two-bit node.
                retained.grow(256,AnalysisResources.Phase.CONTROL);scratch.grow(128,AnalysisResources.Phase.CONTROL);
            }
            flags.put(node,joined);queue.addLast(node);
        }
        private static long textBytes(String text){return 32+2L*text.length();}
        private static long unitBytes(UnitId unit){return 96+textBytes(unit.publication().localId())+textBytes(unit.localId());}
        private static long scopeBytes(Scopes.ControlScope root,AnalysisResources.Reservation scratch) {
            long bytes=512;var pending=new ArrayDeque<Scopes.ControlScope>();pending.add(root);
            while(!pending.isEmpty()) {
                var scope=pending.removeLast();bytes=Math.addExact(bytes,96);
                if(scope instanceof Scopes.UnitControl unit)bytes=Math.addExact(bytes,unitBytes(unit.unit()));
                else if(scope instanceof Scopes.LabelsControl labels)for(var label:labels.labels())bytes=Math.addExact(bytes,96+unitBytes(label.unit())+textBytes(label.localId()));
                else if(scope instanceof Scopes.ControlUnion union) {
                    scratch.grow(64L*union.members().size(),AnalysisResources.Phase.CONTROL);
                    pending.addAll(union.members());
                }
            }
            return bytes;
        }
        public void close(){flags=null;groups=null;retained.close();}
    }

    private static boolean open(ProgramStore.CoverageView c){return c.inventory()!=Evidence.InventoryStatus.COMPLETE||!c.uncertainties().isEmpty();}
    private static boolean open(Evidence.Claim c){return c.status()!=Evidence.PrecisionStatus.EXACT&&c.status()!=Evidence.PrecisionStatus.NOT_APPLICABLE;}
    private static boolean open(Operations.Header h){return h.coverage()!=Evidence.CoverageStatus.MODELED||open(h.precision().control())||open(h.precision().effects())||open(h.precision().storage())||open(h.precision().values());}
}
