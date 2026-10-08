package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.LabelId;
import io.github.gustavo2358.analysis.cfg.domain.CfgNode;
import io.github.gustavo2358.analysis.cfg.domain.LocalControlRules;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;

/** AIR scope membership only. Open edges are enumerated lazily, never stored as a dense graph. */
final class OpenControl {
    // Independent admission of the original AIR; never invoke/reuse the CFG builder.
    static boolean supportsInvoke(Operations.Invoke invoke) {
        return invoke.outcomes().known().stream().allMatch(a->a instanceof Control.Normal||a instanceof Control.Exceptional||a instanceof Control.AnyException||a instanceof Control.HaltAlternative)
            && (invoke.outcomes().remainder() instanceof Scopes.NoControl
                || invoke.outcomes().remainder() instanceof Scopes.WithinControl w
                    && (w.scope() instanceof Scopes.AllControl || w.scope() instanceof Scopes.UnitControl || w.scope() instanceof Scopes.LabelsControl || w.scope() instanceof Scopes.ControlUnion));
    }
    static boolean supportsOpaque(Operations.Opaque opaque) {
        return opaque.envelope().control().known().stream().allMatch(a -> a instanceof Control.JumpAlternative || a instanceof Control.Normal || a instanceof Control.ReturnAlternative || a instanceof Control.Exceptional || a instanceof Control.AnyException || a instanceof Control.HaltAlternative);
    }
    static java.util.List<Control.ControlAlternative> alternatives(io.github.gustavo2358.air.model.Terminator t) {
        if(t instanceof Operations.LocalResume || t instanceof Operations.LocalBoundary b&&b.resumeKey().isPresent())return java.util.List.of(new Control.Exceptional("invalid_local_return",Control.Propagate.INSTANCE));
        if(t instanceof Operations.LocalUnwind)return java.util.List.of(new Control.Exceptional("invalid_local_unwind",Control.Propagate.INSTANCE));
        return t instanceof Operations.Invoke i?new java.util.ArrayList<>(i.outcomes().known()):t instanceof Operations.Opaque o?o.envelope().control().known():java.util.List.of();
    }
    static LabelId exceptionLabel(Control.ControlAlternative a) {
        var destination=a instanceof Control.Exceptional e?e.destination():a instanceof Control.AnyException e?e.destination():null;
        return destination instanceof Control.Handler h?h.label():null;
    }
    static boolean outside(Control.ControlAlternative a) {
        return a instanceof Control.HaltAlternative||a instanceof Control.Exceptional e&&e.destination() instanceof Control.Propagate
            ||a instanceof Control.AnyException e&&e.destination() instanceof Control.Propagate;
    }
    static LabelId alternativeLabel(Control.ControlAlternative a) {
        return a instanceof Control.JumpAlternative j ? j.label() : a instanceof Control.Normal n ? n.label() : null;
    }
    static boolean opaqueDestination(Operations.Opaque o,LabelId label) {
        return o.envelope().control().known().stream().anyMatch(a -> label.equals(alternativeLabel(a)));
    }

    private OpenControl() { }
    static boolean partial(ProgramIndex.Node node, ProjectionPolicy policy) {
        if (policy != ProjectionPolicy.PARTIAL_ANALYSIS || !(node.source() instanceof CfgNode.SequenceNode s)) return false;
        var term=s.terminator();
        return !(LocalControlRules.local(term) || term instanceof Operations.Return || term instanceof Operations.Jump || term instanceof Operations.Branch || term instanceof Operations.Halt
            || term instanceof Operations.Invoke i && supportsInvoke(i) || term instanceof Operations.Opaque o && supportsOpaque(o));
    }
    static Scopes.ControlBound bound(ProgramIndex.Node node, ProjectionPolicy policy) {
        if (partial(node,policy)) return new Scopes.WithinControl(new Scopes.UnitControl(node.owner().id(),true,true,true,true,true,true));
        if(node.source() instanceof CfgNode.SequenceNode s) {
            if(s.terminator() instanceof Operations.Opaque o)return o.envelope().control().remainder();
            if(s.terminator() instanceof Operations.Invoke i)return i.outcomes().remainder();
        }
        return Scopes.NoControl.INSTANCE;
    }
    static boolean allows(ProgramIndex.Node source,ProgramIndex.Node target,Entries.Entry entry, ProjectionPolicy policy) {
        if(!source.owner().id().equals(entry.id().unit())||!target.owner().id().equals(entry.id().unit()))return false;
        return bound(source,policy) instanceof Scopes.WithinControl w && contains(w.scope(),target,entry);
    }
    private static boolean contains(Scopes.ControlScope scope,ProgramIndex.Node target,Entries.Entry entry) {
        if(target.source() instanceof CfgNode.EntryNode)return false;
        if(target.source() instanceof CfgNode.NormalExit exit&&!exit.entryId().equals(entry.id()))return false;
        if(scope instanceof Scopes.AllControl)return true;
        if(scope instanceof Scopes.LabelsControl labels)return target.source() instanceof CfgNode.SequenceNode s&&labels.labels().contains(s.label());
        if(scope instanceof Scopes.UnitControl u)return u.unit().equals(target.owner().id())
            &&(target.source() instanceof CfgNode.SequenceNode&&u.labels()||target.source() instanceof CfgNode.NormalExit&&u.normalExit()
                ||target.source() instanceof CfgNode.HaltExit&&u.halt()
                ||target.source() instanceof CfgNode.OutcomeExit e&&(e.outcome() instanceof Control.HaltAlternative?u.halt():u.exceptionalExit()));
        return ((Scopes.ControlUnion)scope).members().stream().anyMatch(s->contains(s,target,entry));
    }
}
