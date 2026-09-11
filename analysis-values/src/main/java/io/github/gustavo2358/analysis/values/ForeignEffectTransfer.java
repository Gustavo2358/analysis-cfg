package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Interactions;
import io.github.gustavo2358.air.model.Scopes;
import java.util.List;

/** Generic foreign may-write interpretation. No target, language or resource-name semantics. */
final class ForeignEffectTransfer {
    private final List<TextProfile.Location> affected;
    private ForeignEffectTransfer(List<TextProfile.Location> affected) { this.affected=affected; /* Prepared once as an immutable cell index by TextProfile. */ }
    static ForeignEffectTransfer prepare(Interactions.EffectBound bound,List<TextProfile.Location> cells) {
        if(!bound.perOutcome().isEmpty()||!bound.otherwise().mustOverwrite().isEmpty()
                ||!supported(bound.otherwise().reads())||!supported(bound.otherwise().writes()))
            throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");
        return new ForeignEffectTransfer(bound.otherwise().writes() instanceof Scopes.NoMemory?List.of():cells);
    }
    private static boolean supported(Scopes.MemoryBound bound) {
        return bound instanceof Scopes.NoMemory || bound instanceof Scopes.WithinMemory within
                && within.scope() instanceof Scopes.AllMemory;
    }
    PossibleValuesState apply(PossibleValuesState state,ValuesWork work) {
        if(!state.isReached())return state;
        var result=state;
        for(var location:affected) {
            var previous=result.value(location.ordinal(),work);
            // May-write includes preservation. Retain each candidate and its own producers.
            result=result.assign(location.ordinal(),previous.withOpen(work),work);
        }
        return result;
    }
}
