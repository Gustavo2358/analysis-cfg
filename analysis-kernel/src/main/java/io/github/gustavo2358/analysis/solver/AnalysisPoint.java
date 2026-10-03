package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.ContextView;
import io.github.gustavo2358.analysis.structure.ProgramIndex;

/** An effective contextual block point. Identity and dense ordinal belong to one solve. */
public final class AnalysisPoint {
    final int ordinal;
    private final ContextView context;
    private final ProgramIndex.Node node;
    private final ContextView.Point traversal;
    AnalysisPoint(int ordinal, ContextView context, ContextView.Point traversal) {
        this.ordinal = ordinal; this.context = context; this.node = traversal.node(); this.traversal = traversal;
    }
    AnalysisPoint(int ordinal, ContextView context, ProgramIndex.Node node) {
        this.ordinal=ordinal;this.context=context;this.node=node;this.traversal=null;
    }
    /** Concrete cursor handle when this point was not summarized across activation stacks. */
    public java.util.Optional<ContextView.Point> concreteTraversal() { return java.util.Optional.ofNullable(traversal); }
    /** A summarized point has no single stack; never manufacture an empty-stack handle. */
    public ContextView.Point traversal() {
        if(traversal==null)throw new IllegalStateException("summarized activation point has no concrete traversal");
        return traversal;
    }
    public ContextView context() { return context; }
    public ProgramIndex.Node node() { return node; }
}
