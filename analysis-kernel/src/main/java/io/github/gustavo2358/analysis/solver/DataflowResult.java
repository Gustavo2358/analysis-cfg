package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.ContextView;
import io.github.gustavo2358.analysis.structure.ProgramIndex;
import java.util.*;

/** Constructed only after convergence. Roots obey the definition's isolation contract. */
public final class DataflowResult<S> {
    private final IdentityHashMap<ContextView, IdentityHashMap<ProgramIndex.Node, List<AnalysisPoint>>> lookup;
    private final Object[] in, out;
    private final SolverMetrics metrics;
    DataflowResult(SolverTopology topology, Object[] in, Object[] out, SolverMetrics metrics) {
        this(topology.lookup,in,out,metrics);
    }
    DataflowResult(IdentityHashMap<ContextView,IdentityHashMap<ProgramIndex.Node,List<AnalysisPoint>>> lookup,Object[] in,Object[] out,SolverMetrics metrics) {
        this.lookup=lookup;this.in=in;this.out=out;this.metrics=metrics;
    }
    public enum Status { STABLE }
    public Status status() { return Status.STABLE; }
    public SolverMetrics metrics() { return metrics; }
    /** False for an unselected context, foreign node or structurally unreachable point. */
    public boolean contains(ContextView context, ProgramIndex.Node node) { var nodes=lookup.get(context); return nodes!=null&&nodes.containsKey(node); }
    @SuppressWarnings("unchecked")
    public S in(ContextView context, ProgramIndex.Node node) { return (S) in[require(context, node).ordinal]; }
    @SuppressWarnings("unchecked")
    public S out(ContextView context, ProgramIndex.Node node) { return (S) out[require(context, node).ordinal]; }
    /** Separate roots; joining before replay could invent cross-context expression values. */
    @SuppressWarnings("unchecked")
    public List<S> states(ContextView context,ProgramIndex.Node node,boolean atIn) {
        var nodes=lookup.get(context);var points=nodes==null?null:nodes.get(node);
        if(points==null)return List.of();
        var roots=new ArrayList<S>(points.size());
        for(var point:points)roots.add((S)(atIn?in:out)[point.ordinal]);
        return List.copyOf(roots);
    }
    private AnalysisPoint find(ContextView context, ProgramIndex.Node node) {
        var nodes = lookup.get(context);var points=nodes==null?null:nodes.get(node);
        if(points==null)return null;
        if(points.size()!=1)throw new IllegalArgumentException("multiple local contexts: replay each root before joining");
        return points.getFirst();
    }
    private AnalysisPoint require(ContextView context, ProgramIndex.Node node) {
        var p = find(context, node);
        if (p == null) throw new IllegalArgumentException("point is outside the selected reachable contexts");
        return p;
    }
}
