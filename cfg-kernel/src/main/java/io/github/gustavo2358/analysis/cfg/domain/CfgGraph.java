package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Ids.EntryId;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.Publication;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable inventory of projected known control. Source knowledge is detached from the original AIR
 * publication. Missing nodes/edges do not prove absence when that inventory is partial.
 */
public final class CfgGraph {
    private final Map<CfgNodeId,LocalControlRules.Rule> localRules;
    private final CfgSource source;
    private final WeakReference<Publication> sourceWitness;
    private final List<CfgNode> nodes;
    private final List<CfgTransition> transitions;
    private final List<CfgNode.EntryNode> entries;
    private final List<CfgNode.NormalExit> normalExits;
    private final List<CfgNode.HaltExit> haltExits;
    private final List<Capabilities.Capability> preciseControlCapabilities;

    public CfgGraph(Publication publication, List<CfgNode> nodes, List<CfgTransition> transitions) {
        this(publication, CfgSource.from(publication), nodes, transitions);
    }

    public CfgGraph(CfgSource source, List<CfgNode> nodes, List<CfgTransition> transitions) {
        this(source, nodes, transitions, new WeakReference<>(null));
    }

    CfgGraph(Publication publication, CfgSource source, List<CfgNode> nodes,
             List<CfgTransition> transitions) {
        this(source, nodes, transitions,
                new WeakReference<>(Objects.requireNonNull(publication, "publication")));
    }

