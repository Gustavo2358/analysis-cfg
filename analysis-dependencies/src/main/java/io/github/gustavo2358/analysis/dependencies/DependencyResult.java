package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;

/** Detached dependency facts; an explicit native lease may borrow canonical metadata.
 * Such metadata expires with its checked SnapshotProgram. Ordinary callers still receive copies. */
public record DependencyResult(PublicationId publication,SemanticVersion airVersion,List<DependencySiteFact> sites,
        List<Edge> edges,Map<String,Long> metrics,Evidence.InventoryStatus publicationInventory,
        List<Origins.Origin> origins,List<Origins.Artifact> artifacts,List<UncertaintyId> sourceUncertaintyRefs,List<String> analysisReasons,FileDependencyResult fileDependencies,SourceDependencyResult sourceDependencies,Optional<SourceQualifiedDependencyResult> sourceQualifiedDependencies,List<TargetResolver.Resolution> programDependencies) {
    public DependencyResult(PublicationId publication,SemanticVersion airVersion,List<DependencySiteFact> sites,List<Edge> edges,Map<String,Long> metrics,
            Evidence.InventoryStatus publicationInventory,List<Origins.Origin> origins,List<Origins.Artifact> artifacts,List<UncertaintyId> sourceUncertaintyRefs,List<String> analysisReasons,FileDependencyResult fileDependencies,SourceDependencyResult sourceDependencies,Optional<SourceQualifiedDependencyResult> sourceQualifiedDependencies) {
        this(publication,airVersion,sites,edges,metrics,publicationInventory,origins,artifacts,sourceUncertaintyRefs,analysisReasons,fileDependencies,sourceDependencies,sourceQualifiedDependencies,List.of());
    }
    public DependencyResult withProgramInventory(List<TargetResolver.Resolution> inventory,Map<String,Long> counts) {
        var reasons=new TreeSet<>(analysisReasons);
        for(var resolution:inventory)for(var reason:resolution.analysisReasons())if(reason.startsWith("CONDITIONAL_")||reason.equals("SOURCE_CONTROL_UNAVAILABLE"))reasons.add(reason);
        if(counts.getOrDefault("conditionalSourceResourceLimits",0L)>0)reasons.add("CONDITIONAL_SOURCE_RESOURCE_LIMIT");
        return new DependencyResult(publication,airVersion,sites,edges,counts,publicationInventory,origins,artifacts,sourceUncertaintyRefs,List.copyOf(reasons),fileDependencies,sourceDependencies,sourceQualifiedDependencies,inventory);
    }
    public DependencyResult(PublicationId publication,SemanticVersion airVersion,List<DependencySiteFact> sites,List<Edge> edges,Map<String,Long> metrics,
            Evidence.InventoryStatus publicationInventory,List<Origins.Origin> origins,List<Origins.Artifact> artifacts,List<UncertaintyId> sourceUncertaintyRefs,List<String> analysisReasons,FileDependencyResult fileDependencies,SourceDependencyResult sourceDependencies) {
        this(publication,airVersion,sites,edges,metrics,publicationInventory,origins,artifacts,sourceUncertaintyRefs,analysisReasons,fileDependencies,sourceDependencies,Optional.empty());
    }
    public DependencyResult withSourceEvidence(io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies evidence) {
        return withSourceEvidence(SourceQualifiedDependencyResult.admit(evidence,publication.localId()));
    }
    /** Reuse a certificate already admitted by the input port; result construction rechecks AIR identity. */
    public DependencyResult withSourceEvidence(SourceQualifiedDependencyResult source) {
        Objects.requireNonNull(source);
        var reasons=new TreeSet<>(analysisReasons);
        if(source.nativeFiles().stream().anyMatch(SourceQualifiedDependencyResult.NativeFileResult::remainder))reasons.add("SOURCE_NATIVE_FILE_REMAINDER");
        return new DependencyResult(publication,airVersion,sites,edges,metrics,publicationInventory,origins,artifacts,sourceUncertaintyRefs,List.copyOf(reasons),fileDependencies,sourceDependencies,Optional.of(source),programDependencies);
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
    public boolean partial() { return sourceQualifiedDependencies.stream().anyMatch(s->s.nativeFiles().stream().anyMatch(SourceQualifiedDependencyResult.NativeFileResult::remainder))||sourceDependencies.available()&&sourceDependencies.partial()||!analysisReasons.isEmpty()||sites.stream().anyMatch(s->s.analysisStatus()==DependencySiteFact.AnalysisStatus.PARTIAL); }
    public boolean structuralScope() { return sites.stream().anyMatch(s->s.reachability()==DependencySiteFact.Reachability.UNKNOWN)||sites.isEmpty()&&analysisReasons.stream().anyMatch(r->!r.startsWith("CONDITIONAL_")&&!Set.of("SOURCE_CONTROL_UNAVAILABLE","SOURCE_NATIVE_FILE_REMAINDER").contains(r)); }
    public DependencyResult {
        programDependencies=List.copyOf(programDependencies);
        Objects.requireNonNull(sourceQualifiedDependencies);sourceQualifiedDependencies.ifPresent(s->{if(s.evidence().air().size()!=1 || !s.evidence().air().getFirst().publication().equals(publication.localId()))throw new IllegalArgumentException("source/AIR identity mismatch");});
        Objects.requireNonNull(sourceDependencies);Objects.requireNonNull(fileDependencies);analysisReasons=List.copyOf(analysisReasons);if(analysisReasons.stream().anyMatch(String::isBlank))throw new IllegalArgumentException("empty analysis reason");
        Objects.requireNonNull(publication);Objects.requireNonNull(airVersion);sites=List.copyOf(sites);edges=List.copyOf(edges);
        metrics=Map.copyOf(metrics);Objects.requireNonNull(publicationInventory);origins=metadataCopy(origins);artifacts=metadataCopy(artifacts);sourceUncertaintyRefs=metadataCopy(sourceUncertaintyRefs);
    }
    /** True only for the package's canonical, immutable native metadata lease. */
    public static boolean isBorrowedMetadata(List<?> values){return values instanceof MetadataList<?>;}
    private static <T> List<T> metadataCopy(List<T> values){return isBorrowedMetadata(values)?values:List.copyOf(values);}
    static <T> List<T> borrowedMetadata(long count,java.util.function.IntFunction<T> access,Runnable owner){
        return new MetadataList<>(Math.toIntExact(count),access,owner);
    }
    private static final class MetadataList<T> extends AbstractList<T> implements RandomAccess {
        private final int count;private final java.util.function.IntFunction<T> access;private final Runnable owner;
        MetadataList(int count,java.util.function.IntFunction<T> access,Runnable owner){
            if(count<0)throw new IllegalArgumentException("negative metadata inventory");
            this.count=count;this.access=Objects.requireNonNull(access);this.owner=Objects.requireNonNull(owner);owner.run();
        }
        @Override public int size(){owner.run();return count;}
        @Override public T get(int ordinal){owner.run();Objects.checkIndex(ordinal,count);return Objects.requireNonNull(access.apply(ordinal));}
        @Override public void clear(){throw new UnsupportedOperationException("immutable borrowed metadata");}
    }
}
