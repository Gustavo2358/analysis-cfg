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
                case OPAQUE_JUMP, OPAQUE_RETURN, OPAQUE_UNKNOWN -> throw new AssertionError("Opaque belongs to WORK-CFG-038");
                case INVOKE_NORMAL -> throw new AssertionError("Invoke belongs to CP6 W1D");
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
    private static BuildOptions options(ProjectionPolicy policy) {
        return new BuildOptions(ValidationOptions.defaults(), policy);
    }
    private static CfgBuildResult rejected(Publication p, ProjectionPolicy policy, CfgBuildResult.Status status) {
        var result = build(p, options(policy));
        assertEquals(status, result.status());
        assertTrue(result.graph().isEmpty());
        return result;
    }
    private static void inventoryIssue(Publication p, ProjectionPolicy policy, Id... subjects) {
        var result = rejected(p, policy, CfgBuildResult.Status.UNSUPPORTED_INPUT);
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, result.preflight().status());
        assertEquals(java.util.Arrays.stream(subjects).map(id -> new CfgProjectionIssue(
                CfgProjectionIssue.Code.INCOMPLETE_INVENTORY, id)).toList(), result.projectionIssues());
    }

    @Test
    void defaultProjectsBothPartialInventoriesWithExactReturnOracle() {
        Publication p = inventory(minimal(), PARTIAL, PARTIAL);
        assertEquals(minimalExpected(), observe(built(p, BuildOptions.defaults())));
    }

    @Test
    void allPublicDefaultsSelectKnownSubsetAndNullPolicyIsRejected() {
        assertEquals(ProjectionPolicy.KNOWN_SUBSET, BuildOptions.defaults().projectionPolicy());
        var validation = new ValidationOptions(128, 1000, 100);
        var legacy = new BuildOptions(validation);
        assertSame(validation, legacy.validation());
        assertEquals(ProjectionPolicy.KNOWN_SUBSET, legacy.projectionPolicy());
        assertEquals(minimalExpected(), observe(built(inventory(minimal(), PARTIAL, PARTIAL), legacy)));
        assertTrue(CoreCfgProjection.unsupported(inventory(minimal(), PARTIAL, PARTIAL)).isEmpty());
        assertThrows(NullPointerException.class, () -> new BuildOptions(validation, null));
        assertThrows(NullPointerException.class, () -> new BuildOptions(null, ProjectionPolicy.KNOWN_SUBSET));
        assertThrows(NullPointerException.class, () -> CoreCfgProjection.unsupported(minimal(), null));
    }

    @Test
    void knownSubsetCompleteBuildsManualReturnGraph() {
        assertEquals(minimalExpected(), observe(built(minimal(), options(ProjectionPolicy.KNOWN_SUBSET))));
    }

    @Test
    void strictCompleteBuildsManualReturnGraph() {
        assertEquals(minimalExpected(), observe(built(minimal(), options(ProjectionPolicy.STRICT))));
    }

    @Test
    void knownSubsetAllowsPartialAtEitherScopeIndependently() {
        for (Publication p : List.of(inventory(minimal(), PARTIAL, COMPLETE),
                inventory(minimal(), COMPLETE, PARTIAL))) {
            assertEquals(minimalExpected(), observe(built(p, options(ProjectionPolicy.KNOWN_SUBSET))));
        }
    }

    @Test
    void strictRejectsPartialPublicationWithTypedSubject() {
        inventoryIssue(inventory(minimal(), PARTIAL, COMPLETE), ProjectionPolicy.STRICT, P);
    }

    @Test
    void strictRejectsPartialUnitWithTypedSubject() {
        inventoryIssue(inventory(minimal(), COMPLETE, PARTIAL), ProjectionPolicy.STRICT, U);
    }

    @Test
    void strictReportsBothIncompleteScopesInStableOrder() {
        inventoryIssue(inventory(minimal(), PARTIAL, PARTIAL), ProjectionPolicy.STRICT, P, U);
    }

    @Test
    void unavailableInventoryIsRejectedAtEitherScopeUnderBothPolicies() {
        for (var policy : ProjectionPolicy.values()) {
            inventoryIssue(inventory(minimal(), UNAVAILABLE, COMPLETE), policy, P);
            inventoryIssue(inventory(minimal(), COMPLETE, UNAVAILABLE), policy, U);
            inventoryIssue(inventory(minimal(), UNAVAILABLE, UNAVAILABLE), policy, P, U);
        }
    }

    @Test
    void emptyPartialInventoryRemainsPartialWithoutInventedNodes() {
        Publication p = inventory(publication(List.of()), PARTIAL, COMPLETE);
        CfgGraph graph = built(p, BuildOptions.defaults());
        assertEquals(new Observation(Set.of(), Set.of()), observe(graph));
        assertEquals(PARTIAL, graph.publication().coverage().inventory());
        inventoryIssue(p, ProjectionPolicy.STRICT, P);
        inventoryIssue(inventory(publication(List.of()), UNAVAILABLE, COMPLETE), ProjectionPolicy.KNOWN_SUBSET, P);
    }

    @Test
    void unsupportedOrphanTerminatorIsNeverSilentlyOmitted() {
        var op = new OperationId(U, "unsupported");
        Publication p = inventory(publication(List.of(unit(U, List.of(entry(E, L)),
                List.of(returning(L), new Sequence(TAIL, List.of(),
                        new Operations.Raise(header(op), "failure", List.of()), ORIGIN))))), PARTIAL, PARTIAL);
        for (var policy : ProjectionPolicy.values()) {
            var result = rejected(p, policy, CfgBuildResult.Status.UNSUPPORTED_INPUT);
            assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, result.preflight().status());
            assertTrue(result.projectionIssues().contains(new CfgProjectionIssue(
                    CfgProjectionIssue.Code.UNSUPPORTED_TERMINATOR, op)));
        }
    }

    @Test
    void unsupportedCapabilityStillBlocksBeforeProjectionPolicy() {
        var capability = new Capabilities.Capability("test.unsupported", "1");
        Publication p = requiring(inventory(minimal(), PARTIAL, PARTIAL), List.of(capability));
        for (var policy : ProjectionPolicy.values()) {
            var result = rejected(p, policy, CfgBuildResult.Status.UNSUPPORTED_CAPABILITY);
            assertEquals(List.of(capability), result.unsupportedCapabilities());
            assertTrue(result.projectionIssues().isEmpty());
        }
    }

    @Test
    void registeredKnownCapabilityStillRequiresImplementedSemantics() {
        var capability = Capabilities.LOCAL_CONTROL;
        Publication p = requiring(inventory(minimal(), PARTIAL, PARTIAL), List.of(capability));
        for (var policy : ProjectionPolicy.values()) {
            var result = new CfgBuildCoordinator(SemanticInterpreterRegistry.of(List.of(() -> capability)))
                    .build(p, options(policy));
            assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, result.preflight().status());
            assertEquals(CfgBuildResult.Status.UNSUPPORTED_INPUT, result.status());
            assertTrue(result.graph().isEmpty());
            assertTrue(result.projectionIssues().contains(new CfgProjectionIssue(
                    CfgProjectionIssue.Code.EXTENSION_SEMANTICS_OUTSIDE_SLICE, P)));
        }
    }

    @Test
    void upstreamUnsupportedCapabilityCannotBeOverriddenByInventoryPolicy() {
        var capability = new Capabilities.Capability("test.unvalidated", "1");
        Publication p = requiring(inventory(minimal(), PARTIAL, PARTIAL), List.of(capability));
        for (var policy : ProjectionPolicy.values()) {
            var result = new CfgBuildCoordinator(SemanticInterpreterRegistry.of(List.of(() -> capability)))
                    .build(p, options(policy));
            assertEquals(CfgBuildResult.Status.UNSUPPORTED_CAPABILITY, result.status());
            assertTrue(result.preflight().hasIssues(io.github.gustavo2358.air.validation.ValidationIssue.Kind.UNSUPPORTED_CAPABILITY));
            assertTrue(result.graph().isEmpty());
            assertEquals(AirValidator.validate(p), result.preflight());
            assertTrue(result.projectionIssues().isEmpty());
        }
    }

    @Test
    void invalidReferencesAndUnexplainedPartialRemainInvalidAir() {
        Publication missingTarget = inventory(publication(List.of(unit(U, List.of(entry(E, L)),
                List.of(jump(L, TAIL, List.of()))))), PARTIAL, PARTIAL);
        Publication p = minimal();
        Publication missingReason = new Publication(p.id(), p.airVersion(), p.capabilities(), p.artifacts(),
                p.units(), p.storage(), p.resources(), p.artifactRelations(), p.origins(),
                new Evidence.Coverage(PARTIAL, p.coverage().scope(), List.of(), List.of()),
                p.uncertainties(), p.premises());
        for (var policy : ProjectionPolicy.values()) {
            for (Publication invalid : List.of(missingTarget, missingReason)) {
                var result = rejected(invalid, policy, CfgBuildResult.Status.INVALID_IR);
                assertTrue(result.projectionIssues().isEmpty());
                assertEquals(ValidationResult.Status.INVALID_IR, result.preflight().status());
            }
        }
    }

    @Test
    void unavailableBodyStillBlocksKnownControlProjection() {
        var unavailableEntry = new Entries.Entry(E, Optional.empty(), entry(E, L).signature(),
                entry(E, L).state(), ORIGIN);
        var u = new Unit(U, Optional.empty(), List.of(), List.of(), List.of(unavailableEntry), List.of(),
                List.of(), Unit.BodyAvailability.UNAVAILABLE, Optional.of(INVENTORY_GAP),
                coverage(new Scopes.UnitScope(U)), ORIGIN);
        Publication p = inventory(publication(List.of(u)), PARTIAL, PARTIAL);
        for (var policy : ProjectionPolicy.values()) {
            var result = rejected(p, policy, CfgBuildResult.Status.UNSUPPORTED_INPUT);
            assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, result.preflight().status());
            assertTrue(result.projectionIssues().contains(new CfgProjectionIssue(
                    CfgProjectionIssue.Code.BODY_UNAVAILABLE, U)));
        }
    }

    @Test
    void resourceLimitsStillBlockBeforeProjection() {
        Publication p = inventory(minimal(), PARTIAL, PARTIAL);
        for (var policy : ProjectionPolicy.values()) {
            var result = build(p, new BuildOptions(new ValidationOptions(128, 1, 100), policy));
            assertEquals(CfgBuildResult.Status.RESOURCE_LIMIT, result.status());
            assertEquals(ValidationResult.Status.INCOMPLETE_VALIDATION,result.preflight().status());
            assertTrue(result.preflight().hasIssues(io.github.gustavo2358.air.validation.ValidationIssue.Kind.RESOURCE_LIMIT));
            assertFalse(result.preflight().diagnostics().traversalCompleted());
            assertTrue(result.graph().isEmpty());
            assertTrue(result.projectionIssues().isEmpty());
        }
    }

    @Test
    void partialCoverageItemsPremisesAndDimensionalEvidenceRemainOriginal() {
        Publication base = withData(List.of(returning(L)), false);
        var original = base.units().getFirst().sequences().getFirst();
        var h = original.terminator().header();
        var unavailable = new Evidence.Claim(new Scopes.UnitScope(U), Evidence.PrecisionStatus.UNAVAILABLE, List.of(GAP));
        var precision = new Evidence.Precision(h.precision().control(), unavailable, unavailable, unavailable, unavailable);
        var sequence = new Sequence(L, List.of(), new Operations.Return(new Operations.Header(h.id(), h.origin(),
                h.coverage(), precision, List.of(GAP)), List.of()), original.origin());
        base = replaceUnits(base, List.of(replaceSequences(base.units().getFirst(), List.of(sequence))));
        var premise = new Proofs.Premise(new PremiseId(P, "domain"), "test-contract", "object and cell share text domain",
                ORIGIN, new Proofs.SameDomain(new Proofs.ObjectDomain(OBJECT), new Proofs.CellDomain(CELL),
                new Proofs.UnitDomain(U)));
        base = new Publication(P, base.airVersion(), base.capabilities(), base.artifacts(), base.units(), base.storage(),
                base.resources(), base.artifactRelations(), base.origins(), base.coverage(),
                List.of(new Evidence.Uncertainty(GAP, "INPUT_MISSING", List.of(Evidence.Dimension.STORAGE,
                        Evidence.Dimension.EFFECTS, Evidence.Dimension.VALUES, Evidence.Dimension.DEPENDENCIES),
                        new Scopes.UnitScope(U), "non-control precision is not established", ORIGIN)), List.of(premise));
        Publication partial = inventory(base, PARTIAL, PARTIAL);
        var items = List.of(new Evidence.CoverageItem("known", ORIGIN, Evidence.CoverageStatus.MODELED,
                        List.of(L, h.id()), List.of(), Optional.empty()),
                new Evidence.CoverageItem("unpublished", ORIGIN, Evidence.CoverageStatus.INPUT_MISSING,
                        List.of(), List.of(INVENTORY_GAP), Optional.empty()));
        Publication p = new Publication(P, partial.airVersion(), partial.capabilities(), partial.artifacts(), partial.units(),
                partial.storage(), partial.resources(), partial.artifactRelations(), partial.origins(),
                new Evidence.Coverage(PARTIAL, partial.coverage().scope(), items, partial.coverage().uncertainties()),
                partial.uncertainties(), partial.premises());
        int hash = p.hashCode();
        var graph = built(p, BuildOptions.defaults());
        assertEquals(minimalExpected(), observe(graph));
        assertSame(p.coverage(), graph.publication().coverage());
        assertSame(p.units().getFirst().coverage(), graph.publication().units().getFirst().coverage());
        assertEquals(PARTIAL, graph.publication().units().getFirst().coverage().inventory());
        assertSame(p.uncertainties(), graph.publication().uncertainties());
        assertSame(p.origins(), graph.publication().origins());
        assertSame(p.premises(), graph.publication().premises());
        assertSame(premise, graph.publication().premises().getFirst());
        var seen = graph.nodes().stream().filter(CfgNode.SequenceNode.class::isInstance)
                .map(CfgNode.SequenceNode.class::cast).findFirst().orElseThrow().source();
        assertSame(sequence, seen);
        assertSame(precision, seen.terminator().header().precision());
        assertEquals(hash, p.hashCode());
    }

    @Test
    void gapCodesReasonsAndOriginTextCannotDecideAdmissionOrControl() {
        Publication first = inventory(minimal(), PARTIAL, PARTIAL);
        Publication renamed = new Publication(P, first.airVersion(), first.capabilities(), first.artifacts(), first.units(),
                first.storage(), first.resources(), first.artifactRelations(),
                List.of(new Origins.Unavailable(ORIGIN, "/unopened/arbitrary/source")), first.coverage(),
                List.of(new Evidence.Uncertainty(INVENTORY_GAP, "another.vendor:any-gap", List.of(Evidence.Dimension.CONTROL),
                        first.uncertainties().getFirst().scope(), "arbitrary explanation", ORIGIN)), first.premises());
        assertEquals(minimalExpected(), observe(built(first, BuildOptions.defaults())));
        assertEquals(minimalExpected(), observe(built(renamed, BuildOptions.defaults())));
        inventoryIssue(first, ProjectionPolicy.STRICT, P, U);
        inventoryIssue(renamed, ProjectionPolicy.STRICT, P, U);
    }

    @Test
    void partialMixedControlKeepsAllArmsContextsOrphansAndOrdering() {
        EntryId second = new EntryId(U, "second");
        LabelId stopped = new LabelId(U, "stopped");
        var sequences = List.of(branch(L, bool(L, true), JOIN, JOIN), returning(JOIN),
                jump(TAIL, TAIL, List.of()), halt(stopped, Operations.HaltKind.NORMAL));
        var entries = List.of(entry(E, L), entry(second, JOIN));
        Publication p = inventory(publication(List.of(unit(U, entries, sequences))), PARTIAL, PARTIAL);
        Node stop = haltExit(new OperationId(U, "halt-stopped"));
        var expectedEdges = new HashSet<>(Set.of(edge(en(E), seq(L), Arm.ENTRY, E),
                edge(en(second), seq(JOIN), Arm.ENTRY, second)));
        for (var e : List.of(E, second)) {
            expectedEdges.addAll(Set.of(edge(seq(L), seq(JOIN), Arm.TRUE, e), edge(seq(L), seq(JOIN), Arm.FALSE, e),
                    edge(seq(JOIN), exit(e), Arm.RETURN, e), edge(seq(TAIL), seq(TAIL), Arm.JUMP, e),
                    edge(seq(stopped), stop, Arm.HALT, e)));
        }
        var expected = new Observation(Set.of(en(E), en(second), seq(L), seq(JOIN), seq(TAIL), seq(stopped),
                exit(E), exit(second), stop), Set.copyOf(expectedEdges));
        var graph = built(p, BuildOptions.defaults());
        assertEquals(expected, observe(graph));
        Publication reversed = inventory(publication(List.of(unit(U, entries.reversed(), sequences.reversed()))), PARTIAL, PARTIAL);
        var reordered = built(reversed, BuildOptions.defaults());
        assertEquals(expected, observe(reordered));
        assertEquals(graph.nodes(), reordered.nodes());
        assertEquals(graph.transitions(), reordered.transitions());
    }
}
