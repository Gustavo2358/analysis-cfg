package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;

/** Separate dependency-result 1.0.0; no W5 result format change. */
public record DependencyResult(PublicationId publication,SemanticVersion airVersion,List<DependencySiteFact> sites,
        List<Edge> edges,Map<String,Long> metrics,Evidence.InventoryStatus publicationInventory,
        List<Origins.Origin> origins,List<Origins.Artifact> artifacts,List<UncertaintyId> sourceUncertaintyRefs) {
    public record Edge(UnitId caller,EntryId entry,OperationId site,DependencySiteFact.Candidate candidate,boolean openSite) { }
    public DependencyResult {
        Objects.requireNonNull(publication);Objects.requireNonNull(airVersion);sites=List.copyOf(sites);edges=List.copyOf(edges);
        metrics=Map.copyOf(metrics);Objects.requireNonNull(publicationInventory);origins=List.copyOf(origins);artifacts=List.copyOf(artifacts);sourceUncertaintyRefs=List.copyOf(sourceUncertaintyRefs);
    }
}
