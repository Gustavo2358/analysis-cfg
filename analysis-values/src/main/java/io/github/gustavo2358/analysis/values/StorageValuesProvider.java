package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.AnalysisProvider;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.PointQuery;
import io.github.gustavo2358.analysis.solver.Direction;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.util.*;
import io.github.gustavo2358.analysis.storage.StorageSubject;

/** Explicit regional provider; one execution and shared replay per selected Entry. */
public final class StorageValuesProvider implements AnalysisProvider<StorageSubject,StorageValueFact> {
    public static final String IMPLEMENTATION = "StorageValues";
    public static final String VERSION = "1";
    public static final String PRECISION = "FINITE_CORRELATED_STORAGE_IMAGES";
    public static final String PROJECTION = "StorageValueFact@1";
    public static AnalysisKey key(EntryId entry) {
        return new AnalysisKey(IMPLEMENTATION,VERSION,RegionalValuesAnalysis.PROFILE,Direction.FORWARD,PRECISION,Map.of(),entry);
    }
    public static ObservationBatchId<StorageSubject,StorageValueFact> batch(String id, AnalysisKey key) {
        return new ObservationBatchId<>(id,key,PROJECTION,StorageSubject.class,StorageValueFact.class);
    }
    public String implementation() { return IMPLEMENTATION; }
    public String version() { return VERSION; }
    public Set<String> semanticOptionNames() { return Set.of(); }
    public boolean supports(AnalysisKey key) {
        return key.implementation().equals(IMPLEMENTATION) && key.version().equals(VERSION)
            && key.profile().equals(RegionalValuesAnalysis.PROFILE) && key.direction() == Direction.FORWARD
            && key.precisionPolicy().equals(PRECISION) && key.options().isEmpty();
    }
    public String projection() { return PROJECTION; }
    public Class<StorageSubject> subjectType() { return StorageSubject.class; }
    public Class<StorageValueFact> factType() { return StorageValueFact.class; }
    public Comparator<StorageSubject> subjectOrder() { return StorageSubject.ORDER; }
    public Prepared<StorageSubject,StorageValueFact> prepare(AnalysisSession owner, AnalysisKey key) {
        if (!supports(key)) throw new IllegalArgumentException("unsupported RegionalValues key");
        return RegionalProviderSupport.prepare(owner,key,RegionalValuesAnalysis.Execution::observeStorage);
    }
}
