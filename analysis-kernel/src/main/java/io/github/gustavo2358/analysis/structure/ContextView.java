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
        return new EdgeCursor(index, forward ? index.forwardNext : index.backwardNext, head, node, entry, forward);
    }

    /** Stored edges retain identity; bounded unknown edges are transient views over original nodes. */
    public static final class EdgeCursor {
        private final ProgramIndex index;
        private final int[] next;
        private int position;
        private int current = -1;
        private long edgesVisited;
        private final ProgramIndex.Node anchor;
        private final Entries.Entry entry;
        private final boolean forward;
        private final java.util.Iterator<ProgramIndex.Node> candidates;
        private ProgramIndex.Node symbolicSource, symbolicTarget;
        private CfgTransition symbolicEdge;
        EdgeCursor(ProgramIndex index, int[] next, int position, ProgramIndex.Node anchor, Entries.Entry entry, boolean forward) {
            this.index = index; this.next = next; this.position = position; this.anchor = anchor; this.entry = entry; this.forward = forward;
            var sources = index.openSources.getOrDefault(entry.id().unit(), java.util.List.of());
            candidates = (sources.isEmpty() || forward && !(OpenControl.bound(anchor) instanceof io.github.gustavo2358.air.model.Scopes.WithinControl)
                ? java.util.List.<ProgramIndex.Node>of() : forward ? index.unitNodes.get(entry.id().unit()) : sources).iterator();
        }
        public boolean advance() {
            symbolicEdge = null; current = position;
            if (current != -1) { position = next[current]; edgesVisited = Math.incrementExact(edgesVisited); return true; }
            while (candidates.hasNext()) {
                var candidate = candidates.next(); var source = forward ? anchor : candidate; var target = forward ? candidate : anchor;
                if (!OpenControl.allows(source, target, entry)) continue;
                symbolicSource = source; symbolicTarget = target;
                symbolicEdge = new CfgTransition(source.source().id(), target.source().id(), CfgTransition.Kind.OPAQUE_UNKNOWN, entry.id());
                edgesVisited = Math.incrementExact(edgesVisited); return true;
            }
            return false;
        }
        private void requireCurrent() { if (current == -1 && symbolicEdge == null) throw new NoSuchElementException("cursor has no current edge"); }
        public CfgTransition transition() { requireCurrent(); return symbolicEdge != null ? symbolicEdge : index.edges[current]; }
        public ProgramIndex.Node source() { requireCurrent(); return symbolicEdge != null ? symbolicSource : index.nodes[index.from[current]]; }
        public ProgramIndex.Node target() { requireCurrent(); return symbolicEdge != null ? symbolicTarget : index.nodes[index.to[current]]; }
        /** Actual cursor edge reads, independent of index-construction counters. */
        public long edgesVisited() { return edgesVisited; }
    }
}
