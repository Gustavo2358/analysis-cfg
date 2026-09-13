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
import java.util.stream.Collectors;

import static io.github.gustavo2358.analysis.cfg.testing.CfgFirstPublications.*;
import static io.github.gustavo2358.analysis.cfg.testing.LinearPublications.*;
import static io.github.gustavo2358.analysis.cfg.testing.BranchPublications.*;
import static org.junit.jupiter.api.Assertions.*;

/** EVAL-CFG-029: manual expected facts precede productive Branch projection. No AIR profile claim. */
class EvalCfg029Test {
    private enum Role { ENTRY, SEQUENCE, NORMAL_EXIT, HALT_EXIT }
    private enum Arm { ENTRY, TRUE, FALSE, JUMP, RETURN, HALT }
    private record Node(Role role, Id identity) { }
    private record Edge(Node from, Node to, Arm arm, EntryId activation) { }
    private record Observation(Set<Node> nodes, Set<Edge> edges) { }

    private static Node en(EntryId e) { return new Node(Role.ENTRY, e); }
    private static Node seq(LabelId l) { return new Node(Role.SEQUENCE, l); }
    private static Node normal(EntryId e) { return new Node(Role.NORMAL_EXIT, e); }
    private static Node stopped(OperationId o) { return new Node(Role.HALT_EXIT, o); }
    private static Edge enter(EntryId e, LabelId l) { return new Edge(en(e), seq(l), Arm.ENTRY, e); }
    private static Edge edge(LabelId from, LabelId to, Arm arm, EntryId e) {
        return new Edge(seq(from), seq(to), arm, e);
    }
    private static Edge ret(LabelId l, EntryId e) { return new Edge(seq(l), normal(e), Arm.RETURN, e); }
    private static Edge stop(LabelId l, OperationId op, EntryId e) {
        return new Edge(seq(l), stopped(op), Arm.HALT, e);
    }
    private static Observation diamondExpected() {
        return new Observation(Set.of(en(E), seq(L), seq(YES), seq(NO), seq(JOIN), normal(E)),
                Set.of(enter(E, L), edge(L, YES, Arm.TRUE, E), edge(L, NO, Arm.FALSE, E),
                        edge(YES, JOIN, Arm.JUMP, E), edge(NO, JOIN, Arm.JUMP, E), ret(JOIN, E)));
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
        assertTrue(result.projectionIssues().isEmpty());
        return result.graph().orElseThrow();
    }
    private static Observation observe(CfgGraph graph) {
        Map<CfgNodeId, Node> nodes = new HashMap<>();
        for (CfgNode node : graph.nodes()) {
            Node seen = switch (node) {
                case CfgNode.EntryNode n -> en(n.source().id());
                case CfgNode.SequenceNode n -> seq(n.source().label());
                case CfgNode.NormalExit n -> normal(n.entryId());
                case CfgNode.HaltExit n -> stopped(n.source().header().id());
            };
            assertNull(nodes.put(node.id(), seen));
        }
        assertEquals(nodes.size(), new HashSet<>(nodes.values()).size());
        Set<Edge> edges = new HashSet<>();
        for (CfgTransition t : graph.transitions()) {
            assertTrue(nodes.containsKey(t.from()));
            assertTrue(nodes.containsKey(t.to()));
            Arm arm = switch (t.kind()) {
                case OPAQUE_JUMP, OPAQUE_RETURN, OPAQUE_UNKNOWN -> throw new AssertionError("Opaque belongs to WORK-CFG-038");
                case INVOKE_NORMAL -> throw new AssertionError("Invoke belongs to CP6 W1D");
                case ENTRY -> Arm.ENTRY;
                case BRANCH_TRUE -> Arm.TRUE;
                case BRANCH_FALSE -> Arm.FALSE;
                case JUMP -> Arm.JUMP;
                case RETURN -> Arm.RETURN;
                case HALT -> Arm.HALT;
            };
            assertTrue(edges.add(new Edge(nodes.get(t.from()), nodes.get(t.to()), arm, t.activationEntry())),
                    "duplicate semantic alternative");
        }
        return new Observation(Set.copyOf(nodes.values()), Set.copyOf(edges));
    }
    private static CfgNode.SequenceNode sequence(CfgGraph graph, LabelId label) {
        return graph.nodes().stream().filter(CfgNode.SequenceNode.class::isInstance)
                .map(CfgNode.SequenceNode.class::cast).filter(n -> n.source().label().equals(label))
                .findFirst().orElseThrow();
    }
    private static void invalid(Publication p) {
        var result = build(p);
        assertEquals(CfgBuildResult.Status.INVALID_IR, result.status());
        assertTrue(result.graph().isEmpty());
        assertTrue(result.projectionIssues().isEmpty(), "preflight stops before projection");
        assertEquals(AirValidator.validate(p), result.preflight());
        assertTrue(result.preflight().issues().stream().anyMatch(i -> i.kind() == ValidationIssue.Kind.INVALID_IR));
    }

