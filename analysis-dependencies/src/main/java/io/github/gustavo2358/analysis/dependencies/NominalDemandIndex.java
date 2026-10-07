package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.NominalValues;
import java.util.*;

/**
 * Immutable directed demand graph. Assignment reads propagate only from receivers to inputs.
 * Condition incidence is bidirectional: demanding any read demands every read of that condition.
 * Separate expression layers prevent a shared condition/assignment AST from inventing receivers.
 * Each AST identity and each input incidence is indexed once; no condition read-pair product.
 * This index serves the explicit resident source API; it makes no managed-spill guarantee.
 */
final class NominalDemandIndex {
    record Closure(Set<String> symbols, long nodeVisits, long edgeVisits) { }
    private final Map<String, Integer> symbols;
    private final String[] names;
    private final int[] heads, next, targets;

    NominalDemandIndex(NominalValues facts) {
        Objects.requireNonNull(facts);
        var builder = new Builder();
        names = new String[facts.symbols().size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = facts.symbols().get(i).node();
            builder.symbols.put(names[i], builder.node());
        }
        for (var assignment : facts.assignments())
            builder.edge(builder.symbol(assignment.target()), builder.term(assignment.source(), false));
        for (var condition : facts.conditions()) builder.predicate(condition.predicate());
        builder.expand();
        symbols = Collections.unmodifiableMap(builder.symbols);
        heads = Arrays.copyOf(builder.heads, builder.nodes);
        next = Arrays.copyOf(builder.next, builder.edges);
        targets = Arrays.copyOf(builder.targets, builder.edges);
    }

    long edgeCount() { return targets.length; }

    Closure closure(Set<String> requested) {
        Objects.requireNonNull(requested);
        if (requested.isEmpty()) return new Closure(Set.of(), 0, 0);
        var seen = new BitSet(heads.length); var pending = new int[heads.length];
        int first = 0, last = 0; long edgeVisits = 0;
        for (var name : requested) {
            Integer node = symbols.get(name);
            if (node == null) throw new IllegalArgumentException("undeclared nominal demand symbol");
            if (!seen.get(node)) { seen.set(node); pending[last++] = node; }
        }
        while (first < last) {
            int node = pending[first++];
            for (int edge = heads[node]; edge != -1; edge = next[edge]) {
                edgeVisits++;
                int destination = targets[edge];
                if (!seen.get(destination)) { seen.set(destination); pending[last++] = destination; }
            }
        }
        var result = new HashSet<String>();
        for (int node = seen.nextSetBit(0); node >= 0 && node < names.length; node = seen.nextSetBit(node + 1))
            result.add(names[node]);
        return new Closure(Collections.unmodifiableSet(result), last, edgeVisits);
    }

    private static final class Builder {
        final Map<String, Integer> symbols = new HashMap<>();
        final IdentityHashMap<NominalValues.Term, Integer> assignmentTerms = new IdentityHashMap<>();
        final IdentityHashMap<NominalValues.Term, Integer> conditionTerms = new IdentityHashMap<>();
        final IdentityHashMap<NominalValues.Predicate, Integer> predicates = new IdentityHashMap<>();
        final ArrayDeque<NominalValues.Term> assignmentPending = new ArrayDeque<>();
        final ArrayDeque<NominalValues.Term> conditionPending = new ArrayDeque<>();
        final ArrayDeque<NominalValues.Predicate> predicatePending = new ArrayDeque<>();
        int[] heads = new int[16], next = new int[16], targets = new int[16];
        int nodes, edges;
        int node() {
            int count = Math.addExact(nodes, 1);
            if (count > heads.length) heads = Arrays.copyOf(heads, capacity(heads.length, count));
            heads[nodes] = -1; return nodes++;
        }
        void edge(int source, int destination) {
            int count = Math.addExact(edges, 1);
            if (count > next.length) {
                int capacity = capacity(next.length, count);
                next = Arrays.copyOf(next, capacity); targets = Arrays.copyOf(targets, capacity);
            }
            targets[edges] = destination; next[edges] = heads[source]; heads[source] = edges++;
        }
        void incidence(int left, int right) { edge(left, right); edge(right, left); }
        int symbol(String name) {
            Integer id = symbols.get(name);
            if (id == null) throw new IllegalArgumentException("undeclared nominal read/receiver");
            return id;
        }
        int term(NominalValues.Term term, boolean condition) {
            var index = condition ? conditionTerms : assignmentTerms;
            Integer id = index.get(term); if (id != null) return id;
            int created = node(); index.put(term, created);
            (condition ? conditionPending : assignmentPending).addLast(term);
            return created;
        }
        int predicate(NominalValues.Predicate predicate) {
            Integer id = predicates.get(predicate); if (id != null) return id;
            int created = node(); predicates.put(predicate, created); predicatePending.addLast(predicate);
            return created;
        }
        void expand() {
            while (!predicatePending.isEmpty()) {
                var value = predicatePending.removeFirst(); int source = predicates.get(value);
                for (var child : value.children()) incidence(source, predicate(child));
                for (var term : value.terms()) incidence(source, term(term, true));
            }
            expandTerms(false); expandTerms(true);
        }
        void expandTerms(boolean condition) {
            var pending = condition ? conditionPending : assignmentPending;
            var index = condition ? conditionTerms : assignmentTerms;
            while (!pending.isEmpty()) {
                var value = pending.removeFirst(); int source = index.get(value);
                if (value.kind().equals("READ")) {
                    if (condition) incidence(source, symbol(value.value())); else edge(source, symbol(value.value()));
                }
                for (var child : value.arguments()) {
                    int destination = term(child, condition);
                    if (condition) incidence(source, destination); else edge(source, destination);
                }
            }
        }
        private static int capacity(int current, int required) {
            return (int) Math.max(required, Math.min(Integer.MAX_VALUE, 2L * current));
        }
    }
}
