package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;

/** Detached dependency facts; 1.2.0 additionally represents semantic preparation limits. */
public record DependencyResult(PublicationId publication,SemanticVersion airVersion,List<DependencySiteFact> sites,
        List<Edge> edges,Map<String,Long> metrics,Evidence.InventoryStatus publicationInventory,
        List<Origins.Origin> origins,List<Origins.Artifact> artifacts,List<UncertaintyId> sourceUncertaintyRefs,List<String> analysisReasons,FileDependencyResult fileDependencies,SourceDependencyResult sourceDependencies,Optional<SourceQualifiedDependencyResult> sourceQualifiedDependencies,List<TargetResolver.Resolution> programDependencies) {
    public DependencyResult(PublicationId publication,SemanticVersion airVersion,List<DependencySiteFact> sites,List<Edge> edges,Map<String,Long> metrics,
            Evidence.InventoryStatus publicationInventory,List<Origins.Origin> origins,List<Origins.Artifact> artifacts,List<UncertaintyId> sourceUncertaintyRefs,List<String> analysisReasons,FileDependencyResult fileDependencies,SourceDependencyResult sourceDependencies,Optional<SourceQualifiedDependencyResult> sourceQualifiedDependencies) {
        this(publication,airVersion,sites,edges,metrics,publicationInventory,origins,artifacts,sourceUncertaintyRefs,analysisReasons,fileDependencies,sourceDependencies,sourceQualifiedDependencies,List.of());
    }
    public DependencyResult withProgramInventory(List<TargetResolver.Resolution> inventory,Map<String,Long> counts) {
        var reasons=new TreeSet<>(analysisReasons);
        for(var resolution:inventory)for(var reason:resolution.analysisReasons())if(reason.startsWith("CONDITIONAL_"))reasons.add(reason);
        if(counts.getOrDefault("conditionalSourceResourceLimits",0L)>0)reasons.add("CONDITIONAL_SOURCE_RESOURCE_LIMIT");
        return new DependencyResult(publication,airVersion,sites,edges,counts,publicationInventory,origins,artifacts,sourceUncertaintyRefs,List.copyOf(reasons),fileDependencies,sourceDependencies,sourceQualifiedDependencies,inventory);
    }
    public DependencyResult(PublicationId publication,SemanticVersion airVersion,List<DependencySiteFact> sites,List<Edge> edges,Map<String,Long> metrics,
            Evidence.InventoryStatus publicationInventory,List<Origins.Origin> origins,List<Origins.Artifact> artifacts,List<UncertaintyId> sourceUncertaintyRefs,List<String> analysisReasons,FileDependencyResult fileDependencies,SourceDependencyResult sourceDependencies) {
        this(publication,airVersion,sites,edges,metrics,publicationInventory,origins,artifacts,sourceUncertaintyRefs,analysisReasons,fileDependencies,sourceDependencies,Optional.empty());
    }
    public DependencyResult withSourceEvidence(io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies evidence) {
        return new DependencyResult(publication,airVersion,sites,edges,metrics,publicationInventory,origins,artifacts,sourceUncertaintyRefs,analysisReasons,fileDependencies,sourceDependencies,Optional.of(SourceQualifiedDependencyResult.admit(evidence,publication.localId())),programDependencies);
    }
    public DependencyResult(PublicationId publication,SemanticVersion airVersion,List<DependencySiteFact> sites,List<Edge> edges,Map<String,Long> metrics,
            Evidence.InventoryStatus publicationInventory,List<Origins.Origin> origins,List<Origins.Artifact> artifacts,List<UncertaintyId> sourceUncertaintyRefs,List<String> analysisReasons,FileDependencyResult fileDependencies) {
        this(publication,airVersion,sites,edges,metrics,publicationInventory,origins,artifacts,sourceUncertaintyRefs,analysisReasons,fileDependencies,SourceDependencyResult.unavailable());
    }
    public record Edge(UnitId caller,EntryId entry,OperationId site,DependencySiteFact.Candidate candidate,boolean openSite) { }
    public DependencyResult(PublicationId publication,SemanticVersion airVersion,List<DependencySiteFact> sites,List<Edge> edges,Map<String,Long> metrics,
            Evidence.InventoryStatus publicationInventory,List<Origins.Origin> origins,List<Origins.Artifact> artifacts,List<UncertaintyId> sourceUncertaintyRefs) {
        this(publication,airVersion,sites,edges,metrics,publicationInventory,origins,artifacts,sourceUncertaintyRefs,List.of());
    }
    public DependencyResult(PublicationId publication,SemanticVersion airVersion,List<DependencySiteFact> sites,List<Edge> edges,Map<String,Long> metrics,
            Evidence.InventoryStatus publicationInventory,List<Origins.Origin> origins,List<Origins.Artifact> artifacts,List<UncertaintyId> sourceUncertaintyRefs,List<String> analysisReasons) {
        this(publication,airVersion,sites,edges,metrics,publicationInventory,origins,artifacts,sourceUncertaintyRefs,analysisReasons,FileDependencyResult.unavailable());
    }
    public boolean partial() { return sourceDependencies.available()&&sourceDependencies.partial()||!analysisReasons.isEmpty()||sites.stream().anyMatch(s->s.analysisStatus()==DependencySiteFact.AnalysisStatus.PARTIAL); }
    public boolean structuralScope() { return sites.stream().anyMatch(s->s.reachability()==DependencySiteFact.Reachability.UNKNOWN)||sites.isEmpty()&&!analysisReasons.isEmpty(); }
    public DependencyResult {
        programDependencies=List.copyOf(programDependencies);
        Objects.requireNonNull(sourceQualifiedDependencies);sourceQualifiedDependencies.ifPresent(s->{if(s.evidence().air().size()!=1 || !s.evidence().air().getFirst().publication().equals(publication.localId()))throw new IllegalArgumentException("source/AIR identity mismatch");});
        Objects.requireNonNull(sourceDependencies);Objects.requireNonNull(fileDependencies);analysisReasons=List.copyOf(analysisReasons);if(analysisReasons.stream().anyMatch(String::isBlank))throw new IllegalArgumentException("empty analysis reason");
        Objects.requireNonNull(publication);Objects.requireNonNull(airVersion);sites=List.copyOf(sites);edges=List.copyOf(edges);
        metrics=Map.copyOf(metrics);Objects.requireNonNull(publicationInventory);origins=List.copyOf(origins);artifacts=List.copyOf(artifacts);sourceUncertaintyRefs=List.copyOf(sourceUncertaintyRefs);
    }
}