    @Test
    void diamondMatchesManualOracleWithoutSiblingOrImplicitJoinEdges() {
        Observation expected = diamondExpected();
        Publication input = diamond(unknown(L, new Types.Known(Types.Builtin.BOOL)));
        CfgGraph g = graph(input);
        assertEquals(expected, observe(g));
        assertFalse(observe(g).edges().stream().anyMatch(e ->
                e.from().equals(seq(YES)) && e.to().equals(seq(NO))
                || e.from().equals(seq(NO)) && e.to().equals(seq(YES))
                || e.from().equals(seq(L)) && e.to().equals(seq(JOIN))));
        assertEquals(List.of(new OperationId(U, "opY")), sequence(g, YES).source().instructions().stream()
                .map(i -> i.header().id()).toList());
        assertEquals(List.of(new OperationId(U, "opN")), sequence(g, NO).source().instructions().stream()
                .map(i -> i.header().id()).toList());
        for (Sequence s : input.units().getFirst().sequences()) assertSame(s, sequence(g, s.label()).source());
    }

    @Test
    void literalTrueDoesNotPruneFalseAlternative() {
        assertEquals(diamondExpected(), observe(graph(diamond(bool(L, true)))));
    }

    @Test
    void literalFalseDoesNotPruneTrueAlternative() {
        assertEquals(diamondExpected(), observe(graph(diamond(bool(L, false)))));
    }

    @Test
    void unknownBooleanRetainsPredicateDependenciesReasonTypeAndOriginByIdentity() {
        var predicate = unknown(L, new Types.Known(Types.Builtin.BOOL));
        Publication input = diamond(predicate);
        var original = (Operations.Branch) input.units().getFirst().sequences().getFirst().terminator();
        int hash = input.hashCode();
        CfgGraph g = graph(input);
        var seen = (Operations.Branch) sequence(g, L).source().terminator();
        assertEquals(diamondExpected(), observe(g));
        assertSame(input, g.publication());
        assertSame(original, seen);
        assertSame(predicate, seen.predicate());
        assertSame(predicate.typeRef(), ((Expressions.Unknown) seen.predicate()).typeRef());
        assertSame(predicate.dependencies(), ((Expressions.Unknown) seen.predicate()).dependencies());
        assertSame(predicate.dependencies().getFirst(), ((Expressions.Unknown) seen.predicate()).dependencies().getFirst());
        assertSame(Scopes.NoMemory.INSTANCE, predicate.remainingReads());
        assertEquals(GAP, predicate.reason());
        assertEquals(Operand.Role.PREDICATE, predicate.header().role());
        assertEquals(ORIGIN, predicate.header().origin());
        assertEquals(OBJECT, ((Places.ObjectPlace) ((Expressions.Read) predicate.dependencies().getFirst()).place()).object());
        assertSame(input.origins(), g.publication().origins());
        assertSame(input.uncertainties(), g.publication().uncertainties());
        assertEquals(hash, input.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> predicate.dependencies().clear());
        assertTrue(build(input).preflight().issues().stream().anyMatch(i ->
                i.kind() == ValidationIssue.Kind.SEMANTIC_OBLIGATION), "purity obligation is preserved, not discharged by CFG");
    }

