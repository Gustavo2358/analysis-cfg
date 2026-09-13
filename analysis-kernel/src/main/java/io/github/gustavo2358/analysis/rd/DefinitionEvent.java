package io.github.gustavo2358.analysis.rd;

import io.github.gustavo2358.air.model.Control;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;

/** Finite producer event, independent of the number of paths or solver visits. */
public record DefinitionEvent(EntryId entry,Optional<OperationId> operation,Optional<OperandId> destination,
                             int slot,Optional<Control.OutcomeKey> outcome,StorageId storage,
                             Kind kind,boolean unknown,OriginId origin,List<PremiseId> premises,List<UncertaintyId> uncertainties,List<String> reasons) {
    public enum Kind { ENTRY_UNKNOWN, INITIAL_CONDITION, ENTRY_PRESERVE, ENTRY_UNINITIALIZED, ENTRY_PARAMETER, ENTRY_EXTERNAL, ASSIGN, COPY, UNKNOWN_WRITE }
    public DefinitionEvent { Objects.requireNonNull(entry);Objects.requireNonNull(operation);Objects.requireNonNull(destination);Objects.requireNonNull(outcome);Objects.requireNonNull(storage);Objects.requireNonNull(kind);Objects.requireNonNull(origin);premises=ordered(premises);uncertainties=ordered(uncertainties);reasons=reasons.stream().distinct().sorted().toList(); }
    private static <T extends Id> List<T> ordered(List<T> ids){return ids.stream().distinct().sorted(Comparator.comparing((T id)->id.publication().localId()).thenComparing(Id::localId)).toList();}
}
