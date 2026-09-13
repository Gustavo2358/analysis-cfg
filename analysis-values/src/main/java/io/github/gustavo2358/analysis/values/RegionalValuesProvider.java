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
    public static final String VERSION = "1";
    public static final String PRECISION = "FINITE_REGIONAL_IMAGES";
    public static final String PROJECTION = "RegionalValueFact@1";
    public static AnalysisKey key(EntryId entry) {
        return new AnalysisKey(IMPLEMENTATION,VERSION,RegionalValuesAnalysis.PROFILE,Direction.FORWARD,PRECISION,Map.of(),entry);
    }
    public static ObservationBatchId<ObjectId,RegionalValueFact> batch(String id, AnalysisKey key) {
        return new ObservationBatchId<>(id,key,PROJECTION,ObjectId.class,RegionalValueFact.class);
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
    public Class<ObjectId> subjectType() { return ObjectId.class; }
    public Class<RegionalValueFact> factType() { return RegionalValueFact.class; }
    public Comparator<ObjectId> subjectOrder() { return RegionalValuesAnalysis.OBJECT_ORDER; }
    public Prepared<ObjectId,RegionalValueFact> prepare(AnalysisSession owner, AnalysisKey key) {
        if (!supports(key)) throw new IllegalArgumentException("unsupported RegionalValues key");
        var scoped = owner.selectEntries(List.of(key.entry()));
        var admission = RegionalValuesAnalysis.prepare(scoped);
        return new Prepared<>() {
            public AnalysisKey key() { return key; }
            public AnalysisOutcome refusal() {
                return admission.status() == RegionalValuesAnalysis.Status.ACCEPTED ? null : new AnalysisOutcome(key,
                    admission.status() == RegionalValuesAnalysis.Status.INVALID_INPUT ? AnalysisOutcome.Status.INVALID_INPUT : AnalysisOutcome.Status.UNSUPPORTED,
                    admission.reason(),Map.of());
            }
            public Run<ObjectId,RegionalValueFact> execute() {
                var execution = admission.analysis().orElseThrow().execute();
                var metrics = new TreeMap<String,Long>();
                var solver = execution.dataflow().metrics();
                metrics.put("analysisPoints",solver.analysisPoints());
                metrics.put("contextualEdges",solver.contextualEdges());
                metrics.put("boundaryJoins",solver.boundaryJoins());
                metrics.put("initializationAttempts",solver.initializationAttempts());
                metrics.put("worklistAttempts",solver.worklistAttempts());
                metrics.put("worklistPushes",solver.worklistPushes());
                metrics.put("nodesPopped",solver.nodesPopped());
                metrics.put("duplicatePushesSuppressed",solver.duplicatePushesSuppressed());
                metrics.put("maxWorklistSize",solver.maxWorklistSize());
                metrics.put("nodesTransferred",solver.nodesTransferred());
                metrics.put("operationsTransferred",solver.operationsTransferred());
                metrics.put("firstPublications",solver.firstPublications());
                metrics.put("publishedStatesChanged",solver.publishedStatesChanged());
                metrics.put("publishedStatesUnchanged",solver.publishedStatesUnchanged());
                metrics.put("edgeTransferInvocations",solver.edgeTransferInvocations());
                metrics.put("edgeContributionJoins",solver.edgeContributionJoins());
                metrics.put("accumulatorStatesChanged",solver.accumulatorStatesChanged());
                metrics.put("accumulatorStatesUnchanged",solver.accumulatorStatesUnchanged());
                metrics.put("predecessorContributionReads",solver.predecessorContributionReads());
                metrics.put("successorContributionReads",solver.successorContributionReads());
                metrics.put("joinEntriesVisited",solver.joinEntriesVisited());
                metrics.put("stateCompareEntries",solver.stateCompareEntries());
                execution.preparationMetrics().forEach((k,v) -> metrics.put("prepare_"+k,v));
                execution.solveMetrics().forEach((k,v) -> metrics.put("solve_"+k,v));
                var outcome = new AnalysisOutcome(key,AnalysisOutcome.Status.STABLE,null,metrics);
                return new Run<>() {
                    public AnalysisOutcome outcome() { return outcome; }
                    public Materialized<ObjectId,RegionalValueFact> observe(List<PointQuery<ObjectId>> queries) {
                        var before=execution.metrics();var observations=execution.observe(queries);var metrics=new TreeMap<String,Long>();
                        execution.metrics().forEach((k,v)->metrics.put("replay_"+k,v-before.get(k)));
                        for(String quality:List.of("modelOpenResults","sourceOpenResults","effectiveOpenResults","closedInModelResults"))metrics.put(quality,0L);
                        for(var observation:observations.observations())if(observation.value()!=null) {
                            var value=observation.value();
                            if(Boolean.TRUE.equals(value.modelValueRemainder()))metrics.merge("modelOpenResults",1L,Math::addExact);
                            if(Boolean.FALSE.equals(value.modelValueRemainder()))metrics.merge("closedInModelResults",1L,Math::addExact);
                            if(value.sourceUnknownRemainder())metrics.merge("sourceOpenResults",1L,Math::addExact);
                            if(value.effectiveUnknownRemainder())metrics.merge("effectiveOpenResults",1L,Math::addExact);
                        }
                        return new Materialized<>(observations,metrics);
                    }
                };
            }
        };
    }
}