    @Test
    void unknownTypePredicateIsInvalidWithoutBooleanInference() {
        var predicate = unknown(L, new Types.UnknownType(TYPE_GAP));
        Publication input = withTypeGap(diamond(predicate));
        invalid(input);
        assertSame(predicate, ((Operations.Branch) input.units().getFirst().sequences().getFirst().terminator()).predicate());
        assertEquals(TYPE_GAP, ((Types.UnknownType) predicate.typeRef()).uncertainty());
        assertEquals(GAP, predicate.reason());
        assertEquals(1, predicate.dependencies().size());
        assertTrue(build(input).preflight().issues().stream().anyMatch(i -> i.rule().equals("I-08")));
    }

    @Test
    void incorrectPredicateRoleIsRejectedByPreflight() {
        var p = new Expressions.Literal(operand(branchId(L), "predicate", Operand.Role.VALUE_READ), new Values.BoolValue(true));
        invalid(diamond(p));
    }

    @Test
    void missingTrueTargetIsInvalidWithoutRepair() {
        invalid(plain(List.of(branch(L, bool(L, true), new LabelId(U, "missing"), JOIN), returning(JOIN))));
    }

    @Test
    void missingFalseTargetIsInvalidWithoutRepair() {
        invalid(plain(List.of(branch(L, bool(L, true), JOIN, new LabelId(U, "missing")), returning(JOIN))));
    }

    @Test
    void targetInAnotherUnitIsInvalidEvenWithSameLocalLabel() {
        UnitId v = new UnitId(P, "V");
        LabelId target = new LabelId(v, JOIN.localId());
        invalid(publication(List.of(unit(U, List.of(entry(E, L)),
                        List.of(branch(L, bool(L, true), target, JOIN), returning(JOIN))),
                unit(v, List.of(entry(new EntryId(v, "E"), target)), List.of(returning(target))))));
    }

    @Test
    void emptyFalseArmUsesExplicitJoinWithoutSyntheticNodesOrOperations() {
        Observation expected = new Observation(Set.of(en(E), seq(L), seq(YES), seq(JOIN), normal(E)),
                Set.of(enter(E, L), edge(L, YES, Arm.TRUE, E), edge(L, JOIN, Arm.FALSE, E),
                        edge(YES, JOIN, Arm.JUMP, E), ret(JOIN, E)));
        var g = graph(withData(List.of(branch(L, bool(L, true), YES, JOIN),
                jump(YES, JOIN, List.of(assign("opY", "Y"))), returning(JOIN)), false));
        assertEquals(expected, observe(g));
        assertTrue(sequence(g, L).source().instructions().isEmpty());
        assertTrue(sequence(g, JOIN).source().instructions().isEmpty());
        assertEquals(1, sequence(g, YES).source().instructions().size());
    }

    @Test
    void nestedBranchesUseTheirOwnExplicitDestinations() {
        LabelId inner = new LabelId(U, "inner");
        LabelId innerJoin = new LabelId(U, "innerJoin");
        Observation expected = new Observation(Set.of(en(E), seq(L), seq(inner), seq(YES), seq(NO),
                seq(innerJoin), seq(JOIN), normal(E)), Set.of(enter(E, L),
                edge(L, inner, Arm.TRUE, E), edge(L, JOIN, Arm.FALSE, E),
                edge(inner, YES, Arm.TRUE, E), edge(inner, NO, Arm.FALSE, E),
                edge(YES, innerJoin, Arm.JUMP, E), edge(NO, innerJoin, Arm.JUMP, E),
                edge(innerJoin, JOIN, Arm.JUMP, E), ret(JOIN, E)));
        var g = graph(plain(List.of(branch(L, bool(L, true), inner, JOIN), returning(JOIN),
                branch(inner, bool(inner, false), YES, NO), jump(YES, innerJoin, List.of()),
                jump(NO, innerJoin, List.of()), jump(innerJoin, JOIN, List.of()))));
        assertEquals(expected, observe(g));
        assertFalse(observe(g).edges().contains(edge(inner, JOIN, Arm.TRUE, E)));
        assertFalse(observe(g).edges().contains(edge(inner, JOIN, Arm.FALSE, E)));
    }

