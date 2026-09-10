package io.github.gustavo2358.analysis.plan;

import io.github.gustavo2358.air.model.Ids.EntryId;
import io.github.gustavo2358.analysis.solver.Direction;
import java.util.*;

/** Logical semantics only. Snapshot ownership belongs to the execution epoch, never to a graph hash. */
public record AnalysisKey(String implementation, String version, String profile, Direction direction,
                          String precisionPolicy, Map<String,String> options, EntryId entry) {
    public AnalysisKey {
        for (String value : List.of(implementation, version, profile, precisionPolicy))
            if (value.isBlank()) throw new IllegalArgumentException("blank semantic identity");
        Objects.requireNonNull(direction); Objects.requireNonNull(entry);
        var sorted = new TreeMap<String,String>();
        options.forEach((k,v) -> { Objects.requireNonNull(v); if (k.isBlank()) throw new IllegalArgumentException("blank option"); sorted.put(k,v); });
        options = Collections.unmodifiableMap(sorted);
    }
    public static final Comparator<EntryId> ENTRY_ORDER = Comparator.comparing((EntryId e) -> e.unit().publication().localId())
        .thenComparing(e -> e.unit().localId()).thenComparing(EntryId::localId);
    public static final Comparator<AnalysisKey> ORDER = Comparator.comparing(AnalysisKey::implementation)
        .thenComparing(AnalysisKey::version).thenComparing(AnalysisKey::profile).thenComparing(AnalysisKey::direction)
        .thenComparing(AnalysisKey::precisionPolicy).thenComparing(AnalysisKey::options, AnalysisKey::compareOptions)
        .thenComparing(AnalysisKey::entry, ENTRY_ORDER);
    private static int compareOptions(Map<String,String> a, Map<String,String> b) {
        var left = a.entrySet().iterator(); var right = b.entrySet().iterator();
        while (left.hasNext() && right.hasNext()) {
            var x = left.next(); var y = right.next(); int c = x.getKey().compareTo(y.getKey());
            if (c == 0) c = x.getValue().compareTo(y.getValue()); if (c != 0) return c;
        }
        return Boolean.compare(left.hasNext(), right.hasNext());
    }
}
