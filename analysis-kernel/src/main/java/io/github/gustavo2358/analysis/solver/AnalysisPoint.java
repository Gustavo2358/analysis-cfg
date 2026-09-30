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
        this(ordinal,context,new ContextView.Point(node,io.github.gustavo2358.analysis.cfg.domain.LocalControlRules.Stack.EMPTY,context.entry().id()));
    }
    public ContextView.Point traversal() { return traversal; }
    public ContextView context() { return context; }
    public ProgramIndex.Node node() { return node; }
}