    @Test
    void terminatingHaltArmNeverReconvergesOrFallsThrough() {
        OperationId halt = new OperationId(U, "halt-yes");
        Observation expected = new Observation(Set.of(en(E), seq(L), seq(YES), seq(NO), seq(JOIN),
                normal(E), stopped(halt)), Set.of(enter(E, L), edge(L, YES, Arm.TRUE, E),
                edge(L, NO, Arm.FALSE, E), stop(YES, halt, E), edge(NO, JOIN, Arm.JUMP, E), ret(JOIN, E)));
        var g = graph(plain(List.of(branch(L, bool(L, true), YES, NO),
                halt(YES, Operations.HaltKind.NORMAL), returning(JOIN), jump(NO, JOIN, List.of()))));
        assertEquals(expected, observe(g));
        assertTrue(g.transitions().stream().noneMatch(t -> t.from().equals(g.haltExits().getFirst().id())));
        assertFalse(observe(g).edges().stream().anyMatch(e -> e.from().equals(seq(YES)) && e.to().equals(seq(JOIN))));
    }

    @Test
    void terminatingReturnArmNeverReconvergesOrFallsThrough() {
        var expected = new Observation(Set.of(en(E), seq(L), seq(YES), seq(NO), seq(JOIN), normal(E)),
                Set.of(enter(E, L), edge(L, YES, Arm.TRUE, E), edge(L, NO, Arm.FALSE, E),
                        ret(YES, E), edge(NO, JOIN, Arm.JUMP, E), ret(JOIN, E)));
        var g = graph(plain(List.of(branch(L, bool(L, true), YES, NO), returning(YES),
                returning(JOIN), jump(NO, JOIN, List.of()))));
        assertEquals(expected, observe(g));
    }

    @Test
    void sameDestinationPreservesTwoAlternativesAndThePredicate() {
        var predicate = unknown(L, new Types.Known(Types.Builtin.BOOL));
        var input = withData(List.of(branch(L, predicate, JOIN, JOIN), returning(JOIN)), false);
        var expected = new Observation(Set.of(en(E), seq(L), seq(JOIN), normal(E)),
                Set.of(enter(E, L), edge(L, JOIN, Arm.TRUE, E), edge(L, JOIN, Arm.FALSE, E), ret(JOIN, E)));
        var g = graph(input);
        assertEquals(expected, observe(g));
        assertSame(predicate, ((Operations.Branch) sequence(g, L).source().terminator()).predicate());
        assertEquals(2, g.transitions().stream().filter(t -> t.from().equals(sequence(g, L).id())).count());
    }

    @Test
    void physicalPermutationPreservesCorrelatedControlAndCfgIds() {
        Publication original = diamond(unknown(L, new Types.Known(Types.Builtin.BOOL)));
        var u = original.units().getFirst();
        var s = u.sequences();
        var first = graph(original);
        for (var order : List.of(s.reversed(), List.of(s.get(3), s.get(0), s.get(2), s.get(1)),
                List.of(s.get(1), s.get(2), s.get(0), s.get(3)))) {
            var permuted = graph(replaceUnits(original, List.of(replaceSequences(u, order))));
            assertEquals(diamondExpected(), observe(permuted));
            assertEquals(first.nodes(), permuted.nodes());
            assertEquals(first.transitions(), permuted.transitions());
        }
    }

