package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.Publication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable CFG-FIRST inventory. The original AIR snapshot retains all provenance, operands and gaps. */
public final class CfgGraph {
    private final Publication publication;
    private final List<CfgNode> nodes;
    private final List<CfgTransition> transitions;
    private final List<CfgNode.EntryNode> entries;
    private final List<CfgNode.NormalExit> normalExits;

    public CfgGraph(Publication publication, List<CfgNode> nodes, List<CfgTransition> transitions) {
        this.publication = Objects.requireNonNull(publication, "publication");
        this.nodes = List.copyOf(nodes);
        this.transitions = List.copyOf(transitions);
        List<CfgNode.EntryNode> entryNodes = new ArrayList<>();
        List<CfgNode.NormalExit> exitNodes = new ArrayList<>();
        Map<CfgNodeId, CfgNode> indexed = new HashMap<>();
        for (CfgNode node : this.nodes) {
            if (!node.id().publicationId().equals(publication.id())
                    || indexed.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("duplicate or foreign CFG node ID");
            }
            if (node instanceof CfgNode.EntryNode entry) {
                entryNodes.add(entry);
            } else if (node instanceof CfgNode.NormalExit exit) {
                exitNodes.add(exit);
            }
        }
        entries = List.copyOf(entryNodes);
        normalExits = List.copyOf(exitNodes);
        if (new HashSet<>(this.transitions).size() != this.transitions.size()) {
            throw new IllegalArgumentException("duplicate CFG transition");
        }
        for (CfgTransition transition : this.transitions) {
            CfgNode from = indexed.get(transition.from());
            CfgNode to = indexed.get(transition.to());
            boolean valid = switch (transition.kind()) {
                case ENTRY -> from instanceof CfgNode.EntryNode entry
                        && to instanceof CfgNode.SequenceNode sequence
                        && entry.source().id().equals(transition.activationEntry())
                        && entry.source().initialLabel().filter(sequence.source().label()::equals).isPresent();
                case RETURN -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.source().terminator() instanceof Operations.Return
                        && to instanceof CfgNode.NormalExit exit
                        && sequence.source().label().unit().equals(exit.unitId())
                        && exit.entryId().equals(transition.activationEntry());
            };
            if (!valid) {
                throw new IllegalArgumentException("CFG transition disagrees with its typed endpoints/correlation");
            }
        }
    }

    public Publication publication() {
        return publication;
    }

    public List<CfgNode> nodes() {
        return nodes;
    }

    public List<CfgTransition> transitions() {
        return transitions;
    }

    /** Returns the immutable inventory materialized at construction, in O(1) without allocation. */
    public List<CfgNode.EntryNode> entries() {
        return entries;
    }

    /** Returns the immutable inventory materialized at construction, in O(1) without allocation. */
    public List<CfgNode.NormalExit> normalExits() {
        return normalExits;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CfgGraph graph
                && publication.equals(graph.publication)
                && nodes.equals(graph.nodes)
                && transitions.equals(graph.transitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publication, nodes, transitions);
    }

    @Override
    public String toString() {
        return "CfgGraph[publication=" + publication + ", nodes=" + nodes + ", transitions=" + transitions + "]";
    }
}
