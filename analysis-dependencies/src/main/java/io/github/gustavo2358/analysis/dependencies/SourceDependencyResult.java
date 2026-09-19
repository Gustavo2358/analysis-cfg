package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import io.github.gustavo2358.air.model.Ids.*;

/** Nominal source dependencies, independent from graph reachability and values. */
public record SourceDependencyResult(boolean available,List<Dependency> dependencies,List<String> gapCodes,long occurrences) {
    public enum Kind { COPYBOOK, DCLGEN, SQL_INCLUDE, DB2_TABLE }
    public enum Resolution { RESOLVED, UNRESOLVED, CYCLIC, IO_ERROR, NOT_APPLICABLE }
    public enum Operation { NONE, SELECT, INSERT, UPDATE, DELETE, MERGE }
    public enum Access { NONE, READ, WRITE, READ_WRITE }
    public record Support(ResourceId occurrence,OriginId origin,ArtifactId sourceOwner,boolean transitive,
            Resolution resolution,String resolvedArtifact,String authority,Operation operation,Access access) {
        public Support(ResourceId occurrence,OriginId origin,ArtifactId sourceOwner,boolean transitive,Resolution resolution,String resolvedArtifact,String authority){this(occurrence,origin,sourceOwner,transitive,resolution,resolvedArtifact,authority,Operation.NONE,Access.NONE);}
        public Support {Objects.requireNonNull(operation);Objects.requireNonNull(access);Objects.requireNonNull(occurrence);Objects.requireNonNull(origin);Objects.requireNonNull(sourceOwner);Objects.requireNonNull(resolution);Objects.requireNonNull(resolvedArtifact);Objects.requireNonNull(authority);}
    }
    public record Dependency(UnitId program,Kind kind,String name,String qualification,List<Support> supports,boolean remainder) {
        public Dependency {Objects.requireNonNull(program);Objects.requireNonNull(kind);Objects.requireNonNull(name);Objects.requireNonNull(qualification);supports=List.copyOf(supports);if(supports.isEmpty())throw new IllegalArgumentException("Source dependency needs evidence");}
    }
    public SourceDependencyResult {dependencies=List.copyOf(dependencies);gapCodes=List.copyOf(gapCodes);}
    public boolean partial(){return !available||!gapCodes.isEmpty()||dependencies.stream().anyMatch(Dependency::remainder);}
    public static SourceDependencyResult unavailable(){return new SourceDependencyResult(false,List.of(),List.of("SOURCE_DEPENDENCIES_UNAVAILABLE"),0);}
}
