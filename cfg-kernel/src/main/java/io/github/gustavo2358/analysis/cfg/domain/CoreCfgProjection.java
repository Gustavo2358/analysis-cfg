package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Control;
import io.github.gustavo2358.air.model.Entries;
import io.github.gustavo2358.air.model.Ids.LabelId;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.model.Sequence;
import io.github.gustavo2358.air.model.Scopes;
import io.github.gustavo2358.air.model.Unit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact Entry/Jump/Branch/Return/Halt rules for preflight-validated AIR, without reachability or physical fallthrough. */
public final class CoreCfgProjection {
    private CoreCfgProjection() { }

    /** AIR memory.regions@1 has precise sequential control; this does not interpret storage or effects. */
    public static boolean supportsControlCapability(Capabilities.Capability capability) {
        return Capabilities.MEMORY_REGIONS.equals(capability);
    }

    /** Default admission of the known subset; requires the same preflight as explicit policy admission. */
    public static List<CfgProjectionIssue> unsupported(Publication publication) {
        return unsupported(publication, ProjectionPolicy.KNOWN_SUBSET);
    }

    /** Enumerates every rejection; inventory policy never supplies missing semantic interpretation. */
    public static List<CfgProjectionIssue> unsupported(Publication publication, ProjectionPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        List<CfgProjectionIssue> issues = new ArrayList<>();
        if (publication.capabilities().required().stream().anyMatch(c -> !supportsControlCapability(c))) {
            issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.EXTENSION_SEMANTICS_OUTSIDE_SLICE,
                    publication.id()));
        }
        if (!policy.acceptsInventory(publication.coverage().inventory())) {
            issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.INCOMPLETE_INVENTORY, publication.id()));
        }
        for (Unit unit : orderedUnits(publication)) {
            if (unit.body() != Unit.BodyAvailability.AVAILABLE) {
                issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.BODY_UNAVAILABLE, unit.id()));
            }
            if (!policy.acceptsInventory(unit.coverage().inventory())) {
                issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.INCOMPLETE_INVENTORY, unit.id()));
            }
            for (Sequence sequence : orderedSequences(unit)) {
                // All five sealed AIR Instruction variants continue inside their Sequence.
                // Retaining the original Sequence preserves every occurrence and its explicit order.
                if (!(sequence.terminator() instanceof Operations.Return)
                        && !(sequence.terminator() instanceof Operations.Jump)
                        && !(sequence.terminator() instanceof Operations.Branch)
                        && !(sequence.terminator() instanceof Operations.Invoke invoke && supportsInvoke(invoke))
                        && !(sequence.terminator() instanceof Operations.Opaque opaque && supportsOpaque(opaque))
                        && !(sequence.terminator() instanceof Operations.Halt)) {
                    issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.UNSUPPORTED_TERMINATOR,
                            sequence.terminator().header().id()));
                }
            }
        }
        return List.copyOf(issues);
    }

    /** First neutral invocation slice: one explicit local Normal, with closed or open AllControl remainder.
     * Open remainder remains on the original AIR; this projection enumerates known control only. */
    public static boolean supportsInvoke(Operations.Invoke invoke) {
        return invoke.outcomes().known().stream().allMatch(Control.Normal.class::isInstance)
                && (invoke.outcomes().remainder() instanceof Scopes.NoControl
                    || invoke.outcomes().remainder() instanceof Scopes.WithinControl bound
                        && (bound.scope() instanceof Scopes.AllControl || bound.scope() instanceof Scopes.UnitControl || bound.scope() instanceof Scopes.LabelsControl));
    }

    public static boolean supportsOpaque(Operations.Opaque opaque) {
        return opaque.envelope().control().known().stream().allMatch(a -> a instanceof Control.JumpAlternative
            || a instanceof Control.Normal || a instanceof Control.ReturnAlternative);
    }
    public static LabelId alternativeLabel(Control.ControlAlternative a) {
        return a instanceof Control.JumpAlternative j ? j.label() : a instanceof Control.Normal n ? n.label() : null;
    }
    public static boolean opaqueDestination(Operations.Opaque o, LabelId label) {
        return o.envelope().control().known().stream().anyMatch(a -> label.equals(alternativeLabel(a)));
    }

    /** Requires successful AirValidator preflight and an empty unsupported inventory. */
    public static CfgGraph project(Publication publication) {
        List<CfgNode> nodes = new ArrayList<>();
        List<CfgTransition> transitions = new ArrayList<>();
        for (Unit unit : orderedUnits(publication)) {
            Map<LabelId, CfgNode.SequenceNode> sequences = new HashMap<>();
            Map<LabelId, CfgNode.HaltExit> halts = new HashMap<>();
            List<Sequence> orderedSequences = orderedSequences(unit);
            for (Sequence sequence : orderedSequences) {
                CfgNode.SequenceNode node = new CfgNode.SequenceNode(
                        new CfgNodeId(publication.id(), nodes.size()), sequence);
                sequences.put(sequence.label(), node);
                nodes.add(node);
                if (sequence.terminator() instanceof Operations.Halt halt) {
                    CfgNode.HaltExit termination = new CfgNode.HaltExit(
                            new CfgNodeId(publication.id(), nodes.size()), halt);
                    nodes.add(termination);
                    halts.put(sequence.label(), termination);
                }
            }
            List<Entries.Entry> entries = unit.entries().stream()
                    .sorted(Comparator.comparing(entry -> entry.id().localId())).toList();
            for (Entries.Entry entry : entries) {
                CfgNode.EntryNode entryNode = new CfgNode.EntryNode(
                        new CfgNodeId(publication.id(), nodes.size()), entry);
                nodes.add(entryNode);
                CfgNode.NormalExit exit = new CfgNode.NormalExit(
                        new CfgNodeId(publication.id(), nodes.size()), publication.id(), unit.id(), entry.id());
                nodes.add(exit);
                transitions.add(new CfgTransition(entryNode.id(),
                        sequences.get(entry.initialLabel().orElseThrow()).id(),
                        CfgTransition.Kind.ENTRY, entry.id()));
                for (Sequence sequence : orderedSequences) {
                    CfgNodeId from = sequences.get(sequence.label()).id();
                    // Contextual rules include orphans; they do not assert reachability from this Entry.
                    if (sequence.terminator() instanceof Operations.Return) {
                        transitions.add(new CfgTransition(from, exit.id(), CfgTransition.Kind.RETURN, entry.id()));
                    } else if (sequence.terminator() instanceof Operations.Jump jump) {
                        transitions.add(new CfgTransition(from, sequences.get(jump.destination()).id(),
                                CfgTransition.Kind.JUMP, entry.id()));
                    } else if (sequence.terminator() instanceof Operations.Invoke invoke && supportsInvoke(invoke)) {
                        for (var outcome : invoke.outcomes().known()) {
                        var normal = (Control.Normal) outcome;
                        transitions.add(new CfgTransition(from, sequences.get(normal.label()).id(),
                                CfgTransition.Kind.INVOKE_NORMAL, entry.id()));
                        }
                    } else if (sequence.terminator() instanceof Operations.Opaque opaque) {
                        var destinations = new java.util.HashSet<LabelId>();
                        for (var alternative : opaque.envelope().control().known()) {
                            var target = alternativeLabel(alternative);
                            if (target != null && destinations.add(target)) transitions.add(new CfgTransition(from, sequences.get(target).id(), CfgTransition.Kind.OPAQUE_JUMP, entry.id()));
                            else if (alternative instanceof Control.ReturnAlternative) transitions.add(new CfgTransition(from, exit.id(), CfgTransition.Kind.OPAQUE_RETURN, entry.id()));
                        }
                    } else if (sequence.terminator() instanceof Operations.Halt) {
                        transitions.add(new CfgTransition(from, halts.get(sequence.label()).id(),
                                CfgTransition.Kind.HALT, entry.id()));
                    } else if (sequence.terminator() instanceof Operations.Branch branch) {
                        // Structural alternatives remain distinct, including equal targets and literal predicates.
                        transitions.add(new CfgTransition(from, sequences.get(branch.trueDestination()).id(),
                                CfgTransition.Kind.BRANCH_TRUE, entry.id()));
                        transitions.add(new CfgTransition(from, sequences.get(branch.falseDestination()).id(),
                                CfgTransition.Kind.BRANCH_FALSE, entry.id()));
                    } else {
                        throw new IllegalArgumentException("projection requires a supported terminator");
                    }
                }
            }
        }
        return new CfgGraph(publication, nodes, transitions);
    }

    private static List<Unit> orderedUnits(Publication publication) {
        return publication.units().stream().sorted(Comparator.comparing(unit -> unit.id().localId())).toList();
    }

    private static List<Sequence> orderedSequences(Unit unit) {
        return unit.sequences().stream().sorted(Comparator.comparing(sequence -> sequence.label().localId())).toList();
    }
}
