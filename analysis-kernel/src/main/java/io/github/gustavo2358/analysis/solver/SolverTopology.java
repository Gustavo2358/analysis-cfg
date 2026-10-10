package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.structure.ContextView;
import io.github.gustavo2358.analysis.structure.ProgramIndex;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.util.*;

/** Dense execution directory derived once from W1 cursors; no semantic graph reconstruction. */
final class SolverTopology implements AutoCloseable {
    final AnalysisPoint[] points;
    final CfgTransition[] edges;
    final int[] from, to, forwardHead, backwardHead, forwardNext, backwardNext;
    final IdentityHashMap<ContextView, IdentityHashMap<ProgramIndex.Node, List<AnalysisPoint>>> lookup;
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
        List<CfgTransition> es = new ArrayList<>();
        List<Integer> sources = new ArrayList<>(), targets = new ArrayList<>();
        for (ContextView context : session.contexts()) {
            retained.grow(256,AnalysisResources.Phase.CONTROL);working.grow(256,AnalysisResources.Phase.CONTROL);
            session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
            var nodes = new IdentityHashMap<ProgramIndex.Node, List<AnalysisPoint>>();
            var traversals = new HashMap<ContextView.Point,AnalysisPoint>();
            lookup.put(context, nodes);
            int begin = ps.size();
            add(ps, nodes, traversals, context, context.initialPoint());
            // Reachability is always program-forward, including for a backward analysis.
            for (int p = begin; p < ps.size(); p++) {
                session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
                AnalysisPoint point = ps.get(p);
                var cursor = context.successors(point.traversal());
                while (cursor.advance()) {
                    session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
                    AnalysisPoint target = add(ps, nodes, traversals, context, cursor.target());
                    Math.incrementExact(es.size());
                    working.grow(160,AnalysisResources.Phase.CONTROL);
                    es.add(cursor.transition()); sources.add(point.ordinal); targets.add(target.ordinal);
                }
            }
        }
        working.grow(128L+16L*ps.size()+24L*es.size(),AnalysisResources.Phase.CONTROL);
        points = ps.toArray(AnalysisPoint[]::new); edges = es.toArray(CfgTransition[]::new);
        from = new int[edges.length]; to = new int[edges.length];
        forwardNext = new int[edges.length]; backwardNext = new int[edges.length];
        forwardHead = new int[points.length]; backwardHead = new int[points.length];
        Arrays.fill(forwardHead, -1); Arrays.fill(backwardHead, -1);
        for (int e = edges.length - 1; e >= 0; e--) {
            session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
            from[e] = sources.get(e); to[e] = targets.get(e);
            forwardNext[e] = forwardHead[from[e]]; forwardHead[from[e]] = e;
            backwardNext[e] = backwardHead[to[e]]; backwardHead[to[e]] = e;
        }
        }catch(RuntimeException|Error failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    private AnalysisPoint add(List<AnalysisPoint> points, IdentityHashMap<ProgramIndex.Node, List<AnalysisPoint>> nodes,
                                     Map<ContextView.Point,AnalysisPoint> traversals, ContextView context, ContextView.Point traversal) {
        AnalysisPoint existing = traversals.get(traversal);
        if (existing != null) return existing;
        int ordinal = points.size(); Math.incrementExact(ordinal);
        retained.grow(256,AnalysisResources.Phase.CONTROL);working.grow(128,AnalysisResources.Phase.CONTROL);
        AnalysisPoint point = new AnalysisPoint(ordinal, context, traversal);
        traversals.put(traversal,point); nodes.computeIfAbsent(traversal.node(),ignored->new ArrayList<>()).add(point); points.add(point); return point;
    }
    AnalysisPoint find(ContextView context, ProgramIndex.Node node) {
        var nodes = lookup.get(context); var matches=nodes==null?null:nodes.get(node);
        if(matches==null)return null;
        if(matches.size()!=1)throw new IllegalArgumentException("boundary requires a unique local context");
        return matches.getFirst();
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
