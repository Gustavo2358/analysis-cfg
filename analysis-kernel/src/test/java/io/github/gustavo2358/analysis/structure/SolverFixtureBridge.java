package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import java.util.*;

/** Test-only bridge to existing structural AIR generator, with independent adjacency inputs. */
public final class SolverFixtureBridge {
    private SolverFixtureBridge() { }
    public static AnalysisSession graph(int[][] successors, int[] roots, int selected) {
        var p = new PublicationId("solver-fixture"); var u = new UnitId(p, "unit");
        List<Sequence> sequences = new ArrayList<>();
        for (int n = 0; n < successors.length; n++) {
            int[] targets = successors[n]; String label = "n" + n;
            sequences.add(switch (targets.length) {
                case 0 -> StructuralFixtures.returning(u, label, List.of());
                case 1 -> StructuralFixtures.jump(u, label, "n" + targets[0]);
                case 2 -> StructuralFixtures.branch(u, label, "n" + targets[0], "n" + targets[1]);
                default -> throw new IllegalArgumentException("fixture uses core binary branches");
            });
        }
        List<Entries.Entry> entries = new ArrayList<>();
        for (int e = 0; e < roots.length; e++) entries.add(StructuralFixtures.entry(u, "e" + e, "n" + roots[e]));
        Publication snapshot = StructuralFixtures.publication(p, List.of(StructuralFixtures.unit(u, entries, sequences, List.of())), List.of());
        return AnalysisSession.open(StructuralFixtures.build(snapshot), snapshot, ProjectionPolicy.KNOWN_SUBSET,
                entries.subList(0, selected)).session().orElseThrow();
    }
    public static ProgramIndex.Node node(AnalysisSession session, int n) {
        return session.index().sequence(new LabelId(new UnitId(session.index().publication().id(), "unit"), "n" + n));
    }
}
