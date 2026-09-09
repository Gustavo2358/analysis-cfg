package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Values.TextValue;
import java.util.List;
import java.util.Objects;

/** Facts are detached from solver roots/universe. Exactness is conditional on the admitted model. */
public record ValueFact(StorageId cell,Reachability reachability,List<TextValue> candidates,Boolean modelValueRemainder,
                        boolean sourceUnknownRemainder,boolean effectiveUnknownRemainder,List<PremiseId> premises,
                        List<Id> evidence) {
    public enum Reachability { REACHABLE, UNREACHABLE_IN_MODEL }
    public ValueFact {
        Objects.requireNonNull(cell);Objects.requireNonNull(reachability);
        if(reachability==Reachability.REACHABLE) {
            candidates=List.copyOf(candidates);Objects.requireNonNull(modelValueRemainder);
            if(candidates.isEmpty()&&!modelValueRemainder)throw new IllegalArgumentException("empty closed reached value");
        } else if(candidates!=null||modelValueRemainder!=null)throw new IllegalArgumentException("unreachable has no value");
        premises=List.copyOf(premises);evidence=List.copyOf(evidence);
        if(effectiveUnknownRemainder!=(Boolean.TRUE.equals(modelValueRemainder)||sourceUnknownRemainder))throw new IllegalArgumentException("effective remainder");
    }
}