    private CfgGraph(CfgSource source, List<CfgNode> nodes, List<CfgTransition> transitions,
                     WeakReference<Publication> sourceWitness) {
        this.source = Objects.requireNonNull(source, "source");
        this.sourceWitness = sourceWitness;
        this.nodes = List.copyOf(nodes);
        this.transitions = transitions instanceof CfgTransitionTable table ? table : List.copyOf(transitions);
        if(this.transitions instanceof CfgTransitionTable table&&!table.source().equals(source))
            throw new IllegalArgumentException("foreign factored projection owner");
        List<CfgNode.EntryNode> entryNodes = new ArrayList<>();
        List<CfgNode.NormalExit> exitNodes = new ArrayList<>();
        List<CfgNode.HaltExit> haltNodes = new ArrayList<>();
        Map<EntryId, CfgNode.EntryNode> activationEntries = new HashMap<>();
        Map<CfgNodeId, CfgNode> indexed = new HashMap<>();
        for (CfgNode node : this.nodes) {
            if (!node.id().publicationId().equals(source.publicationId())
                    || indexed.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("duplicate or foreign CFG node ID");
            }
            if (node instanceof CfgNode.EntryNode entry) {
                entryNodes.add(entry);
                if (activationEntries.putIfAbsent(entry.entry(), entry) != null) {
                    throw new IllegalArgumentException("duplicate activation Entry");
                }
            } else if (node instanceof CfgNode.NormalExit exit) {
                exitNodes.add(exit);
            } else if (node instanceof CfgNode.HaltExit halt) {
                haltNodes.add(halt);
            }
        }
        localRules = LocalControlRules.project(this.nodes);
        entries = List.copyOf(entryNodes);
        normalExits = List.copyOf(exitNodes);
        haltExits = List.copyOf(haltNodes);
        preciseControlCapabilities = source.preciseControlCapabilities();
        var stored=this.transitions instanceof CfgTransitionTable table?table.stored():this.transitions;
        if(this.transitions instanceof CfgTransitionTable table)for(int g=0;g<table.groups();g++)for(int e=0;e<table.entries(g);e++) {
            var binding=table.entry(g,e);var exit=indexed.get(table.normalExit(g,e));
            if(!binding.activationEntry().unit().equals(table.unit(g))||!(exit instanceof CfgNode.NormalExit normal)
                    ||!normal.entryId().equals(binding.activationEntry())||!normal.unitId().equals(table.unit(g)))
                throw new IllegalArgumentException("factored entry/normal-exit correlation");
        }
        if (new HashSet<>(stored).size() != stored.size()) {
            throw new IllegalArgumentException("duplicate CFG transition");
        }
        for (CfgTransition transition : stored) {
            CfgNode from = indexed.get(transition.from());
            CfgNode to = indexed.get(transition.to());
            if (!activationEntries.containsKey(transition.activationEntry())) {
                throw new IllegalArgumentException("transition requires an inventoried activation Entry");
            }
            boolean valid = switch (transition.kind()) {
                case EXCEPTION -> from instanceof CfgNode.SequenceNode sequence
                    &&to instanceof CfgNode.SequenceNode target
                    &&sequence.label().unit().equals(transition.activationEntry().unit())
                    &&target.label().unit().equals(transition.activationEntry().unit())
                    &&CoreCfgProjection.alternatives(sequence.terminator()).stream()
                        .anyMatch(a->target.label().equals(CoreCfgProjection.exceptionLabel(a)));
                case CONTROL_EXIT -> from instanceof CfgNode.SequenceNode sequence
                    &&to instanceof CfgNode.OutcomeExit exit&&sequence.terminator().header().id().equals(exit.operation())
                    &&CoreCfgProjection.alternatives(sequence.terminator()).contains(exit.outcome())
                    &&sequence.label().unit().equals(transition.activationEntry().unit());
                case ENTRY -> from instanceof CfgNode.EntryNode entry
                        && to instanceof CfgNode.SequenceNode sequence
                        && entry.entry().equals(transition.activationEntry())
                        && entry.initialLabel().filter(sequence.label()::equals).isPresent();
                case JUMP -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.terminator() instanceof Operations.Jump jump
                        && to instanceof CfgNode.SequenceNode target
                        && jump.destination().equals(target.label())
                        && sequence.label().unit().equals(transition.activationEntry().unit())
                        && target.label().unit().equals(transition.activationEntry().unit());
                case OPAQUE_JUMP -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.terminator() instanceof Operations.Opaque opaque
                        && to instanceof CfgNode.SequenceNode target
                        && CoreCfgProjection.opaqueDestination(opaque, target.label())
                        && sequence.label().unit().equals(transition.activationEntry().unit());
                case OPAQUE_RETURN -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.terminator() instanceof Operations.Opaque opaque
                        && opaque.envelope().control().known().contains(io.github.gustavo2358.air.model.Control.ReturnAlternative.INSTANCE)
                        && to instanceof CfgNode.NormalExit exit && exit.entryId().equals(transition.activationEntry());
                case OPAQUE_UNKNOWN, LOCAL -> false; // symbolic transitions exist only in the contextual cursor
                case INVOKE_NORMAL -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.terminator() instanceof Operations.Invoke invoke
                        && to instanceof CfgNode.SequenceNode target
                        && invoke.outcomes().known().stream().anyMatch(o -> o instanceof io.github.gustavo2358.air.model.Control.Normal n && n.label().equals(target.label()))
                        && sequence.label().unit().equals(transition.activationEntry().unit())
                        && target.label().unit().equals(transition.activationEntry().unit());
                case BRANCH_TRUE, BRANCH_FALSE -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.terminator() instanceof Operations.Branch branch
                        && to instanceof CfgNode.SequenceNode target
                        && (transition.kind() == CfgTransition.Kind.BRANCH_TRUE
                            ? branch.trueDestination() : branch.falseDestination()).equals(target.label())
                        && sequence.label().unit().equals(transition.activationEntry().unit())
                        && target.label().unit().equals(transition.activationEntry().unit());
                case HALT -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.terminator() instanceof Operations.Halt halt
                        && to instanceof CfgNode.HaltExit exit
                        && halt.header().id().equals(exit.operation())
                        && sequence.label().unit().equals(transition.activationEntry().unit());
                case RETURN -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.terminator() instanceof Operations.Return
                        && to instanceof CfgNode.NormalExit exit
                        && sequence.label().unit().equals(exit.unitId())
                        && exit.entryId().equals(transition.activationEntry());
            };
            if (!valid) {
                throw new IllegalArgumentException("CFG transition disagrees with its typed endpoints/correlation");
            }
        }
    }

    public Map<CfgNodeId,LocalControlRules.Rule> localRules() { return localRules; }

    public CfgSource source() {
        return source;
    }

    /** Exact, non-owning admission witness; the graph never keeps the AIR publication alive. */
    public boolean wasProjectedFrom(Publication publication) {
        return sourceWitness.get() == publication;
    }

    public List<CfgNode> nodes() {
        return nodes;
    }

    /** Actual retained transition rows; bindings may expose additional logical contextual edges. */
    public int storedTransitionCount(){return transitions instanceof CfgTransitionTable table?table.stored().size():transitions.size();}

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
                && source.equals(graph.source)
                && nodes.equals(graph.nodes)
                && transitions.equals(graph.transitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, nodes, transitions);
    }

    @Override
    public String toString() {
        return "CfgGraph[source=" + source + ", nodes=" + nodes + ", transitions=" + transitions + "]";
    }
}
