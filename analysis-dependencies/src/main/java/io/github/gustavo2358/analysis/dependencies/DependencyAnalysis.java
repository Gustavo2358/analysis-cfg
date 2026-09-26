package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.consumers.SiteView;
import io.github.gustavo2358.analysis.query.ObservationBatch;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.values.PossibleValuesProvider;
import io.github.gustavo2358.analysis.values.RegionalValuesProvider;
import io.github.gustavo2358.analysis.values.StorageValuesProvider;
import java.util.*;
import io.github.gustavo2358.analysis.values.StorageAnalysisMode;

/** CP6 W1D application boundary. */
public final class DependencyAnalysis {
    private final StorageAnalysisMode mode;
    public DependencyAnalysis(){this(StorageAnalysisMode.LOGICAL_ONLY);}
    public DependencyAnalysis(StorageAnalysisMode mode){this.mode=Objects.requireNonNull(mode);}
    public DependencyResult prepare(Publication publication) {
        return prepare(new DependencyInput(publication,Optional.empty(),List.of()));
    }
    public DependencyResult prepare(DependencyInput input) {
        var occurrences=input.occurrences();
        var result=prepareExecutable(input.publication());
        if(input.source().isPresent())result=result.withSourceEvidence(input.source().get());
        var byOperation=new HashMap<OperationId,List<DependencySiteFact>>();
        for(var site:result.sites())byOperation.computeIfAbsent(site.operation(),ignored->new ArrayList<>()).add(site);
        var inventory=new ArrayList<TargetResolver.Resolution>();long reused=0;
        for(var occurrence:occurrences) {
            var sites=occurrence.executableOperations().stream().flatMap(op->byOperation.getOrDefault(op,List.of()).stream()).toList();
            var resolved=TargetResolver.resolve(occurrence,sites);inventory.add(resolved);
            if(occurrence.source().isPresent()&&!occurrence.qualifications().isEmpty()&&occurrence.targetKind().equals("COMPUTED")&&!resolved.candidates().isEmpty())reused++;
        }
        var metrics=new TreeMap<>(result.metrics());metrics.put("sourceQualifiedResolvedByExistingQuery",reused);
        metrics.put("qualifiedComputedOccurrences",occurrences.stream().filter(o->o.targetKind().equals("COMPUTED")&&(!o.qualifications().isEmpty()||!o.executableOperations().isEmpty())).count());
        metrics.put("targetResolutionRequests",(long)occurrences.size());
        return result.withProgramInventory(inventory,metrics);
    }
    private DependencyResult prepareExecutable(Publication publication) {
        Objects.requireNonNull(publication);
        var defaults=BuildOptions.defaults();var options=new BuildOptions(defaults.validation(),io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.PARTIAL_ANALYSIS);var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(publication,options);
        switch(cfg.status()) {
            case CFG_BUILT -> { }
            case INVALID_IR -> throw new Failure(Kind.INVALID_INPUT,"INVALID_IR");
            case UNSUPPORTED_INPUT,UNSUPPORTED_CAPABILITY -> { return partialInventory(publication,"CFG_UNSUPPORTED"); }
            case RESOURCE_LIMIT -> throw new Failure(Kind.RESOURCE_LIMIT,"RESOURCE_LIMIT");
            case VALIDATION_LIMIT,INCOMPLETE_VALIDATION -> throw new Failure(Kind.INPUT_INCOMPLETE,"INCOMPLETE_VALIDATION");
        }
        var opened=AnalysisSession.open(cfg,publication,options.projectionPolicy(),publication.units().stream().flatMap(u->u.entries().stream()).filter(e->e.initialLabel().isPresent()).toList());
        if(opened.status()!=AnalysisSession.Status.ACCEPTED)return partialInventory(publication,opened.reason());
        var session=opened.session().orElseThrow();
        try(var execution=new PlanningExecution(session,new AnalysisRegistry(List.of(new PossibleValuesProvider(),new RegionalValuesProvider(),new StorageValuesProvider(),new ReachabilityProvider())))) {
            var selection=CallDependencyPlan.choose(session,"call",false,mode);
            var plan=execution.plan(selection.registrations());var result=execution.execute("dependencies@1",plan);
            for(var analysis:result.analyses())if(analysis.status()==AnalysisOutcome.Status.INVALID_INPUT)throw new Failure(Kind.INVALID_INPUT,analysis.reason());
            var reasons=new TreeSet<String>();
            if(session.index().hasUnprovedPreconditions())reasons.add("UNPROVED_OPERATION_PRECONDITION");
            if(publication.units().stream().anyMatch(u->u.body()!=Unit.BodyAvailability.AVAILABLE))reasons.add("UNIT_BODY_UNAVAILABLE");
            if(publication.units().stream().anyMatch(u->session.index().partialControl(u.id())))reasons.add("PARTIAL_CONTROL_PROJECTION");
            var entryReasons=new HashMap<EntryId,Set<String>>();
            for(var analysis:result.analyses())if(analysis.status()!=AnalysisOutcome.Status.STABLE) {
                reasons.add(analysis.reason());entryReasons.computeIfAbsent(analysis.key().entry(),ignored->new TreeSet<>()).add(analysis.reason());
            }
            if(result.preparationStatus()!=PreparedAnalysisResult.PreparationStatus.COMPLETE)reasons.add("DEPENDENCY_PREPARATION_INCOMPLETE");
            var retained=new HashMap<SiteKey,DependencySiteFact>();
            result.consumers().stream().flatMap(c->c.facts().stream()).forEach(f->retained.put(new SiteKey(f.entry(),f.operation()),f));
            var reachability=new HashMap<SiteKey,ReachabilityProvider.Fact>();
            for(var batch:result.results())if(batch.batchId().analysisKey().implementation().equals("Reachability"))
                for(var observation:batch.observations())if(observation.status()==ObservationBatch.QueryStatus.VALUE&&observation.value() instanceof ReachabilityProvider.Fact fact)
                    reachability.put(new SiteKey(observation.query().point().entry(),observation.query().point().operation()),fact);
            for(var site:inventory(publication)) {
                var key=new SiteKey(site.entry(),site.operationId());
                if(!retained.containsKey(key))retained.put(key,CallDependencyConsumer.partial(site,Optional.ofNullable(reachability.get(key)),
                    List.copyOf(entryReasons.getOrDefault(site.entry(),Set.of("DEPENDENCY_PREPARATION_INCOMPLETE")))));
            }
            var physicalNotAnalyzed=physicalDemands(session,selection);
            var sites=retained.values().stream()
                .map(f->f.targetKind()==DependencySiteFact.TargetKind.COMPUTED&&physicalNotAnalyzed.contains(new CallDependencyPlan.SiteKey(f.entry(),f.operation()))?f.withPartialAnalysis("PHYSICAL_PROPAGATION_DISABLED"):f).map(f->session.index().partialControl(f.caller())?f.withPartialAnalysis("PARTIAL_CONTROL_PROJECTION"):f)
                .map(f->session.index().unprovedPreconditions(f.caller())?f.withPartialAnalysis("UNPROVED_OPERATION_PRECONDITION"):f).sorted(Comparator.comparing(DependencySiteFact::entry,AnalysisKey.ENTRY_ORDER).thenComparing(f->f.operation().localId())).toList();
            var edges=new ArrayList<DependencyResult.Edge>();
            for(var site:sites)if(site.reachability()!=DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL)
                for(var candidate:site.candidates())edges.add(new DependencyResult.Edge(site.caller(),site.entry(),site.operation(),candidate,site.effectiveUnknownRemainder()));
            var metrics=new TreeMap<String,Long>(selection.metrics());
            result.metrics().forEach((phase,counts)->counts.forEach((name,value)->metrics.put(phase+"."+name,value)));
            long values=result.analyses().stream().filter(a->a.key().implementation().equals(PossibleValuesProvider.IMPLEMENTATION)||a.key().implementation().equals(RegionalValuesProvider.IMPLEMENTATION)||a.key().implementation().equals(StorageValuesProvider.IMPLEMENTATION)).count();
            metrics.put("possibleValuesPreparations",values);metrics.put("possibleValuesRuns",result.analyses().stream().filter(a->a.status()==AnalysisOutcome.Status.STABLE&&(a.key().implementation().equals(PossibleValuesProvider.IMPLEMENTATION)||a.key().implementation().equals(RegionalValuesProvider.IMPLEMENTATION)||a.key().implementation().equals(StorageValuesProvider.IMPLEMENTATION))).count());
            metrics.put("partialSites",sites.stream().filter(f->f.analysisStatus()==DependencySiteFact.AnalysisStatus.PARTIAL).count());
            metrics.put("reachabilityRuns",result.analyses().stream().filter(a->a.key().implementation().equals("Reachability")).count());
            metrics.put("indexedOperations",session.index().metrics().operationsIndexed());
            for(var analysis:result.analyses())analysis.metrics().forEach((name,value)->metrics.merge(analysis.key().implementation()+"."+name,value,Math::addExact));
            var fileResult=FileDependencyAnalysis.prepare(publication,session,execution,null,mode);
            var sourceResult=new SourceDependencyAnalysis().prepare(publication);
            metrics.put("sourceDependencyOccurrences",sourceResult.occurrences());
            metrics.put("sourceDependencyUnique",(long)sourceResult.dependencies().size());
            metrics.put("logicalOnlyMode",mode.physical()?0L:1L);
            metrics.put("experimentalPhysicalMode",mode.physical()?1L:0L);
            for(var counter:List.of("physicalGroupsApplied","physicalWritesApplied")) {
                long count=java.util.stream.Stream.concat(metrics.entrySet().stream(),fileResult.metrics().entrySet().stream())
                    .filter(e->e.getKey().endsWith("solve_"+counter)||e.getKey().endsWith("replay_"+counter)).mapToLong(Map.Entry::getValue).sum();
                metrics.put(counter,count);
            }
            for(var counter:List.of("physicalPlansPrepared","baseComparisons","targetsPrepared")) {
                long count=java.util.stream.Stream.concat(metrics.entrySet().stream(),fileResult.metrics().entrySet().stream())
                    .filter(e->e.getKey().endsWith("prepare_"+counter)).mapToLong(Map.Entry::getValue).sum();
                metrics.put(counter,count);
            }
            return new DependencyResult(publication.id(),publication.airVersion(),sites,edges,metrics,publication.coverage().inventory(),publication.origins(),publication.artifacts(),publication.uncertainties().stream().map(Evidence.Uncertainty::id).toList(),List.copyOf(reasons),fileResult,sourceResult);
        }
    }
    private Set<CallDependencyPlan.SiteKey> physicalDemands(AnalysisSession session,CallDependencyPlan.Selection selection) {
        var result=new HashSet<CallDependencyPlan.SiteKey>();
        if(mode.physical()||selection.providers().values().stream().noneMatch(p->p==CallDependencyPlan.Provider.REGIONAL||p==CallDependencyPlan.Provider.STORAGE))return result;
        try {
            var storage=new io.github.gustavo2358.analysis.storage.StorageIndex(session);
            for(var selected:selection.providers().entrySet()) {
                if(selected.getValue()!=CallDependencyPlan.Provider.REGIONAL&&selected.getValue()!=CallDependencyPlan.Provider.STORAGE)continue;
                var invoke=(Operations.Invoke)session.index().site(selected.getKey().operation()).operation();
                var place=((Expressions.Read)((Interactions.ComputedTarget)invoke.target()).name()).place();
                var resolution=storage.resolve(place);var locations=new ArrayList<>(resolution.candidates());
                if(resolution.remainder() instanceof Scopes.WithinMemory remainder)locations.addAll(storage.select(remainder.scope()).candidates());
                if(locations.stream().anyMatch(c->session.index().storage(c.location().base().id()) instanceof Memory.Region))result.add(selected.getKey());
            }
        } catch(io.github.gustavo2358.analysis.storage.StorageIndex.UngroundedBound unsupported) {
            // The provider's refusal remains explicit; an ungrounded bound is not
            // evidence that this query needs physical propagation.
        }
        return result;
    }
    private record SiteKey(EntryId entry,OperationId operation) { }
    private static List<SiteView> inventory(Publication publication) {
        var result=new ArrayList<SiteView>();
        for(var unit:publication.units())for(var entry:unit.entries())for(var sequence:unit.sequences())
            if(sequence.terminator() instanceof Operations.Invoke invoke&&CallDependencyPlan.selected(invoke))
                result.add(new SiteView(entry.id(),sequence.label(),sequence.instructions().size(),invoke));
        return List.copyOf(result);
    }
    private DependencyResult partialInventory(Publication publication,String reason) {
        var sites=inventory(publication).stream().map(s->CallDependencyConsumer.partial(s,Optional.empty(),List.of(reason)))
            .sorted(Comparator.comparing(DependencySiteFact::entry,AnalysisKey.ENTRY_ORDER).thenComparing(f->f.operation().localId())).toList();
        var edges=new ArrayList<DependencyResult.Edge>();
        for(var site:sites)for(var candidate:site.candidates())edges.add(new DependencyResult.Edge(site.caller(),site.entry(),site.operation(),candidate,true));
        return new DependencyResult(publication.id(),publication.airVersion(),sites,edges,
            Map.of("possibleValuesPreparations",0L,"possibleValuesRuns",0L,"reachabilityRuns",0L,"partialSites",(long)sites.size(),"logicalOnlyMode",mode.physical()?0L:1L,"experimentalPhysicalMode",mode.physical()?1L:0L,"physicalGroupsApplied",0L,"physicalWritesApplied",0L),
            publication.coverage().inventory(),publication.origins(),publication.artifacts(),publication.uncertainties().stream().map(Evidence.Uncertainty::id).toList(),List.of(reason),FileDependencyAnalysis.prepare(publication,null,null,reason,mode),new SourceDependencyAnalysis().prepare(publication));
    }
    public enum Kind { INVALID_INPUT,INPUT_INCOMPLETE,CFG_UNSUPPORTED,ANALYSIS_UNSUPPORTED,RESOURCE_LIMIT,CONSUMER_FAILURE }
    public static final class Failure extends RuntimeException {
        private static final long serialVersionUID=1L;
        private final Kind kind;
        public Failure(Kind kind,String message){super(message);this.kind=kind;}
        public Kind kind(){return kind;}
    }
}
