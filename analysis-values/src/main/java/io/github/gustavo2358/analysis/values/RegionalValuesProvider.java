package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.AnalysisProvider;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.PointQuery;
import io.github.gustavo2358.analysis.solver.Direction;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.util.*;

/** Explicit regional provider; one execution and shared replay per selected Entry. */
public final class RegionalValuesProvider implements AnalysisProvider<ObjectId,RegionalValueFact> {
    public static final String IMPLEMENTATION = "RegionalValues";
    public static final String VERSION = "2";
    public static final String PRECISION = "FINITE_CORRELATED_STORAGE_IMAGES";
    public static final String PROJECTION = "RegionalValueFact@1";
    public static AnalysisKey key(EntryId entry) { return key(entry,StorageAnalysisMode.LOGICAL_ONLY); }
    public static AnalysisKey key(EntryId entry,StorageAnalysisMode mode) {
        return new AnalysisKey(IMPLEMENTATION,VERSION,mode.profile(),Direction.FORWARD,mode.precision(),Map.of(),entry);
    }
    public static ObservationBatchId<ObjectId,RegionalValueFact> batch(String id, AnalysisKey key) {
        return new ObservationBatchId<>(id,key,PROJECTION,ObjectId.class,RegionalValueFact.class);
    }
    public String implementation() { return IMPLEMENTATION; }
    public String version() { return VERSION; }
    public Set<String> semanticOptionNames() { return Set.of(); }
    public boolean supports(AnalysisKey key) {
        return key.implementation().equals(IMPLEMENTATION) && key.version().equals(VERSION)
            && Arrays.stream(StorageAnalysisMode.values()).anyMatch(mode->key.profile().equals(mode.profile())&&key.precisionPolicy().equals(mode.precision()))
            && key.direction() == Direction.FORWARD && key.options().isEmpty();
    }
    public String projection() { return PROJECTION; }
    public Class<ObjectId> subjectType() { return ObjectId.class; }
    public Class<RegionalValueFact> factType() { return RegionalValueFact.class; }
    public Comparator<ObjectId> subjectOrder() { return RegionalValuesAnalysis.OBJECT_ORDER; }
    public Prepared<ObjectId,RegionalValueFact> prepare(AnalysisSession owner, AnalysisKey key) {
        if (!supports(key)) throw new IllegalArgumentException("unsupported RegionalValues key");
        return RegionalProviderSupport.prepare(owner,key,RegionalValuesAnalysis.Execution::observe);
    }
}
