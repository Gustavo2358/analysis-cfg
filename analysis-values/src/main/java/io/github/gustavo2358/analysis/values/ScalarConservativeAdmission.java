package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.util.*;
import java.util.function.Consumer;

/** Shared global conservative-place/scope guards. Selection never hides an
 * unsupported explicit effect; all occurrence guards precede selected MUST
 * authority checks. Does not prepare transfers, candidates or observations. */
final class ScalarConservativeAdmission {
    private ScalarConservativeAdmission() { }
    static TextProfile.Location occurrence(OperandId id,AnalysisSession session,Map<ObjectId,TextProfile.Location> subjects) {
        var object=session.index().referencedObject(id);
        if(object==null||!subjects.containsKey(object.id()))throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PLACE");
        return subjects.get(object.id());
    }
    static TextProfile.Location exact(Place place,Map<ObjectId,TextProfile.Location> subjects) {
        if(!(place instanceof Places.ObjectPlace object)||!subjects.containsKey(object.object()))throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PLACE");
        return subjects.get(object.object());
    }
    static void scope(Scopes.MemoryScope scope,AnalysisSession session,Map<ObjectId,TextProfile.Location> subjects) {
        var pending=new ArrayDeque<Scopes.MemoryScope>();pending.add(scope);
        while(!pending.isEmpty()) {
            var current=pending.removeFirst();
            if(current instanceof Scopes.ObjectsMemory objects) {
                if(objects.objects().stream().anyMatch(object->!subjects.containsKey(object)))throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PLACE");
            } else if(current instanceof Scopes.VisibleMemory visible) {
                var unit=session.index().unit(visible.unit());
                for(var owner:session.index().store().units())for(var object:owner.objects()) {
                    boolean included=owner.id().equals(visible.unit())||unit.visibleObjects().contains(object.id())
                        ||visible.includingExternal()&&object.visibility()!=Memory.Visibility.PRIVATE;
                    if(included&&!subjects.containsKey(object.id()))throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PLACE");
                }
            } else if(current instanceof Scopes.MemoryUnion union)pending.addAll(union.members());
        }
    }
    /** Emits MUST locations only after every global place/scope check has passed.
     * Duplicate references need no new set: the caller may combine equal minima. */
    static void must(Operation operation,AnalysisSession session,Map<ObjectId,TextProfile.Location> subjects,Consumer<TextProfile.Location> selectedAuthority) {
        if(operation instanceof Operations.HavocMust havoc)selectedAuthority.accept(exact(havoc.destination(),subjects));
        else if(operation instanceof Operations.HavocMay havoc)scope(havoc.scope(),session,subjects);
        else if(operation instanceof Operations.Opaque opaque) {
            var memory=opaque.envelope().memory();
            if(memory.otherWrites() instanceof Scopes.WithinMemory within)scope(within.scope(),session,subjects);
            for(var id:memory.knownWrites())occurrence(id,session,subjects);
            for(var id:memory.mustOverwrite())occurrence(id,session,subjects);
            for(var id:memory.mustOverwrite())selectedAuthority.accept(occurrence(id,session,subjects));
        } else throw new IllegalArgumentException("not a conservative operation");
    }
}
