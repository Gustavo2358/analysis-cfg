package io.github.gustavo2358.analysis.application;

import java.util.*;

/** Checked counts at actual phase events. Counts never decide admission or precision. */
final class Counts {
    private final Map<String,Long> values = new TreeMap<>();
    Counts(String... names) { for (String name : names) values.put(name,0L); }
    void add(String name, long amount) { values.put(name,Math.addExact(values.getOrDefault(name,0L),amount)); }
    Map<String,Long> snapshot() { return Collections.unmodifiableMap(new TreeMap<>(values)); }
}
