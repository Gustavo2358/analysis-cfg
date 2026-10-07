package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.solver.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SourceValueStoreTest {
    private static AnalysisResources resources() {
        return new AnalysisResources(new AnalysisResources.Limits(64_000_000, 32768, 0, 0, 0, 1_000_000_000, 1_000_000));
    }
    private static Map<String, Set<Long>> facts(SourceValueStore store, long value) {
        var result = new TreeMap<String, Set<Long>>();
        try (var candidates = store.candidates(value)) {
            while (candidates.advance()) {
                var support = new TreeSet<Long>();
                try (var proofs = store.supports(candidates.value())) {
                    while (proofs.advance()) { assertEquals(1, proofs.value()); support.add(proofs.key()); }
                }
                result.put(store.text(candidates.key()), support);
            }
        }
        return result;
    }
    @Test void valueJoinsPreserveEveryCandidateSupportAndAllOpenModelTableFlags() {
        var resources = resources();
        try (var pages = new ResidentPageStore(512, resources); var values = new SourceValueStore(pages, resources)) {
            long a = values.literal("A😀", 0, 11), b = values.literal("A😀", SourceValueStore.TABLE, 12);
            long c = values.literal("B", SourceValueStore.MODEL | SourceValueStore.OPEN, 13);
            long joined = values.join(values.join(a, b), c);
            assertEquals(Map.of("A😀", Set.of(11L, 12L), "B", Set.of(13L)), facts(values, joined));
            assertEquals(7, values.flags(joined));
            assertEquals(joined, values.join(a, values.join(c, b))); assertEquals(joined, values.join(c, values.join(b, a)));
            assertEquals(joined, values.join(joined, joined)); assertEquals(0, resources.used(AnalysisResources.Pool.SCRATCH));
        }
        assertEquals(0, resources.heapUsed());
    }
    @Test void emptySupportIsKnownCandidateAndUnknownJoinDoesNotEraseKnownFacts() {
        var resources = resources();
        try (var pages = new ResidentPageStore(512, resources); var values = new SourceValueStore(pages, resources)) {
            long literal = values.literal("", 0, 0), unknown = values.unknown(false), model = values.unknown(true);
            assertEquals(Map.of("", Set.of()), facts(values, literal)); assertEquals(0, values.flags(literal));
            assertEquals(Map.of(), facts(values, unknown)); assertEquals(SourceValueStore.OPEN, values.flags(unknown));
            long combined = values.join(literal, model);
            assertEquals(Map.of("", Set.of()), facts(values, combined));
            assertEquals(SourceValueStore.OPEN | SourceValueStore.MODEL, values.flags(combined));
        }
        assertEquals(0, resources.heapUsed());
    }
    @Test void successiveAssignmentsShareGrowingSupportInsteadOfCopyingAllPriorSets() {
        var resources = resources();
        try (var pages = new ResidentPageStore(512, resources); var values = new SourceValueStore(pages, resources)) {
            int n = 1024; long value = values.literal("SYNPROG ", 0, 1); long[] roots = new long[n];
            for (int i = 0; i < n; i++) { value = values.addSupport(value, i + 2L); roots[i] = values.retain(value); }
            assertEquals(n + 1, facts(values, value).get("SYNPROG ").size());
            assertTrue(values.records() < 20L * n, "support histories were copied instead of shared");
            // Initial literal's candidate leaf and value were never retained; its support atom
            // remains reachable from every later value. Exactly those two records retire.
            assertEquals(2, values.collect());
            for (long token : roots) values.release(token);
            assertTrue(values.collect() > 0); assertEquals(0, values.records());
        }
        assertEquals(0, resources.heapUsed());
    }
    @Test void supportAndFlagChangesKeepOldValuesAndCallerRootsIndependent() {
        var resources = resources();
        try (var pages = new ResidentPageStore(512, resources); var values = new SourceValueStore(pages, resources)) {
            long old = values.literal("A", 0, 1), a = values.retain(old), b = values.retain(old);
            long changed = values.withFlags(values.addSupport(old, 2), SourceValueStore.OPEN);
            assertEquals(Map.of("A", Set.of(1L)), facts(values, old)); assertEquals(0, values.flags(old));
            assertEquals(Map.of("A", Set.of(1L, 2L)), facts(values, changed));
            values.release(a); values.collect(); assertEquals(Map.of("A", Set.of(1L)), facts(values, old));
            values.release(b); values.collect(); assertEquals(0, values.records());
        }
        assertEquals(0, resources.heapUsed());
    }
    @Test void sparseReachableStatesJoinMissingValuesWithDefaultsWithoutInventingKnownFacts() {
        var resources = resources();
        try (var pages = new ResidentPageStore(512, resources); var values = new SourceValueStore(pages, resources)) {
            long empty = values.emptyState(), known = values.literal("A", 0, 1);
            long a = values.put(empty, 10, known), b = values.put(empty, 20, values.literal("B", 0, 2));
            assertEquals(0, values.flags(values.get(a, 10, false)));
            long combined = values.joinStates(a, b);
            assertEquals(Map.of("A", Set.of(1L)), facts(values, values.get(combined, 10, false)));
            assertEquals(SourceValueStore.OPEN, values.flags(values.get(combined, 10, false)));
            assertEquals(SourceValueStore.OPEN, values.flags(values.get(values.joinStates(a, empty), 10, false)));
            assertEquals(a, values.joinStates(a, a)); assertEquals(combined, values.joinStates(b, a));
            assertEquals(Map.of(), facts(values, values.get(empty, 10, false)));
            assertEquals(3, values.flags(values.get(empty, 30, true)));
            long same = values.put(a, 10, known); assertEquals(a, same);
        }
        assertEquals(0, resources.heapUsed());
    }

    private record Expected(Map<String,Set<Long>> candidates,int flags) { }
    @Test void randomSparseJoinsMatchIndependentDenseDefaultStatesIncludingModelsAndTables() {
        var resources=resources();var random=new Random(5048);
        try(var pages=new ResidentPageStore(512,resources);var values=new SourceValueStore(pages,resources)) {
            for(int round=0;round<120;round++) {
                long left=values.emptyState(),right=values.emptyState();
                var a=new HashMap<Integer,Expected>();var b=new HashMap<Integer,Expected>();
                for(int symbol=0;symbol<31;symbol++)for(int side=0;side<2;side++)if(random.nextBoolean()) {
                    boolean model=symbol%3==0;int flags=random.nextInt(8)|(model?SourceValueStore.MODEL:0);
                    String text="name"+random.nextInt(4);long proof=round*100L+symbol*2L+side+1;
                    long value=values.literal(text,flags,proof);
                    if(side==0){left=values.put(left,symbol,value);a.put(symbol,new Expected(Map.of(text,Set.of(proof)),flags));}
                    else{right=values.put(right,symbol,value);b.put(symbol,new Expected(Map.of(text,Set.of(proof)),flags));}
                }
                long joined=values.joinStates(left,right);assertEquals(joined,values.joinStates(right,left));
                for(int symbol=0;symbol<31;symbol++) {
                    var fallback=new Expected(Map.of(),symbol%3==0?3:1);
                    var av=a.getOrDefault(symbol,fallback);var bv=b.getOrDefault(symbol,fallback);
                    var expected=new TreeMap<String,Set<Long>>();
                    for(var entry:av.candidates().entrySet())expected.put(entry.getKey(),new TreeSet<>(entry.getValue()));
                    for(var entry:bv.candidates().entrySet())expected.computeIfAbsent(entry.getKey(),k->new TreeSet<>()).addAll(entry.getValue());
                    long observed=values.get(joined,symbol,symbol%3==0);
                    assertEquals(expected,facts(values,observed));assertEquals(av.flags()|bv.flags(),values.flags(observed));
                }
                values.collect();assertEquals(0,values.records());
            }
        }
        assertEquals(0,resources.heapUsed());
    }

}
