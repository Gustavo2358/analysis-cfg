package io.github.gustavo2358.analysis.dataflow;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.*;
import io.github.gustavo2358.analysis.solver.SolverMetrics;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.values.RegionalValuesAnalysis;
import java.util.*;

/** Generic regional observation composition over the existing CFG, solver and replay. */
public final class RegionalAnalysis {
    public RegionalAnalysisResult prepare(Publication publication,String resultId,List<PointQuery<StorageSubject>> queries) {
        return prepare(publication,resultId,queries,publication.units().stream().flatMap(u->u.entries().stream()).map(Entries.Entry::id).toList());
    }
    public RegionalAnalysisResult prepare(Publication publication,String resultId,List<PointQuery<StorageSubject>> queries,List<EntryId> entries) {
        return prepare(publication,resultId,queries,entries,BuildOptions.defaults());
    }
    /** Explicit partial admission retains open proof/control in both BEFORE observations. */
    public RegionalAnalysisResult preparePartial(Publication publication,String resultId,List<PointQuery<StorageSubject>> queries) {
        var defaults=BuildOptions.defaults();
        return prepare(publication,resultId,queries,publication.units().stream().flatMap(u->u.entries().stream()).filter(e->e.initialLabel().isPresent()).map(Entries.Entry::id).toList(),
            new BuildOptions(defaults.validation(),io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.PARTIAL_ANALYSIS));
    }
    RegionalAnalysisResult prepare(Publication publication,String resultId,List<PointQuery<StorageSubject>> queries,List<EntryId> entries,BuildOptions options) {
        Objects.requireNonNull(publication);Objects.requireNonNull(resultId);if(resultId.isBlank())throw new IllegalArgumentException("empty resultId");queries=List.copyOf(queries);
        var selected=new HashSet<>(entries);var declarations=publication.units().stream().flatMap(u->u.entries().stream()).filter(e->selected.contains(e.id())).toList();
        if(declarations.size()!=selected.size())throw new IllegalArgumentException("entry outside publication");
        var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(publication,options);AnalysisDataflow.requireBuilt(cfg);
        var admission=AnalysisSession.open(cfg,publication,options.projectionPolicy(),declarations);
        if(admission.status()!=AnalysisSession.Status.ACCEPTED)throw new AnalysisDataflow.PreparationException(admission.status()==AnalysisSession.Status.INVALID_INPUT?AnalysisDataflow.Failure.INVALID_INPUT:AnalysisDataflow.Failure.UNSUPPORTED_PROFILE,admission.reason());
        var session=admission.session().orElseThrow();
        var rdAdmission=ReachingDefinitions.prepare(new StatementEffects(new StorageIndex(session)));
        if(rdAdmission.status()!=ReachingDefinitions.Status.ACCEPTED)throw new AnalysisDataflow.PreparationException(rdAdmission.status()==ReachingDefinitions.Status.INVALID_INPUT?AnalysisDataflow.Failure.INVALID_INPUT:AnalysisDataflow.Failure.UNSUPPORTED_PROFILE,rdAdmission.reason());
        var valueAdmission=RegionalValuesAnalysis.prepare(session);
        if(valueAdmission.status()!=RegionalValuesAnalysis.Status.ACCEPTED)throw new AnalysisDataflow.PreparationException(valueAdmission.status()==RegionalValuesAnalysis.Status.INVALID_INPUT?AnalysisDataflow.Failure.INVALID_INPUT:AnalysisDataflow.Failure.UNSUPPORTED_PROFILE,valueAdmission.reason());
        var rd=rdAdmission.analysis().orElseThrow().execute();var values=valueAdmission.analysis().orElseThrow().execute();
        var rdSolve=new TreeMap<>(rd.metrics());rdSolve.putAll(solver(rd.dataflow().metrics()));
        var valueSolve=new TreeMap<>(values.solveMetrics());valueSolve.putAll(solver(values.dataflow().metrics()));values.preparationMetrics().forEach((k,v)->valueSolve.put("prepare_"+k,v));
        var definitions=rd.observeStorage(queries);var projections=values.observeStorage(queries);
        if(definitions.status()!=ObservationBatch.Status.COMPLETE||projections.status()!=ObservationBatch.Status.COMPLETE)throw new IllegalStateException("regional observation failed");
        var byQuery=new HashMap<PointQuery<StorageSubject>,ObservationBatch.Observation<StorageSubject,DefinitionFact>>();definitions.observations().forEach(o->byQuery.put(o.query(),o));
        var observations=projections.observations().stream().map(o->new RegionalAnalysisResult.Observation(o.query(),Objects.requireNonNull(byQuery.remove(o.query())),o)).toList();
        if(!byQuery.isEmpty())throw new IllegalStateException("regional batch mismatch");
        return new RegionalAnalysisResult(resultId,publication.id(),inventory(publication,selected),observations,
            Map.of("composition",Map.of("cfgBuilds",1L,"rdRuns",1L,"valueRuns",1L,"selectedEntries",(long)selected.size()),
                "rd",rdSolve,"values",valueSolve,"rdObservation",observation(definitions.metrics()),"valueObservation",observation(projections.metrics())));
    }
    private static RegionalAnalysisResult.Inventory inventory(Publication publication,Set<EntryId> entries) {
        var ids=new HashSet<Id>();ids.add(publication.id());var scopes=new ArrayList<RegionalAnalysisResult.SourceScope>();
        publication.origins().forEach(o->ids.add(o.id()));publication.premises().forEach(p->ids.add(p.id()));publication.uncertainties().forEach(u->ids.add(u.id()));
        var storages=new ArrayList<RegionalAnalysisResult.Storage>();
        for(var storage:publication.storage()) {
            ids.add(storage.header().id());storages.add(storage instanceof Memory.Region r?new RegionalAnalysisResult.Storage(r.header(),true,r.extent(),r.extentUnknown()):new RegionalAnalysisResult.Storage(storage.header(),false,Optional.empty(),Optional.empty()));
        }
        for(var unit:publication.units()) {
            ids.add(unit.id());unit.objects().forEach(o->ids.add(o.id()));
            for(var entry:unit.entries()) {
                ids.add(entry.id());
                if(entries.contains(entry.id()))scopes.add(new RegionalAnalysisResult.SourceScope(entry.id(),publication.coverage().inventory(),unit.coverage().inventory(),publication.coverage().uncertainties(),unit.coverage().uncertainties(),entry.state().uncertainties()));
                for(var condition:entry.state().conditions()) {
                    operands(ids,List.of(condition.place()));if(condition.value() instanceof Entries.LiteralInitial literal)operands(ids,List.of(literal.value()));
                }
            }
            for(var sequence:unit.sequences()) {
                ids.add(sequence.label());
                for(var operation:sequence.instructions()){ids.add(operation.header().id());operands(ids,Operands.roots(operation));}
                ids.add(sequence.terminator().header().id());operands(ids,Operands.roots(sequence.terminator()));
            }
        }
        return new RegionalAnalysisResult.Inventory(ids,storages,scopes);
    }
    private static void operands(Set<Id> ids,List<Operand> roots) {
        var pending=new ArrayDeque<>(roots);while(!pending.isEmpty()){var operand=pending.removeLast();ids.add(operand.header().id());pending.addAll(Operands.children(operand));}
    }
    private static Map<String,Long> solver(SolverMetrics m) {
        return Map.of("analysisPoints",m.analysisPoints(),"contextualEdges",m.contextualEdges(),"nodesTransferred",m.nodesTransferred(),"operationsTransferred",m.operationsTransferred(),
            "edgeContributionJoins",m.edgeContributionJoins(),"joinEntriesVisited",m.joinEntriesVisited(),"stateCompareEntries",m.stateCompareEntries(),"worklistPushes",m.worklistPushes(),"maxWorklistSize",m.maxWorklistSize());
    }
    private static Map<String,Long> observation(ObservationBatch.Metrics m) {
        return Map.of("queryRequests",m.queryRequests(),"uniqueQueries",m.uniqueQueries(),"sequencesReplayed",m.sequencesReplayed(),"operationsReplayed",m.operationsReplayed(),
            "maxRequestedOffsetObserved",m.maxRequestedOffsetObserved(),"queriesAnswered",m.queriesAnswered(),"unsupportedQueries",m.unsupportedQueries(),"queriesNotMaterialized",m.queriesNotMaterialized(),"observationFailures",m.observationFailures());
    }
}
