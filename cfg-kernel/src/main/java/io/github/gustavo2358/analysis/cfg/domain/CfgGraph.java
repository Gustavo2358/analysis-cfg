package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Ids.EntryId;
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
 * Snapshot-backed operation and source-unit inventories borrow the program owner and must be consumed before it
 * closes; resident operation inventories remain detached immutable lists.
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
        this(CfgSource.from(publication), nodes, transitions,
                new WeakReference<>(Objects.requireNonNull(publication,"publication")),false);
    }

    public CfgGraph(CfgSource source, List<CfgNode> nodes, List<CfgTransition> transitions) {
        this(source, nodes, transitions, new WeakReference<>(null),false);
    }

    CfgGraph(Publication publication, CfgSource source, List<CfgNode> nodes,
             List<CfgTransition> transitions) {
        this(source, nodes, transitions,
                new WeakReference<>(Objects.requireNonNull(publication, "publication")),true);
    }

    static CfgGraph projected(CfgSource source,List<CfgNode> nodes,List<CfgTransition> transitions) {
        return new CfgGraph(source,nodes,transitions,new WeakReference<>(null),true);
    }

    private CfgGraph(CfgSource source, List<CfgNode> nodes, List<CfgTransition> transitions,
                     WeakReference<Publication> sourceWitness,boolean canonicalNodes) {
        this.source = Objects.requireNonNull(source, "source");
        this.sourceWitness = sourceWitness;
        this.nodes = nodes instanceof CfgNodeInventory ? nodes : List.copyOf(nodes);
        this.transitions = transitions instanceof CfgTransitionTable table ? table : List.copyOf(transitions);
        if(this.transitions instanceof CfgTransitionTable table&&!table.source().equals(source))
            throw new IllegalArgumentException("foreign factored projection owner");
        List<CfgNode.EntryNode> entryNodes = new ArrayList<>();
        List<CfgNode.NormalExit> exitNodes = new ArrayList<>();
        List<CfgNode.HaltExit> haltNodes = new ArrayList<>();
        Map<EntryId, CfgNode.EntryNode> activationEntries = new HashMap<>();
        Map<CfgNodeId, CfgNode> indexed = new HashMap<>();
        var borrowed=this.nodes instanceof CfgNodeInventory inventory?inventory:null;
        int ordinal=0,entryCount=0,normalCount=0,haltCount=0;EntryId previousEntry=null;
        for (CfgNode node : this.nodes) {
            if (!node.id().publicationId().equals(source.publicationId())
                    || (canonicalNodes ? node.id().ordinal()!=ordinal : indexed.putIfAbsent(node.id(), node) != null)) {
                throw new IllegalArgumentException("duplicate or foreign CFG node ID");
            }
            ordinal++;
            if (node instanceof CfgNode.EntryNode entry) {
                if(borrowed==null)entryNodes.add(entry);
                else if(entryCount>=borrowed.entries().size()||!entry.equals(borrowed.entries().get(entryCount)))throw new IllegalArgumentException("CFG Entry role index disagrees with nodes");
                entryCount++;
                if (canonicalNodes ? previousEntry!=null&&compareEntries(previousEntry,entry.entry())>=0
                        : activationEntries.putIfAbsent(entry.entry(), entry) != null) {
                    throw new IllegalArgumentException("duplicate activation Entry");
                }
                previousEntry=entry.entry();
            } else if (node instanceof CfgNode.NormalExit exit) {
                if(borrowed==null)exitNodes.add(exit);
                else if(normalCount>=borrowed.normalExits().size()||!exit.equals(borrowed.normalExits().get(normalCount)))throw new IllegalArgumentException("CFG normal-exit role index disagrees with nodes");
                normalCount++;
            } else if (node instanceof CfgNode.HaltExit halt) {
                if(borrowed==null)haltNodes.add(halt);
                else if(haltCount>=borrowed.haltExits().size()||!halt.equals(borrowed.haltExits().get(haltCount)))throw new IllegalArgumentException("CFG halt-exit role index disagrees with nodes");
                haltCount++;
            }
        }
        localRules = LocalControlRules.project(this.nodes);
        if(borrowed!=null&&(entryCount!=borrowed.entries().size()||normalCount!=borrowed.normalExits().size()||haltCount!=borrowed.haltExits().size()))
            throw new IllegalArgumentException("CFG role counts disagree with nodes");
        entries = borrowed==null?List.copyOf(entryNodes):borrowed.entries();
        normalExits = borrowed==null?List.copyOf(exitNodes):borrowed.normalExits();
        haltExits = borrowed==null?List.copyOf(haltNodes):borrowed.haltExits();
        preciseControlCapabilities = source.preciseControlCapabilities();
        var stored=this.transitions instanceof CfgTransitionTable table?table.stored():this.transitions;
        if(this.transitions instanceof CfgTransitionTable table)for(int g=0;g<table.groups();g++)for(int e=0;e<table.entries(g);e++) {
            var binding=table.entry(g,e);var exit=canonicalNodes?ordinalNode(table.normalExit(g,e)):indexed.get(table.normalExit(g,e));
            if(!binding.activationEntry().unit().equals(table.unit(g))||!(exit instanceof CfgNode.NormalExit normal)
                    ||!normal.entryId().equals(binding.activationEntry())||!normal.unitId().equals(table.unit(g)))
                throw new IllegalArgumentException("factored entry/normal-exit correlation");
        }
        if (new HashSet<>(stored).size() != stored.size()) {
            throw new IllegalArgumentException("duplicate CFG transition");
        }
        for (CfgTransition transition : stored) {
            CfgNode from = canonicalNodes?ordinalNode(transition.from()):indexed.get(transition.from());
            CfgNode to = canonicalNodes?ordinalNode(transition.to()):indexed.get(transition.to());
            if (!(canonicalNodes?hasActivation(transition.activationEntry()):activationEntries.containsKey(transition.activationEntry()))) {
                throw new IllegalArgumentException("transition requires an inventoried activation Entry");
            }
            boolean valid = switch (transition.kind()) {
                case EXCEPTION -> from instanceof CfgNode.SequenceNode sequence
                    &&to instanceof CfgNode.SequenceNode target
                    &&sequence.label().unit().equals(transition.activationEntry().unit())
                    &&target.label().unit().equals(transition.activationEntry().unit())
                    &&CfgControl.alternatives(sequence.control()).stream()
                        .anyMatch(a->target.label().equals(CoreCfgProjection.exceptionLabel(a)));
                case CONTROL_EXIT -> from instanceof CfgNode.SequenceNode sequence
                    &&to instanceof CfgNode.OutcomeExit exit&&sequence.control().operation().equals(exit.operation())
                    &&CfgControl.alternatives(sequence.control()).contains(exit.outcome())
                    &&sequence.label().unit().equals(transition.activationEntry().unit());
                case ENTRY -> from instanceof CfgNode.EntryNode entry
                        && to instanceof CfgNode.SequenceNode sequence
                        && entry.entry().equals(transition.activationEntry())
                        && entry.initialLabel().filter(sequence.label()::equals).isPresent();
                case JUMP -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.control() instanceof CfgControl.Jump jump
                        && to instanceof CfgNode.SequenceNode target
                        && jump.destination().equals(target.label())
                        && sequence.label().unit().equals(transition.activationEntry().unit())
                        && target.label().unit().equals(transition.activationEntry().unit());
                case OPAQUE_JUMP -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.control() instanceof CfgControl.Opaque opaque
                        && to instanceof CfgNode.SequenceNode target
                        && opaque.alternatives().stream().anyMatch(a -> target.label().equals(CoreCfgProjection.alternativeLabel(a)))
                        && sequence.label().unit().equals(transition.activationEntry().unit());
                case OPAQUE_RETURN -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.control() instanceof CfgControl.Opaque opaque
                        && opaque.alternatives().contains(io.github.gustavo2358.air.model.Control.ReturnAlternative.INSTANCE)
                        && to instanceof CfgNode.NormalExit exit && exit.entryId().equals(transition.activationEntry());
                case OPAQUE_UNKNOWN, LOCAL -> false; // symbolic transitions exist only in the contextual cursor
                case INVOKE_NORMAL -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.control() instanceof CfgControl.Invoke invoke
                        && to instanceof CfgNode.SequenceNode target
                        && invoke.alternatives().stream().anyMatch(o -> o instanceof io.github.gustavo2358.air.model.Control.Normal n && n.label().equals(target.label()))
                        && sequence.label().unit().equals(transition.activationEntry().unit())
                        && target.label().unit().equals(transition.activationEntry().unit());
                case BRANCH_TRUE, BRANCH_FALSE -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.control() instanceof CfgControl.Branch branch
                        && to instanceof CfgNode.SequenceNode target
                        && (transition.kind() == CfgTransition.Kind.BRANCH_TRUE
                            ? branch.trueDestination() : branch.falseDestination()).equals(target.label())
                        && sequence.label().unit().equals(transition.activationEntry().unit())
                        && target.label().unit().equals(transition.activationEntry().unit());
                case HALT -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.control() instanceof CfgControl.Halt halt
                        && to instanceof CfgNode.HaltExit exit
                        && halt.operation().equals(exit.operation())
                        && sequence.label().unit().equals(transition.activationEntry().unit());
                case RETURN -> from instanceof CfgNode.SequenceNode sequence
                        && sequence.control() instanceof CfgControl.Return
                        && to instanceof CfgNode.NormalExit exit
                        && sequence.label().unit().equals(exit.unitId())
                        && exit.entryId().equals(transition.activationEntry());
            };
            if (!valid) {
                throw new IllegalArgumentException("CFG transition disagrees with its typed endpoints/correlation");
            }
        }
    }

    // Core projection publishes dense ordinals and canonical Unit/Entry order. Both are
    // checked above; public arbitrary-order graphs retain their general validation indexes.
    private CfgNode ordinalNode(CfgNodeId id) {
        return id.publicationId().equals(source.publicationId())&&id.ordinal()<nodes.size()
                ? nodes.get((int)id.ordinal()) : null;
    }
    private static int compareEntries(EntryId first,EntryId second) {
        int unit=first.unit().localId().compareTo(second.unit().localId());
        return unit!=0?unit:first.localId().compareTo(second.localId());
    }
    private boolean hasActivation(EntryId id) {
        if(!id.publication().equals(source.publicationId()))return false;
        int low=0,high=entries.size();
        while(low<high) {
            int middle=(low+high)>>>1;int order=compareEntries(entries.get(middle).entry(),id);
            if(order<0)low=middle+1;else high=middle;
        }
        return low<entries.size()&&entries.get(low).entry().equals(id);
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
