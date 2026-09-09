package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.Entries;
import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Selected activation Entry, not a local invocation frame or a reachability certificate. */
public final class ContextView {
    private final ProgramIndex index;
    private final Entries.Entry entry;
    private final int ordinal;
    ContextView(ProgramIndex index, Entries.Entry entry) {
        this.index = index; this.entry = entry; ordinal = index.entryOrdinals.get(entry.id());
    }
    public Entries.Entry entry() { return entry; }
    public ProgramIndex.Node entryNode() { return index.entryNodes.get(entry.id()); }
    public ProgramIndex.Node normalExit() { return index.normalExits.get(entry.id()); }
    public EdgeCursor successors(ProgramIndex.Node node) { return cursor(node, true); }
    public EdgeCursor predecessors(ProgramIndex.Node node) { return cursor(node, false); }

    private EdgeCursor cursor(ProgramIndex.Node node, boolean forward) {
        Objects.requireNonNull(node, "node");
        if (node.identity != index.identity) throw new IllegalArgumentException("node from another index");
        long key = LongIntDirectory.key(ordinal, node.ordinal);
        int head = (forward ? index.forwardHeads : index.backwardHeads).get(key);
        return new EdgeCursor(index, forward ? index.forwardNext : index.backwardNext, head);
    }

    /** A cursor allocates once per traversal; each step returns original edge/node instances. */
    public static final class EdgeCursor {
        private final ProgramIndex index;
        private final int[] next;
        private int position;
        private int current = -1;
        private long edgesVisited;
        EdgeCursor(ProgramIndex index, int[] next, int position) {
            this.index = index; this.next = next; this.position = position;
        }
        public boolean advance() {
            current = position;
            if (current == -1) return false;
            position = next[current];
            edgesVisited = Math.incrementExact(edgesVisited);
            return true;
        }
        private void requireCurrent() { if (current == -1) throw new NoSuchElementException("cursor has no current edge"); }
        public CfgTransition transition() { requireCurrent(); return index.edges[current]; }
        public ProgramIndex.Node source() { requireCurrent(); return index.nodes[index.from[current]]; }
        public ProgramIndex.Node target() { requireCurrent(); return index.nodes[index.to[current]]; }
        /** Actual cursor edge reads, independent of index-construction counters. */
        public long edgesVisited() { return edgesVisited; }
    }
}
