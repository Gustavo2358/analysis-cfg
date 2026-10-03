package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.Entries;
import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.cfg.domain.CfgNode;
import io.github.gustavo2358.analysis.cfg.domain.LocalControlRules;
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
    public EdgeCursor successors(ProgramIndex.Node node) { requireOrdinary(); return cursor(node, true); }
    public EdgeCursor predecessors(ProgramIndex.Node node) { requireOrdinary(); return cursor(node, false); }

    private void requireOrdinary() {
        if(!index.localRules.isEmpty())throw new IllegalArgumentException("local control requires a traversal point with its stack");
    }
    /** A shared node plus the exact pending return frames. Entry selection is owned by this view. */
    public record Point(ProgramIndex.Node node,LocalControlRules.Stack stack,io.github.gustavo2358.air.model.Ids.EntryId activation) {
        public Point { Objects.requireNonNull(node);Objects.requireNonNull(stack);Objects.requireNonNull(activation); }
    }
    /** An explicit empty-stack point, including for generic solver test boundaries. */
    public Point emptyStackPoint(ProgramIndex.Node node) {
        if(node.identity!=index.identity||!node.owner().id().equals(entry.id().unit()))throw new IllegalArgumentException("foreign node");
        return new Point(node,LocalControlRules.Stack.EMPTY,entry.id());
    }
    public Point initialPoint(){return emptyStackPoint(entryNode());}
    public Successors successors(Point point) {
        if(!point.activation().equals(entry.id())||point.node().identity!=index.identity||!point.node().owner().id().equals(entry.id().unit()))
            throw new IllegalArgumentException("point outside selected activation");
        return new Successors(point);
    }
    public final class Successors {
        private final Point source;
        private final EdgeCursor ordinary;
        private final LocalControlRules.Rule rule;
        private boolean localVisited;
        private Point target;
        private CfgTransition transition;
        private Successors(Point point) {
            source=point;rule=index.localRules.get(point.node().source().id());
            ordinary=rule==null?cursor(point.node(),true):null;
        }
        public boolean advance() {
            target=null;transition=null;
            if(rule!=null) {
                if(localVisited)return false;localVisited=true;
                var step=LocalControlRules.step(rule,source.stack());
                target=new Point(index.node(step.destination()),step.stack(),entry.id());
                transition=new CfgTransition(source.node().source().id(),step.destination(),CfgTransition.Kind.LOCAL,entry.id());
                return true;
            }
            if(!ordinary.advance())return false;
            var node=ordinary.target();
            // Leaving the activation discards all local frames (AIR 05.7).
            var stack=node.source() instanceof CfgNode.SequenceNode?source.stack():LocalControlRules.Stack.EMPTY;
            target=new Point(node,stack,entry.id());transition=ordinary.transition();return true;
        }
        public Point target(){if(target==null)throw new NoSuchElementException();return target;}
        public CfgTransition transition(){if(transition==null)throw new NoSuchElementException();return transition;}
    }

    /** Ordinary edges for the activation tabulator; local rules are handled separately. */
    EdgeCursor ordinarySuccessors(ProgramIndex.Node node) {
        if(index.localRules.containsKey(node.source().id()))throw new IllegalArgumentException("local rule has no ordinary successors");
        return cursor(node,true);
    }

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
        private final boolean exactLabels;
        private final java.util.Iterator<ProgramIndex.Node> candidates;
        private ProgramIndex.Node symbolicSource, symbolicTarget;
        private CfgTransition symbolicEdge;
        EdgeCursor(ProgramIndex index, int[] next, int position, ProgramIndex.Node anchor, Entries.Entry entry, boolean forward) {
            this.index = index; this.next = next; this.position = position; this.anchor = anchor; this.entry = entry; this.forward = forward;
            var sources = index.openSources.getOrDefault(entry.id().unit(), java.util.List.of());
            var labels=forward ? index.labelTargets.get(anchor) : null;
            exactLabels=labels!=null;
            candidates=(exactLabels
                ? anchor.owner().id().equals(entry.id().unit()) ? labels : java.util.List.<ProgramIndex.Node>of()
                : sources.isEmpty() || forward && !(OpenControl.bound(anchor,index.policy) instanceof io.github.gustavo2358.air.model.Scopes.WithinControl)
                    ? java.util.List.<ProgramIndex.Node>of() : forward ? index.unitNodes.get(entry.id().unit()) : sources).iterator();
        }
        public boolean advance() {
            symbolicEdge = null; current = position;
            if (current != -1) { position = next[current]; edgesVisited = Math.incrementExact(edgesVisited); return true; }
            while (candidates.hasNext()) {
                var candidate = candidates.next(); var source = forward ? anchor : candidate; var target = forward ? candidate : anchor;
                if (!exactLabels && !OpenControl.allows(source, target, entry,index.policy)) continue;
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
