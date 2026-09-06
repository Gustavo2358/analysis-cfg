package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.Publication;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable CFG-FIRST inventory. The original AIR snapshot retains all provenance, operands and gaps. */
public record CfgGraph(Publication publication, List<CfgNode> nodes, List<CfgTransition> transitions) {
    public CfgGraph {
        Objects.requireNonNull(publication, "publication");
        nodes = List.copyOf(nodes);
        transitions = List.copyOf(transitions);
        Map<CfgNodeId, CfgNode> indexed = new HashMap<>();
        for (CfgNode node : nodes) {
            if (!node.id().publicationId().equals(publication.id())
                    || indexed.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("duplicate or foreign CFG node ID");
            }
        }
        if (new HashSet<>(transitions).size() != transitions.size()) {
            throw new IllegalArgumentException("duplicate CFG transition");
        }
        for (CfgTransition transition : transitions) {
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

    public List<CfgNode.EntryNode> entries() {
        return nodes.stream().filter(CfgNode.EntryNode.class::isInstance)
                .map(CfgNode.EntryNode.class::cast).toList();
    }

    public List<CfgNode.NormalExit> normalExits() {
        return nodes.stream().filter(CfgNode.NormalExit.class::isInstance)
                .map(CfgNode.NormalExit.class::cast).toList();
    }
}
