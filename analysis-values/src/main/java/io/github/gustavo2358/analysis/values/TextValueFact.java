package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Values.TextValue;
import java.util.List;

/** Consumer projection shared by explicit scalar and regional products. */
public interface TextValueFact {
    ValueFact.Reachability reachability();
    List<TextValue> candidates();
    Boolean modelValueRemainder();
    boolean sourceUnknownRemainder();
    boolean effectiveUnknownRemainder();
    List<PremiseId> premises();
    List<Id> evidence();
    List<OriginId> provenance();
    List<ValueFact.CandidateSupport> candidateSupports();
}
