package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Values;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import io.github.gustavo2358.analysis.rd.DefinitionEvent;
import io.github.gustavo2358.analysis.storage.*;
import java.util.*;

/** Detached read alternatives and interval provenance. Abstract supports never certify a path. */
public record StorageValueFact(ProgramPoint point,StorageSubject subject,List<RegionalValueFact.Interpretation> interpretations,
        ValueFact.Reachability reachability,List<Values.TextValue> candidates,Boolean modelValueRemainder,
        boolean sourceUnknownRemainder,boolean effectiveUnknownRemainder,List<PremiseId> premises,List<Id> evidence,
        List<OriginId> provenance,List<ValueFact.CandidateSupport> candidateSupports,List<String> modelReasons,
        List<Alternative> alternatives) implements TextValueFact {
    public enum FragmentKind { KNOWN_BYTES, UNKNOWN_BYTES, LOGICAL_VALUE, UNKNOWN_LOGICAL, LOGICAL_CAPTURE }
    public record Producer(DefinitionEvent definition,StorageIndex.ContextualLocation contributedRange) {
        public Producer { Objects.requireNonNull(definition);Objects.requireNonNull(contributedRange); }
    }
    public record Capture(DefinitionEvent definition,ProgramPoint before,StorageIndex.ContextualLocation sourceRange,
            StorageIndex.ContextualLocation destinationRange,StorageIndex.ContextualLocation sourceContribution,
            StorageIndex.ContextualLocation destinationContribution) {
        public Capture { Objects.requireNonNull(definition);Objects.requireNonNull(before);Objects.requireNonNull(sourceRange);Objects.requireNonNull(destinationRange);Objects.requireNonNull(sourceContribution);Objects.requireNonNull(destinationContribution); }
    }
    public record SourceGap(StorageIndex.ContextualLocation affectedLocation,OriginId origin,List<UncertaintyId> uncertainties) {
        public SourceGap { Objects.requireNonNull(affectedLocation);Objects.requireNonNull(origin);uncertainties=List.copyOf(uncertainties); }
    }
    public record Fragment(StorageIndex.ContextualLocation location,FragmentKind kind,Optional<Values.BytesValue> bytes,
            Optional<Producer> producer,Optional<DefinitionEvent> unknownWriter,List<Capture> captures,List<SourceGap> sourceGaps,List<String> modelReasons) {
        public Fragment { Objects.requireNonNull(location);Objects.requireNonNull(kind);Objects.requireNonNull(bytes);Objects.requireNonNull(producer);Objects.requireNonNull(unknownWriter);captures=List.copyOf(captures);sourceGaps=List.copyOf(sourceGaps);modelReasons=List.copyOf(modelReasons); }
    }
    public record Alternative(RegionalValueFact.Interpretation interpretation,Optional<Values.TextValue> candidate,List<Fragment> fragments) {
        public Alternative { Objects.requireNonNull(interpretation);Objects.requireNonNull(candidate);fragments=List.copyOf(fragments); }
    }
    public StorageValueFact {
        Objects.requireNonNull(point);Objects.requireNonNull(subject);Objects.requireNonNull(reachability);interpretations=List.copyOf(interpretations);
        if(reachability==ValueFact.Reachability.REACHABLE) {
            candidates=List.copyOf(candidates);Objects.requireNonNull(modelValueRemainder);
            if(candidates.isEmpty()&&!modelValueRemainder)throw new IllegalArgumentException("empty closed reached value");
        } else if(candidates!=null||modelValueRemainder!=null||!alternatives.isEmpty())throw new IllegalArgumentException("unreachable has no read alternatives");
        premises=List.copyOf(premises);evidence=List.copyOf(evidence);provenance=List.copyOf(provenance);candidateSupports=List.copyOf(candidateSupports);modelReasons=List.copyOf(modelReasons);alternatives=List.copyOf(alternatives);
        if(effectiveUnknownRemainder!=(Boolean.TRUE.equals(modelValueRemainder)||sourceUnknownRemainder))throw new IllegalArgumentException("effective remainder");
    }
    public RegionalValueFact asObjectFact() {
        if(!(subject instanceof StorageSubject.NamedObject named))throw new IllegalArgumentException("physical range has no synthetic ObjectId");
        return new RegionalValueFact(point,named.object(),interpretations,reachability,candidates,modelValueRemainder,sourceUnknownRemainder,effectiveUnknownRemainder,premises,evidence,provenance,candidateSupports,modelReasons);
    }
}
