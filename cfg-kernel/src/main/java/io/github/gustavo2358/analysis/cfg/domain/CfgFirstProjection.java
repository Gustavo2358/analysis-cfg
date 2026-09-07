package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Entries;
import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids.LabelId;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.model.Sequence;
import io.github.gustavo2358.air.model.Unit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exact Entry/Return rules for preflight-validated AIR, without reachability or physical fallthrough. */
public final class CfgFirstProjection {
    private CfgFirstProjection() { }

    /** Enumerates every unsupported occurrence. Capability registration alone supplies no interpretation. */
    public static List<CfgProjectionIssue> unsupported(Publication publication) {
        List<CfgProjectionIssue> issues = new ArrayList<>();
        if (!publication.capabilities().required().isEmpty()) {
            issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.EXTENSION_SEMANTICS_OUTSIDE_SLICE,
                    publication.id()));
        }
        if (publication.coverage().inventory() != Evidence.InventoryStatus.COMPLETE) {
            issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.INCOMPLETE_INVENTORY, publication.id()));
        }
        for (Unit unit : orderedUnits(publication)) {
            if (unit.body() != Unit.BodyAvailability.AVAILABLE) {
                issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.BODY_UNAVAILABLE, unit.id()));
            }
            if (unit.coverage().inventory() != Evidence.InventoryStatus.COMPLETE) {
                issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.INCOMPLETE_INVENTORY, unit.id()));
            }
            for (Sequence sequence : orderedSequences(unit)) {
                if (!sequence.instructions().isEmpty()) {
                    issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.INSTRUCTIONS_OUTSIDE_SLICE,
                            sequence.label()));
                }
                if (!(sequence.terminator() instanceof Operations.Return)) {
                    issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.UNSUPPORTED_TERMINATOR,
                            sequence.terminator().header().id()));
                }
            }
        }
        return List.copyOf(issues);
    }

    /** Requires successful AirValidator preflight and an empty unsupported inventory. */
    public static CfgGraph project(Publication publication) {
        List<CfgNode> nodes = new ArrayList<>();
        List<CfgTransition> transitions = new ArrayList<>();
        for (Unit unit : orderedUnits(publication)) {
            Map<LabelId, CfgNode.SequenceNode> sequences = new HashMap<>();
            List<Sequence> orderedSequences = orderedSequences(unit);
            for (Sequence sequence : orderedSequences) {
                CfgNode.SequenceNode node = new CfgNode.SequenceNode(
                        new CfgNodeId(publication.id(), nodes.size()), sequence);
                sequences.put(sequence.label(), node);
                nodes.add(node);
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
                    // The activation Entry selects its own normal exit, including shared/orphan Returns.
                    transitions.add(new CfgTransition(sequences.get(sequence.label()).id(), exit.id(),
                            CfgTransition.Kind.RETURN, entry.id()));
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
