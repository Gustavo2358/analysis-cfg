package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.AnalysisProvider;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.PointQuery;
import io.github.gustavo2358.analysis.solver.Direction;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.util.*;

import io.github.gustavo2358.analysis.query.ObservationBatch;

/** Shared preparation/solver/metrics for object and physical-range observation projections. */
final class RegionalProviderSupport {
    private RegionalProviderSupport() { }
    static <T,V extends TextValueFact> AnalysisProvider.Prepared<T,V> prepare(AnalysisSession owner,AnalysisKey key,java.util.function.BiFunction<RegionalValuesAnalysis.Execution,List<PointQuery<T>>,ObservationBatch<T,V>> observer) {
        var scoped = owner.selectEntries(List.of(key.entry()));
        var admission = RegionalValuesAnalysis.prepare(scoped);
        return new AnalysisProvider.Prepared<>() {
            public AnalysisKey key() { return key; }
            public AnalysisOutcome refusal() {
                return admission.status() == RegionalValuesAnalysis.Status.ACCEPTED ? null : new AnalysisOutcome(key,
                    admission.status() == RegionalValuesAnalysis.Status.INVALID_INPUT ? AnalysisOutcome.Status.INVALID_INPUT : AnalysisOutcome.Status.UNSUPPORTED,
                    admission.reason(),Map.of());
            }
            public AnalysisProvider.Run<T,V> execute() {
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
                return new AnalysisProvider.Run<>() {
                    public AnalysisOutcome outcome() { return outcome; }
                    public AnalysisProvider.Materialized<T,V> observe(List<PointQuery<T>> queries) {
                        var before=execution.metrics();var observations=observer.apply(execution,queries);var metrics=new TreeMap<String,Long>();
                        execution.metrics().forEach((k,v)->metrics.put("replay_"+k,v-before.get(k)));
                        for(String quality:List.of("modelOpenResults","sourceOpenResults","effectiveOpenResults","closedInModelResults"))metrics.put(quality,0L);
                        for(var observation:observations.observations())if(observation.value()!=null) {
                            var value=observation.value();
                            if(Boolean.TRUE.equals(value.modelValueRemainder()))metrics.merge("modelOpenResults",1L,Math::addExact);
                            if(Boolean.FALSE.equals(value.modelValueRemainder()))metrics.merge("closedInModelResults",1L,Math::addExact);
                            if(value.sourceUnknownRemainder())metrics.merge("sourceOpenResults",1L,Math::addExact);
                            if(value.effectiveUnknownRemainder())metrics.merge("effectiveOpenResults",1L,Math::addExact);
                        }
                        return new AnalysisProvider.Materialized<>(observations,metrics);
                    }
                };
            }
        };
    }
}
