package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Control;
import io.github.gustavo2358.air.model.Ids.LabelId;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.model.Scopes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact Entry/Jump/Branch/Return/Halt rules for preflight-validated AIR, without reachability or physical fallthrough. */
public final class CoreCfgProjection {
    private CoreCfgProjection() { }

    /** Regional operations and the pure IBM1047 codec preserve sequential control after AIR preflight.
     * This role does not calculate storage, bytes or possible values. */
    public static boolean supportsControlCapability(Capabilities.Capability capability) {
        return Capabilities.LOCAL_CONTROL.equals(capability) || Capabilities.LOCAL_REENTRY_GUARD.equals(capability) || Capabilities.LOCAL_RESUME_ROUTES.equals(capability) || Capabilities.LOCAL_BOUNDARY_ROUTES.equals(capability) || Capabilities.LOCAL_UNWIND_ALL.equals(capability) || Capabilities.RESOURCE_BINDINGS.equals(capability) || Capabilities.TARGET_POSSIBILITIES.equals(capability) || Capabilities.MEMORY_REGIONS.equals(capability) || Capabilities.IBM1047.equals(capability) || Capabilities.ENTRY_POSSIBILITIES_V2.equals(capability) || Capabilities.ENTRY_POSSIBILITIES.equals(capability);
    }

    /** Default admission of the known subset; requires the same preflight as explicit policy admission. */
    public static List<CfgProjectionIssue> unsupported(Publication publication) {
        return unsupported(CfgProgram.resident(publication), ProjectionPolicy.KNOWN_SUBSET);
    }

    /** Enumerates every rejection; inventory policy never supplies missing semantic interpretation. */
    public static List<CfgProjectionIssue> unsupported(Publication publication, ProjectionPolicy policy) {
        return unsupported(CfgProgram.resident(publication), policy);
    }

