package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Unit;
import io.github.gustavo2358.air.validation.AirValidator;
import io.github.gustavo2358.air.validation.ValidationIssue;
import io.github.gustavo2358.air.validation.ValidationResult;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.github.gustavo2358.analysis.cfg.testing.CfgFirstPublications.*;
import static org.junit.jupiter.api.Assertions.*;

/** EVAL-CFG-025. Manual structured oracle, authored before the projection exists. */
class EvalCfg025Test {
    private enum Role { ENTRY, SEQUENCE, NORMAL_EXIT }
    private record NodeObservation(Role role, PublicationId publication, UnitId unit, Id airIdentity) { }
    private record EdgeObservation(NodeObservation from, NodeObservation to,
                                   CfgTransition.Kind kind, EntryId activationEntry) { }
    private record Observation(Set<NodeObservation> nodes, Set<EdgeObservation> transitions) { }

    private static NodeObservation entryNode(EntryId id) {
        return new NodeObservation(Role.ENTRY, id.publication(), id.unit(), id);
    }
    private static NodeObservation sequenceNode(LabelId id) {
        return new NodeObservation(Role.SEQUENCE, id.publication(), id.unit(), id);
    }
    private static NodeObservation exitNode(EntryId id) {
        return new NodeObservation(Role.NORMAL_EXIT, id.publication(), id.unit(), id);
    }
    private static EdgeObservation entering(EntryId entry, LabelId label) {
        return new EdgeObservation(entryNode(entry), sequenceNode(label), CfgTransition.Kind.ENTRY, entry);
    }
    private static EdgeObservation returningTo(LabelId label, EntryId entry) {
        return new EdgeObservation(sequenceNode(label), exitNode(entry), CfgTransition.Kind.RETURN, entry);
    }
    private static Observation minimalExpected() {
        return new Observation(Set.of(entryNode(E), sequenceNode(L), exitNode(E)),
                Set.of(entering(E, L), returningTo(L, E)));
    }
    private static CfgBuildResult build(Publication publication) {
        return new CfgBuildCoordinator(SemanticInterpreterRegistry.empty())
                .build(publication, BuildOptions.defaults());
    }
    private static CfgGraph graph(Publication publication) {
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, AirValidator.validate(publication).status(),
                () -> AirValidator.validate(publication).toString());
        CfgBuildResult result = build(publication);
        assertEquals(CfgBuildResult.Status.CFG_BUILT, result.status());
        return result.graph().orElseThrow();
    }
    private static Observation observe(CfgGraph graph) {
        Map<CfgNodeId, NodeObservation> nodes = new HashMap<>();
        for (CfgNode node : graph.nodes()) {
            NodeObservation observation = switch (node) {
                case CfgNode.EntryNode entry -> entryNode(entry.source().id());
                case CfgNode.SequenceNode sequence -> sequenceNode(sequence.source().label());
                case CfgNode.NormalExit exit -> new NodeObservation(Role.NORMAL_EXIT,
                        exit.publicationId(), exit.unitId(), exit.entryId());
            };
            assertNull(nodes.put(node.id(), observation), "CFG IDs must be unique");
        }
        Set<EdgeObservation> transitions = new HashSet<>();
        for (CfgTransition transition : graph.transitions()) {
            assertTrue(nodes.containsKey(transition.from()));
            assertTrue(nodes.containsKey(transition.to()));
            assertTrue(transitions.add(new EdgeObservation(nodes.get(transition.from()),
                    nodes.get(transition.to()), transition.kind(), transition.activationEntry())),
                    "no duplicated semantic transition");
        }
        assertEquals(nodes.size(), new HashSet<>(nodes.values()).size(), "no duplicated correlation");
        return new Observation(Set.copyOf(nodes.values()), Set.copyOf(transitions));
    }

    @Test
    void entryReturnAndNormalExitMatchIndependentOracle() {
        Observation expected = minimalExpected();
        Publication publication = minimal();
        CfgBuildResult result = build(publication);
        assertEquals(CfgBuildResult.Status.CFG_BUILT, result.status());
        assertEquals(expected, observe(graph(publication)));
        assertEquals(P, result.publicationId());
        assertEquals(SemanticVersion.AIR_2_0_0, result.airVersion());
        assertEquals(AirValidator.validate(publication), result.preflight());
        assertTrue(result.projectionIssues().isEmpty());
    }

    @Test
    void missingInitialLabelIsInvalidIrAndIsNotRepaired() {
        Publication publication = publication(List.of(unit(U,
                List.of(entry(E, new LabelId(U, "missing"))), List.of(returning(L)))));
        CfgBuildResult result = build(publication);
        assertEquals(CfgBuildResult.Status.INVALID_IR, result.status());
        assertTrue(result.graph().isEmpty());
        assertEquals(AirValidator.validate(publication), result.preflight());
        assertTrue(result.preflight().issues().stream()
                .anyMatch(issue -> issue.kind() == ValidationIssue.Kind.INVALID_IR));
    }

    @Test
    void sequenceCannotBeConstructedWithoutTerminator() {
        assertThrows(NullPointerException.class, () -> new Sequence(L, List.of(), null, ORIGIN));
        // Any future transport accepting this malformed shape must report INVALID_IR before BuildCfg.
    }

    @Test
    void returnNeverFallsThroughToPhysicalNextSequence() {
        LabelId other = new LabelId(U, "other");
        Observation expected = new Observation(
                Set.of(entryNode(E), sequenceNode(L), sequenceNode(other), exitNode(E)),
                Set.of(entering(E, L), returningTo(L, E), returningTo(other, E)));
        CfgGraph graph = graph(publication(List.of(unit(U, List.of(entry(E, L)),
                List.of(returning(L), returning(other))))));
        assertEquals(expected, observe(graph));
        assertFalse(observe(graph).transitions().stream().anyMatch(edge ->
                edge.from().equals(sequenceNode(L)) && edge.to().equals(sequenceNode(other))));
    }

    @Test
    void physicalSequencePermutationPreservesCorrelatedControlAndCfgIds() {
        Sequence orphan = returning(new LabelId(U, "A-orphan"));
        Sequence initial = returning(L);
        CfgGraph first = graph(publication(List.of(unit(U, List.of(entry(E, L)), List.of(initial, orphan)))));
        CfgGraph permuted = graph(publication(List.of(unit(U, List.of(entry(E, L)), List.of(orphan, initial)))));
        assertEquals(observe(first), observe(permuted));
        assertEquals(first.nodes(), permuted.nodes());
        assertEquals(first.transitions(), permuted.transitions());
        assertTrue(observe(permuted).transitions().contains(entering(E, L)));
        assertFalse(observe(permuted).transitions().contains(entering(E, orphan.label())));
    }

    @Test
    void orphanRemainsInventoriedAndCorrelatedWithoutArtificialPredecessor() {
        Sequence orphan = returning(new LabelId(U, "orphan"));
        CfgGraph graph = graph(publication(List.of(unit(U, List.of(entry(E, L)),
                List.of(orphan, returning(L))))));
        CfgNode.SequenceNode node = graph.nodes().stream().filter(CfgNode.SequenceNode.class::isInstance)
                .map(CfgNode.SequenceNode.class::cast)
                .filter(sequence -> sequence.source().label().equals(orphan.label())).findFirst().orElseThrow();
        assertSame(orphan, node.source());
        assertTrue(graph.transitions().stream().noneMatch(edge -> edge.to().equals(node.id())));
        assertTrue(observe(graph).transitions().contains(returningTo(orphan.label(), E)));
    }

    @Test
    void sharedReturnPreservesBothActivationEntriesAndTheirExits() {
        EntryId second = new EntryId(U, "second");
        Observation expected = new Observation(
                Set.of(entryNode(E), entryNode(second), sequenceNode(L), exitNode(E), exitNode(second)),
                Set.of(entering(E, L), entering(second, L), returningTo(L, E), returningTo(L, second)));
        CfgGraph graph = graph(publication(List.of(unit(U,
                List.of(entry(E, L), entry(second, L)), List.of(returning(L))))));
        assertEquals(expected, observe(graph));
        assertEquals(2, graph.entries().size());
        assertEquals(2, graph.normalExits().size());
        for (EntryId activation : List.of(E, second)) {
            var exits = observe(graph).transitions().stream()
                    .filter(edge -> edge.kind() == CfgTransition.Kind.RETURN)
                    .filter(edge -> edge.activationEntry().equals(activation))
                    .map(EdgeObservation::to).toList();
            assertEquals(List.of(exitNode(activation)), exits);
        }
    }

    @Test
    void unitsWithSameLocalIdsRemainDistinctAndOrderIndependent() {
        UnitId otherUnit = new UnitId(P, "V");
        EntryId otherEntry = new EntryId(otherUnit, E.localId());
        LabelId otherLabel = new LabelId(otherUnit, L.localId());
        Unit first = unit(U, List.of(entry(E, L)), List.of(returning(L)));
        Unit second = unit(otherUnit, List.of(entry(otherEntry, otherLabel)), List.of(returning(otherLabel)));
        Observation expected = new Observation(
                Set.of(entryNode(E), sequenceNode(L), exitNode(E), entryNode(otherEntry),
                        sequenceNode(otherLabel), exitNode(otherEntry)),
                Set.of(entering(E, L), returningTo(L, E), entering(otherEntry, otherLabel),
                        returningTo(otherLabel, otherEntry)));
        CfgGraph graph = graph(publication(List.of(second, first)));
        assertEquals(expected, observe(graph));
        assertEquals(graph.nodes(), graph(publication(List.of(first, second))).nodes());
    }

    @Test
    void cfgIdsHaveTheirOwnDomainEvenWhenAirLocalIdsCollide() {
        EntryId same = new EntryId(U, L.localId());
        CfgGraph graph = graph(publication(List.of(unit(U, List.of(entry(same, L)), List.of(returning(L))))));
        assertEquals(3, graph.nodes().stream().map(CfgNode::id).distinct().count());
        for (CfgNode node : graph.nodes()) {
            assertEquals(CfgNodeId.class, node.id().getClass());
            assertEquals(P, node.id().publicationId());
            assertNotEquals(L, node.id());
            assertNotEquals(same, node.id());
        }
    }

    @Test
    void graphIsImmutableAndRetainsTheOriginalAirObjectsWithoutDeepCopy() {
        Publication publication = minimal();
        int originalHash = publication.hashCode();
        List<Unit> originalUnits = publication.units();
        Unit originalUnit = originalUnits.getFirst();
        List<Sequence> originalSequences = originalUnit.sequences();
        Sequence originalSequence = originalSequences.getFirst();
        Entries.Entry originalEntry = originalUnit.entries().getFirst();
        CfgGraph graph = graph(publication);
        assertSame(publication, graph.publication());
        assertSame(originalUnits, publication.units());
        assertSame(originalUnit, publication.units().getFirst());
        assertSame(originalSequences, originalUnit.sequences());
        assertSame(originalSequence, originalSequences.getFirst());
        assertEquals(originalHash, publication.hashCode());
        assertSame(originalEntry, graph.entries().getFirst().source());
        CfgNode.SequenceNode node = graph.nodes().stream().filter(CfgNode.SequenceNode.class::isInstance)
                .map(CfgNode.SequenceNode.class::cast).findFirst().orElseThrow();
        assertSame(originalSequence, node.source());
        assertSame(originalSequence.terminator(), node.source().terminator());
        assertSame(originalSequence.instructions(), node.source().instructions());
        assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.transitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.normalExits().clear());
        assertThrows(UnsupportedOperationException.class, () -> originalSequences.clear());
        ArrayList<CfgNode> nodes = new ArrayList<>(graph.nodes());
        ArrayList<CfgTransition> edges = new ArrayList<>(graph.transitions());
        CfgGraph copiedContainers = new CfgGraph(publication, nodes, edges);
        nodes.clear(); edges.clear();
        assertEquals(minimalExpected(), observe(copiedContainers));
    }

    @Test
    void haltIsOutsideTheSliceIncludingInAnOrphan() {
        LabelId other = new LabelId(U, "other");
        Operations.Halt halt = new Operations.Halt(header(new OperationId(U, "halt")), Operations.HaltKind.NORMAL);
        Publication publication = publication(List.of(unit(U, List.of(entry(E, L)),
                List.of(returning(L), new Sequence(other, List.of(), halt, ORIGIN)))));
        assertUnsupported(publication, CfgProjectionIssue.Code.UNSUPPORTED_TERMINATOR, halt.header().id());
    }

    @Test
    void jumpIsOutsideTheSliceEvenWithAValidExplicitTarget() {
        Operations.Jump jump = new Operations.Jump(header(new OperationId(U, "jump")), L);
        Publication publication = publication(List.of(unit(U, List.of(entry(E, L)),
                List.of(new Sequence(L, List.of(), jump, ORIGIN)))));
        assertUnsupported(publication, CfgProjectionIssue.Code.UNSUPPORTED_TERMINATOR, jump.header().id());
    }

    @Test
    void instructionsAreExplicitlyOutsideThisSlice() {
        Operations.Nop nop = new Operations.Nop(header(new OperationId(U, "nop")));
        Publication publication = publication(List.of(unit(U, List.of(entry(E, L)),
                List.of(new Sequence(L, List.of(nop), returning(L).terminator(), ORIGIN)))));
        assertUnsupported(publication, CfgProjectionIssue.Code.INSTRUCTIONS_OUTSIDE_SLICE, L);
    }

    @Test
    void unknownRequiredCapabilityCannotBecomeSuccessfulCfg() {
        Publication base = minimal();
        Capabilities.Capability unknown = new Capabilities.Capability("test.unknown", "1");
        Publication publication = new Publication(P, base.airVersion(),
                new Capabilities.Manifest(List.of(unknown), List.of()), base.artifacts(), base.units(),
                base.storage(), base.resources(), base.artifactRelations(), base.origins(),
                base.coverage(), base.uncertainties(), base.premises());
        CfgBuildResult result = build(publication);
        assertEquals(CfgBuildResult.Status.UNSUPPORTED_CAPABILITY, result.status());
        assertEquals(List.of(unknown), result.unsupportedCapabilities());
        assertTrue(result.graph().isEmpty());
    }

    @Test
    void returnOperandsAndOriginsRemainSharedAndOrdered() {
        Operations.Header header = header(new OperationId(U, "values"));
        List<Expression> values = List.of(
                new Expressions.Literal(new Operand.Header(new OperandId(new OperationOwner(header.id()), "v0"),
                        Operand.Role.VALUE_READ, ORIGIN), new Values.TextValue("first")),
                new Expressions.Literal(new Operand.Header(new OperandId(new OperationOwner(header.id()), "v1"),
                        Operand.Role.VALUE_READ, ORIGIN), new Values.TextValue("second")));
        var signature = new Interactions.Signature(entry(E, L).signature().parameters(),
                new Interactions.ResultInventory(List.of(
                        new Interactions.ResultSlot(BigInteger.ZERO, new Types.Known(Types.Builtin.TEXT), ORIGIN),
                        new Interactions.ResultSlot(BigInteger.ONE, new Types.Known(Types.Builtin.TEXT), ORIGIN)),
                        Interactions.NoRemainder.INSTANCE), ORIGIN);
        Operations.Return terminator = new Operations.Return(header, values);
        CfgGraph graph = graph(publication(List.of(unit(U, List.of(new Entries.Entry(E, Optional.of(L),
                signature, entry(E, L).state(), ORIGIN)), List.of(new Sequence(L, List.of(), terminator, ORIGIN))))));
        CfgNode.SequenceNode sequence = graph.nodes().stream().filter(CfgNode.SequenceNode.class::isInstance)
                .map(CfgNode.SequenceNode.class::cast).findFirst().orElseThrow();
        assertSame(terminator, sequence.source().terminator());
        assertSame(terminator.values(), ((Operations.Return) sequence.source().terminator()).values());
        assertEquals(values, ((Operations.Return) sequence.source().terminator()).values());
        assertEquals(minimalExpected(), observe(graph));
    }

    @Test
    void entryCollectionOrderCannotChooseTheActivationOrItsInitialLabel() {
        EntryId second = new EntryId(U, "A-second");
        LabelId other = new LabelId(U, "other");
        var entries = List.of(entry(E, L), entry(second, other));
        var sequences = List.of(returning(L), returning(other));
        CfgGraph first = graph(publication(List.of(unit(U, entries, sequences))));
        CfgGraph reversed = graph(publication(List.of(unit(U, entries.reversed(), sequences))));
        assertEquals(first.nodes(), reversed.nodes());
        assertEquals(first.transitions(), reversed.transitions());
        assertEquals(Set.of(entering(E, L), entering(second, other)),
                observe(first).transitions().stream().filter(edge -> edge.kind() == CfgTransition.Kind.ENTRY)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void unavailableInventoryIsNotACompleteEmptyGraph() {
        UncertaintyId gap = new UncertaintyId(P, "inventory-gap");
        Scopes.PublicationScope scope = new Scopes.PublicationScope(P);
        Publication base = publication(List.of());
        for (Evidence.InventoryStatus inventory : List.of(Evidence.InventoryStatus.PARTIAL,
                Evidence.InventoryStatus.UNAVAILABLE)) {
            Publication publication = new Publication(P, base.airVersion(), base.capabilities(), base.artifacts(),
                    base.units(), base.storage(), base.resources(), base.artifactRelations(), base.origins(),
                    new Evidence.Coverage(inventory, scope, List.of(), List.of(gap)),
                    List.of(new Evidence.Uncertainty(gap, "INPUT_MISSING", List.of(Evidence.Dimension.CONTROL),
                            scope, "inventory not available", ORIGIN)), List.of());
            assertUnsupported(publication, CfgProjectionIssue.Code.INCOMPLETE_INVENTORY, P);
        }
    }

    @Test
    void unavailableUnitBodyIsNotInvented() {
        UncertaintyId gap = new UncertaintyId(P, "body-gap");
        Unit unit = new Unit(U, Optional.empty(), List.of(), List.of(),
                List.of(new Entries.Entry(E, Optional.empty(), entry(E, L).signature(),
                        entry(E, L).state(), ORIGIN)), List.of(), List.of(), Unit.BodyAvailability.UNAVAILABLE,
                Optional.of(gap), coverage(new Scopes.UnitScope(U)), ORIGIN);
        Publication base = publication(List.of(unit));
        Publication publication = new Publication(P, base.airVersion(), base.capabilities(), base.artifacts(),
                base.units(), base.storage(), base.resources(), base.artifactRelations(), base.origins(),
                base.coverage(), List.of(new Evidence.Uncertainty(gap, "INPUT_MISSING",
                        List.of(Evidence.Dimension.CONTROL), new Scopes.UnitScope(U), "body unavailable", ORIGIN)),
                List.of());
        assertUnsupported(publication, CfgProjectionIssue.Code.BODY_UNAVAILABLE, U);
    }

    @Test
    void registeredCapabilityIdentityAloneDoesNotImplementItsSemantics() {
        Publication base = minimal();
        var capability = Capabilities.LOCAL_CONTROL;
        Publication publication = new Publication(P, base.airVersion(),
                new Capabilities.Manifest(List.of(capability), List.of(capability)), base.artifacts(), base.units(),
                base.storage(), base.resources(), base.artifactRelations(), base.origins(),
                base.coverage(), base.uncertainties(), base.premises());
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, AirValidator.validate(publication).status());
        CfgBuildResult result = new CfgBuildCoordinator(SemanticInterpreterRegistry.of(List.of(() -> capability)))
                .build(publication, BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.UNSUPPORTED_INPUT, result.status());
        assertTrue(result.graph().isEmpty());
        assertEquals(List.of(new CfgProjectionIssue(CfgProjectionIssue.Code.EXTENSION_SEMANTICS_OUTSIDE_SLICE, P)),
                result.projectionIssues());
    }

    private static void assertUnsupported(Publication publication, CfgProjectionIssue.Code code, Id subject) {
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, AirValidator.validate(publication).status());
        CfgBuildResult result = build(publication);
        assertEquals(CfgBuildResult.Status.UNSUPPORTED_INPUT, result.status());
        assertTrue(result.graph().isEmpty());
        assertEquals(List.of(new CfgProjectionIssue(code, subject)), result.projectionIssues());
    }
}