    @Test
    void alphaRenamePreservesControlOperandsAndOriginsUnderExplicitCorrelation() {
        var original = graph(plain(List.of(branch(L, bool(L, true), YES, NO), returning(YES), returning(NO))));
        UnitId v = new UnitId(P, "renamed-unit");
        EntryId f = new EntryId(v, "renamed-entry");
        LabelId source = new LabelId(v, "z-source"), yes = new LabelId(v, "a-true"), no = new LabelId(v, "m-false");
        var renamed = graph(publication(List.of(unit(v, List.of(entry(f, source)),
                List.of(returning(no), branch(source, bool(source, true), yes, no), returning(yes))))));
        Map<Id, Id> correlation = Map.of(v, U, f, E, source, L, yes, YES, no, NO,
                branchId(source), branchId(L), new OperationId(v, "return-a-true"), new OperationId(U, "return-yes"),
                new OperationId(v, "return-m-false"), new OperationId(U, "return-no"),
                bool(source, true).header().id(), bool(L, true).header().id());
        Set<Edge> edges = new HashSet<>();
        for (Edge e : observe(renamed).edges()) edges.add(new Edge(
                new Node(e.from().role(), correlation.get(e.from().identity())),
                new Node(e.to().role(), correlation.get(e.to().identity())), e.arm(), (EntryId) correlation.get(e.activation())));
        assertEquals(observe(original).edges(), edges);
        assertEquals(observe(original).nodes(), observe(renamed).nodes().stream()
                .map(n -> new Node(n.role(), correlation.get(n.identity()))).collect(Collectors.toSet()));
        for (var pair : Map.of(source, L, yes, YES, no, NO).entrySet()) {
            var before = sequence(original, pair.getValue()).source();
            var after = sequence(renamed, pair.getKey()).source();
            assertEquals(before.terminator().header().id(), correlation.get(after.terminator().header().id()));
            assertEquals(before.origin(), after.origin());
            assertEquals(before.terminator().header().origin(), after.terminator().header().origin());
        }
        var p = ((Operations.Branch) sequence(renamed, source).source().terminator()).predicate();
        assertEquals(bool(L, true).header().id(), correlation.get(p.header().id()));
        assertEquals(ORIGIN, p.header().origin());
        assertEquals(new Values.BoolValue(true), ((Expressions.Literal) p).value());
    }

    @Test
    void displayAndOriginPresentationCannotSelectTargets() {
        Publication base = diamond(unknown(L, new Types.Known(Types.Builtin.BOOL)));
        Unit u = base.units().getFirst();
        var o = u.objects().getFirst();
        var displayed = new Memory.ObjectDeclaration(o.id(), Optional.of("FALSE yes TRUE no END-IF join"),
                o.typeRef(), o.storage(), o.visibility(), o.origin(), o.coverage(), o.precision());
        var changed = new Unit(u.id(), u.containingUnit(), List.of(displayed), u.visibleObjects(), u.entries(),
                u.sequences(), u.completionPorts(), u.body(), u.bodyUnavailable(), u.coverage(), u.origin());
        var origins = List.<Origins.Origin>of(new Origins.Unavailable(ORIGIN, "next sequence is the true target"));
        var input = new Publication(base.id(), base.airVersion(), base.capabilities(), base.artifacts(), List.of(changed),
                base.storage(), base.resources(), base.artifactRelations(), origins, base.coverage(), base.uncertainties(), base.premises());
        var g = graph(input);
        assertEquals(diamondExpected(), observe(g));
        assertSame(displayed, g.publication().units().getFirst().objects().getFirst());
        assertSame(input.origins(), g.publication().origins());
        assertSame(base.units().getFirst().sequences().getFirst().terminator(), sequence(g, L).source().terminator());
        assertNotEquals(base.origins(), g.publication().origins());
    }

    @Test
    void sequenceSplitPreservesBranchContinuationAndOriginalPoints() {
        var first = assign("first", "A");
        var second = assign("second", "B");
        var branch = branch(L, bool(L, true), YES, NO).terminator();
        var whole = graph(withData(List.of(new Sequence(L, List.of(first, second), branch, ORIGIN),
                returning(YES), returning(NO)), false));
        var split = graph(withData(List.of(jump(L, JOIN, List.of(first)), returning(NO),
                new Sequence(JOIN, List.of(second), branch, ORIGIN), returning(YES)), false));
        assertEquals(Set.of(enter(E, L), edge(L, YES, Arm.TRUE, E), edge(L, NO, Arm.FALSE, E),
                ret(YES, E), ret(NO, E)), observe(whole).edges());
        assertEquals(new Observation(Set.of(en(E), seq(L), seq(JOIN), seq(YES), seq(NO), normal(E)),
                Set.of(enter(E, L), edge(L, JOIN, Arm.JUMP, E), edge(JOIN, YES, Arm.TRUE, E),
                        edge(JOIN, NO, Arm.FALSE, E), ret(YES, E), ret(NO, E))), observe(split));
        // Explicit original point correspondence: L[0,1,terminator] -> L[0], join[0,terminator].
        assertSame(first, sequence(whole, L).source().instructions().get(0));
        assertSame(first, sequence(split, L).source().instructions().get(0));
        assertSame(second, sequence(whole, L).source().instructions().get(1));
        assertSame(second, sequence(split, JOIN).source().instructions().get(0));
        assertSame(branch, sequence(whole, L).source().terminator());
        assertSame(branch, sequence(split, JOIN).source().terminator());
        assertSame(((Operations.Branch) branch).predicate(),
                ((Operations.Branch) sequence(split, JOIN).source().terminator()).predicate());
    }

