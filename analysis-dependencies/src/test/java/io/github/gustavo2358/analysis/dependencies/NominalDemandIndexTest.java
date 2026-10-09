package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.NominalValues;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NominalDemandIndexTest {
    @Test void sharedProgressChecksIndexConstructionAndClosureWithoutChangingDemand() {
        var assignments=new ArrayList<NominalValues.Assignment>();
        for(int i=0;i<64;i++)assignments.add(new NominalValues.Assignment("s"+i,"v"+i,read(i+1)));
        var facts=facts(65,assignments,List.of());
        var stopped=new IllegalStateException("shared owner stopped");
        int[] visits={0};
        assertSame(stopped,assertThrows(IllegalStateException.class,()->new NominalDemandIndex(facts,()->{
            if(++visits[0]==17)throw stopped;
        })));
        assertEquals(17,visits[0]);
        boolean[] stopClosure={false};visits[0]=0;
        var index=new NominalDemandIndex(facts,()->{if(stopClosure[0]&&++visits[0]==17)throw stopped;});
        stopClosure[0]=true;
        assertSame(stopped,assertThrows(IllegalStateException.class,()->index.closure(Set.of("v0"))));
        assertEquals(17,visits[0]);
        stopClosure[0]=false;
        assertEquals(oracle(facts,Set.of("v0")),index.closure(Set.of("v0")).symbols());
    }
    private static NominalValues.Term read(int id) { return new NominalValues.Term("READ", "v" + id); }
    private static NominalValues facts(int n, List<NominalValues.Assignment> assignments, List<NominalValues.Condition> conditions) {
        var symbols = new ArrayList<NominalValues.Symbol>();
        for (int i = 0; i < n; i++) symbols.add(new NominalValues.Symbol("v" + i, 8));
        return new NominalValues("NOMINAL_TEXT_SOURCE_V4", symbols, assignments, conditions, List.of());
    }
    /** Small independent set-fixpoint oracle; intentionally does not use the index algorithm. */
    private static Set<String> oracle(NominalValues facts, Set<String> seeds) {
        var result = new HashSet<>(seeds); boolean changed;
        do {
            changed = false;
            for (var assignment : facts.assignments()) if (result.contains(assignment.target()))
                changed |= result.addAll(termReads(assignment.source()));
            for (var condition : facts.conditions()) {
                var reads = new HashSet<String>(); var pending = new ArrayDeque<NominalValues.Predicate>();
                pending.add(condition.predicate());
                while (!pending.isEmpty()) {
                    var predicate = pending.removeFirst();
                    for (var term : predicate.terms()) reads.addAll(termReads(term));
                    pending.addAll(predicate.children());
                }
                if (!Collections.disjoint(reads, result)) changed |= result.addAll(reads);
            }
        } while (changed);
        return result;
    }
    private static Set<String> termReads(NominalValues.Term root) {
        var result = new HashSet<String>(); var pending = new ArrayDeque<NominalValues.Term>(); pending.add(root);
        while (!pending.isEmpty()) {
            var term = pending.removeFirst(); if (term.kind().equals("READ")) result.add(term.value());
            pending.addAll(term.arguments());
        }
        return result;
    }
    @Test void indexedClosureAgreesWithIndependentFixpointAcrossPermutationsAndCycles() {
        var random = new Random(810734);
        for (int sample = 0; sample < 180; sample++) {
            int n = 3 + random.nextInt(25); var assignments = new ArrayList<NominalValues.Assignment>();
            var conditions = new ArrayList<NominalValues.Condition>();
            for (int i = 0; i < n; i++) {
                assignments.add(new NominalValues.Assignment("s" + i, "v" + random.nextInt(n),
                    new NominalValues.Term("CHOICE", "", List.of(read(random.nextInt(n)), read(random.nextInt(n))))));
                conditions.add(new NominalValues.Condition("c" + i,
                    new NominalValues.Predicate("EQ", List.of(read(random.nextInt(n)), read(random.nextInt(n))), List.of())));
            }
            var seeds = Set.of("v" + random.nextInt(n));
            var facts = facts(n, assignments, conditions); var expected = oracle(facts, seeds);
            var index = new NominalDemandIndex(facts);
            assertEquals(expected, index.closure(seeds).symbols());
            Collections.shuffle(assignments, random); Collections.shuffle(conditions, random);
            assertEquals(expected, new NominalDemandIndex(facts(n, assignments, conditions)).closure(seeds).symbols());
            assertEquals(Set.of(), index.closure(Set.of()).symbols());
        }
    }
    @Test void reversedChainVisitsEachIndexedNodeAndEdgeOnce() {
        int n = 131072; var assignments = new ArrayList<NominalValues.Assignment>();
        for (int i = 0; i < n; i++) assignments.add(new NominalValues.Assignment("s" + i, "v" + i, read(i + 1)));
        Collections.reverse(assignments);
        var index = new NominalDemandIndex(facts(n + 1, assignments, List.of()));
        var result = index.closure(Set.of("v0"));
        assertEquals(n + 1, result.symbols().size()); assertTrue(result.symbols().contains("v" + n));
        assertEquals(2L * n + 1, result.nodeVisits()); assertEquals(2L * n, result.edgeVisits());
        assertEquals(2L * n, index.edgeCount());
        assertEquals(Set.of("v" + n), index.closure(Set.of("v" + n)).symbols());
    }
    @Test void conditionReadsUseLinearIncidenceRatherThanPairwiseEdges() {
        int n = 4096; var terms = new ArrayList<NominalValues.Term>();
        for (int i = 0; i < n; i++) terms.add(read(i));
        var choice = new NominalValues.Term("CHOICE", "", terms);
        var predicate = new NominalValues.Predicate("EQ", List.of(choice, new NominalValues.Term("LITERAL", "X")), List.of());
        var index = new NominalDemandIndex(facts(n, List.of(), List.of(new NominalValues.Condition("c", predicate))));
        var result = index.closure(Set.of("v0"));
        assertEquals(n, result.symbols().size()); assertEquals(4L * n + 4, index.edgeCount());
        assertEquals(index.edgeCount(), result.edgeVisits());
    }
    @Test void assignmentAndConditionExpressionSharingDoesNotInventReceiverDemand() {
        var shared = new NominalValues.Term("CHOICE", "", List.of(read(0), read(1)));
        var facts = facts(4, List.of(new NominalValues.Assignment("s", "v2", shared)),
            List.of(new NominalValues.Condition("c", new NominalValues.Predicate("EQ", List.of(shared, read(3)), List.of()))));
        var index = new NominalDemandIndex(facts);
        assertEquals(Set.of("v0", "v1", "v3"), index.closure(Set.of("v0")).symbols());
        assertEquals(Set.of("v0", "v1", "v2", "v3"), index.closure(Set.of("v2")).symbols());
    }
    @Test void sharedExpressionDagIsIndexedByIdentityWithoutExpandingOccurrences() {
        int depth = 12; var term = read(0);
        for (int i = 0; i < depth; i++) term = new NominalValues.Term("CHOICE", "", List.of(term, term));
        var index = new NominalDemandIndex(facts(3, List.of(new NominalValues.Assignment("s", "v2", term)), List.of()));
        var result = index.closure(Set.of("v2"));
        assertEquals(Set.of("v0", "v2"), result.symbols());
        assertEquals(depth + 3L, result.nodeVisits()); assertEquals(2L * depth + 2, result.edgeVisits());
    }
}
