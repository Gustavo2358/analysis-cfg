package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.structure.ContextView;
import io.github.gustavo2358.analysis.structure.ProgramIndex;
import java.util.Objects;

/**
 * Fixed configuration for a solve. Join must be associative, commutative, idempotent and an
 * upper bound; equivalence must agree with its order. Both transfers must be monotone.
 * The domain must have finite height or an explicitly justified convergence guarantee.
 * Graph, boundaries and semantic configuration cannot change during execution.
 *
 * All returned states are non-null, isolated roots: callbacks must never mutate any supplied
 * or previously returned root. Immutable, persistent, interned and copy-on-write states are
 * supported without mandatory copies. Bottom and reached-empty are defined by the domain.
 * No guarantee is made for arbitrary functions violating these laws.
 */
public interface AnalysisDefinition<S> {
    Direction direction();
    S bottom();
    Iterable<Boundary<S>> boundaries(AnalysisSession session);
    Join<S> joinInto(S current, S contribution, DomainWork work);
    boolean equivalent(S first, S second, DomainWork work);
    /** FORWARD: IN -> OUT; BACKWARD: OUT -> IN using reverse block semantics. */
    S transferBlock(AnalysisPoint point, S anchor, DomainWork work);
    /** Original program edge in both directions; published is OUT forward, IN backward. */
    S transferEdge(AnalysisPoint publishingPoint, CfgTransition edge, S published, DomainWork work);

    record Join<S>(S state, boolean changed) {
        public Join { Objects.requireNonNull(state, "state"); }
    }
    /** Any effective point may have multiple boundary contributions, joined once each. */
    record Boundary<S>(ContextView context, ProgramIndex.Node node, S state) {
        public Boundary {
            Objects.requireNonNull(context, "context"); Objects.requireNonNull(node, "node");
            Objects.requireNonNull(state, "state");
        }
    }
}
