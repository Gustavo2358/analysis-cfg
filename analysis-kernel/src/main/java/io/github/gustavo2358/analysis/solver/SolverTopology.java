package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.structure.ContextView;
import io.github.gustavo2358.analysis.structure.ProgramIndex;
import java.util.*;

/** Dense execution directory derived once from W1 cursors; no semantic graph reconstruction. */
final class SolverTopology {
    final AnalysisPoint[] points;
    final CfgTransition[] edges;
    final int[] from, to, forwardHead, backwardHead, forwardNext, backwardNext;
    final IdentityHashMap<ContextView, IdentityHashMap<ProgramIndex.Node, List<AnalysisPoint>>> lookup = new IdentityHashMap<>();

    SolverTopology(AnalysisSession session) {
        if(session.hasLocalControl())throw new IllegalArgumentException("local control requires activation tabulation");
        List<AnalysisPoint> ps = new ArrayList<>();
        List<CfgTransition> es = new ArrayList<>();
        List<Integer> sources = new ArrayList<>(), targets = new ArrayList<>();
        for (ContextView context : session.contexts()) {
            var nodes = new IdentityHashMap<ProgramIndex.Node, List<AnalysisPoint>>();
            var traversals = new HashMap<ContextView.Point,AnalysisPoint>();
            lookup.put(context, nodes);
            int begin = ps.size();
            add(ps, nodes, traversals, context, context.initialPoint());
            // Reachability is always program-forward, including for a backward analysis.
            for (int p = begin; p < ps.size(); p++) {
                AnalysisPoint point = ps.get(p);
                var cursor = context.successors(point.traversal());
                while (cursor.advance()) {
                    AnalysisPoint target = add(ps, nodes, traversals, context, cursor.target());
                    Math.incrementExact(es.size());
                    es.add(cursor.transition()); sources.add(point.ordinal); targets.add(target.ordinal);
                }
            }
        }
        points = ps.toArray(AnalysisPoint[]::new); edges = es.toArray(CfgTransition[]::new);
        from = new int[edges.length]; to = new int[edges.length];
        forwardNext = new int[edges.length]; backwardNext = new int[edges.length];
        forwardHead = new int[points.length]; backwardHead = new int[points.length];
        Arrays.fill(forwardHead, -1); Arrays.fill(backwardHead, -1);
        for (int e = edges.length - 1; e >= 0; e--) {
            from[e] = sources.get(e); to[e] = targets.get(e);
            forwardNext[e] = forwardHead[from[e]]; forwardHead[from[e]] = e;
            backwardNext[e] = backwardHead[to[e]]; backwardHead[to[e]] = e;
        }
    }
    private static AnalysisPoint add(List<AnalysisPoint> points, IdentityHashMap<ProgramIndex.Node, List<AnalysisPoint>> nodes,
                                     Map<ContextView.Point,AnalysisPoint> traversals, ContextView context, ContextView.Point traversal) {
        AnalysisPoint existing = traversals.get(traversal);
        if (existing != null) return existing;
        int ordinal = points.size(); Math.incrementExact(ordinal);
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
}
