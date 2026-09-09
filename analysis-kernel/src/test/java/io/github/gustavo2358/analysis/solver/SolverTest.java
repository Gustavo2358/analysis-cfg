package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.structure.SolverFixtureBridge.*;

class SolverTest {
    @Test void forwardBoundaryBlockAndEdgeHaveProgramOrderMeaning() {
        var session = graph(new int[][]{{1}, {}}, new int[]{0}, 1);
        var context = session.contexts().iterator().next();
        AnalysisDefinition<Set<String>> definition = new AnalysisDefinition<>() {
            public Direction direction() { return Direction.FORWARD; }
            public Set<String> bottom() { return Set.of(); }
            public Iterable<Boundary<Set<String>>> boundaries(AnalysisSession s) {
                return List.of(new Boundary<>(context, context.entryNode(), Set.of("boundary")));
            }
            public Join<Set<String>> joinInto(Set<String> a, Set<String> b, DomainWork w) {
                Set<String> merged = new TreeSet<>(a); merged.addAll(b);
                return new Join<>(Set.copyOf(merged), !merged.equals(a));
            }
            public boolean equivalent(Set<String> a, Set<String> b, DomainWork w) { return a.equals(b); }
            public Set<String> transferBlock(AnalysisPoint p, Set<String> a, DomainWork w) {
                Set<String> b = new TreeSet<>(a); if (p.node() == node(session, 0)) b.add("block"); return Set.copyOf(b);
            }
            public Set<String> transferEdge(AnalysisPoint p, CfgTransition e, Set<String> a, DomainWork w) {
                Set<String> b = new TreeSet<>(a); if (p.node() == node(session, 0)) b.add("edge"); return Set.copyOf(b);
            }
        };
        var result = DataflowSolver.solve(session, definition);
        assertEquals(Set.of("boundary"), result.in(context, node(session, 0)));
        assertEquals(Set.of("boundary", "block"), result.out(context, node(session, 0)));
        assertEquals(Set.of("boundary", "block", "edge"), result.in(context, node(session, 1)));
        assertEquals(DataflowResult.Status.STABLE, result.status());
    }
}
