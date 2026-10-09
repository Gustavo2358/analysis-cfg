package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;

class SourceCorrelationIndexTest {
    private static final UnitId UNIT = new UnitId("synthetic", List.of(0), "FILES");
    private static final Location LOCATION = new Location("synthetic.cbl", 1, 0, 1, 1);
    private static final Provenance ORIGIN = new Provenance(LOCATION, LOCATION, List.of(), true);
    private static final Support SUPPORT = new Support("ENTRY_UNKNOWN", List.of(), List.of(), "NONE");
    private static NativeFileUse use(int ordinal, String location, List<String> qualifications) {
        return new NativeFileUse(new StatementId(UNIT, "s" + ordinal), 0, location, "READ", false,
            List.of(new NativeFileName("declaration", UNIT, "SYNFILE", "SYNFILE", List.of(ORIGIN))), ORIGIN, qualifications, List.of());
    }
    private static UnitEvidence unit(List<Statement> statements, List<Node> nodes, List<NativeFileUse> files) {
        var steps = new ArrayList<Derivation>();
        for (var node : nodes) steps.add(new Derivation("d/" + node.id(), List.of(), node.id(), List.of(), "PRIMARY_ENTRY", List.of("p"), List.of()));
        return new UnitEvidence(UNIT, true, statements, List.of(), List.of(), nodes, steps, List.of(), List.of(), List.of(),
            List.of(new Proof("p", "LOCAL_GRAMMAR", "synthetic", ORIGIN, List.of())), List.of(), Optional.empty(), files);
    }
    private static final class CountedNodes extends AbstractList<Node> implements RandomAccess {
        final List<Node> values; long reads;
        CountedNodes(List<Node> values) { this.values = values; }
        @Override public int size() { return values.size(); }
        @Override public Node get(int index) { reads++; return values.get(index); }
    }
    @Test void nativeFileAdmissionIndexesNodesOnceInsteadOfScanningPerUse() {
        int n = 2048; var statements = new ArrayList<Statement>(); var nodes = new ArrayList<Node>();
        var files = new ArrayList<NativeFileUse>();
        for (int i = 0; i < n; i++) {
            statements.add(new Statement(new StatementId(UNIT, "s" + i), ORIGIN));
            nodes.add(new Node("n" + i, "ROOT", "s" + i, SUPPORT));
            files.add(use(i, "s" + i, List.of("n" + i)));
        }
        var counted = new CountedNodes(nodes); var admitted = unit(statements, counted, files);
        assertEquals(files, admitted.nativeFiles()); assertEquals(nodes, admitted.nodes());
        assertTrue(counted.reads <= 12L * n, "per-file inventory scans retained: " + counted.reads);
    }
    @Test void completeAlternativeSetsAndQualificationOwnershipRemainMandatory() {
        var statements = List.of(new Statement(new StatementId(UNIT, "s0"), ORIGIN), new Statement(new StatementId(UNIT, "s1"), ORIGIN));
        var nodes = List.of(new Node("a", "ROOT", "s0", SUPPORT), new Node("b", "ROOT", "s0", SUPPORT), new Node("c", "ROOT", "s1", SUPPORT));
        var files = List.of(use(0, "s0", List.of("b", "a")), use(1, "s1", List.of("c")));
        assertEquals(files, unit(statements, nodes, files).nativeFiles());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> unit(statements, nodes,
            List.of(use(0, "s0", List.of("a"))))).getMessage().contains("complete native file alternatives"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> unit(statements, nodes,
            List.of(use(0, "s0", List.of("a", "c"))))).getMessage().contains("native file qualification owner"));
        assertThrows(IllegalArgumentException.class, () -> unit(statements, nodes, List.of(use(0, "s0", List.of("missing")))));
    }
}
