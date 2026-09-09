package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.ContextView;
import io.github.gustavo2358.analysis.structure.ProgramIndex;
import java.util.IdentityHashMap;

/** Constructed only after convergence. Roots obey the definition's isolation contract. */
public final class DataflowResult<S> {
    private final IdentityHashMap<ContextView, IdentityHashMap<ProgramIndex.Node, AnalysisPoint>> lookup;
    private final Object[] in, out;
    private final SolverMetrics metrics;
    DataflowResult(SolverTopology topology, Object[] in, Object[] out, SolverMetrics metrics) {
        this.lookup = topology.lookup; this.in = in; this.out = out; this.metrics = metrics;
    }
    public enum Status { STABLE }
    public Status status() { return Status.STABLE; }
    public SolverMetrics metrics() { return metrics; }
    /** False for an unselected context, foreign node or structurally unreachable point. */
    public boolean contains(ContextView context, ProgramIndex.Node node) { return find(context, node) != null; }
    @SuppressWarnings("unchecked")
    public S in(ContextView context, ProgramIndex.Node node) { return (S) in[require(context, node).ordinal]; }
    @SuppressWarnings("unchecked")
    public S out(ContextView context, ProgramIndex.Node node) { return (S) out[require(context, node).ordinal]; }
    private AnalysisPoint find(ContextView context, ProgramIndex.Node node) {
        var nodes = lookup.get(context); return nodes == null ? null : nodes.get(node);
    }
    private AnalysisPoint require(ContextView context, ProgramIndex.Node node) {
        var p = find(context, node);
        if (p == null) throw new IllegalArgumentException("point is outside the selected reachable contexts");
        return p;
    }
}
