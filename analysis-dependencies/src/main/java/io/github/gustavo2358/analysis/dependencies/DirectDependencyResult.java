package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Origins;
import java.util.List;
import java.util.Objects;

/** Detached result of the snapshot-native dependency path. */
public record DirectDependencyResult(PublicationId publication,Evidence.InventoryStatus coverage,List<Origins.Origin> origins,List<Origins.Artifact> artifacts,List<Site> sites) {
    public record Support(OperationId producer,OriginId origin,List<PremiseId> premises) {public Support{Objects.requireNonNull(producer);Objects.requireNonNull(origin);premises=List.copyOf(premises);}}
    public record Candidate(String referenceName,String rawValue,List<Support> supports) {public Candidate{Objects.requireNonNull(referenceName);Objects.requireNonNull(rawValue);supports=List.copyOf(supports);}}
    public record Site(UnitId caller,EntryId entry,LabelId sequence,OperationId operation,OriginId siteOrigin,
            OriginId targetOrigin,Evidence.CoverageStatus coverage,String namespace,ObjectId subject,List<Candidate> candidates,boolean unknownRemainder) {
        public Site{Objects.requireNonNull(caller);Objects.requireNonNull(entry);Objects.requireNonNull(sequence);Objects.requireNonNull(operation);Objects.requireNonNull(siteOrigin);Objects.requireNonNull(targetOrigin);Objects.requireNonNull(coverage);Objects.requireNonNull(namespace);Objects.requireNonNull(subject);candidates=List.copyOf(candidates);}
    }
    public DirectDependencyResult{Objects.requireNonNull(publication);Objects.requireNonNull(coverage);origins=List.copyOf(origins);artifacts=List.copyOf(artifacts);sites=List.copyOf(sites);}
}