    /** Same admission rules for resident and snapshot-backed programs. */
    public static List<CfgProjectionIssue> unsupported(CfgProgram program, ProjectionPolicy policy) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(policy, "policy");
        List<CfgProjectionIssue> issues = new ArrayList<>();
        if (program.requiredCapabilities().stream().anyMatch(c -> !supportsControlCapability(c)
                && !program.namePolicyExtensions().contains(c))) {
            issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.EXTENSION_SEMANTICS_OUTSIDE_SLICE,
                    program.source().publicationId()));
        }
        if (!policy.acceptsInventory(program.source().publicationInventory())) {
            issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.INCOMPLETE_INVENTORY,
                    program.source().publicationId()));
        }
        program.units(unit -> {
            if (unit.body() != io.github.gustavo2358.air.model.Unit.BodyAvailability.AVAILABLE
                    && policy != ProjectionPolicy.PARTIAL_ANALYSIS) {
                issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.BODY_UNAVAILABLE, unit.id()));
            }
            if (!policy.acceptsInventory(unit.inventory())) {
                issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.INCOMPLETE_INVENTORY, unit.id()));
            }
            unit.sequences(sequence -> {
                // All five sealed AIR Instruction variants continue inside their Sequence.
                // Operation identities preserve every occurrence and their explicit order.
                if (!supports(sequence.control()) && policy != ProjectionPolicy.PARTIAL_ANALYSIS) {
                    issues.add(new CfgProjectionIssue(CfgProjectionIssue.Code.UNSUPPORTED_TERMINATOR,
                            sequence.control().operation()));
                }
            });
        });
        return List.copyOf(issues);
    }

    private static boolean supports(CfgControl control) {
        return control instanceof CfgControl.Return || control instanceof CfgControl.Jump
                || control instanceof CfgControl.Branch || control instanceof CfgControl.Halt
                || control instanceof CfgControl.Invoke invoke && supportsInvoke(invoke)
                || control instanceof CfgControl.Opaque opaque && supportsOpaque(opaque)
                || LocalControlRules.local(control);
    }

    private static boolean supportsInvoke(CfgControl.Invoke invoke) {
        return invoke.alternatives().stream().allMatch(a -> a instanceof Control.Normal
                || a instanceof Control.Exceptional || a instanceof Control.AnyException
                || a instanceof Control.HaltAlternative)
                && supportedRemainder(invoke.remainder());
    }

    private static boolean supportsOpaque(CfgControl.Opaque opaque) {
        return opaque.alternatives().stream().allMatch(a -> a instanceof Control.JumpAlternative
                || a instanceof Control.Normal || a instanceof Control.ReturnAlternative
                || a instanceof Control.Exceptional || a instanceof Control.AnyException
                || a instanceof Control.HaltAlternative);
    }

    private static boolean supportedRemainder(Scopes.ControlBound remainder) {
        return remainder instanceof Scopes.NoControl
                || remainder instanceof Scopes.WithinControl bound
                && (bound.scope() instanceof Scopes.AllControl || bound.scope() instanceof Scopes.UnitControl
                || bound.scope() instanceof Scopes.LabelsControl || bound.scope() instanceof Scopes.ControlUnion);
    }

    /** Explicit normal/exceptional/halt alternatives with closed or bounded open control.
     * Open remainder remains on the original AIR; this projection enumerates known control only. */
    public static boolean supportsInvoke(Operations.Invoke invoke) {
        return invoke.outcomes().known().stream().allMatch(a->a instanceof Control.Normal||a instanceof Control.Exceptional||a instanceof Control.AnyException||a instanceof Control.HaltAlternative)
                && supportedRemainder(invoke.outcomes().remainder());
    }

    public static boolean supportsOpaque(Operations.Opaque opaque) {
        return opaque.envelope().control().known().stream().allMatch(a -> a instanceof Control.JumpAlternative
            || a instanceof Control.Normal || a instanceof Control.ReturnAlternative || a instanceof Control.Exceptional || a instanceof Control.AnyException || a instanceof Control.HaltAlternative);
    }
    public static java.util.List<Control.ControlAlternative> alternatives(io.github.gustavo2358.air.model.Terminator t) {
        if(LocalControlRules.invalid(t)!=null)return List.of(LocalControlRules.invalid(t));
        return t instanceof Operations.Invoke i?new java.util.ArrayList<>(i.outcomes().known()):t instanceof Operations.Opaque o?o.envelope().control().known():java.util.List.of();
    }
    public static LabelId exceptionLabel(Control.ControlAlternative a) {
        var destination=a instanceof Control.Exceptional e?e.destination():a instanceof Control.AnyException e?e.destination():null;
        return destination instanceof Control.Handler h?h.label():null;
    }
    public static boolean outside(Control.ControlAlternative a) {
        return a instanceof Control.HaltAlternative||a instanceof Control.Exceptional e&&e.destination() instanceof Control.Propagate
            ||a instanceof Control.AnyException e&&e.destination() instanceof Control.Propagate;
    }
    public static LabelId alternativeLabel(Control.ControlAlternative a) {
        return a instanceof Control.JumpAlternative j ? j.label() : a instanceof Control.Normal n ? n.label() : null;
    }
    public static boolean opaqueDestination(Operations.Opaque o, LabelId label) {
        return o.envelope().control().known().stream().anyMatch(a -> label.equals(alternativeLabel(a)));
    }

    /** Requires successful AirValidator preflight and an empty unsupported inventory. */
    public static CfgGraph project(Publication publication) { return project(publication, ProjectionPolicy.KNOWN_SUBSET); }

    public static CfgGraph project(CfgProgram program) { return project(program, ProjectionPolicy.KNOWN_SUBSET); }

    public static CfgGraph project(Publication publication, ProjectionPolicy policy) {
        return project(CfgProgram.resident(publication), policy);
    }

    /** The single CFG semantics for both resident and snapshot-backed programs. */
    public static CfgGraph project(CfgProgram program, ProjectionPolicy policy) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(policy, "policy");
        List<CfgNode> nodes = new ArrayList<>();
        var source=program.source();
        var table=new CfgTransitionTable.Builder(source);
        program.units(unit -> {
            Map<LabelId, CfgNode.SequenceNode> sequences = new HashMap<>();
            Map<LabelId, CfgNode.HaltExit> halts = new HashMap<>();
            var outsideNodes=new HashMap<LabelId,java.util.List<CfgNode.OutcomeExit>>();
            int firstSequence=nodes.size();
            unit.sequences(sequence -> {
                CfgNode.SequenceNode node = new CfgNode.SequenceNode(
                        new CfgNodeId(source.publicationId(), nodes.size()), sequence.label(),
                        sequence.operations(), sequence.control());
                sequences.put(sequence.label(), node);
                nodes.add(node);
                var exits=new ArrayList<CfgNode.OutcomeExit>();
                for(var alternative:CfgControl.alternatives(node.control()).stream()
                        .filter(CoreCfgProjection::outside).distinct().toList()) {
                    var end=new CfgNode.OutcomeExit(new CfgNodeId(source.publicationId(),nodes.size()),
                            node.control(),(Control.InvocationAlternative)alternative);
                    nodes.add(end);exits.add(end);
                }
                if(!exits.isEmpty())outsideNodes.put(sequence.label(),exits);
                if (node.control() instanceof CfgControl.Halt halt) {
                    CfgNode.HaltExit termination = new CfgNode.HaltExit(
                            new CfgNodeId(source.publicationId(), nodes.size()), halt.operation(), halt.haltKind());
                    nodes.add(termination);
                    halts.put(sequence.label(), termination);
                }
            });
            int sequenceEnd=nodes.size();
            var entryEdges=new ArrayList<CfgTransition>();var normalExits=new ArrayList<CfgNodeId>();
            var representative=new io.github.gustavo2358.air.model.Ids.EntryId[1];
            unit.entries(entry -> {
                if (entry.initialLabel().isEmpty() && policy == ProjectionPolicy.PARTIAL_ANALYSIS) return;
                CfgNode.EntryNode entryNode = new CfgNode.EntryNode(
                        new CfgNodeId(source.publicationId(), nodes.size()), entry.id(), entry.initialLabel());
                nodes.add(entryNode);
                CfgNode.NormalExit exit = new CfgNode.NormalExit(
                        new CfgNodeId(source.publicationId(), nodes.size()), source.publicationId(), unit.id(), entry.id());
                nodes.add(exit);
                entryEdges.add(new CfgTransition(entryNode.id(),sequences.get(entry.initialLabel().orElseThrow()).id(),CfgTransition.Kind.ENTRY,entry.id()));
                normalExits.add(exit.id());if(representative[0]==null)representative[0]=entry.id();
            });
            var transitions=new ArrayList<CfgTransition>();
            if(representative[0]!=null) {
                var entry=representative[0];var normalExit=normalExits.getFirst();
                for (int ordinal=firstSequence;ordinal<sequenceEnd;ordinal++) {
                    if(!(nodes.get(ordinal) instanceof CfgNode.SequenceNode sequence))continue;
                    CfgNodeId from = sequence.id();var control=sequence.control();
                    var exceptionalLabels=new java.util.HashSet<LabelId>();
                    for(var alternative:CfgControl.alternatives(control)) {
                        var handler=exceptionLabel(alternative);
                        if(handler!=null&&exceptionalLabels.add(handler))transitions.add(new CfgTransition(from,sequences.get(handler).id(),CfgTransition.Kind.EXCEPTION,entry));
                    }
                    if(!LocalControlRules.local(control))for(var end:outsideNodes.getOrDefault(sequence.label(),List.of()))
                        transitions.add(new CfgTransition(from,end.id(),CfgTransition.Kind.CONTROL_EXIT,entry));
                    // Contextual rules include orphans; they do not assert reachability from this Entry.
                    if (control instanceof CfgControl.Return) {
                        transitions.add(new CfgTransition(from, normalExit, CfgTransition.Kind.RETURN, entry));
                    } else if (control instanceof CfgControl.Jump jump) {
                        transitions.add(new CfgTransition(from, sequences.get(jump.destination()).id(),
                                CfgTransition.Kind.JUMP, entry));
                    } else if (control instanceof CfgControl.Invoke invoke) {
                        for (var outcome : invoke.alternatives()) {
                        if (!(outcome instanceof Control.Normal normal)) continue;
                        transitions.add(new CfgTransition(from, sequences.get(normal.label()).id(),
                                CfgTransition.Kind.INVOKE_NORMAL, entry));
                        }
                    } else if (control instanceof CfgControl.Opaque opaque) {
                        var destinations = new java.util.HashSet<LabelId>();
                        for (var alternative : opaque.alternatives()) {
                            var target = alternativeLabel(alternative);
                            if (target != null && destinations.add(target)) transitions.add(new CfgTransition(from, sequences.get(target).id(), CfgTransition.Kind.OPAQUE_JUMP, entry));
                            else if (alternative instanceof Control.ReturnAlternative) transitions.add(new CfgTransition(from, normalExit, CfgTransition.Kind.OPAQUE_RETURN, entry));
                        }
                    } else if (control instanceof CfgControl.Halt) {
                        transitions.add(new CfgTransition(from, halts.get(sequence.label()).id(),
                                CfgTransition.Kind.HALT, entry));
                    } else if (control instanceof CfgControl.Branch branch) {
                        // Structural alternatives remain distinct, including equal targets and literal predicates.
                        transitions.add(new CfgTransition(from, sequences.get(branch.trueDestination()).id(),
                                CfgTransition.Kind.BRANCH_TRUE, entry));
                        transitions.add(new CfgTransition(from, sequences.get(branch.falseDestination()).id(),
                                CfgTransition.Kind.BRANCH_FALSE, entry));
                    } else if (!LocalControlRules.local(control) && policy != ProjectionPolicy.PARTIAL_ANALYSIS) {
                        throw new IllegalArgumentException("projection requires a supported terminator");
                    }
                }
            }
            table.add(unit.id(),entryEdges,normalExits,transitions);
        });
        return program instanceof CfgProgram.Resident resident
                ? new CfgGraph(resident.publication(), source, nodes, table.build())
                : CfgGraph.projected(source, nodes, table.build());
    }
}
