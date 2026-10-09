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
import io.github.gustavo2358.analysis.structure.ProgramStore;
import io.github.gustavo2358.analysis.values.PossibleValuesProvider;
import io.github.gustavo2358.analysis.values.RegionalValuesProvider;
import io.github.gustavo2358.analysis.values.StorageValuesProvider;
import java.util.*;
import io.github.gustavo2358.analysis.values.StorageAnalysisMode;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PageStore;

/** CP6 W1D application boundary. */
public final class DependencyAnalysis {
    private final StorageAnalysisMode mode;
    public DependencyAnalysis(){this(StorageAnalysisMode.LOGICAL_ONLY);}
    public DependencyAnalysis(StorageAnalysisMode mode){this.mode=Objects.requireNonNull(mode);}
    public DependencyResult prepare(Publication publication) {
        return prepare(new DependencyInput(publication,Optional.empty(),List.of()));
    }
    public DependencyResult prepare(DependencyInput input) {
        return enrich(prepareExecutable(input),input.source(),input.preparedSource(),input.occurrences(),null,null,null);
    }
    private DependencyResult enrich(DependencyResult result,Optional<QualifiedSourceDependencies> source,
            Optional<SourceQualifiedDependencyResult> preparedSource,List<QualifiedDependencyOccurrence> occurrences,
            ProgramStore.Structural program,PageStore pages,AnalysisResources resources) {
        if(preparedSource.isPresent())result=result.withSourceEvidence(preparedSource.get());
        var byOperation=new HashMap<OperationId,List<DependencySiteFact>>();
        for(var site:result.sites()){if(program!=null)program.progress(ProgramStore.ExecutionPhase.DEMAND);byOperation.computeIfAbsent(site.operation(),ignored->new ArrayList<>()).add(site);}
        var sourceValues=new HashMap<io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.StatementId,List<SourceValuesProvider.Candidate>>();
        long conditionalRuns=0,conditionalQueries=0,conditionalWork=0,conditionalLimited=0;
        if(source.isPresent())for(var unit:source.get().units())if(unit.nominalValues().isPresent()) {
            if(program!=null)program.progress(ProgramStore.ExecutionPhase.DEMAND);
            var requested=new TreeSet<String>();
            for(var occurrence:occurrences)if(occurrence.source().filter(s->s.unit().equals(unit.unit())).isPresent()) {
                if(program!=null)program.progress(ProgramStore.ExecutionPhase.DEMAND);
                var sites=occurrence.executableOperations().stream().flatMap(op->byOperation.getOrDefault(op,List.of()).stream()).toList();
                if(TargetResolver.requiresSourceValues(occurrence,sites))requested.add(occurrence.source().orElseThrow().handle());
            }
            requested.retainAll(unit.nominalValues().get().facts().queries().stream().map(q->q.statement()).collect(java.util.stream.Collectors.toSet()));
            if(!requested.isEmpty()) {
                var provider=new SourceValuesProvider(unit,requested,pages,resources);conditionalRuns++;conditionalQueries+=requested.size();conditionalWork+=provider.workItems();if(provider.limited())conditionalLimited++;
                for(var statement:requested)sourceValues.put(new io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.StatementId(unit.unit(),statement),provider.candidates(statement));
            }
        }
        var inventory=new ArrayList<TargetResolver.Resolution>();long reused=0;
        for(var occurrence:occurrences) {
            if(program!=null)program.progress(ProgramStore.ExecutionPhase.DOMAIN);
            var sites=occurrence.executableOperations().stream().flatMap(op->byOperation.getOrDefault(op,List.of()).stream()).toList();
            var resolved=TargetResolver.resolve(occurrence,sites,occurrence.source().map(s->sourceValues.getOrDefault(s,List.of())).orElse(List.of()),()->{if(program!=null)program.progress(ProgramStore.ExecutionPhase.DOMAIN);});inventory.add(resolved);
            if(occurrence.source().isPresent()&&!occurrence.qualifications().isEmpty()&&occurrence.targetKind().equals("COMPUTED")&&resolved.candidates().stream().anyMatch(c->!c.executableSupports().isEmpty()))reused++;
        }
        var metrics=new TreeMap<>(result.metrics());metrics.put("sourceQualifiedResolvedByExistingQuery",reused);
        metrics.put("qualifiedComputedOccurrences",occurrences.stream().filter(o->o.targetKind().equals("COMPUTED")&&(!o.qualifications().isEmpty()||!o.executableOperations().isEmpty())).count());
        metrics.put("conditionalSourceValueRuns",conditionalRuns);metrics.put("conditionalSourceValueQueries",conditionalQueries);metrics.put("conditionalSourceWorkItems",conditionalWork);metrics.put("conditionalSourceResourceLimits",conditionalLimited);
        metrics.put("targetResolutionRequests",(long)occurrences.size());
        return result.withProgramInventory(inventory,metrics);
    }
    private DependencyResult prepareExecutable(DependencyInput input) {
        var publication=input.publication();
        Objects.requireNonNull(publication);
        var defaults=BuildOptions.defaults();var options=new BuildOptions(defaults.validation(),io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.PARTIAL_ANALYSIS);var coordinator=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());
        var cfg=input.checked().map(checked->coordinator.buildChecked(checked,options)).orElseGet(()->coordinator.build(publication,options));
        switch(cfg.status()) {
            case CFG_BUILT -> { }
            case INVALID_IR -> throw new Failure(Kind.INVALID_INPUT,"INVALID_IR");
            case UNSUPPORTED_INPUT,UNSUPPORTED_CAPABILITY -> { return partialInventory(publication,"CFG_UNSUPPORTED"); }
            case RESOURCE_LIMIT -> throw new Failure(Kind.RESOURCE_LIMIT,"RESOURCE_LIMIT");
            case VALIDATION_LIMIT,INCOMPLETE_VALIDATION -> throw new Failure(Kind.INPUT_INCOMPLETE,"INCOMPLETE_VALIDATION");
        }
        return prepareExecutable(ProgramStore.resident(publication),cfg);
    }
    /** Validated native program -> existing general session/planner; no owning AIR body reconstruction. */
    public DependencyResult prepare(SnapshotProgram program,CfgBuildResult cfg) {
        Objects.requireNonNull(program);Objects.requireNonNull(cfg);program.admission();
        if(cfg.status()!=CfgBuildResult.Status.CFG_BUILT)throw new Failure(Kind.CFG_UNSUPPORTED,"CFG_UNSUPPORTED");
        var qualification=DependencyInput.qualify(program,Optional.empty(),List.of());
        return enrich(prepareExecutable(program,cfg),Optional.empty(),qualification.preparedSource(),qualification.occurrences(),program,null,null);
    }
    /** Native application result whose metadata borrows the checked program's lifetime. */
    public DependencyResult prepareLeased(SnapshotProgram program,CfgBuildResult cfg) {
        Objects.requireNonNull(program);Objects.requireNonNull(cfg);program.admission();
        if(cfg.status()!=CfgBuildResult.Status.CFG_BUILT)throw new Failure(Kind.CFG_UNSUPPORTED,"CFG_UNSUPPORTED");
        var qualification=DependencyInput.qualify(program,Optional.empty(),List.of());
        return enrich(prepareExecutable(program,cfg,program),Optional.empty(),qualification.preparedSource(),qualification.occurrences(),program,null,null);
    }
    /** Explicit source authority composes with the same native executable analysis and shared page owner. */
    public DependencyResult prepare(SnapshotProgram program,CfgBuildResult cfg,QualifiedSourceDependencies source,
            List<DependencyInput.StatementCorrelation> correlations,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(program);Objects.requireNonNull(cfg);Objects.requireNonNull(source);
        Objects.requireNonNull(pages);Objects.requireNonNull(resources);program.admission();
        if(cfg.status()!=CfgBuildResult.Status.CFG_BUILT)throw new Failure(Kind.CFG_UNSUPPORTED,"CFG_UNSUPPORTED");
        var qualification=DependencyInput.qualify(program,Optional.of(source),correlations);
        return enrich(prepareExecutable(program,cfg),Optional.of(source),qualification.preparedSource(),qualification.occurrences(),program,pages,resources);
    }
    /** Full source/physical semantics, with canonical metadata borrowed until publication finishes. */
    public DependencyResult prepareLeased(SnapshotProgram program,CfgBuildResult cfg,QualifiedSourceDependencies source,
            List<DependencyInput.StatementCorrelation> correlations,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(program);Objects.requireNonNull(cfg);Objects.requireNonNull(source);
        Objects.requireNonNull(pages);Objects.requireNonNull(resources);program.admission();
        if(cfg.status()!=CfgBuildResult.Status.CFG_BUILT)throw new Failure(Kind.CFG_UNSUPPORTED,"CFG_UNSUPPORTED");
        var qualification=DependencyInput.qualify(program,Optional.of(source),correlations);
        return enrich(prepareExecutable(program,cfg,program),Optional.of(source),qualification.preparedSource(),qualification.occurrences(),program,pages,resources);
    }
    private DependencyResult prepareExecutable(ProgramStore.Structural publication,CfgBuildResult cfg) {
        return prepareExecutable(publication,cfg,null);
    }
    private DependencyResult prepareExecutable(ProgramStore.Structural publication,CfgBuildResult cfg,SnapshotProgram metadataOwner) {
        var opened=AnalysisSession.open(cfg,publication,cfg.options().projectionPolicy(),publication.units().stream()
            .flatMap(u->u.entries().stream()).filter(e->e.initialLabel().isPresent()).map(Entries.Entry::id).toList());
        if(opened.status()!=AnalysisSession.Status.ACCEPTED)return partialInventory(publication,opened.reason(),metadataOwner);
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
            return new DependencyResult(publication.publicationId(),publication.airVersion(),sites,edges,metrics,publication.coverage().inventory(),
                metadataOwner==null?publication.origins():metadataOwner.orderedOrigins(),
                metadataOwner==null?publication.artifacts():metadataOwner.orderedArtifacts(),
                metadataOwner==null?publication.uncertainties().stream().map(Evidence.Uncertainty::id).toList():metadataOwner.orderedUncertaintyRefs(),List.copyOf(reasons),fileResult,sourceResult);
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
        return inventory(ProgramStore.resident(publication));
    }
    private static List<SiteView> inventory(ProgramStore.Structural publication) {
        var result=new ArrayList<SiteView>();
        for(var unit:publication.units())for(var entry:unit.entries())for(var sequence:unit.sequences())
            if(sequence.terminator() instanceof Operations.Invoke invoke&&CallDependencyPlan.selected(invoke))
                result.add(new SiteView(entry.id(),sequence.label(),sequence.instructions().size(),invoke));
        return List.copyOf(result);
    }
    private DependencyResult partialInventory(Publication publication,String reason) {
        return partialInventory(ProgramStore.resident(publication),reason);
    }
    private DependencyResult partialInventory(ProgramStore.Structural publication,String reason) {
        return partialInventory(publication,reason,null);
    }
    private DependencyResult partialInventory(ProgramStore.Structural publication,String reason,SnapshotProgram metadataOwner) {
        var sites=inventory(publication).stream().map(s->CallDependencyConsumer.partial(s,Optional.empty(),List.of(reason)))
            .sorted(Comparator.comparing(DependencySiteFact::entry,AnalysisKey.ENTRY_ORDER).thenComparing(f->f.operation().localId())).toList();
        var edges=new ArrayList<DependencyResult.Edge>();
        for(var site:sites)for(var candidate:site.candidates())edges.add(new DependencyResult.Edge(site.caller(),site.entry(),site.operation(),candidate,true));
        return new DependencyResult(publication.publicationId(),publication.airVersion(),sites,edges,
            Map.of("possibleValuesPreparations",0L,"possibleValuesRuns",0L,"reachabilityRuns",0L,"partialSites",(long)sites.size(),"logicalOnlyMode",mode.physical()?0L:1L,"experimentalPhysicalMode",mode.physical()?1L:0L,"physicalGroupsApplied",0L,"physicalWritesApplied",0L),
            publication.coverage().inventory(),metadataOwner==null?publication.origins():metadataOwner.orderedOrigins(),
            metadataOwner==null?publication.artifacts():metadataOwner.orderedArtifacts(),
            metadataOwner==null?publication.uncertainties().stream().map(Evidence.Uncertainty::id).toList():metadataOwner.orderedUncertaintyRefs(),List.of(reason),FileDependencyAnalysis.prepare(publication,null,null,reason,mode),new SourceDependencyAnalysis().prepare(publication));
    }
    public enum Kind { INVALID_INPUT,INPUT_INCOMPLETE,CFG_UNSUPPORTED,ANALYSIS_UNSUPPORTED,RESOURCE_LIMIT,CONSUMER_FAILURE }
    public static final class Failure extends RuntimeException {
        private static final long serialVersionUID=1L;
        private final Kind kind;
        public Failure(Kind kind,String message){super(message);this.kind=kind;}
        public Kind kind(){return kind;}
    }
}
