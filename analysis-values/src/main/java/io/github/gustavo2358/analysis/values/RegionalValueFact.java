package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Memory;
import io.github.gustavo2358.air.model.Values.TextValue;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import io.github.gustavo2358.analysis.storage.StorageIndex;
import java.util.*;

/** Regional projection: physical locations and codecs remain distinct from logical Cell identity. */
public record RegionalValueFact(ProgramPoint point,ObjectId subject,List<Interpretation> interpretations,
        ValueFact.Reachability reachability,List<TextValue> candidates,Boolean modelValueRemainder,
        boolean sourceUnknownRemainder,boolean effectiveUnknownRemainder,List<PremiseId> premises,
        List<Id> evidence,List<OriginId> provenance,List<ValueFact.CandidateSupport> candidateSupports,
        List<String> modelReasons) implements TextValueFact {
    public record Interpretation(StorageIndex.ContextualLocation location,Optional<Memory.Codec> codec) { }
    public RegionalValueFact {
        Objects.requireNonNull(point);Objects.requireNonNull(subject);Objects.requireNonNull(reachability);
        interpretations=List.copyOf(interpretations);
        if(reachability==ValueFact.Reachability.REACHABLE) {
            candidates=List.copyOf(candidates);Objects.requireNonNull(modelValueRemainder);
            if(candidates.isEmpty()&&!modelValueRemainder)throw new IllegalArgumentException("empty closed reached value");
        } else if(candidates!=null||modelValueRemainder!=null)throw new IllegalArgumentException("unreachable has no value");
        premises=List.copyOf(premises);evidence=List.copyOf(evidence);provenance=List.copyOf(provenance);
        candidateSupports=List.copyOf(candidateSupports);modelReasons=List.copyOf(modelReasons);
        if(effectiveUnknownRemainder!=(Boolean.TRUE.equals(modelValueRemainder)||sourceUnknownRemainder))throw new IllegalArgumentException("effective remainder");
    }
}
