package io.github.gustavo2358.analysis.rd;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import io.github.gustavo2358.analysis.storage.StorageIndex;
import java.util.*;

/** Stable typed product. Contributed ranges are surviving physical footprints, not whole names. */
public record DefinitionFact(ProgramPoint point,Reachability reachability,List<Contribution> definitions,
                             Boolean unknownRemainder,boolean resolutionRemainder,boolean sourceUnknownRemainder,
                             List<PremiseId> premises,List<OriginId> origins,List<UncertaintyId> uncertainties) {
    public enum Reachability { REACHABLE, UNREACHABLE_IN_MODEL }
    public record Contribution(DefinitionEvent definition,List<StorageIndex.ContextualLocation> contributedRanges) {
        public Contribution { contributedRanges=List.copyOf(contributedRanges); }
    }
    public DefinitionFact { definitions=List.copyOf(definitions);premises=List.copyOf(premises);origins=List.copyOf(origins);uncertainties=List.copyOf(uncertainties); }
}
