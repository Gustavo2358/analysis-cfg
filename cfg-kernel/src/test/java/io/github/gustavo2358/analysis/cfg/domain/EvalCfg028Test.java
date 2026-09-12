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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.github.gustavo2358.analysis.cfg.testing.CfgFirstPublications.*;
import static io.github.gustavo2358.analysis.cfg.testing.LinearPublications.*;
import static org.junit.jupiter.api.Assertions.*;

/** Local slice oracle. Expected records/enums are test-owned and manually authored before production. */
class EvalCfg028Test {
    private enum Role { ENTRY, SEQUENCE, NORMAL_EXIT, HALT_EXIT }
    private enum EdgeKind { ENTRY, JUMP, RETURN, HALT }
    private enum Termination { NORMAL, ABNORMAL }
    private record Node(Role role, Id correlation) { }
    private record Edge(Node from, Node to, EdgeKind kind, EntryId activation) { }
    private record Observation(Set<Node> nodes, Set<Edge> edges,
                               Map<LabelId, List<OperationId>> instructions,
                               Map<OperationId, Termination> halts) { }

    private static Node en(EntryId id) { return new Node(Role.ENTRY, id); }
    private static Node seq(LabelId id) { return new Node(Role.SEQUENCE, id); }
    private static Node normal(EntryId id) { return new Node(Role.NORMAL_EXIT, id); }
    private static Node stopped(OperationId id) { return new Node(Role.HALT_EXIT, id); }
    private static Edge enter(EntryId id, LabelId label) { return new Edge(en(id), seq(label), EdgeKind.ENTRY, id); }
    private static Edge jumpEdge(LabelId from, LabelId to, EntryId e) {
        return new Edge(seq(from), seq(to), EdgeKind.JUMP, e);
    }
    private static Edge ret(LabelId from, EntryId e) { return new Edge(seq(from), normal(e), EdgeKind.RETURN, e); }
    private static Edge stop(LabelId from, OperationId op, EntryId e) {
        return new Edge(seq(from), stopped(op), EdgeKind.HALT, e);
    }
    private static CfgBuildResult build(Publication p) {
        return new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p, BuildOptions.defaults());
    }
    private static CfgGraph graph(Publication p) {
        var validation = AirValidator.validate(p);
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, validation.status(), () -> validation.toString());
        var result = build(p);
        assertEquals(CfgBuildResult.Status.CFG_BUILT, result.status(), () -> result.toString());
        assertEquals(validation, result.preflight());
        return result.graph().orElseThrow();
    }
    private static Observation observe(CfgGraph graph) {
        Map<CfgNodeId, Node> nodes = new HashMap<>();
        Map<LabelId, List<OperationId>> instructions = new HashMap<>();
        Map<OperationId, Termination> halts = new HashMap<>();
        for (CfgNode node : graph.nodes()) {
            Node seen = switch (node) {
                case CfgNode.EntryNode n -> en(n.source().id());
                case CfgNode.SequenceNode n -> {
                    assertNull(instructions.put(n.source().label(), n.source().instructions().stream()
                            .map(i -> i.header().id()).toList()));
                    yield seq(n.source().label());
                }
                case CfgNode.NormalExit n -> normal(n.entryId());
                case CfgNode.HaltExit n -> {
                    assertNull(halts.put(n.source().header().id(), switch (n.source().haltKind()) {
                        case NORMAL -> Termination.NORMAL;
                        case ABNORMAL -> Termination.ABNORMAL;
                    }));
                    yield stopped(n.source().header().id());
                }
            };
            assertNull(nodes.put(node.id(), seen));
        }
        assertEquals(nodes.size(), new HashSet<>(nodes.values()).size(), "duplicate semantic nodes");
        Set<Edge> edges = new HashSet<>();
        for (CfgTransition t : graph.transitions()) {
            assertTrue(nodes.containsKey(t.from()));
            assertTrue(nodes.containsKey(t.to()));
            EdgeKind kind = switch (t.kind()) {
                case INVOKE_NORMAL -> throw new AssertionError("Invoke belongs to CP6 W1D");
                case ENTRY -> EdgeKind.ENTRY;
                case JUMP -> EdgeKind.JUMP;
                case RETURN -> EdgeKind.RETURN;
                case HALT -> EdgeKind.HALT;
                case BRANCH_TRUE, BRANCH_FALSE -> throw new AssertionError("Branch belongs to EVAL-CFG-029");
            };
            assertTrue(edges.add(new Edge(nodes.get(t.from()), nodes.get(t.to()), kind, t.activationEntry())));
        }
        return new Observation(Set.copyOf(nodes.values()), Set.copyOf(edges), Map.copyOf(instructions), Map.copyOf(halts));
    }
    private static CfgNode.SequenceNode sequence(CfgGraph graph, LabelId label) {
        return graph.nodes().stream().filter(CfgNode.SequenceNode.class::isInstance)
                .map(CfgNode.SequenceNode.class::cast).filter(n -> n.source().label().equals(label))
                .findFirst().orElseThrow();
    }
    private static Publication withSequences(List<Sequence> sequences) {
        return publication(List.of(unit(U, List.of(entry(E, L)), sequences)));
    }
    private static Observation m1Expected() {
        return new Observation(Set.of(en(E), seq(L), seq(TAIL), normal(E)),
                Set.of(enter(E, L), jumpEdge(L, TAIL, E), ret(TAIL, E)),
                Map.of(L, List.of(new OperationId(U, "z-first"), new OperationId(U, "a-second")), TAIL, List.of()),
                Map.of());
    }

    @Test
    void m1MatchesManualControlAndOrderedOccurrences() {
        Observation expected = m1Expected();
        Operations.Assign first = assign("z-first", "A");
        Operations.Assign second = assign("a-second", "B");
        Sequence initial = jump(L, TAIL, List.of(first, second));
        Publication input = withData(List.of(initial, returning(TAIL)), false);
        CfgGraph graph = graph(input);
        assertEquals(expected, observe(graph));
        assertSame(initial, sequence(graph, L).source());
        assertSame(first, sequence(graph, L).source().instructions().get(0));
        assertSame(second, sequence(graph, L).source().instructions().get(1));
        assertEquals(new Values.TextValue("A"), ((Expressions.Literal) first.value()).value());
        assertEquals(new Values.TextValue("B"), ((Expressions.Literal) second.value()).value());
        assertEquals(OBJECT, ((Places.ObjectPlace) first.destination()).object());
        assertEquals(OBJECT, ((Places.ObjectPlace) second.destination()).object());
    }

    @Test
    void allInstructionKindsRetainOperandsHeadersOriginsPrecisionAndGaps() {
        List<Instruction> instructions = allInstructions();
        Sequence initial = jump(L, TAIL, instructions);
        Publication input = withData(List.of(initial, returning(TAIL)), true);
        int inputHash = input.hashCode();
        CfgGraph graph = graph(input);
        List<Instruction> actual = sequence(graph, L).source().instructions();
        assertEquals(List.of(new OperationId(U, "z-first"), new OperationId(U, "a-second"),
                new OperationId(U, "havoc-must"), new OperationId(U, "havoc-may"),
                new OperationId(U, "nop"), new OperationId(U, "copy")),
                actual.stream().map(i -> i.header().id()).toList());
        for (int i = 0; i < instructions.size(); i++) {
            assertSame(instructions.get(i), actual.get(i), "entire occurrence, including operands");
            assertSame(instructions.get(i).header(), actual.get(i).header());
        }
        assertSame(initial.instructions(), actual);
        assertSame(input, graph.publication());
        assertSame(input.uncertainties(), graph.publication().uncertainties());
        assertSame(input.origins(), graph.publication().origins());
        assertEquals(List.of(GAP), actual.get(2).header().uncertainties());
        assertEquals(Evidence.PrecisionStatus.OPEN, actual.get(2).header().precision().values().status());
        assertEquals(inputHash, input.hashCode());
        assertEquals(Set.of(enter(E, L), jumpEdge(L, TAIL, E), ret(TAIL, E)), observe(graph).edges());
        assertThrows(UnsupportedOperationException.class, () -> actual.clear());
    }

    @Test
    void jumpUsesExplicitForwardTargetInsteadOfPhysicalOrSortedNeighbor() {
        LabelId decoy = new LabelId(U, "M-decoy");
        CfgGraph graph = graph(withSequences(List.of(jump(L, TAIL, List.of()), returning(decoy), returning(TAIL))));
        assertEquals(new Observation(Set.of(en(E), seq(L), seq(decoy), seq(TAIL), normal(E)),
                Set.of(enter(E, L), jumpEdge(L, TAIL, E), ret(decoy, E), ret(TAIL, E)),
                Map.of(L, List.of(), decoy, List.of(), TAIL, List.of()), Map.of()), observe(graph));
    }

    @Test
    void backwardTargetAndPhysicalPermutationPreserveControlAndCfgIds() {
        Sequence initial = jump(L, TAIL, List.of(assign("z-first", "A"), assign("a-second", "B")));
        var forward = graph(withData(List.of(initial, returning(TAIL)), false));
        var backward = graph(withData(List.of(returning(TAIL), initial), false));
        assertEquals(m1Expected(), observe(backward));
        assertEquals(observe(forward), observe(backward));
        assertEquals(forward.nodes(), backward.nodes());
        assertEquals(forward.transitions(), backward.transitions());
    }

    @Test
    void alphaRenamePreservesControlUnderExplicitDomainCorrelation() {
        OperationId originalOp = new OperationId(U, "original-op");
        var original = graph(withSequences(List.of(jump(L, TAIL,
                List.of(new Operations.Nop(header(originalOp)))), returning(TAIL))));
        UnitId v = new UnitId(P, "renamed-unit");
        EntryId f = new EntryId(v, "renamed-entry");
        LabelId a = new LabelId(v, "z-source");
        LabelId b = new LabelId(v, "a-target");
        OperationId renamedOp = new OperationId(v, "renamed-op");
        var renamed = graph(publication(List.of(unit(v, List.of(entry(f, a)),
                List.of(returning(b), jump(a, b, List.of(new Operations.Nop(header(renamedOp)))))))));
        Map<Id, Id> correlation = Map.of(f, E, a, L, b, TAIL,
                new OperationId(v, "jump-z-source"), new OperationId(U, "jump-L"),
                new OperationId(v, "return-a-target"), new OperationId(U, "return-tail"), renamedOp, originalOp);
        Set<Edge> mapped = new HashSet<>();
        for (Edge edge : observe(renamed).edges()) {
            mapped.add(new Edge(new Node(edge.from().role(), correlation.get(edge.from().correlation())),
                    new Node(edge.to().role(), correlation.get(edge.to().correlation())), edge.kind(),
                    (EntryId) correlation.get(edge.activation())));
        }
        assertEquals(observe(original).edges(), mapped);
        assertEquals(List.of(originalOp), sequence(renamed, a).source().instructions().stream()
                .map(i -> correlation.get(i.header().id())).toList());
        assertEquals(Set.of(en(E), seq(L), seq(TAIL), normal(E)), observe(renamed).nodes().stream()
                .map(n -> new Node(n.role(), correlation.get(n.correlation())))
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(correlation.get(sequence(renamed, a).source().terminator().header().id()),
                sequence(original, L).source().terminator().header().id());
        assertEquals(correlation.get(sequence(renamed, b).source().terminator().header().id()),
                sequence(original, TAIL).source().terminator().header().id());
    }

    @Test
    void explicitSplitPreservesOriginalOperationOrderAndCorrelatedPoints() {
        var first = assign("z-first", "A");
        var second = assign("a-second", "B");
        var returned = returning(L).terminator();
        CfgGraph whole = graph(withData(List.of(new Sequence(L, List.of(first, second), returned, ORIGIN)), false));
        CfgGraph split = graph(withData(List.of(new Sequence(TAIL, List.of(second), returned, ORIGIN),
                jump(L, TAIL, List.of(first))), false));
        assertEquals(Set.of(enter(E, L), ret(L, E)), observe(whole).edges());
        assertEquals(Set.of(enter(E, L), jumpEdge(L, TAIL, E), ret(TAIL, E)), observe(split).edges());
        // Explicit correspondence of original points: whole[L,0/1/terminator] -> split[L,0;tail,0/terminator].
        assertSame(first, sequence(whole, L).source().instructions().get(0));
        assertSame(first, sequence(split, L).source().instructions().get(0));
        assertSame(second, sequence(whole, L).source().instructions().get(1));
        assertSame(second, sequence(split, TAIL).source().instructions().get(0));
        assertSame(returned, sequence(whole, L).source().terminator());
        assertSame(returned, sequence(split, TAIL).source().terminator());
        assertEquals(Map.of(L, List.of(first.header().id()), TAIL, List.of(second.header().id())),
                observe(split).instructions());
        assertEquals(2, split.nodes().stream().filter(CfgNode.SequenceNode.class::isInstance).count());
    }

    @Test
    void displayAndOriginPresentationCannotChooseControlAndAreRetained() {
        Publication base = withData(List.of(jump(L, TAIL, List.of(assign("z-first", "A"))), returning(TAIL)), false);
        Unit u = base.units().getFirst();
        Memory.ObjectDeclaration object = u.objects().getFirst();
        Memory.ObjectDeclaration displayed = new Memory.ObjectDeclaration(object.id(), Optional.of("tail goto L"),
                object.typeRef(), object.storage(), object.visibility(), object.origin(), object.coverage(), object.precision());
        Unit changed = new Unit(u.id(), u.containingUnit(), List.of(displayed), u.visibleObjects(), u.entries(),
                u.sequences(), u.completionPorts(), u.body(), u.bodyUnavailable(), u.coverage(), u.origin());
        Publication variant = new Publication(base.id(), base.airVersion(), base.capabilities(), base.artifacts(),
                List.of(changed), base.storage(), base.resources(), base.artifactRelations(),
                List.of(new Origins.Unavailable(ORIGIN, "presentation changed: jump nowhere")), base.coverage(),
                base.uncertainties(), base.premises());
        var graph = graph(variant);
        assertEquals(observe(graph(base)), observe(graph));
        assertSame(displayed, graph.publication().units().getFirst().objects().getFirst());
        assertSame(variant.origins(), graph.publication().origins());
        assertNotEquals(base.origins(), graph.publication().origins());
    }

    @Test
    void missingJumpLabelIsInvalidIrWithoutRepairOrExternalTarget() {
        Publication p = withSequences(List.of(jump(L, new LabelId(U, "missing"), List.of()), returning(TAIL)));
        var result = build(p);
        assertEquals(CfgBuildResult.Status.INVALID_IR, result.status());
        assertEquals(AirValidator.validate(p), result.preflight());
        assertTrue(result.preflight().issues().stream().anyMatch(i -> i.kind() == ValidationIssue.Kind.INVALID_IR));
        assertTrue(result.graph().isEmpty());
    }

    @Test
    void explicitSelfLoopIsPreservedWithoutInventedCompletion() {
        assertEquals(new Observation(Set.of(en(E), seq(L), normal(E)),
                Set.of(enter(E, L), jumpEdge(L, L, E)), Map.of(L, List.of()), Map.of()),
                observe(graph(withSequences(List.of(jump(L, L, List.of()))))));
    }

    @Test
    void orphanJumpAndInstructionsRemainWithoutArtificialPredecessor() {
        Sequence orphan = jump(TAIL, L, List.of(new Operations.Nop(header(new OperationId(U, "orphan-op")))));
        var graph = graph(withSequences(List.of(returning(L), orphan)));
        assertEquals(new Observation(Set.of(en(E), seq(L), seq(TAIL), normal(E)),
                Set.of(enter(E, L), ret(L, E), jumpEdge(TAIL, L, E)),
                Map.of(L, List.of(), TAIL, List.of(new OperationId(U, "orphan-op"))), Map.of()), observe(graph));
        assertSame(orphan, sequence(graph, TAIL).source());
        assertTrue(graph.transitions().stream().noneMatch(t -> t.to().equals(sequence(graph, TAIL).id())));
    }

    @Test
    void haltDiffersFromReturnAndNeverFallsThroughOrReturnsNormally() {
        OperationId halted = new OperationId(U, "halt-L");
        var normal = graph(withSequences(List.of(returning(L), returning(TAIL))));
        var halt = graph(withSequences(List.of(halt(L, Operations.HaltKind.NORMAL), returning(TAIL))));
        assertEquals(new Observation(Set.of(en(E), seq(L), seq(TAIL), normal(E), stopped(halted)),
                Set.of(enter(E, L), stop(L, halted, E), ret(TAIL, E)),
                Map.of(L, List.of(), TAIL, List.of()), Map.of(halted, Termination.NORMAL)), observe(halt));
        assertEquals(Set.of(enter(E, L), ret(L, E), ret(TAIL, E)), observe(normal).edges());
        assertNotEquals(observe(normal), observe(halt));
        assertTrue(halt.transitions().stream().noneMatch(t -> t.from().equals(halt.haltExits().getFirst().id())));
    }

    @Test
    void haltKindsAndSeparateOrphanOccurrencesKeepTheirOwnCorrelation() {
        var first = halt(L, Operations.HaltKind.NORMAL);
        var other = halt(TAIL, Operations.HaltKind.ABNORMAL);
        var graph = graph(withSequences(List.of(first, other)));
        OperationId one = new OperationId(U, "halt-L");
        OperationId two = new OperationId(U, "halt-tail");
        assertEquals(new Observation(Set.of(en(E), seq(L), seq(TAIL), normal(E), stopped(one), stopped(two)),
                Set.of(enter(E, L), stop(L, one, E), stop(TAIL, two, E)),
                Map.of(L, List.of(), TAIL, List.of()), Map.of(one, Termination.NORMAL, two, Termination.ABNORMAL)), observe(graph));
        assertSame(first.terminator(), graph.haltExits().get(0).source());
        assertSame(other.terminator(), graph.haltExits().get(1).source());
        assertTrue(graph.transitions().stream().noneMatch(t -> t.to().equals(sequence(graph, TAIL).id())));
    }

    @Test
    void jumpPreservesEveryActivationWithoutReachabilityFiltering() {
        EntryId second = new EntryId(U, "second");
        var graph = graph(publication(List.of(unit(U, List.of(entry(E, L), entry(second, TAIL)),
                List.of(jump(L, TAIL, List.of()), returning(TAIL))))));
        assertEquals(Set.of(enter(E, L), enter(second, TAIL), jumpEdge(L, TAIL, E),
                jumpEdge(L, TAIL, second), ret(TAIL, E), ret(TAIL, second)), observe(graph).edges());
        assertEquals(List.of(E, second), graph.normalExits().stream().map(CfgNode.NormalExit::entryId).toList());
    }

    @Test
    void sharedHaltPreservesBothActivationContextsAndDeterministicInventory() {
        EntryId second = new EntryId(U, "second");
        OperationId op = new OperationId(U, "halt-tail");
        var entries = List.of(entry(E, L), entry(second, TAIL));
        var sequences = List.of(jump(L, TAIL, List.of()), halt(TAIL, Operations.HaltKind.NORMAL));
        var first = graph(publication(List.of(unit(U, entries, sequences))));
        var permuted = graph(publication(List.of(unit(U, entries.reversed(), sequences.reversed()))));
        assertEquals(Set.of(enter(E, L), enter(second, TAIL), jumpEdge(L, TAIL, E), jumpEdge(L, TAIL, second),
                stop(TAIL, op, E), stop(TAIL, op, second)), observe(first).edges());
        assertEquals(1, first.haltExits().size(), "one termination node per AIR occurrence; contexts live on transitions");
        assertEquals(first.nodes(), permuted.nodes());
        assertEquals(first.transitions(), permuted.transitions());
    }

    @Test
    void jumpAndHaltNeverFuseHomonymousUnitNamespaces() {
        UnitId other = new UnitId(P, "V");
        EntryId f = new EntryId(other, E.localId());
        LabelId a = new LabelId(other, L.localId());
        LabelId b = new LabelId(other, TAIL.localId());
        Unit u = unit(U, List.of(entry(E, L)), List.of(jump(L, TAIL, List.of()), halt(TAIL, Operations.HaltKind.NORMAL)));
        Unit v = unit(other, List.of(entry(f, a)), List.of(halt(b, Operations.HaltKind.NORMAL), jump(a, b, List.of())));
        var first = graph(publication(List.of(u, v)));
        var reversed = graph(publication(List.of(v, u)));
        assertEquals(Set.of(enter(E, L), jumpEdge(L, TAIL, E), stop(TAIL, new OperationId(U, "halt-tail"), E),
                enter(f, a), jumpEdge(a, b, f), stop(b, new OperationId(other, "halt-tail"), f)), observe(first).edges());
        assertEquals(2, first.haltExits().size());
        assertEquals(first.nodes(), reversed.nodes());
        assertEquals(first.transitions(), reversed.transitions());
    }

    @Test
    void unsupportedTerminatorsInOrphansAreAllDiagnosedWithoutPartialGraph() {
        var branchId = new OperationId(U, "branch");
        var dispatchId = new OperationId(U, "dispatch");
        var raiseId = new OperationId(U, "raise");
        var invokeId = new OperationId(U, "invoke");
        var opaqueId = new OperationId(U, "opaque");
        var branch = new Operations.Branch(header(branchId), new Expressions.Literal(
                operand(branchId, "predicate", Operand.Role.PREDICATE), new Values.BoolValue(true)), L, L);
        var dispatch = new Operations.Dispatch(header(dispatchId), new Expressions.Literal(
                operand(dispatchId, "selector", Operand.Role.CONTROL_TARGET), new Values.TextValue("X")), List.of(), L);
        var raise = new Operations.Raise(header(raiseId), "failure", List.of());
        var invoke = new Operations.Invoke(header(invokeId), "call", new Interactions.InternalTarget(E),
                List.of(), List.of(), new Interactions.EntrySignature(E), List.of(),
                new Interactions.EffectBound(new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,
                        Scopes.NoMemory.INSTANCE, List.of()), List.of()),
                new Control.InvocationOutcomes(List.of(new Control.Normal(L), Control.Diverge.INSTANCE), Scopes.NoControl.INSTANCE),
                new Interactions.KnownContract(new Interactions.ContractRef("test-contract", "1", List.of(ORIGIN))));
        var opaque = new Operations.Opaque(new Operations.Header(opaqueId, ORIGIN, Evidence.CoverageStatus.ABSTRACTED,
                header(opaqueId).precision(), List.of(GAP)), "jump display is not semantics", List.of(), List.of(),
                new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(), Scopes.NoMemory.INSTANCE,
                        List.of(), Scopes.NoMemory.INSTANCE, List.of()),
                        new Control.ControlEnvelope(List.of(new Control.JumpAlternative(L)), Scopes.NoControl.INSTANCE),
                        new Envelopes.DependencyEnvelope(List.of(), Scopes.NoResources.INSTANCE)));
        Publication input = withData(List.of(returning(L),
                new Sequence(new LabelId(U, "orphan-1"), List.of(), branch, ORIGIN),
                new Sequence(new LabelId(U, "orphan-2"), List.of(), dispatch, ORIGIN),
                new Sequence(new LabelId(U, "orphan-3"), List.of(), raise, ORIGIN),
                new Sequence(new LabelId(U, "orphan-4"), List.of(), invoke, ORIGIN),
                new Sequence(new LabelId(U, "orphan-5"), List.of(), opaque, ORIGIN)), false);
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, AirValidator.validate(input).status());
        var result = build(input);
        assertEquals(CfgBuildResult.Status.UNSUPPORTED_INPUT, result.status());
        assertTrue(result.graph().isEmpty());
        assertEquals(List.of(dispatchId, raiseId, invokeId, opaqueId), result.projectionIssues().stream().map(CfgProjectionIssue::subject).toList());
        assertTrue(result.projectionIssues().stream().allMatch(i -> i.code() == CfgProjectionIssue.Code.UNSUPPORTED_TERMINATOR));
    }

    @Test
    void instructionCardinalityIsNotLimitedByCapabilityOrIds() {
        List<Instruction> input = new ArrayList<>();
        for (int i = 257; i >= 0; i--) input.add(new Operations.Nop(header(new OperationId(U, "op-" + i))));
        var graph = graph(withSequences(List.of(new Sequence(L, input, returning(L).terminator(), ORIGIN))));
        List<Instruction> actual = sequence(graph, L).source().instructions();
        assertEquals(258, actual.size());
        for (int i = 0; i < input.size(); i++) assertSame(input.get(i), actual.get(i));
        assertEquals(Set.of(enter(E, L), ret(L, E)), observe(graph).edges());
    }

    @Test
    void memoryRegionsConsumptionIsDeclaredPreciselyForControlOnly() {
        var copy = copyBytes();
        var graph = graph(withData(List.of(new Sequence(L, List.of(copy), returning(L).terminator(), ORIGIN)), true));
        assertEquals(List.of(Capabilities.MEMORY_REGIONS), graph.preciseControlCapabilities());
        assertSame(graph.preciseControlCapabilities(), graph.preciseControlCapabilities());
        assertThrows(UnsupportedOperationException.class, () -> graph.preciseControlCapabilities().clear());
        assertSame(copy, sequence(graph, L).source().instructions().getFirst());
        assertSame(copy.fallback(), ((Operations.CopyBytes) sequence(graph, L).source().instructions().getFirst()).fallback());
        assertEquals(Set.of(enter(E, L), ret(L, E)), observe(graph).edges());
        assertTrue(SemanticInterpreterRegistry.empty().find(Capabilities.MEMORY_REGIONS).isEmpty());
    }

    @Test
    void missingMemoryManifestStillFailsAirValidation() {
        var input = withData(List.of(new Sequence(L, List.of(copyBytes()), returning(L).terminator(), ORIGIN)), true);
        var invalid = requiring(input, List.of());
        var result = build(invalid);
        assertEquals(CfgBuildResult.Status.INVALID_IR, result.status());
        assertEquals(AirValidator.validate(invalid), result.preflight());
        assertTrue(result.graph().isEmpty());
    }

    @Test
    void anotherMemoryCapabilityVersionIsNotInvented() {
        var unknown = new Capabilities.Capability("memory.regions", "2");
        var result = build(requiring(minimal(), List.of(unknown)));
        assertEquals(CfgBuildResult.Status.UNSUPPORTED_CAPABILITY, result.status());
        assertEquals(List.of(unknown), result.unsupportedCapabilities());
        assertTrue(result.graph().isEmpty());
    }

    @Test
    void memorySupportDoesNotAbsorbControlOrUnknownCapabilities() {
        for (var unsupported : List.of(Capabilities.LOCAL_CONTROL, Capabilities.INDIRECT_CONTROL,
                new Capabilities.Capability("vendor.control", "1"))) {
            var result = build(requiring(minimal(), List.of(Capabilities.MEMORY_REGIONS, unsupported)));
            assertEquals(CfgBuildResult.Status.UNSUPPORTED_CAPABILITY, result.status());
            assertEquals(List.of(unsupported), result.unsupportedCapabilities());
            assertTrue(result.graph().isEmpty());
        }
    }

    @Test
    void haltNavigationReusesMaterializedImmutableInventory() {
        var graph = graph(withSequences(List.of(halt(L, Operations.HaltKind.NORMAL), halt(TAIL, Operations.HaltKind.ABNORMAL))));
        var exits = graph.haltExits();
        assertEquals(2, exits.size());
        assertSame(exits, graph.haltExits());
        assertSame(graph.entries(), graph.entries());
        assertSame(graph.normalExits(), graph.normalExits());
        assertThrows(UnsupportedOperationException.class, () -> exits.clear());
        var copied = new CfgGraph(graph.publication(), new ArrayList<>(graph.nodes()), new ArrayList<>(graph.transitions()));
        assertEquals(graph, copied);
        assertEquals(graph.hashCode(), copied.hashCode());
        assertEquals(exits, copied.haltExits());
    }
}
