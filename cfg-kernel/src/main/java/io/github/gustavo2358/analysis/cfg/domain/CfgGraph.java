package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Ids.EntryId;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.Publication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable core CFG inventory. The original AIR snapshot retains all provenance, operands and gaps. */
public final class CfgGraph {
    private final Publication publication;
    private final List<CfgNode> nodes;
    private final List<CfgTransition> transitions;
    private final List<CfgNode.EntryNode> entries;
    private final List<CfgNode.NormalExit> normalExits;
    private final List<CfgNode.HaltExit> haltExits;
    private final List<Capabilities.Capability> preciseControlCapabilities;

    public CfgGraph(Publication publication, List<CfgNode> nodes, List<CfgTransition> transitions) {
        this.publication = Objects.requireNonNull(publication, "publication");
        this.nodes = List.copyOf(nodes);
        this.transitions = List.copyOf(transitions);
        List<CfgNode.EntryNode> entryNodes = new ArrayList<>();
        List<CfgNode.NormalExit> exitNodes = new ArrayList<>();
        List<CfgNode.HaltExit> haltNodes = new ArrayList<>();
        Map<EntryId, CfgNode.EntryNode> activationEntries = new HashMap<>();
        Map<CfgNodeId, CfgNode> indexed = new HashMap<>();
        for (CfgNode node : this.nodes) {
            if (!node.id().publicationId().equals(publication.id())
                    || indexed.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("duplicate or foreign CFG node ID");
            }
            if (node instanceof CfgNode.EntryNode entry) {
                entryNodes.add(entry);
                if (activationEntries.putIfAbsent(entry.source().id(), entry) != null) {
                    throw new IllegalArgumentException("duplicate activation Entry");
                }
            } else if (node instanceof CfgNode.NormalExit exit) {
                exitNodes.add(exit);
            } else if (node instanceof CfgNode.HaltExit halt) {
                haltNodes.add(halt);
            }
        }
        entries = List.copyOf(entryNodes);
        normalExits = List.copyOf(exitNodes);
        haltExits = List.copyOf(haltNodes);
        preciseControlCapabilities = publication.capabilities().required().stream()
                .filter(CoreCfgProjection::supportsControlCapability).distinct().toList();
        if (new HashSet<>(this.transitions).size() != this.transitions.size()) {
            throw new IllegalArgumentException("duplicate CFG transition");
        }
        for (CfgTransition transition : this.transitions) {
            CfgNode from = indexed.get(transition.from());
            CfgNode to = indexed.get(transition.to());
            if (!activationEntries.containsKey(transition.activationEntry())) {
                throw new IllegalArgumentException("transition requires an inventoried activation Entry");
            }
            boolean valid = switch (transition.kind()) {
                case ENTRY -> from instanceof CfgNode.EntryNode entry
                        && to instanceof CfgNode.SequenceNode sequence
                        && entry.source().id().equals(transition.activationEntry())
                        && entry.source().initialLabel().filter(sequence.source().label()::equals).isPresent();
                case JUMP -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.source().terminator() instanceof Operations.Jump jump
                        && to instanceof CfgNode.SequenceNode target
                        && jump.destination().equals(target.source().label())
                        && sequence.source().label().unit().equals(transition.activationEntry().unit())
                        && target.source().label().unit().equals(transition.activationEntry().unit());
                case BRANCH_TRUE, BRANCH_FALSE -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.source().terminator() instanceof Operations.Branch branch
                        && to instanceof CfgNode.SequenceNode target
                        && (transition.kind() == CfgTransition.Kind.BRANCH_TRUE
                            ? branch.trueDestination() : branch.falseDestination()).equals(target.source().label())
                        && sequence.source().label().unit().equals(transition.activationEntry().unit())
                        && target.source().label().unit().equals(transition.activationEntry().unit());
                case HALT -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.source().terminator() instanceof Operations.Halt halt
                        && to instanceof CfgNode.HaltExit exit
                        && halt.equals(exit.source())
                        && sequence.source().label().unit().equals(transition.activationEntry().unit());
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

    /** Returns the immutable occurrence inventory materialized once, in O(1). */
    public List<CfgNode.HaltExit> haltExits() {
        return haltExits;
    }

    /** Capabilities interpreted precisely for CFG control only; no storage/effects/dataflow claim. */
    public List<Capabilities.Capability> preciseControlCapabilities() {
        return preciseControlCapabilities;
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
