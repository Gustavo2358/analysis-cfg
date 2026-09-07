package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Unit;
import io.github.gustavo2358.air.validation.AirValidator;
import io.github.gustavo2358.air.validation.ValidationOptions;
import io.github.gustavo2358.air.validation.ValidationResult;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.github.gustavo2358.air.model.Evidence.InventoryStatus.*;
import static io.github.gustavo2358.analysis.cfg.testing.CfgFirstPublications.*;
import static io.github.gustavo2358.analysis.cfg.testing.LinearPublications.*;
import static io.github.gustavo2358.analysis.cfg.testing.BranchPublications.*;
import static org.junit.jupiter.api.Assertions.*;

/** EVAL-CFG-030: admission through BuildCfg; manual facts, never expected output from the projector. */
class EvalCfg030Test {
    private static final UncertaintyId INVENTORY_GAP = new UncertaintyId(P, "inventory-gap");
    private enum Role { ENTRY, SEQUENCE, NORMAL_EXIT, HALT_EXIT }
    private enum Arm { ENTRY, RETURN, JUMP, TRUE, FALSE, HALT }
    private record Node(Role role, Id air) { }
    private record Edge(Node from, Node to, Arm arm, EntryId activation) { }
    private record Observation(Set<Node> nodes, Set<Edge> edges) { }
    private static Node en(EntryId e) { return new Node(Role.ENTRY, e); }
    private static Node seq(LabelId l) { return new Node(Role.SEQUENCE, l); }
    private static Node exit(EntryId e) { return new Node(Role.NORMAL_EXIT, e); }
    private static Node haltExit(OperationId op) { return new Node(Role.HALT_EXIT, op); }
    private static Edge edge(Node from, Node to, Arm arm, EntryId e) { return new Edge(from, to, arm, e); }
    private static Observation minimalExpected() {
        return new Observation(Set.of(en(E), seq(L), exit(E)),
                Set.of(edge(en(E), seq(L), Arm.ENTRY, E), edge(seq(L), exit(E), Arm.RETURN, E)));
    }
    private static Observation observe(CfgGraph graph) {
        Map<CfgNodeId, Node> nodes = new HashMap<>();
        for (CfgNode n : graph.nodes()) {
            Node seen = switch (n) {
                case CfgNode.EntryNode v -> en(v.source().id());
                case CfgNode.SequenceNode v -> seq(v.source().label());
                case CfgNode.NormalExit v -> exit(v.entryId());
                case CfgNode.HaltExit v -> haltExit(v.source().header().id());
            };
            assertNull(nodes.put(n.id(), seen));
        }
        Set<Edge> edges = new HashSet<>();
        for (CfgTransition t : graph.transitions()) {
            Arm arm = switch (t.kind()) {
                case ENTRY -> Arm.ENTRY;
                case RETURN -> Arm.RETURN;
                case JUMP -> Arm.JUMP;
                case BRANCH_TRUE -> Arm.TRUE;
                case BRANCH_FALSE -> Arm.FALSE;
                case HALT -> Arm.HALT;
            };
            assertTrue(edges.add(edge(nodes.get(t.from()), nodes.get(t.to()), arm, t.activationEntry())));
        }
        assertEquals(nodes.size(), new HashSet<>(nodes.values()).size());
        return new Observation(Set.copyOf(nodes.values()), Set.copyOf(edges));
    }
    private static Publication inventory(Publication p, Evidence.InventoryStatus global,
                                         Evidence.InventoryStatus local) {
        List<Unit> units = p.units().stream().map(u -> new Unit(u.id(), u.containingUnit(), u.objects(),
                u.visibleObjects(), u.entries(), u.sequences(), u.completionPorts(), u.body(), u.bodyUnavailable(),
                new Evidence.Coverage(local, u.coverage().scope(), u.coverage().items(), List.of(INVENTORY_GAP)),
                u.origin())).toList();
        List<Evidence.Uncertainty> gaps = new ArrayList<>(p.uncertainties());
        gaps.add(new Evidence.Uncertainty(INVENTORY_GAP, "INPUT_MISSING", List.of(Evidence.Dimension.CONTROL),
                new Scopes.PublicationScope(p.id()), "additional inventory is not enumerated", ORIGIN));
        return new Publication(p.id(), p.airVersion(), p.capabilities(), p.artifacts(), units, p.storage(),
                p.resources(), p.artifactRelations(), p.origins(),
                new Evidence.Coverage(global, p.coverage().scope(), p.coverage().items(), List.of(INVENTORY_GAP)),
                gaps, p.premises());
    }
    private static CfgBuildResult build(Publication p, BuildOptions options) {
        BuildCfg port = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());
        var result = port.build(p, options);
        assertSame(options, result.options());
        assertEquals(AirValidator.validate(p, options.validation()), result.preflight());
        return result;
    }
    private static CfgGraph built(Publication p, BuildOptions options) {
        var result = build(p, options);
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, result.preflight().status());
        assertEquals(CfgBuildResult.Status.CFG_BUILT, result.status(), () -> result.toString());
        assertTrue(result.projectionIssues().isEmpty());
        assertSame(p, result.graph().orElseThrow().publication());
        return result.graph().orElseThrow();
    }

    @Test
    void defaultProjectsBothPartialInventoriesWithExactReturnOracle() {
        Publication p = inventory(minimal(), PARTIAL, PARTIAL);
        assertEquals(minimalExpected(), observe(built(p, BuildOptions.defaults())));
    }
}
