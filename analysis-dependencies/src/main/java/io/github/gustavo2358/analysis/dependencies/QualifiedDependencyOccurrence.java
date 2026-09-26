package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import io.github.gustavo2358.air.model.Ids.OperationId;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.StatementId;

/** One occurrence, its target descriptor and independent qualification authorities. No value algorithm. */
public record QualifiedDependencyOccurrence(Optional<StatementId> source,String caller,String technology,
        String nameProfile,String targetKind,List<String> literals,List<String> qualifications,
        List<OperationId> executableOperations,boolean sourceValueRemainder) {
    public QualifiedDependencyOccurrence {
        Objects.requireNonNull(source);Objects.requireNonNull(caller);Objects.requireNonNull(technology);Objects.requireNonNull(nameProfile);
        if(!Set.of("LITERAL","COMPUTED","UNAVAILABLE").contains(targetKind))throw new IllegalArgumentException("target kind");
        literals=List.copyOf(literals);qualifications=List.copyOf(qualifications);executableOperations=List.copyOf(executableOperations);
    }
}