    @Test
    void multipleEntriesPreserveEveryBranchActivationWithoutReachabilityFiltering() {
        EntryId f = new EntryId(U, "second");
        var entries = List.of(entry(E, L), entry(f, JOIN));
        var sequences = List.of(branch(L, bool(L, true), JOIN, JOIN), returning(JOIN));
        var g = graph(publication(List.of(unit(U, entries, sequences))));
        assertEquals(new Observation(Set.of(en(E), en(f), seq(L), seq(JOIN), normal(E), normal(f)),
                Set.of(enter(E, L), enter(f, JOIN), edge(L, JOIN, Arm.TRUE, E), edge(L, JOIN, Arm.FALSE, E),
                        edge(L, JOIN, Arm.TRUE, f), edge(L, JOIN, Arm.FALSE, f), ret(JOIN, E), ret(JOIN, f))), observe(g));
        var permuted = graph(publication(List.of(unit(U, entries.reversed(), sequences.reversed()))));
        assertEquals(g.nodes(), permuted.nodes());
        assertEquals(g.transitions(), permuted.transitions());
    }

    @Test
    void orphanBranchKeepsBothRulesWithoutArtificialPredecessor() {
        var orphan = branch(NO, bool(NO, false), L, JOIN);
        var expected = new Observation(Set.of(en(E), seq(L), seq(NO), seq(JOIN), normal(E)),
                Set.of(enter(E, L), ret(L, E), edge(NO, L, Arm.TRUE, E), edge(NO, JOIN, Arm.FALSE, E), ret(JOIN, E)));
        var g = graph(plain(List.of(returning(L), orphan, returning(JOIN))));
        assertEquals(expected, observe(g));
        assertSame(orphan, sequence(g, NO).source());
        assertTrue(g.transitions().stream().noneMatch(t -> t.to().equals(sequence(g, NO).id())));
    }

