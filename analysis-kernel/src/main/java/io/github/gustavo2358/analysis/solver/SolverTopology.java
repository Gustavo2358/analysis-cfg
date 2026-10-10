package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.structure.ContextView;
import io.github.gustavo2358.analysis.structure.ProgramIndex;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.util.*;

/** Stable contextual points only; original program edges are borrowed through W1 cursors. */
final class SolverTopology implements AutoCloseable {
    final AnalysisPoint[] points;
    final IdentityHashMap<ContextView, Map<ProgramIndex.Node, List<AnalysisPoint>>> lookup;
    private final AnalysisResources resources;
    private AnalysisResources.Reservation retained,working;
    final ProgramStore program;

    SolverTopology(AnalysisSession session) {
        this(session,AnalysisResources.executionFor(session.index().store()));
    }
    SolverTopology(AnalysisSession session,AnalysisResources resources) {
        if(session.hasLocalControl())throw new IllegalArgumentException("local control requires activation tabulation");
        this.resources=Objects.requireNonNull(resources);program=session.index().store();
        retained=resources.reserve(AnalysisResources.Pool.RESIDENT,256,AnalysisResources.Phase.CONTROL);
        try {
        working=resources.reserve(AnalysisResources.Pool.SCRATCH,256,AnalysisResources.Phase.CONTROL);
        lookup=new IdentityHashMap<>();
        List<AnalysisPoint> ps = new ArrayList<>();
        for (ContextView context : session.contexts()) {
            retained.grow(256,AnalysisResources.Phase.CONTROL);working.grow(256,AnalysisResources.Phase.CONTROL);
            session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
            var nodes = new PointLookup();
            var groups = new HashSet<ContextView.OpenGroup>();
            lookup.put(context, nodes);
            int begin = ps.size();
            add(ps, nodes, context, context.entryNode());
            // Reachability is always program-forward, including for a backward analysis.
            for (int p = begin; p < ps.size(); p++) {
                session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
                AnalysisPoint point = ps.get(p);
                var cursor = context.knownSuccessors(point.node());
                while (cursor.advance()) {
                    session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
                    add(ps, nodes, context, cursor.target());
                }
                // Only structural discovery shares equal destination groups.
                // Value propagation must still invoke every original edge transfer.
                var group=context.unknownGroup(point.node());
                if(group!=null&&!groups.contains(group)) {
                    working.grow(group.keyBytes(bytes->{program.progress(ProgramStore.ExecutionPhase.CONTROL);working.grow(bytes,AnalysisResources.Phase.CONTROL);}),AnalysisResources.Phase.CONTROL);
                    groups.add(group);
                    cursor=context.unknownSuccessors(point.node());
                    while(cursor.advance()) {
                        session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
                        add(ps,nodes,context,cursor.target());
                    }
                }
            }
        }
        working.grow(128L+8L*ps.size(),AnalysisResources.Phase.CONTROL);
        points = ps.toArray(AnalysisPoint[]::new);
        }catch(RuntimeException|Error failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    private AnalysisPoint add(List<AnalysisPoint> points,PointLookup nodes,ContextView context,ProgramIndex.Node node) {
        AnalysisPoint existing = nodes.points.get(node);
        if (existing != null) return existing;
        int ordinal = points.size(); Math.incrementExact(ordinal);
        retained.grow(256,AnalysisResources.Phase.CONTROL);working.grow(32,AnalysisResources.Phase.CONTROL);
        AnalysisPoint point = new AnalysisPoint(ordinal, context, context.emptyStackPoint(node));
        nodes.points.put(node,point);points.add(point);return point;
    }
    /** List compatibility is a transient query view, not a retained object per point. */
    private static final class PointLookup extends AbstractMap<ProgramIndex.Node,List<AnalysisPoint>> {
        private final IdentityHashMap<ProgramIndex.Node,AnalysisPoint> points=new IdentityHashMap<>();
        @Override public List<AnalysisPoint> get(Object key){var point=points.get(key);return point==null?null:List.of(point);}
        @Override public boolean containsKey(Object key){return points.containsKey(key);}
        @Override public int size(){return points.size();}
        @Override public void clear(){points.clear();}
        @Override public Set<Map.Entry<ProgramIndex.Node,List<AnalysisPoint>>> entrySet(){
            return new AbstractSet<>() {
                @Override public int size(){return points.size();}
                @Override public Iterator<Map.Entry<ProgramIndex.Node,List<AnalysisPoint>>> iterator(){
                    var iterator=points.entrySet().iterator();
                    return new Iterator<>() {
                        @Override public boolean hasNext(){return iterator.hasNext();}
                        @Override public Map.Entry<ProgramIndex.Node,List<AnalysisPoint>> next(){var row=iterator.next();return new SimpleImmutableEntry<>(row.getKey(),List.of(row.getValue()));}
                    };
                }
            };
        }
    }
    AnalysisPoint find(ContextView context, ProgramIndex.Node node) {
        var nodes = (PointLookup)lookup.get(context);return nodes==null?null:nodes.points.get(node);
    }
    AnalysisPoint require(ContextView context, ProgramIndex.Node node) {
        AnalysisPoint p = find(context, node);
        if (p == null) throw new IllegalArgumentException("point is outside the selected reachable contexts");
        return p;
    }
    AnalysisResources.Reservation retainedCapacity(){return retained;}
    void transferRetainedCapacity(){retained=null;}
    AnalysisResources resources(){return resources;}
    @Override public void close(){var scratch=working;var live=retained;working=null;retained=null;AnalysisResources.closeCapacities(scratch,live);}
}
