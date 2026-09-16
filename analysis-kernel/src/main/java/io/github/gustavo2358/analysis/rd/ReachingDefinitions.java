package io.github.gustavo2358.analysis.rd;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.plan.AnalysisKey;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Regional reaching definitions on the existing generic solver. */
public final class ReachingDefinitions {
    public static final String PROFILE="regional-reaching-definitions@1";
    private final StatementEffects effects;
    private final StoragePartition partition;
    private final AnalysisSession session;
    private final Map<Operation,List<Plan>> operations=new IdentityHashMap<>();
    private final Map<Operation,Map<Control.OutcomeKey,List<Plan>>> outcomes=new IdentityHashMap<>();
    private final Map<Operation,List<Plan>> otherwise=new IdentityHashMap<>();
    private final Map<EntryId,List<Initial>> initial=new HashMap<>();
    private final Map<EntryId,List<LogicalInitial>> logicalInitial=new HashMap<>();
    private record LogicalInitial(int slot,Entries.InitialCondition condition,ObjectId object,StorageIndex.Resolution resolution) { }
    private final List<SourceGap> sourceGaps=new ArrayList<>();
    private final Map<UnitId,Boolean> controlOpen=new HashMap<>();
    private record SourceGap(StorageIndex.Location location,OriginId origin,List<UncertaintyId> uncertainties) { }
    private record Plan(Operation operation,StatementEffects.Write write,StatementEffects.Target target,
                        Optional<Control.OutcomeKey> outcome,List<StoragePartition.Segment> segments,StatementEffects.LogicalTarget logical) { }
    private record Initial(int slot,Entries.InitialCondition condition,StatementEffects.Target target,List<StoragePartition.Segment> segments) { }
    public enum Status { ACCEPTED, UNSUPPORTED, INVALID_INPUT }
    public record Admission(Status status,String reason,Optional<ReachingDefinitions> analysis) { }
    private static final class Refusal extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        final Status status;
        Refusal(Status status,String reason){super(reason);this.status=status;}
    }
    public static Admission prepare(StatementEffects effects) {
        try{return new Admission(Status.ACCEPTED,null,Optional.of(new ReachingDefinitions(effects)));}
        catch(Refusal refusal){return new Admission(refusal.status,refusal.getMessage(),Optional.empty());}
    }
    public ReachingDefinitions(StatementEffects effects) {
        this.effects=Objects.requireNonNull(effects);session=effects.storage().session();partition=new StoragePartition(effects);
        for(var object:effects.storage().declarations())if(object.coverage()!=Evidence.CoverageStatus.MODELED||open(object.precision().storage())||open(object.precision().values())) {
            var resolution=effects.storage().object(object.id());var uncertainty=new LinkedHashSet<>(object.precision().storage().reasons());uncertainty.addAll(object.precision().values().reasons());uncertainty.addAll(resolution.uncertainties());
            for(var target:effects.targets(resolution,StatementEffects.Strength.MAY))sourceGaps.add(new SourceGap(target.location(),object.origin(),List.copyOf(uncertainty)));
        }
        for(var unit:session.index().publication().units()) {
            boolean open=effects.storage().session().index().partialControl(unit.id()) || effects.storage().session().index().unprovedPreconditions(unit.id());
            for(var sequence:unit.sequences()) {
                for(var instruction:sequence.instructions())open|=open(instruction.header().precision().control());
                var terminator=sequence.terminator();open|=open(terminator.header().precision().control());
                if(terminator instanceof Operations.Invoke i)open|=i.outcomes().remainder() instanceof Scopes.WithinControl;
                if(terminator instanceof Operations.Opaque o)open|=o.envelope().control().remainder() instanceof Scopes.WithinControl;
            }
            controlOpen.put(unit.id(),open);
        }
        for(var statement:effects.statements()) {
            var op=statement.operation();var normal=Optional.<Control.OutcomeKey>of(Control.NormalOutcome.INSTANCE);
            operations.put(op,compile(op,statement.writes(),normal));
            otherwise.put(op,compile(op,statement.otherwise(),Optional.empty()));
            var choices=new HashMap<Control.OutcomeKey,List<Plan>>();
            statement.outcomes().forEach((key,writes)->choices.put(key,compile(op,writes,Optional.of(key))));
            outcomes.put(op,Map.copyOf(choices));
        }
        for(var context:session.contexts()) {
            var seeds=new ArrayList<Initial>();var logicalSeeds=new ArrayList<LogicalInitial>();var occupied=new HashMap<Integer,Initial>();int slot=0;
            for(var condition:context.entry().state().conditions()) {
                var resolution=effects.storage().resolve(condition.place());
                if(condition.value() instanceof Entries.PossibleLiterals&&condition.place() instanceof Places.ObjectPlace object&&!resolution.exact()) {
                    logicalSeeds.add(new LogicalInitial(slot++,condition,object.object(),resolution));continue;
                }
                for(var target:effects.targets(resolution,condition.value() instanceof Entries.PossibleLiterals?StatementEffects.Strength.MAY:StatementEffects.Strength.MUST)) {
                    var seed=new Initial(slot,condition,target,List.copyOf(partition.intersecting(target.location())));
                    for(var segment:seed.segments()) {
                        var prior=occupied.putIfAbsent(segment.ordinal(),seed);
                        if(prior!=null && prior.slot()!=slot) {
                            // The session owns a fully validated AIR publication: I-17 has
                            // already proved simultaneous literal consistency, including ranges.
                            // This consumer check only excludes unresolved/mixed initial forms.
                            boolean literal=condition.value() instanceof Entries.LiteralInitial&&prior.condition().value() instanceof Entries.LiteralInitial;
                            boolean possible=condition.value() instanceof Entries.PossibleLiterals||prior.condition().value() instanceof Entries.PossibleLiterals;
                            if(!possible&&!(literal&&resolution.exact()&&target.sourceApplicable()&&prior.target().sourceApplicable()))
                                throw new Refusal(Status.UNSUPPORTED,"OVERLAPPING_INITIAL_CONDITIONS");
                        }
                    }
                    seeds.add(seed);
                }
                slot=Math.incrementExact(slot);
            }
            initial.put(context.entry().id(),List.copyOf(seeds));logicalInitial.put(context.entry().id(),List.copyOf(logicalSeeds));
        }
    }
    private List<Plan> compile(Operation operation,List<StatementEffects.Write> writes,Optional<Control.OutcomeKey> outcome) {
        var result=new ArrayList<Plan>();
        for(var write:writes) {
            for(var target:write.targets())result.add(new Plan(operation,write,target,outcome,List.copyOf(partition.intersecting(target.location())),null));
            for(var logical:write.logicalTargets())result.add(new Plan(operation,write,null,outcome,List.of(),logical));
        }
        return List.copyOf(result);
    }
    public StoragePartition partition(){return partition;}
    public Execution execute(){var engine=new Engine(this);var stable=DataflowSolver.solve(session,engine);return new Execution(this,engine,stable);}
    /** Full IDs are materialized once; hot sets compare canonical handles with dense hashes. */
    private static final class EventHandle {
        final int ordinal;final DefinitionEvent definition;
        EventHandle(int ordinal,DefinitionEvent definition){this.ordinal=ordinal;this.definition=definition;}
        @Override public int hashCode(){return ordinal;}
    }
    /** Reached absence is interpreted through the entry event, never as bottom. */
    public static final class State {
        private final EntryId entry;
        private final SegmentMap<Set<EventHandle>> bindings;
        private final Map<ObjectId,Set<EventHandle>> logical;
        private State(EntryId entry,SegmentMap<Set<EventHandle>> bindings,Map<ObjectId,Set<EventHandle>> logical){this.entry=entry;this.bindings=bindings;this.logical=Map.copyOf(logical);}
        public boolean reached(){return entry!=null;}
        public int explicitSegments(){return bindings.size();}
    }
    private static final State BOTTOM=new State(null,new SegmentMap<>(),Map.of());
    private static final class Engine implements AnalysisDefinition<State> {
        private final ReachingDefinitions owner;
        private final Map<EntryId,Map<StorageId,EventHandle>> entryEvents=new HashMap<>();
        private final Map<EntryId,Map<Plan,EventHandle>> events=new HashMap<>();
        private final Map<DefinitionEvent,EventHandle> interned=new HashMap<>();
        private long updates,segmentReads,eventUnions;
        Engine(ReachingDefinitions owner){this.owner=owner;}
        private EventHandle intern(DefinitionEvent definition) {
            var previous=interned.get(definition);if(previous!=null)return previous;
            var handle=new EventHandle(Math.incrementExact(interned.size()),definition);interned.put(definition,handle);return handle;
        }
        @Override public Direction direction(){return Direction.FORWARD;}
        @Override public State bottom(){return BOTTOM;}
        private EventHandle entryEvent(EntryId entry,StoragePartition.Segment segment) {
            var base=segment.location().base();
            return entryEvents.computeIfAbsent(entry,ignored->new HashMap<>()).computeIfAbsent(base.id(),ignored->
                intern(new DefinitionEvent(entry,Optional.empty(),Optional.empty(),-1,Optional.empty(),base.id(),DefinitionEvent.Kind.ENTRY_UNKNOWN,true,base.origin(),List.of(),List.of(),List.of("UNSPECIFIED_ENTRY_CONTENT"))));
        }
        private Set<EventHandle> value(State state,StoragePartition.Segment segment) {
            segmentReads++;var explicit=state.bindings.get(segment.ordinal());
            return explicit==null?Set.of(entryEvent(state.entry,segment)):explicit;
        }
        private Set<EventHandle> union(Set<EventHandle> a,Set<EventHandle> b) {
            eventUnions+=b.size();if(a.containsAll(b))return a;var result=new HashSet<>(a);result.addAll(b);return Set.copyOf(result);
        }
        @Override public Iterable<Boundary<State>> boundaries(AnalysisSession session) {
            if(session!=owner.session)throw new IllegalArgumentException("foreign session");
            var result=new ArrayList<Boundary<State>>();
            for(var context:session.contexts()) {
                var entry=context.entry().id();var root=new SegmentMap<Set<EventHandle>>();
                for(var seed:owner.initial.get(entry)) {
                    var event=intern(DefinitionEvent.initial(entry,seed.condition(),seed.slot(),seed.target(),owner.effects.storage().resolve(seed.condition().place())));
                    for(var segment:seed.segments()) {
                        var previous=root.get(segment.ordinal());
                        if(previous==null)previous=seed.target().strength()==StatementEffects.Strength.MUST?Set.of():Set.of(entryEvent(entry,segment));
                        root=root.put(segment.ordinal(),union(previous,Set.of(event)));
                    }
                }
                var logical=new HashMap<ObjectId,Set<EventHandle>>();
                for(var seed:owner.logicalInitial.get(entry)) {
                    var event=intern(DefinitionEvent.logicalInitial(entry,seed.condition(),seed.slot(),seed.object(),seed.resolution()));
                    logical.merge(seed.object(),Set.of(event),this::union);
                }
                result.add(new Boundary<>(context,context.entryNode(),new State(entry,root,logical)));
            }
            return result;
        }
        @Override public Join<State> joinInto(State a,State b,DomainWork work) {
            if(!b.reached())return new Join<>(a,false);if(!a.reached())return new Join<>(b,true);
            if(!a.entry.equals(b.entry))throw new IllegalArgumentException("different activation contexts");
            // Sparse immutable roots, including the default unknown contribution on missing keys.
            final class Accumulator { SegmentMap<Set<EventHandle>> root=a.bindings; }
            var result=new Accumulator();
            b.bindings.forEach((ordinal,definitions)->{work.joinEntryVisited();var segment=owner.partition.segments().get(ordinal);result.root=result.root.put(ordinal,union(value(a,segment),definitions));});
            a.bindings.forEach((ordinal,definitions)->{if(b.bindings.get(ordinal)==null){work.joinEntryVisited();var segment=owner.partition.segments().get(ordinal);result.root=result.root.put(ordinal,union(definitions,value(b,segment)));}});
            var logical=new HashMap<>(a.logical);b.logical.forEach((key,events)->{work.joinEntryVisited();logical.merge(key,events,this::union);});
            return result.root==a.bindings&&logical.equals(a.logical)?new Join<>(a,false):new Join<>(new State(a.entry,result.root,logical),true);
        }
        @Override public boolean equivalent(State a,State b,DomainWork work) {
            if(a==b)return true;if(!Objects.equals(a.entry,b.entry)||a.bindings.size()!=b.bindings.size()||!a.logical.equals(b.logical))return false;
            var equal=new boolean[]{true};a.bindings.forEach((key,value)->{work.stateCompareEntry();if(!value.equals(b.bindings.get(key)))equal[0]=false;});return equal[0];
        }
        private EventHandle event(EntryId entry,Plan plan) {
            return events.computeIfAbsent(entry,ignored->new IdentityHashMap<>()).computeIfAbsent(plan,p->intern(p.logical==null?DefinitionEvent.write(entry,p.operation,p.write,p.target,p.outcome):DefinitionEvent.logicalWrite(entry,p.operation,p.write,p.logical,p.outcome)));
        }
        private State apply(State state,List<Plan> plans,boolean forceMay) {
            if(!state.reached())return state;var root=state.bindings;var logical=new HashMap<>(state.logical);
            for(var plan:plans) {
                var definition=event(state.entry,plan);
                if(plan.logical!=null) {logical.merge(plan.logical.object(),Set.of(definition),KillAuthority::weakUpdate);continue;}
                var authority=KillAuthority.exact(plan.write,plan.target,forceMay?KillAuthority.Execution.POSSIBLE:KillAuthority.Execution.REQUIRED);
                for(var segment:plan.segments) {
                    var previous=root.get(segment.ordinal());if(previous==null)previous=Set.of(entryEvent(state.entry,segment));
                    var next=authority.isPresent()?KillAuthority.strongOverwrite(authority.get(),Set.of(definition)):union(previous,Set.of(definition));
                    var updated=root.put(segment.ordinal(),next);if(updated!=root)updates++;root=updated;
                }
            }
            return root==state.bindings&&logical.equals(state.logical)?state:new State(state.entry,root,logical);
        }
        private State operation(State state,Operation operation) {
            var plans=owner.operations.get(operation);
            if(plans==null)throw new IllegalArgumentException("foreign operation snapshot");
            return apply(state,plans,false);
        }
        @Override public State transferBlock(AnalysisPoint point,State anchor,DomainWork work) {
            if(!(point.node().source() instanceof CfgNode.SequenceNode node))return anchor;
            var state=anchor;for(var operation:node.source().instructions()){work.operationTransferred();state=operation(state,operation);}
            work.operationTransferred();return operation(state,node.source().terminator());
        }
        @Override public State transferEdge(AnalysisPoint point,CfgTransition edge,State state,DomainWork work) {
            if(!(point.node().source() instanceof CfgNode.SequenceNode node)||!(node.source().terminator() instanceof Operations.Invoke invoke))return state;
            if(edge.kind()==CfgTransition.Kind.INVOKE_NORMAL)return apply(state,owner.outcomes.get(invoke).get(Control.NormalOutcome.INSTANCE),false);
            // Open control does not identify the completed outcome. Preserve every possible old definition.
            state=apply(state,owner.otherwise.get(invoke),true);
            for(var plans:owner.outcomes.get(invoke).values())state=apply(state,plans,true);return state;
        }
    }
    public static final class Execution {
        private final ReachingDefinitions owner;
        private final Engine engine;
        private final DataflowResult<State> stable;
        private Execution(ReachingDefinitions owner,Engine engine,DataflowResult<State> stable){this.owner=owner;this.engine=engine;this.stable=stable;}
        public DataflowResult<State> dataflow(){return stable;}
        public AnalysisKey key(EntryId entry) {
            if(owner.session.context(entry)==null)throw new IllegalArgumentException("unselected entry");
            return new AnalysisKey("reaching-definitions","1",PROFILE,Direction.FORWARD,"finite-interval-events",Map.of("storage",StorageIndex.PROFILE,"effects","regional-effects@1"),entry);
        }
        public Map<String,Long> metrics(){return Map.of("segments",(long)owner.partition.segments().size(),"segmentUpdates",engine.updates,"segmentReads",engine.segmentReads,"eventUnionEntries",engine.eventUnions);}
        public ObservationBatch<ObjectId,DefinitionFact> observe(Iterable<PointQuery<ObjectId>> requests){
            var comparator=Comparator.comparing((ObjectId id)->id.unit().publication().localId()).thenComparing(id->id.unit().localId()).thenComparing(ObjectId::localId);
            return observe(requests,comparator,StorageSubject.NamedObject::new);
        }
        public ObservationBatch<StorageSubject,DefinitionFact> observeStorage(Iterable<PointQuery<StorageSubject>> requests) {
            return observe(requests,StorageSubject.ORDER,java.util.function.Function.identity());
        }
        private <T> ObservationBatch<T,DefinitionFact> observe(Iterable<PointQuery<T>> requests,Comparator<T> comparator,java.util.function.Function<T,StorageSubject> subject) {
            return BatchReplayer.materialize(owner.session,stable,Direction.FORWARD,BOTTOM,requests,comparator,engine::operation,new BatchReplayer.Projection<State,T,DefinitionFact>() {
                @Override public boolean supports(PointQuery<T> query) {
                    return owner.effects.storage().supports(subject.apply(query.subject()),query.point().entry().unit());
                }
                @Override public DefinitionFact project(PointQuery<T> query,State state) { return fact(new PointQuery<>(query.point(),subject.apply(query.subject())),state); }
                @Override public boolean supportsOutcome(PointQuery<T> query) {
                    var site=owner.session.index().site(query.point().operation());
                    return site!=null&&site.operation() instanceof Operations.Invoke invoke&&query.point().outcome()==Control.NormalOutcome.INSTANCE
                        &&invoke.outcomes().known().stream().anyMatch(Control.Normal.class::isInstance);
                }
                @Override public State transferOutcome(PointQuery<T> query,State before) {
                    var operation=owner.session.index().site(query.point().operation()).operation();
                    return engine.apply(before,owner.outcomes.get(operation).get(query.point().outcome()),false);
                }
            });
        }
        private DefinitionFact fact(PointQuery<StorageSubject> query,State state) {
            var storage=owner.effects.storage();var resolution=storage.resolve(query.subject());
            var origins=new LinkedHashSet<>(storage.subjectOrigins(query.subject()));var premises=new LinkedHashSet<PremiseId>();var uncertainties=new LinkedHashSet<>(resolution.uncertainties());
            var contributions=new LinkedHashMap<DefinitionEvent,Set<StorageIndex.Location>>();
            boolean unknown=!(resolution.remainder() instanceof Scopes.NoMemory);
            boolean source=sourceOpen(query);
            for(var gap:owner.sourceGaps)if(resolution.candidates().stream().anyMatch(c->!storage.disjoint(c.location(),gap.location()))) {
                source=true;origins.add(gap.origin());uncertainties.addAll(gap.uncertainties());
            }
            if(state.reached())for(var candidate:resolution.candidates()) {
                origins.addAll(candidate.origins());
                for(var segment:owner.partition.intersecting(candidate.location())) {
                    var location=intersection(segment.location(),candidate.location());
                    for(var handle:engine.value(state,segment)) {
                        var definition=handle.definition;
                        contributions.computeIfAbsent(definition,ignored->new LinkedHashSet<>()).add(location);
                        origins.add(definition.origin());premises.addAll(definition.premises());uncertainties.addAll(definition.uncertainties());unknown|=definition.unknown();
                    }
                }
            }
            if(state.reached()&&query.subject() instanceof StorageSubject.NamedObject named)for(var handle:state.logical.getOrDefault(named.object(),Set.of())) {
                var definition=handle.definition;contributions.putIfAbsent(definition,Set.of());origins.add(definition.origin());uncertainties.addAll(definition.uncertainties());
            }
            var ordered=new ArrayList<>(contributions.keySet());ordered.sort(Comparator.comparing((DefinitionEvent e)->e.operation().map(OperationId::localId).orElse("")).thenComparingInt(DefinitionEvent::slot).thenComparing(e->e.storage().map(StorageId::localId).orElse("")).thenComparing(DefinitionEvent::unknown).thenComparing(DefinitionEvent.ORDER));
            var output=new ArrayList<DefinitionFact.Contribution>();
            for(var event:ordered)output.add(new DefinitionFact.Contribution(event,coalesce(contributions.get(event)).stream().map(l->l.in(query.point().entry())).toList()));
            return new DefinitionFact(query.point(),state.reached()?DefinitionFact.Reachability.REACHABLE:DefinitionFact.Reachability.UNREACHABLE_IN_MODEL,output,state.reached()?unknown:null,!(resolution.remainder() instanceof Scopes.NoMemory),source,evidenceOrder(premises),evidenceOrder(origins),evidenceOrder(uncertainties));
        }
        private boolean sourceOpen(PointQuery<StorageSubject> query) {
            var publication=owner.session.index().publication();var unit=owner.session.index().unit(query.point().entry().unit());
            var object=query.subject() instanceof StorageSubject.NamedObject named?owner.session.index().object(named.object()):null;
            return publication.coverage().inventory()!=Evidence.InventoryStatus.COMPLETE||!publication.coverage().uncertainties().isEmpty()
                ||unit.coverage().inventory()!=Evidence.InventoryStatus.COMPLETE||!unit.coverage().uncertainties().isEmpty()
                ||owner.controlOpen.getOrDefault(unit.id(),false)
                ||!owner.session.context(query.point().entry()).entry().state().uncertainties().isEmpty()
                ||object!=null&&(object.coverage()!=Evidence.CoverageStatus.MODELED||open(object.precision().storage())||open(object.precision().values()));
        }
    }
    private static boolean open(Evidence.Claim claim){return claim.status()!=Evidence.PrecisionStatus.EXACT&&claim.status()!=Evidence.PrecisionStatus.NOT_APPLICABLE;}
    private static <T extends Id> List<T> evidenceOrder(Collection<T> evidence){return evidence.stream().sorted(Comparator.comparing((T id)->id.publication().localId()).thenComparing(Id::localId)).toList();}
    private static StorageIndex.Location intersection(StorageIndex.Location a,StorageIndex.Location b) {
        return a.range().isEmpty()?a:new StorageIndex.Location(a.base(),a.range().get().intersect(b.range().orElseThrow()));
    }
    private static List<StorageIndex.Location> coalesce(Set<StorageIndex.Location> locations) {
        var sorted=new ArrayList<>(locations);sorted.sort(Comparator.comparing((StorageIndex.Location l)->l.base().id().publication().localId()).thenComparing(l->l.base().id().localId()).thenComparing(l->l.range().map(StorageRange::start).orElse(java.math.BigInteger.ZERO)));
        var result=new ArrayList<StorageIndex.Location>();
        for(var location:sorted) {
            if(!result.isEmpty()) {
                var prior=result.getLast();
                if(prior.base().equals(location.base())&&prior.range().isPresent()&&location.range().isPresent()&&prior.range().get().end().equals(Optional.of(location.range().get().start()))) {
                    result.set(result.size()-1,new StorageIndex.Location(prior.base(),Optional.of(new StorageRange(prior.range().get().start(),location.range().get().end()))));continue;
                }
            }
            result.add(location);
        }
        return List.copyOf(result);
    }
}