    @Test
    void unsupportedOrphanStillBlocksAnOtherwiseValidBranchGraph() {
        var raised = new Operations.Raise(header(new OperationId(U, "unsupported")), "failure", List.of());
        var input = plain(List.of(branch(L, bool(L, true), JOIN, JOIN), returning(JOIN),
                new Sequence(NO, List.of(), raised, ORIGIN)));
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, AirValidator.validate(input).status());
        var result = build(input);
        assertEquals(CfgBuildResult.Status.UNSUPPORTED_INPUT, result.status());
        assertTrue(result.graph().isEmpty());
        assertEquals(List.of(new CfgProjectionIssue(CfgProjectionIssue.Code.UNSUPPORTED_TERMINATOR, raised.header().id())),
                result.projectionIssues());
    }

    @Test
    void all258BranchOccurrencesRetainBothExplicitAlternatives() {
        List<Sequence> sequences = new ArrayList<>();
        Set<Node> nodes = new HashSet<>(Set.of(en(E), normal(E), seq(JOIN)));
        Set<Edge> edges = new HashSet<>(Set.of(enter(E, L), ret(JOIN, E)));
        for (int i = 0; i < 258; i++) {
            LabelId source = i == 0 ? L : new LabelId(U, "branch-" + i);
            LabelId next = i == 257 ? JOIN : new LabelId(U, "branch-" + (i + 1));
            // Independent declarative chain expectation: true advances, false exits; no depth traversal.
            nodes.add(seq(source));
            edges.add(edge(source, next, Arm.TRUE, E));
            edges.add(edge(source, JOIN, Arm.FALSE, E));
            sequences.add(branch(source, bool(source, i % 2 == 0), next, JOIN));
        }
        sequences.add(returning(JOIN));
        var g = graph(plain(sequences.reversed()));
        assertEquals(new Observation(Set.copyOf(nodes), Set.copyOf(edges)), observe(g));
        assertEquals(518, g.transitions().size());
        for (Sequence s : sequences) assertSame(s, sequence(g, s.label()).source());
    }

    @Test
    void graphRejectsWrongBranchArmsTargetsAndEndpointKinds() {
        var g = graph(diamond(bool(L, true)));
        CfgNodeId from = sequence(g, L).id();
        for (var kind : List.of(CfgTransition.Kind.BRANCH_TRUE, CfgTransition.Kind.BRANCH_FALSE)) {
            LabelId wrong = kind == CfgTransition.Kind.BRANCH_TRUE ? NO : YES;
            assertThrows(IllegalArgumentException.class, () -> new CfgGraph(g.publication(), g.nodes(),
                    List.of(new CfgTransition(from, sequence(g, wrong).id(), kind, E))));
            assertThrows(IllegalArgumentException.class, () -> new CfgGraph(g.publication(), g.nodes(),
                    List.of(new CfgTransition(from, g.normalExits().getFirst().id(), kind, E))));
            assertThrows(IllegalArgumentException.class, () -> new CfgGraph(g.publication(), g.nodes(),
                    List.of(new CfgTransition(g.entries().getFirst().id(), sequence(g, YES).id(), kind, E))));
            assertThrows(IllegalArgumentException.class, () -> new CfgGraph(g.publication(), g.nodes(),
                    List.of(new CfgTransition(sequence(g, YES).id(), sequence(g, JOIN).id(), kind, E))));
            assertThrows(IllegalArgumentException.class, () -> new CfgGraph(g.publication(), g.nodes(),
                    List.of(new CfgTransition(sequence(g, JOIN).id(), sequence(g, YES).id(), kind, E))));
        }
    }

    @Test
    void graphRejectsForeignOrMissingBranchActivationEntries() {
        UnitId v = new UnitId(P, "V");
        EntryId f = new EntryId(v, "E");
        LabelId target = new LabelId(v, "target");
        var g = graph(publication(List.of(unit(U, List.of(entry(E, L)),
                List.of(branch(L, bool(L, true), JOIN, JOIN), returning(JOIN))),
                unit(v, List.of(entry(f, target)), List.of(returning(target))))));
        for (var kind : List.of(CfgTransition.Kind.BRANCH_TRUE, CfgTransition.Kind.BRANCH_FALSE)) {
            for (EntryId invalid : List.of(f, new EntryId(U, "absent"))) {
                assertThrows(IllegalArgumentException.class, () -> new CfgGraph(g.publication(), g.nodes(),
                        List.of(new CfgTransition(sequence(g, L).id(), sequence(g, JOIN).id(), kind, invalid))));
            }
        }
    }

    @Test
    void sameDestinationTransitionEqualityKeepsArmsAndRejectsExactDuplicates() {
        var g = graph(plain(List.of(branch(L, bool(L, true), JOIN, JOIN), returning(JOIN))));
        var arms = g.transitions().stream().filter(t -> t.from().equals(sequence(g, L).id())).toList();
        assertEquals(2, arms.size());
        assertNotEquals(arms.get(0), arms.get(1));
        assertEquals(2, new HashSet<>(arms).size());
        assertEquals(Set.of(CfgTransition.Kind.BRANCH_TRUE, CfgTransition.Kind.BRANCH_FALSE),
                arms.stream().map(CfgTransition::kind).collect(Collectors.toSet()));
        assertEquals(arms, new CfgGraph(g.publication(), g.nodes(), arms).transitions());
        assertThrows(IllegalArgumentException.class, () -> new CfgGraph(g.publication(), g.nodes(),
                List.of(arms.getFirst(), arms.getFirst())));
    }
}
