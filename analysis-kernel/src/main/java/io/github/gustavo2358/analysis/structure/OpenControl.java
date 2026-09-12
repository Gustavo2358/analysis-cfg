package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.LabelId;
import io.github.gustavo2358.analysis.cfg.domain.CfgNode;

/** AIR scope membership only. Open edges are enumerated lazily, never stored as a dense graph. */
final class OpenControl {
    // Independent admission of the original AIR; never invoke/reuse the CFG builder.
    static boolean supportsInvoke(Operations.Invoke invoke) {
        return invoke.outcomes().known().stream().allMatch(Control.Normal.class::isInstance)
            && (invoke.outcomes().remainder() instanceof Scopes.NoControl
                || invoke.outcomes().remainder() instanceof Scopes.WithinControl w
                    && (w.scope() instanceof Scopes.AllControl || w.scope() instanceof Scopes.UnitControl || w.scope() instanceof Scopes.LabelsControl));
    }
    static boolean supportsOpaque(Operations.Opaque opaque) {
        return opaque.envelope().control().known().stream().allMatch(a -> a instanceof Control.JumpAlternative || a instanceof Control.Normal || a instanceof Control.ReturnAlternative);
    }
    static LabelId alternativeLabel(Control.ControlAlternative a) {
        return a instanceof Control.JumpAlternative j ? j.label() : a instanceof Control.Normal n ? n.label() : null;
    }
    static boolean opaqueDestination(Operations.Opaque o,LabelId label) {
        return o.envelope().control().known().stream().anyMatch(a -> label.equals(alternativeLabel(a)));
    }

    private OpenControl() { }
    static Scopes.ControlBound bound(ProgramIndex.Node node) {
        if(node.source() instanceof CfgNode.SequenceNode s) {
            if(s.source().terminator() instanceof Operations.Opaque o)return o.envelope().control().remainder();
            if(s.source().terminator() instanceof Operations.Invoke i && i.outcomes().known().isEmpty())return i.outcomes().remainder();
        }
        return Scopes.NoControl.INSTANCE;
    }
    static boolean allows(ProgramIndex.Node source,ProgramIndex.Node target,Entries.Entry entry) {
        if(!source.owner().id().equals(entry.id().unit())||!target.owner().id().equals(entry.id().unit()))return false;
        return bound(source) instanceof Scopes.WithinControl w && contains(w.scope(),target,entry);
    }
    private static boolean contains(Scopes.ControlScope scope,ProgramIndex.Node target,Entries.Entry entry) {
        if(target.source() instanceof CfgNode.EntryNode)return false;
        if(target.source() instanceof CfgNode.NormalExit exit&&!exit.entryId().equals(entry.id()))return false;
        if(scope instanceof Scopes.AllControl)return true;
        if(scope instanceof Scopes.LabelsControl labels)return target.source() instanceof CfgNode.SequenceNode s&&labels.labels().contains(s.source().label());
        if(scope instanceof Scopes.UnitControl u)return u.unit().equals(target.owner().id())
            &&(target.source() instanceof CfgNode.SequenceNode&&u.labels()||target.source() instanceof CfgNode.NormalExit&&u.normalExit()
                ||target.source() instanceof CfgNode.HaltExit&&u.halt());
        return ((Scopes.ControlUnion)scope).members().stream().anyMatch(s->contains(s,target,entry));
    }
}
