package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Interactions;
import io.github.gustavo2358.air.model.Scopes;
import java.util.*;
import io.github.gustavo2358.air.model.Ids.*;

/** Generic foreign may-write interpretation. No target, language or resource-name semantics. */
final class ForeignEffectTransfer {
    private final List<TextProfile.Location> affected;
    private ForeignEffectTransfer(List<TextProfile.Location> affected) { this.affected=affected; /* Prepared once as an immutable cell index by TextProfile. */ }
    static ForeignEffectTransfer prepare(Interactions.EffectBound bound,List<TextProfile.Location> cells,
            Map<ObjectId,TextProfile.Location> subjects) {
        if(!bound.perOutcome().isEmpty()||!bound.otherwise().mustOverwrite().isEmpty()
                ||!supported(bound.otherwise().writes()))
            throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");
        // A read bound cannot change a stored TEXT value; only writes select affected cells.
        var scope=bound.otherwise().writes();
        if(scope instanceof Scopes.NoMemory)return new ForeignEffectTransfer(List.of());
        var selected=new LinkedHashSet<TextProfile.Location>();
        select(((Scopes.WithinMemory)scope).scope(),cells,subjects,selected);
        return new ForeignEffectTransfer(List.copyOf(selected));
    }
    private static boolean supported(Scopes.MemoryBound bound) {
        return bound instanceof Scopes.NoMemory || bound instanceof Scopes.WithinMemory within
                && supportedScope(within.scope());
    }
    private static boolean supportedScope(Scopes.MemoryScope scope) {
        return scope instanceof Scopes.AllMemory||scope instanceof Scopes.ObjectsMemory
            ||scope instanceof Scopes.StorageMemory||scope instanceof Scopes.MemoryUnion union
                &&union.members().stream().allMatch(ForeignEffectTransfer::supportedScope);
    }
    private static void select(Scopes.MemoryScope scope,List<TextProfile.Location> cells,
            Map<ObjectId,TextProfile.Location> subjects,Set<TextProfile.Location> selected) {
        if(scope instanceof Scopes.AllMemory)selected.addAll(cells);
        else if(scope instanceof Scopes.ObjectsMemory objects)for(var object:objects.objects()) {
            var location=subjects.get(object);
            if(location==null)throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PLACE");
            if(cells.contains(location))selected.add(location);
        }
        else if(scope instanceof Scopes.StorageMemory storage)for(var location:cells)
            if(storage.storage().contains(location.cell().header().id()))selected.add(location);
        else if(scope instanceof Scopes.MemoryUnion union)for(var member:union.members())select(member,cells,subjects,selected);
    }
    PossibleValuesState apply(PossibleValuesState state,ValuesWork work) {
        if(!state.isReached())return state;
        var result=state;
        for(var location:affected) {
            // May-write includes preservation. Retain each candidate and its own producers.
            result=result.widenUnknown(location.ordinal(),work);
        }
        return result;
    }
}
