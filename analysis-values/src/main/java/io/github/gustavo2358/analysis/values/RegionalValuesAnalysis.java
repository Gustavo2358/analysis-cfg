package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.ReachingDefinitions;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.structure.*;
import java.math.BigInteger;
import java.util.*;

/** Finite images per physical base; uses canonical effects and the shared solver/replayer. */
public final class RegionalValuesAnalysis {
    public static final String PROFILE="regional-text-images@1";
    static final Comparator<ObjectId> OBJECT_ORDER=Comparator.comparing((ObjectId id)->id.unit().publication().localId()).thenComparing(id->id.unit().localId()).thenComparing(ObjectId::localId);
    private final AnalysisSession session;
    private final StatementEffects effects;
    private final StoragePartition partition;
    private final List<StorageIndex.Location> bases;
    private final Map<StorageId,Integer> ordinals=new HashMap<>();
    private final Map<Operation,List<Plan>> operations=new IdentityHashMap<>();
    private final Map<Operation,Map<Control.OutcomeKey,List<Plan>>> outcomes=new IdentityHashMap<>();
    private final Map<Operation,List<Plan>> otherwise=new IdentityHashMap<>();
    private final Map<EntryId,List<Plan>> initial=new HashMap<>();
    private final List<ValueFact.Support> events=new ArrayList<>();
    private final List<SourceGap> sourceGaps=new ArrayList<>();
    private final Set<UnitId> controlOpen=new HashSet<>();
    private record Plan(StatementEffects.Write write,StatementEffects.Target target,int event) { }
    private record SourceGap(StorageIndex.Location location,OriginId origin) { }
    private sealed interface Content permits Bytes,Scalar { }
    private record Bytes(ByteImage image) implements Content { }
    private record Scalar(Optional<Values.TextValue> text,Set<Integer> producers,Set<String> reasons) implements Content {
        Scalar { producers=Set.copyOf(producers);reasons=Set.copyOf(reasons); }
    }
    public enum Status { ACCEPTED, UNSUPPORTED, INVALID_INPUT }
    public record Admission(Status status,String reason,Optional<RegionalValuesAnalysis> analysis) { }
    public static Admission prepare(AnalysisSession session) {
        var effects=new StatementEffects(new StorageIndex(session));
        // Share the established simultaneous-initial-condition admission, including alias conflicts.
        var admission=ReachingDefinitions.prepare(effects);
        if(admission.status()!=ReachingDefinitions.Status.ACCEPTED)
            return new Admission(admission.status()==ReachingDefinitions.Status.INVALID_INPUT?Status.INVALID_INPUT:Status.UNSUPPORTED,admission.reason(),Optional.empty());
        return new Admission(Status.ACCEPTED,null,Optional.of(new RegionalValuesAnalysis(effects,admission.analysis().orElseThrow().partition())));
    }
    private RegionalValuesAnalysis(StatementEffects effects,StoragePartition partition) {
        this.effects=effects;this.partition=partition;session=effects.storage().session();
        bases=effects.storage().bases().stream().map(b->effects.storage().whole(b.header().id()))
            .sorted(Comparator.comparing((StorageIndex.Location l)->l.base().id().localId())).toList();
        for(int i=0;i<bases.size();i++)ordinals.put(bases.get(i).base().id(),i);
        for(var statement:effects.statements()) {
            var op=statement.operation();operations.put(op,compile(statement.writes(),op.header().id(),op.header().origin(),List.of()));
            otherwise.put(op,compile(statement.otherwise(),op.header().id(),op.header().origin(),List.of()));
            var choices=new HashMap<Control.OutcomeKey,List<Plan>>();
            statement.outcomes().forEach((key,writes)->choices.put(key,compile(writes,op.header().id(),op.header().origin(),List.of())));
            outcomes.put(op,Map.copyOf(choices));
            if(open(op.header().precision().control())||op instanceof Operations.Invoke i&&i.outcomes().remainder() instanceof Scopes.WithinControl)
                controlOpen.add(op.header().id().unit());
            if(open(op.header().precision().storage())||open(op.header().precision().values())||open(op.header().precision().effects())) {
                var writes=new ArrayList<>(statement.writes());writes.addAll(statement.otherwise());statement.outcomes().values().forEach(writes::addAll);
                if(writes.isEmpty())controlOpen.add(op.header().id().unit());
                for(var write:writes)for(var target:write.targets())sourceGaps.add(new SourceGap(target.location(),op.header().origin()));
            }
        }
        for(var object:effects.storage().declarations())if(object.coverage()!=Evidence.CoverageStatus.MODELED||open(object.precision().storage())||open(object.precision().values()))
            for(var target:effects.targets(effects.storage().object(object.id()),StatementEffects.Strength.MAY))sourceGaps.add(new SourceGap(target.location(),object.origin()));
        for(var context:session.contexts()) {
            var seeds=new ArrayList<Plan>();int slot=0;var seededLocations=new HashSet<StorageIndex.Location>();
            for(var condition:context.entry().state().conditions()) {
                var resolution=effects.storage().resolve(condition.place());
                StatementEffects.Source source=condition.value() instanceof Entries.LiteralInitial l?new StatementEffects.ExpressionSource(l.value()):new StatementEffects.UnknownSource("ENTRY_CONTENT_NOT_LITERAL");
                var write=new StatementEffects.Write(slot++,Optional.of(condition.place().header().id()),resolution,source,effects.targets(resolution,StatementEffects.Strength.MUST));
                for(var plan:compile(List.of(write),condition.place().header().id(),condition.origin(),condition.premises())) {
                    var target=plan.target();
                    // Admission proved equal simultaneous literals on an identical footprint.
                    // Preserve both supports without retaining the unspecified entry possibility.
                    if(target.strength()==StatementEffects.Strength.MUST&&!seededLocations.add(target.location()))
                        target=new StatementEffects.Target(target.location(),StatementEffects.Strength.MAY,target.sourceApplicable(),target.premises(),target.reasons());
                    seeds.add(new Plan(plan.write(),target,plan.event()));
                }
            }
            initial.put(context.entry().id(),List.copyOf(seeds));
        }
    }
    private List<Plan> compile(List<StatementEffects.Write> writes,Id evidence,OriginId origin,List<PremiseId> initialPremises) {
        var result=new ArrayList<Plan>();
        for(var write:writes)for(var target:write.targets()) {
            var premises=new LinkedHashSet<>(initialPremises);premises.addAll(target.premises());
            var event=events.size();events.add(new ValueFact.Support(evidence,origin,ordered(premises)));
            result.add(new Plan(write,target,event));
        }
        return List.copyOf(result);
    }
    public static final class State {
        private final EntryId entry;
        private final SegmentMap<Set<Content>> bindings;
        private State(EntryId entry,SegmentMap<Set<Content>> bindings){this.entry=entry;this.bindings=bindings;}
        public boolean reached(){return entry!=null;}
        public int explicitBases(){return bindings.size();}
    }
    private static final State BOTTOM=new State(null,new SegmentMap<>());
    private static Scalar unknownScalar(String reason){return new Scalar(Optional.empty(),Set.of(),Set.of(reason));}
    private Content unknown(StorageIndex.Location location,String reason) {
        return location.range().isPresent()?new Bytes(ByteImage.unknown(location.range().get().end().map(e->e.subtract(location.range().get().start())),reason)):unknownScalar(reason);
    }
    private final class Engine implements AnalysisDefinition<State> {
        long contentReads,contentUpdates,alternativeVisits;
        @Override public Direction direction(){return Direction.FORWARD;}
        @Override public State bottom(){return BOTTOM;}
        private Set<Content> value(State state,int ordinal) {
            contentReads++;var present=state.bindings.get(ordinal);
            return present==null?Set.of(unknown(bases.get(ordinal),"UNSPECIFIED_ENTRY_CONTENT")):present;
        }
        @Override public Iterable<Boundary<State>> boundaries(AnalysisSession selected) {
            if(session!=selected)throw new IllegalArgumentException("foreign session");
            var result=new ArrayList<Boundary<State>>();
            for(var context:session.contexts()) {
                var seed=new State(context.entry().id(),new SegmentMap<>());
                result.add(new Boundary<>(context,context.entryNode(),apply(seed,initial.get(seed.entry),false)));
            }
            return result;
        }
        private Set<Content> union(Set<Content> a,Set<Content> b){if(a.containsAll(b))return a;var r=new HashSet<>(a);r.addAll(b);return Set.copyOf(r);}
        @Override public Join<State> joinInto(State a,State b,DomainWork work) {
            if(!b.reached())return new Join<>(a,false);if(!a.reached())return new Join<>(b,true);
            if(!a.entry.equals(b.entry))throw new IllegalArgumentException("different entries");
            final class Accumulator { SegmentMap<Set<Content>> root=a.bindings; }
            var acc=new Accumulator();
            b.bindings.forEach((key,v)->{work.joinEntryVisited();acc.root=acc.root.put(key,union(value(a,key),v));});
            a.bindings.forEach((key,v)->{if(b.bindings.get(key)==null){work.joinEntryVisited();acc.root=acc.root.put(key,union(v,value(b,key)));}});
            return acc.root==a.bindings?new Join<>(a,false):new Join<>(new State(a.entry,acc.root),true);
        }
        @Override public boolean equivalent(State a,State b,DomainWork work) {
            if(a==b)return true;if(!Objects.equals(a.entry,b.entry)||a.bindings.size()!=b.bindings.size())return false;
            var same=new boolean[]{true};a.bindings.forEach((key,v)->{work.stateCompareEntry();if(!v.equals(b.bindings.get(key)))same[0]=false;});return same[0];
        }
        private State apply(State before,List<Plan> plans,boolean forceMay) {
            if(!before.reached()||plans.isEmpty())return before;
            // Group by destination base to keep every same-base read tied to its pre-write image.
            var grouped=new LinkedHashMap<Integer,List<Plan>>();
            for(var plan:plans)grouped.computeIfAbsent(ordinals.get(plan.target.location().base().id()),ignored->new ArrayList<>()).add(plan);
            var root=before.bindings;
            for(var entry:grouped.entrySet()) {
                int ordinal=entry.getKey();var all=new HashSet<Content>();
                for(var original:value(before,ordinal)) {
                    Set<Content> current=Set.of(original);
                    for(var plan:entry.getValue()) {
                        var next=new HashSet<Content>();
                        if(forceMay||plan.target.strength()==StatementEffects.Strength.MAY)next.addAll(current);
                        var replacements=replacements(before,plan,ordinal,original);
                        for(var old:current)for(var replacement:replacements) {
                            alternativeVisits++;
                            next.add(old instanceof Bytes b?new Bytes(b.image().write(plan.target.location().range().orElseThrow(),((Bytes)replacement).image())):replacement);
                        }
                        current=Set.copyOf(next);
                    }
                    all.addAll(current);
                }
                var updated=root.put(ordinal,Set.copyOf(all));if(updated!=root)contentUpdates++;root=updated;
            }
            return root==before.bindings?before:new State(before.entry,root);
        }
        private Set<Content> replacements(State before,Plan plan,int destination,Content original) {
            var target=plan.target.location();
            if(!plan.target.sourceApplicable())return Set.of(unknown(target,"UNPROVEN_WRITE_DESTINATION"));
            var source=plan.write.source();
            if(source instanceof StatementEffects.CapturedBytes copy) {
                var c=copy.source().candidates().getFirst();int ordinal=ordinals.get(c.location().base().id());
                var result=new HashSet<Content>();
                for(var content:ordinal==destination?Set.of(original):value(before,ordinal))
                    result.add(new Bytes(((Bytes)content).image().slice(c.location().range().orElseThrow()).copied(plan.event)));
                return Set.copyOf(result);
            }
            if(source instanceof StatementEffects.UnknownSource u)return Set.of(unknown(target,u.reason()));
            var expression=((StatementEffects.ExpressionSource)source).value();
            if(expression instanceof Expressions.Literal literal) {
                var v=literal.value();
                if(target.range().isEmpty())return Set.of(v instanceof Values.TextValue t?new Scalar(Optional.of(t),Set.of(plan.event),Set.of()):unknownScalar("NON_TEXT_LOGICAL_VALUE"));
                var range=target.range().get();var extent=range.end().map(e->e.subtract(range.start()));
                if(v instanceof Values.BytesValue bytes&&extent.equals(Optional.of(BigInteger.valueOf(bytes.octets().size()))))
                    return Set.of(new Bytes(ByteImage.literal(bytes,plan.event)));
                if(v instanceof Values.TextValue text&&extent.isPresent()) {
                    var codecs=plan.write.destination().candidates().stream().filter(c->c.location().equals(target)).map(StorageIndex.Candidate::codec).distinct().toList();
                    if(codecs.size()==1&&codecs.getFirst().isPresent()) {
                        var encoded=MemoryCodecs.encodeText(codecs.getFirst().get(),text,extent.get());
                        if(encoded.value().isPresent())return Set.of(new Bytes(ByteImage.literal(encoded.value().get(),plan.event)));
                    }
                }
                return Set.of(unknown(target,"UNINTERPRETED_LITERAL_REPRESENTATION"));
            }
            if(target.range().isEmpty()&&expression instanceof Expressions.Read read) {
                var resolution=effects.storage().resolve(read.place());var result=new HashSet<Content>();
                if(!(resolution.remainder() instanceof Scopes.NoMemory))result.add(unknownScalar("READ_LOCATION_REMAINDER"));
                for(var candidate:resolution.candidates()) {
                    int ordinal=ordinals.get(candidate.location().base().id());
                    for(var content:ordinal==destination?Set.of(original):value(before,ordinal))result.add(project(content,candidate));
                }
                if(!result.isEmpty())return Set.copyOf(result);
            }
            return Set.of(unknown(target,"UNINTERPRETED_VALUE_EXPRESSION"));
        }
        private State operation(State state,Operation operation) {
            var plans=operations.get(operation);if(plans==null)throw new IllegalArgumentException("foreign operation");return apply(state,plans,false);
        }
        @Override public State transferBlock(AnalysisPoint point,State anchor,DomainWork work) {
            if(!(point.node().source() instanceof CfgNode.SequenceNode node))return anchor;
            var state=anchor;for(var op:node.source().instructions()){work.operationTransferred();state=operation(state,op);}
            work.operationTransferred();return operation(state,node.source().terminator());
        }
        @Override public State transferEdge(AnalysisPoint point,CfgTransition edge,State state,DomainWork work) {
            if(!(point.node().source() instanceof CfgNode.SequenceNode node)||!(node.source().terminator() instanceof Operations.Invoke invoke))return state;
            if(edge.kind()==CfgTransition.Kind.INVOKE_NORMAL)return apply(state,outcomes.get(invoke).get(Control.NormalOutcome.INSTANCE),false);
            state=apply(state,otherwise.get(invoke),true);for(var plans:outcomes.get(invoke).values())state=apply(state,plans,true);return state;
        }
        private Map<String,Long> metrics(){return Map.of("contentReads",contentReads,"contentUpdates",contentUpdates,"alternativeVisits",alternativeVisits);}
    }
    private Scalar project(Content content,StorageIndex.Candidate candidate) {
        if(content instanceof Scalar scalar)return scalar;
        var range=candidate.location().range().orElseThrow();var read=((Bytes)content).image().read(range);
        if(read.bytes().isEmpty())return new Scalar(Optional.empty(),Set.of(),read.reasons());
        if(candidate.codec().isEmpty()||range.end().isEmpty())return unknownScalar("UNINTERPRETED_VIEW");
        var decoded=MemoryCodecs.decodeText(candidate.codec().get(),read.bytes().get(),range.end().get().subtract(range.start()));
        if(decoded.value().isEmpty())return unknownScalar(decoded.status().name());
        // W3 admits a complete slice of one literal producer. Composition is qualified in W5.
        var producers=new HashSet<Integer>();int prior=-1;BigInteger expected=null;
        for(var part:read.parts()) {
            if(prior!=-1&&(part.producer()!=prior||!part.producerOffset().equals(expected)))return unknownScalar("FRAGMENT_COMPOSITION_PENDING");
            producers.add(part.producer());prior=part.producer();expected=part.producerOffset().add(part.range().end().orElseThrow().subtract(part.range().start()));
        }
        return new Scalar(decoded.value(),producers,Set.of());
    }
    public Execution execute(){var engine=new Engine();return new Execution(engine,DataflowSolver.solve(session,engine));}
    public final class Execution {
        private final Engine engine;private final DataflowResult<State> stable;private final Map<String,Long> solveMetrics;
        private Execution(Engine engine,DataflowResult<State> stable){this.engine=engine;this.stable=stable;solveMetrics=engine.metrics();}
        public DataflowResult<State> dataflow(){return stable;}
        public Map<String,Long> solveMetrics(){return solveMetrics;}
        /** Cumulative domain work, including observed replay; no semantic budget. */
        public Map<String,Long> metrics(){return engine.metrics();}
        public Map<String,Long> preparationMetrics(){var result=new TreeMap<>(effects.preparationMetrics());result.putAll(effects.storage().preparationMetrics());result.put("partitionSegments",(long)partition.segments().size());result.put("eventsPrepared",(long)events.size());return Map.copyOf(result);}
        public ObservationBatch<ObjectId,RegionalValueFact> observe(Iterable<PointQuery<ObjectId>> requests) {
            return BatchReplayer.materialize(session,stable,Direction.FORWARD,BOTTOM,requests,OBJECT_ORDER,engine::operation,new BatchReplayer.Projection<State,ObjectId,RegionalValueFact>() {
                @Override public boolean supports(PointQuery<ObjectId> query) {
                    var object=session.index().object(query.subject());var unit=session.index().unit(query.point().entry().unit());
                    return object!=null&&object.typeRef().equals(Types.known(Types.Builtin.TEXT))&&unit!=null&&(object.id().unit().equals(unit.id())||unit.visibleObjects().contains(object.id()));
                }
                @Override public RegionalValueFact project(PointQuery<ObjectId> query,State state){return fact(query,state);}
                @Override public boolean supportsOutcome(PointQuery<ObjectId> query) {
                    var site=session.index().site(query.point().operation());return site!=null&&site.operation() instanceof Operations.Invoke invoke
                        &&query.point().outcome()==Control.NormalOutcome.INSTANCE&&invoke.outcomes().known().stream().anyMatch(Control.Normal.class::isInstance);
                }
                @Override public State transferOutcome(PointQuery<ObjectId> query,State before){return engine.apply(before,outcomes.get(session.index().site(query.point().operation()).operation()).get(query.point().outcome()),false);}
            });
        }
        private RegionalValueFact fact(PointQuery<ObjectId> query,State state) {
            var storage=effects.storage();var resolution=storage.object(query.subject());var reasons=new TreeSet<String>(resolution.reasons());
            boolean model=!(resolution.remainder() instanceof Scopes.NoMemory);if(model)reasons.add("READ_LOCATION_REMAINDER");
            var supports=new TreeMap<String,Set<Integer>>();var origins=new LinkedHashSet<>(storage.objectOrigins(query.subject()));
            boolean source=sourceOpen(query);
            for(var gap:sourceGaps)if(resolution.candidates().stream().anyMatch(c->!storage.disjoint(c.location(),gap.location()))) {source=true;origins.add(gap.origin());}
            if(state.reached())for(var candidate:resolution.candidates()) {
                origins.addAll(candidate.origins());
                for(var content:engine.value(state,ordinals.get(candidate.location().base().id()))) {
                    var value=RegionalValuesAnalysis.this.project(content,candidate);
                    if(value.text().isEmpty()){model=true;reasons.addAll(value.reasons());}
                    else supports.computeIfAbsent(value.text().get().value(),ignored->new HashSet<>()).addAll(value.producers());
                }
            }
            if(state.reached()&&supports.isEmpty()){model=true;reasons.add("NO_KNOWN_TEXT_PROJECTION");}
            var candidates=new ArrayList<Values.TextValue>();var associations=new ArrayList<ValueFact.CandidateSupport>();
            var premises=new LinkedHashSet<PremiseId>();var evidence=new LinkedHashSet<Id>();
            for(var entry:supports.entrySet()) {
                var text=new Values.TextValue(entry.getKey());candidates.add(text);
                var producers=entry.getValue().stream().map(events::get).distinct().sorted(Comparator.comparing((ValueFact.Support s)->s.evidence().localId()).thenComparing(s->s.origin().localId())).toList();
                associations.add(new ValueFact.CandidateSupport(text,producers));
                for(var producer:producers){premises.addAll(producer.premises());evidence.add(producer.evidence());origins.add(producer.origin());}
            }
            return new RegionalValueFact(query.point(),query.subject(),resolution.candidates().stream().map(c->new RegionalValueFact.Interpretation(c.location().in(query.point().entry()),c.codec())).toList(),
                state.reached()?ValueFact.Reachability.REACHABLE:ValueFact.Reachability.UNREACHABLE_IN_MODEL,state.reached()?candidates:null,state.reached()?model:null,
                source,state.reached()&&model||source,ordered(premises),ordered(evidence),ordered(origins),associations,state.reached()?List.copyOf(reasons):List.of());
        }
    }
    private boolean sourceOpen(PointQuery<ObjectId> query) {
        var p=session.index().publication();var unit=session.index().unit(query.point().entry().unit());var object=session.index().object(query.subject());
        return p.coverage().inventory()!=Evidence.InventoryStatus.COMPLETE||!p.coverage().uncertainties().isEmpty()
            ||unit.coverage().inventory()!=Evidence.InventoryStatus.COMPLETE||!unit.coverage().uncertainties().isEmpty()||controlOpen.contains(unit.id())
            ||!session.context(query.point().entry()).entry().state().uncertainties().isEmpty()
            ||object.coverage()!=Evidence.CoverageStatus.MODELED||open(object.precision().storage())||open(object.precision().values());
    }
    private static boolean open(Evidence.Claim c){return c.status()!=Evidence.PrecisionStatus.EXACT&&c.status()!=Evidence.PrecisionStatus.NOT_APPLICABLE;}
    private static <T extends Id> List<T> ordered(Collection<T> ids){return ids.stream().sorted(Comparator.comparing((T id)->id.publication().localId()).thenComparing(Id::localId)).toList();}
}
