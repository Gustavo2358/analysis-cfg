package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** Downstream gate before changing lower: point queries over one shared entry analysis. */
class MultiCallModelTest {
    static Operations.Invoke literal(UnitId u, String id, String name, String next) {
        var i = W1dModelTest.call(u, id, next, null, false);
        return new Operations.Invoke(i.header(), i.action(), new Interactions.LiteralTarget("program", "cobol.program",
            name, Interactions.ExactName.INSTANCE, origin(u.publication())), i.arguments(), i.results(), i.signature(),
            i.effectOperands(), i.effectBound(), i.outcomes(), i.contract());
    }
    static Sequence invoke(UnitId u, String label, List<Instruction> instructions, Operations.Invoke call) {
        return new Sequence(new LabelId(u, label), instructions, call, origin(u.publication()));
    }
    static Publication linear(boolean orphan) {
        return W1dModelTest.model(1, u -> {
            var a = new ObjectId(u, "object-0"); var seq = new ArrayList<Sequence>();
            seq.add(invoke(u, "start", List.of(assign(u, "produce-a", a, "PROGA   ")), W1dModelTest.call(u, "site-a", "second", a, false)));
            seq.add(invoke(u, "second", List.of(assign(u, "produce-b", a, "PROGB   ")), W1dModelTest.call(u, "site-b", "third", a, false)));
            seq.add(invoke(u, "third", List.of(), literal(u, "site-c", "PROGC", "end")));
            seq.add(returning(u, "end", List.of()));
            if (orphan) seq.add(invoke(u, "orphan", List.of(), literal(u, "site-bad", "BADPROG", "end")));
            return seq;
        });
    }
    static DependencyResult oracle(Publication p, Map<String, Map<String, String>> expected, int dynamic, Set<String> global) {
        var r = new DependencyAnalysis().prepare(p); var u = p.units().getFirst();
        var facts = r.sites().stream().collect(Collectors.toMap(s -> s.operation().localId(), s -> s));
        var invocations = u.sequences().stream().filter(s -> s.terminator() instanceof Operations.Invoke).toList();
        assertEquals(invocations.size(), facts.size());
        assertEquals(expected.keySet(), facts.values().stream().filter(s -> s.reachability() == DependencySiteFact.Reachability.REACHABLE)
            .map(s -> s.operation().localId()).collect(Collectors.toSet()));
        for (var sequence : invocations) {
            var i = (Operations.Invoke) sequence.terminator(); var s = facts.get(i.header().id().localId());
            assertNotNull(s); assertEquals(i.header().id(), s.operation()); assertEquals(sequence.label(), s.sequence());
            assertEquals(sequence.instructions().size(), s.offset()); assertEquals(u.entries().getFirst().id(), s.entry());
            if (!expected.containsKey(s.operation().localId())) {
                assertEquals(DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL, s.reachability()); assertTrue(s.candidates().isEmpty()); continue;
            }
            boolean computed = i.target() instanceof Interactions.ComputedTarget;
            assertEquals(computed ? ProgramPoint.before(s.entry(), s.operation()) : null, s.valuePoint());
            assertEquals(computed ? new ObjectId(u.id(), "object-0") : null, s.subject());
            assertEquals(expected.get(s.operation().localId()).keySet(), s.candidates().stream().map(DependencySiteFact.Candidate::referenceName).collect(Collectors.toSet()));
            assertEquals(expected.get(s.operation().localId()).size(), s.candidates().size());
            assertEquals(false, s.modelValueRemainder()); assertFalse(s.sourceValueRemainder());
            assertFalse(s.interpretationUnknownRemainder()); assertFalse(s.effectiveUnknownRemainder());
            for (var c : s.candidates()) assertEquals(List.of(expected.get(s.operation().localId()).get(c.referenceName())),
                c.supports().stream().map(support -> support.producer().localId()).toList());
            assertEquals(s.candidates(), r.edges().stream().filter(e -> e.site().equals(s.operation())).map(DependencyResult.Edge::candidate).toList());
        }
        assertEquals(global, r.edges().stream().map(e -> e.candidate().referenceName()).collect(Collectors.toSet()));
        assertEquals(expected.values().stream().mapToInt(Map::size).sum(), r.edges().size());
        assertEquals(dynamic == 0 ? 0L : 1L, r.metrics().get("possibleValuesRuns"));
        assertEquals(1L, r.metrics().get("reachabilityRuns"));
        assertEquals((long) invocations.size() + dynamic, r.metrics().get("planning.uniqueQueries"));
        return r;
    }
    @Test void threeMixedSitesKeepPointValuesSupportsAndExcludeUnreachableLiteral() {
        oracle(linear(true), Map.of("site-a", Map.of("PROGA", "produce-a"), "site-b", Map.of("PROGB", "produce-b"),
            "site-c", Map.of("PROGC", "site-c")), 2, Set.of("PROGA", "PROGB", "PROGC"));
    }
    @Test void ifJoinThenOverwriteSameObjectUsesDifferentProgramPoints() {
        var p = W1dModelTest.model(1, u -> {
            var a = new ObjectId(u, "object-0");
            return List.of(branch(u, "start", "then", "else"),
                new Sequence(new LabelId(u, "then"), List.of(assign(u, "produce-a", a, "PROGA   ")), new Operations.Jump(header(u, "join-a"), new LabelId(u, "first")), origin(u.publication())),
                new Sequence(new LabelId(u, "else"), List.of(assign(u, "produce-b", a, "PROGB   ")), new Operations.Jump(header(u, "join-b"), new LabelId(u, "first")), origin(u.publication())),
                invoke(u, "first", List.of(), W1dModelTest.call(u, "site-ab", "second", a, false)),
                invoke(u, "second", List.of(assign(u, "produce-c", a, "PROGC   ")), W1dModelTest.call(u, "site-c", "end", a, false)),
                returning(u, "end", List.of()));
        });
        var expected = Map.of("site-ab", Map.of("PROGA", "produce-a", "PROGB", "produce-b"), "site-c", Map.of("PROGC", "produce-c"));
        var r = oracle(p, expected, 2, Set.of("PROGA", "PROGB", "PROGC"));
        var seq = new ArrayList<>(p.units().getFirst().sequences()); Collections.reverse(seq);
        var reversed = oracle(W1dEffectsTest.sequences(p, seq, p.units().getFirst().entries()), expected, 2, Set.of("PROGA", "PROGB", "PROGC"));
        assertEquals(r.sites(), reversed.sites()); assertEquals(r.edges(), reversed.edges());
    }
    @Test void twoDynamicThreeLiteralSitesShareRunsAndDeduplicateGlobalNames() {
        var p = linear(false); var u = p.units().getFirst().id(); var seq = new ArrayList<>(p.units().getFirst().sequences());
        seq.set(2, invoke(u, "third", List.of(), literal(u, "site-c", "PROGC", "fourth")));
        seq.add(invoke(u, "fourth", List.of(), literal(u, "site-a-literal", "PROGA", "fifth")));
        seq.add(invoke(u, "fifth", List.of(), literal(u, "site-c-literal", "PROGC", "end")));
        oracle(W1dEffectsTest.sequences(p, seq, p.units().getFirst().entries()), Map.of(
            "site-a", Map.of("PROGA", "produce-a"), "site-b", Map.of("PROGB", "produce-b"), "site-c", Map.of("PROGC", "site-c"),
            "site-a-literal", Map.of("PROGA", "site-a-literal"), "site-c-literal", Map.of("PROGC", "site-c-literal")), 2, Set.of("PROGA", "PROGB", "PROGC"));
    }
}
