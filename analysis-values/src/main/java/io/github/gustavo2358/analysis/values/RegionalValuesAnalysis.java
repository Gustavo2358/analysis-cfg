package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.ReachingDefinitions;
import io.github.gustavo2358.analysis.rd.DefinitionEvent;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.structure.*;
import java.math.BigInteger;
import java.util.*;

/** Finite joint images of copy-connected bases; canonical effects and the shared solver/replayer. */
public final class RegionalValuesAnalysis {
    public static final String PROFILE="regional-text-images@2";
    static final Comparator<ObjectId> OBJECT_ORDER=Comparator.comparing((ObjectId id)->id.unit().publication().localId()).thenComparing(id->id.unit().localId()).thenComparing(ObjectId::localId);
    private final AnalysisSession session;
    private final StatementEffects effects;
    private final StoragePartition partition;
    private final List<StorageIndex.Location> bases;
    private final Map<StorageId,Integer> ordinals=new HashMap<>();
    private final List<List<Integer>> groups;
    private final int[] groupOf,positionInGroup;
    private final Map<StatementEffects.Write,StorageIndex.Resolution> preparedReads=new IdentityHashMap<>();
    private final Map<Operation,List<Plan>> operations=new IdentityHashMap<>();
    private final Map<Operation,Map<Control.OutcomeKey,List<Plan>>> outcomes=new IdentityHashMap<>();
    private final Map<Operation,List<Plan>> otherwise=new IdentityHashMap<>();
    private final Map<EntryId,List<Plan>> initial=new HashMap<>();
    private final List<ValueFact.Support> events=new ArrayList<>();
    private final List<PreparedEvent> eventDetails=new ArrayList<>();
    private final List<SourceGap> sourceGaps=new ArrayList<>();
    private final Map<StorageIndex.Location,List<CapturedGap>> capturedGaps=new HashMap<>();
    private final Set<UnitId> controlOpen=new HashSet<>();
    private record Plan(StatementEffects.Write write,StatementEffects.Target target,int event,StatementEffects.LogicalTarget logical) { }
    private record LogicalValue(Values.TextValue text,int event) { }
    private record SourceGap(StorageIndex.Location location,OriginId origin,List<UncertaintyId> uncertainties) {
        SourceGap { uncertainties=List.copyOf(uncertainties); }
    }
    private record PreparedEvent(Operation operation,Entries.InitialCondition initial,StatementEffects.Write write,StatementEffects.Target target,Optional<Control.OutcomeKey> outcome,StatementEffects.LogicalTarget logical) {
        DefinitionEvent definition(EntryId entry) {
            if(logical!=null)return operation==null?DefinitionEvent.logicalInitial(entry,initial,write.slot(),logical.object(),write.destination()):DefinitionEvent.logicalWrite(entry,operation,write,logical,outcome);
            return operation==null?DefinitionEvent.initial(entry,initial,write.slot(),target,write.destination()):DefinitionEvent.write(entry,operation,write,target,outcome);
        }
    }
    private record CapturedGap(int gap,Optional<StorageRange> range) { }
    private sealed interface Content permits Bytes,Scalar { }
    private record Bytes(ByteImage image) implements Content { }
    private record CapturedRead(StorageIndex.Location sourceRange,StorageIndex.Location sourceContribution,StorageIndex.Location destinationContribution) { }
    private record Trace(StorageIndex.Location observed,Optional<Values.BytesValue> bytes,int producer,Optional<StorageIndex.Location> original,
                         Map<Integer,Set<CapturedRead>> captures,Set<Integer> gaps,Set<String> reasons,Set<ByteImage.LogicalSupport> logicalSupports) {
        Trace(StorageIndex.Location observed,Optional<Values.BytesValue> bytes,int producer,Optional<StorageIndex.Location> original,Map<Integer,Set<CapturedRead>> captures,Set<Integer> gaps,Set<String> reasons){this(observed,bytes,producer,original,captures,gaps,reasons,Set.of());}
        Trace {
            logicalSupports=Set.copyOf(logicalSupports);
            var immutable=new HashMap<Integer,Set<CapturedRead>>();captures.forEach((key,value)->immutable.put(key,Set.copyOf(value)));captures=Map.copyOf(immutable);gaps=Set.copyOf(gaps);reasons=Set.copyOf(reasons);
        }
        Trace withGap(int gap){var next=new HashSet<>(gaps);next.add(gap);return new Trace(observed,bytes,producer,original,captures,next,reasons,logicalSupports);}
        Trace captured(int event,StorageIndex.Location source,StorageIndex.Location destination) {
            var next=new HashMap<>(captures);var contributions=new HashSet<>(next.getOrDefault(event,Set.of()));
            contributions.add(new CapturedRead(source,observed,destination));next.put(event,Set.copyOf(contributions));
            return new Trace(destination,bytes,producer,original,next,gaps,reasons,logicalSupports);
        }
    }
    private record Store(List<Content> contents) {
        Store { contents=List.copyOf(contents); }
        Store put(int position,Content content) {
            if(contents.get(position).equals(content))return this;
            var updated=new ArrayList<>(contents);updated.set(position,content);return new Store(updated);
        }
    }
    private record Scalar(Optional<Values.TextValue> text,Set<Integer> producers,Set<String> reasons,Set<Integer> sourceGaps,List<Trace> traces) implements Content {
        Scalar { producers=Set.copyOf(producers);reasons=Set.copyOf(reasons);sourceGaps=Set.copyOf(sourceGaps);traces=List.copyOf(traces); }
        Scalar captured(int event,StorageIndex.Location source,StorageIndex.Location destination) {
            return new Scalar(text,producers,reasons,sourceGaps,traces.stream().map(t->t.captured(event,source,destination)).toList());
        }
    }
    public enum Status { ACCEPTED, UNSUPPORTED, INVALID_INPUT }
    public record Admission(Status status,String reason,Optional<RegionalValuesAnalysis> analysis) { }
    public static Admission prepare(AnalysisSession session) {
        var effects=new StatementEffects(new StorageIndex(session));
        // Share validated original entry facts; operational alias impacts never decide admission.
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
            var op=statement.operation();operations.put(op,compile(statement.writes(),op.header().id(),op.header().origin(),List.of(),op,null,Optional.of(Control.NormalOutcome.INSTANCE)));
            otherwise.put(op,compile(statement.otherwise(),op.header().id(),op.header().origin(),List.of(),op,null,Optional.empty()));
            var choices=new HashMap<Control.OutcomeKey,List<Plan>>();
            statement.outcomes().forEach((key,writes)->choices.put(key,compile(writes,op.header().id(),op.header().origin(),List.of(),op,null,Optional.of(key))));
            outcomes.put(op,Map.copyOf(choices));
            if(open(op.header().precision().control())||op instanceof Operations.Invoke i&&i.outcomes().remainder() instanceof Scopes.WithinControl)
                controlOpen.add(op.header().id().unit());
            if(open(op.header().precision().storage())||open(op.header().precision().values())||open(op.header().precision().effects())) {
                var writes=new ArrayList<>(statement.writes());writes.addAll(statement.otherwise());statement.outcomes().values().forEach(writes::addAll);
                if(writes.isEmpty())controlOpen.add(op.header().id().unit());
                var gaps=new LinkedHashSet<>(op.header().uncertainties());gaps.addAll(op.header().precision().storage().reasons());gaps.addAll(op.header().precision().values().reasons());gaps.addAll(op.header().precision().effects().reasons());
                for(var write:writes)for(var target:write.targets())sourceGaps.add(new SourceGap(target.location(),op.header().origin(),ordered(gaps)));
            }
        }
        for(var object:effects.storage().declarations())if(object.coverage()!=Evidence.CoverageStatus.MODELED||open(object.precision().storage())||open(object.precision().values())) {
            var resolution=effects.storage().object(object.id());var gaps=new LinkedHashSet<>(resolution.uncertainties());gaps.addAll(object.precision().storage().reasons());gaps.addAll(object.precision().values().reasons());
            for(var target:effects.targets(resolution,StatementEffects.Strength.MAY))sourceGaps.add(new SourceGap(target.location(),object.origin(),ordered(gaps)));
        }
        for(var unit:session.index().publication().units())if(session.index().partialControl(unit.id()) || session.index().unprovedPreconditions(unit.id()))controlOpen.add(unit.id());
        for(var context:session.contexts()) {
            var seeds=new ArrayList<Plan>();int slot=0;var seededLocations=new HashSet<StorageIndex.Location>();
            // Simultaneous strong facts initialize first; possible support and open entry
            // content then widen that boundary. Input order is not overwrite authority.
            for(var fact:EntryFacts.admitted(effects.storage(),context.entry())) {
                var condition=fact.condition();var resolution=fact.resolution();
                var sources=new ArrayList<StatementEffects.Source>();
                boolean possible=condition.value() instanceof Entries.PossibleLiterals;
                if(condition.value() instanceof Entries.PossibleLiterals p)
                    p.candidates().forEach(l->sources.add(new StatementEffects.ExpressionSource(l)));
                else sources.add(condition.value() instanceof Entries.LiteralInitial l?new StatementEffects.ExpressionSource(l.value()):new StatementEffects.UnknownSource("ENTRY_CONTENT_NOT_LITERAL"));
                var strength=fact.strength();
                for(var source:sources) {
                    boolean logical=possible&&condition.place() instanceof Places.ObjectPlace&&!resolution.exact();
                    var write=new StatementEffects.Write(slot++,Optional.of(condition.place().header().id()),resolution,source,logical?List.of():effects.targets(resolution,strength),StatementEffects.Selection.SINGLE_DESTINATION,strength,
                        logical?List.of(new StatementEffects.LogicalTarget(((Places.ObjectPlace)condition.place()).object(),true)):List.of());
                    for(var plan:compile(List.of(write),condition.place().header().id(),condition.origin(),condition.premises(),null,condition,Optional.empty())) {
                        if(plan.logical()!=null){seeds.add(plan);continue;}
                        var target=plan.target();
                        // Equal simultaneous strong literals retain both supports; a possible
                        // entry uses MAY from the outset, preserving unspecified entry content.
                        var selectedWrite=plan.write();
                        if(target.location().range().isEmpty()&&target.strength()==StatementEffects.Strength.MUST&&!seededLocations.add(target.location())) {
                            target=new StatementEffects.Target(target.location(),StatementEffects.Strength.MAY,target.sourceApplicable(),target.premises(),target.reasons());
                            selectedWrite=new StatementEffects.Write(selectedWrite.slot(),selectedWrite.occurrence(),selectedWrite.destination(),selectedWrite.source(),selectedWrite.targets(),selectedWrite.selection(),StatementEffects.Strength.MAY);
                        }
                        seeds.add(new Plan(selectedWrite,target,plan.event(),null));
                    }
                }
            }
            initial.put(context.entry().id(),List.copyOf(seeds));
        }
        // Static read/write connectivity preserves branch correlations before the first copy executes.
        int[] parent=new int[bases.size()];for(int i=0;i<parent.length;i++)parent[i]=i;
        var allPlans=new ArrayList<Plan>();operations.values().forEach(allPlans::addAll);otherwise.values().forEach(allPlans::addAll);
        outcomes.values().forEach(m->m.values().forEach(allPlans::addAll));initial.values().forEach(allPlans::addAll);
        for(var plan:allPlans)if(plan.logical==null&&plan.target.sourceApplicable()) {
            var reads=readSource(plan.write);
            if(reads!=null)for(var source:reads.candidates()) {
                capturedGaps.computeIfAbsent(source.location(),this::captureGaps);
                int a=representative(parent,ordinals.get(source.location().base().id()));
                int b=representative(parent,ordinals.get(plan.target.location().base().id()));
                parent[Math.max(a,b)]=Math.min(a,b);
            }
        }
        var components=new TreeMap<Integer,List<Integer>>();
        for(int i=0;i<bases.size();i++)components.computeIfAbsent(representative(parent,i),ignored->new ArrayList<>()).add(i);
        groups=components.values().stream().map(List::copyOf).toList();groupOf=new int[bases.size()];positionInGroup=new int[bases.size()];
        for(int g=0;g<groups.size();g++)for(int i=0;i<groups.get(g).size();i++){int ordinal=groups.get(g).get(i);groupOf[ordinal]=g;positionInGroup[ordinal]=i;}
    }
    private StorageIndex.Resolution readSource(StatementEffects.Write write) {
        if(write.source() instanceof StatementEffects.CapturedBytes copy)return copy.source();
        return preparedReads.computeIfAbsent(write,w->{
            if(!(w.source() instanceof StatementEffects.ExpressionSource e))return null;
            Expression value=e.value();if(value instanceof Expressions.FitText fit)value=fit.value();
            return value instanceof Expressions.Read read?effects.storage().resolve(read.place()):null;
        });
    }
    private static int representative(int[] parent,int member) {
        int root=member;while(parent[root]!=root)root=parent[root];
        while(parent[member]!=member){int next=parent[member];parent[member]=root;member=next;}return root;
    }
    private List<Plan> compile(List<StatementEffects.Write> writes,Id evidence,OriginId origin,List<PremiseId> initialPremises,Operation operation,Entries.InitialCondition initial,Optional<Control.OutcomeKey> outcome) {
        var result=new ArrayList<Plan>();
        for(var write:writes)for(var target:write.targets()) {
            var premises=new LinkedHashSet<>(initialPremises);premises.addAll(target.premises());
            var event=events.size();events.add(new ValueFact.Support(evidence,origin,ordered(premises)));
            eventDetails.add(new PreparedEvent(operation,initial,write,target,outcome,null));
            result.add(new Plan(write,target,event,null));
        }
        for(var write:writes)for(var logical:write.logicalTargets()) {
            int event=events.size();events.add(new ValueFact.Support(evidence,origin,ordered(initialPremises)));
            eventDetails.add(new PreparedEvent(operation,initial,write,null,outcome,logical));
            result.add(new Plan(write,null,event,logical));
        }
        return List.copyOf(result);
    }
    public static final class State {
        private final EntryId entry;
        private final SegmentMap<Set<Store>> bindings;
        private final Map<ObjectId,Set<LogicalValue>> logical;
        private State(EntryId entry,SegmentMap<Set<Store>> bindings,Map<ObjectId,Set<LogicalValue>> logical){this.entry=entry;this.bindings=bindings;this.logical=Map.copyOf(logical);}
        public boolean reached(){return entry!=null;}
        public int explicitBases(){int[] count={0};bindings.forEach((g,stores)->count[0]+=stores.iterator().next().contents().size());return count[0];}
    }
    private static final State BOTTOM=new State(null,new SegmentMap<>(),Map.of());
    private Content unknown(StorageIndex.Location location,String reason) {return unknown(location,reason,-1);}
    private Content unknown(StorageIndex.Location location,String reason,int event) {
        return location.range().isPresent()?new Bytes(ByteImage.unknown(location.range().get().end().map(e->e.subtract(location.range().get().start())),reason,event))
            :new Scalar(Optional.empty(),Set.of(),Set.of(reason),Set.of(),List.of(new Trace(location,Optional.empty(),event,Optional.empty(),Map.of(),Set.of(),Set.of(reason))));
    }
    final class Engine implements AnalysisDefinition<State> {
        long contentReads,contentUpdates,alternativeVisits;
        @Override public Direction direction(){return Direction.FORWARD;}
        @Override public State bottom(){return BOTTOM;}
        private Set<Store> value(State state,int group) {
            contentReads++;var present=state.bindings.get(group);
            return present==null?Set.of(new Store(groups.get(group).stream().map(i->unknown(bases.get(i),"UNSPECIFIED_ENTRY_CONTENT")).toList())):present;
        }
        private Content content(Store store,int ordinal){return store.contents().get(positionInGroup[ordinal]);}
        @Override public Iterable<Boundary<State>> boundaries(AnalysisSession selected) {
            if(session!=selected)throw new IllegalArgumentException("foreign session");
            var result=new ArrayList<Boundary<State>>();
            for(var context:session.contexts()) {
                var seed=new State(context.entry().id(),new SegmentMap<>(),Map.of());
                result.add(new Boundary<>(context,context.entryNode(),apply(seed,initial.get(seed.entry),false)));
            }
            return result;
        }
        private Set<Store> union(Set<Store> a,Set<Store> b){if(a.containsAll(b))return a;var r=new HashSet<>(a);r.addAll(b);return Set.copyOf(r);}
        @Override public Join<State> joinInto(State a,State b,DomainWork work) {
            if(!b.reached())return new Join<>(a,false);if(!a.reached())return new Join<>(b,true);
            if(!a.entry.equals(b.entry))throw new IllegalArgumentException("different entries");
            final class Accumulator { SegmentMap<Set<Store>> root=a.bindings; }
            var acc=new Accumulator();
            b.bindings.forEach((key,v)->{work.joinEntryVisited();acc.root=acc.root.put(key,union(value(a,key),v));});
            a.bindings.forEach((key,v)->{if(b.bindings.get(key)==null){work.joinEntryVisited();acc.root=acc.root.put(key,union(v,value(b,key)));}});
            var logical=new HashMap<>(a.logical);b.logical.forEach((key,v)->{work.joinEntryVisited();logical.merge(key,v,Engine::unionLogical);});
            return acc.root==a.bindings&&logical.equals(a.logical)?new Join<>(a,false):new Join<>(new State(a.entry,acc.root,logical),true);
        }
        @Override public boolean equivalent(State a,State b,DomainWork work) {
            if(a==b)return true;if(!Objects.equals(a.entry,b.entry)||a.bindings.size()!=b.bindings.size()||!a.logical.equals(b.logical))return false;
            var same=new boolean[]{true};a.bindings.forEach((key,v)->{work.stateCompareEntry();if(!v.equals(b.bindings.get(key)))same[0]=false;});return same[0];
        }
        private State apply(State before,List<Plan> plans,boolean forceMay) {
            if(!before.reached()||plans.isEmpty())return before;
            // Preserve write occurrence grouping; each source is captured in the same pre-operation store.
            var grouped=new LinkedHashMap<Integer,LinkedHashMap<StatementEffects.Write,List<Plan>>>();
            for(var plan:plans)if(plan.logical==null)grouped.computeIfAbsent(groupOf[ordinals.get(plan.target.location().base().id())],ignored->new LinkedHashMap<>())
                .computeIfAbsent(plan.write,ignored->new ArrayList<>()).add(plan);
            var root=before.bindings;
            for(var entry:grouped.entrySet()) {
                int group=entry.getKey();var all=new HashSet<Store>();
                for(var original:value(before,group)) {
                    Set<Store> current=Set.of(original);
                    for(var occurrence:entry.getValue().entrySet())current=write(current,original,group,occurrence.getKey(),occurrence.getValue(),forceMay,before.logical);
                    all.addAll(current);
                }
                var updated=root.put(group,Set.copyOf(all));if(updated!=root)contentUpdates++;root=updated;
            }
            var logical=new HashMap<>(before.logical);
            for(var plan:plans)if(plan.logical!=null&&plan.logical.sourceApplicable()) {
                var supported=logicalReplacements(before,plan);
                if(!supported.isEmpty())logical.merge(plan.logical.object(),supported,KillAuthority::weakUpdate);
            }
            return root==before.bindings&&logical.equals(before.logical)?before:new State(before.entry,root,logical);
        }
        private static Set<LogicalValue> unionLogical(Set<LogicalValue> a,Set<LogicalValue> b) {
            if(a.containsAll(b))return a;var result=new HashSet<>(a);result.addAll(b);return Set.copyOf(result);
        }
        private Set<LogicalValue> logicalReplacements(State captured,Plan plan) {
            // Open physical binding gives no authority to kill. Unknown widens the already-open
            // domain; only explicit supported expression values add logical candidates.
            if(!(plan.write.source() instanceof StatementEffects.ExpressionSource expression))return Set.of();
            if(expression.value() instanceof Expressions.Literal literal&&literal.value() instanceof Values.TextValue text)
                return Set.of(new LogicalValue(text,plan.event));
            if(expression.value() instanceof Expressions.Read read) {
                var result=new HashSet<LogicalValue>();
                for(var object:effects.storage().explicitObjects(read.place()))result.addAll(captured.logical.getOrDefault(object,Set.of()));
                for(var candidate:effects.storage().resolve(read.place()).candidates()) {
                    int ordinal=ordinals.get(candidate.location().base().id());
                    for(var store:value(captured,groupOf[ordinal])) {
                        var value=RegionalValuesAnalysis.this.project(content(store,ordinal),candidate);
                        if(value.text().isPresent())for(int event:value.producers())result.add(new LogicalValue(value.text().get(),event));
                    }
                }
                for(var value:List.copyOf(result))result.add(new LogicalValue(value.text(),plan.event));
                return Set.copyOf(result);
            }
            return Set.of();
        }
        private Set<Store> write(Set<Store> current,Store captured,int group,StatementEffects.Write write,List<Plan> plans,boolean forceMay,Map<ObjectId,Set<LogicalValue>> logicalInputs) {
            if(write.selection()==StatementEffects.Selection.MAY_SET) {
                for(var plan:plans)current=weak(current,captured,plan,true,logicalInputs);return current;
            }
            var next=new HashSet<Store>();
            var execution=forceMay?KillAuthority.Execution.POSSIBLE:KillAuthority.Execution.REQUIRED;
            if(!KillAuthority.exhaustive(write,plans.stream().map(Plan::target).toList(),execution))next.addAll(current);
            for(var plan:plans)if(plan.target.sourceApplicable())for(var old:current) {
                var replacement=replace(old,captured,plan,logicalInputs);var authority=KillAuthority.selected(write,plan.target,execution);
                next.addAll(authority.isPresent()?KillAuthority.strongOverwrite(authority.get(),replacement):KillAuthority.weakUpdate(Set.of(old),replacement));
            }
            // No direct destination in this factor: only conservative possible alias/scope impacts.
            if(next.isEmpty())next.addAll(current);
            current=Set.copyOf(next);
            for(var plan:plans)if(!plan.target.sourceApplicable())current=weak(current,captured,plan,true,logicalInputs);
            return current;
        }
        private Set<Store> weak(Set<Store> current,Store captured,Plan plan,boolean keep,Map<ObjectId,Set<LogicalValue>> logicalInputs) {
            var next=new HashSet<Store>();if(keep)next.addAll(current);
            for(var old:current)next.addAll(replace(old,captured,plan,logicalInputs));return Set.copyOf(next);
        }
        private Set<Store> replace(Store old,Store captured,Plan plan,Map<ObjectId,Set<LogicalValue>> logicalInputs) {
            int ordinal=ordinals.get(plan.target.location().base().id());var prior=content(old,ordinal);var result=new HashSet<Store>();
            for(var replacement:replacements(plan,captured,logicalInputs)) {
                alternativeVisits++;
                Content updated=replacement;
                if(prior instanceof Bytes b) {
                    var range=plan.target.location().range().orElseThrow();var image=((Bytes)replacement).image();
                    updated=new Bytes(eventDetails.get(plan.event).initial()!=null&&eventDetails.get(plan.event).initial().value() instanceof Entries.LiteralInitial
                        ?b.image().initialize(range,image):b.image().write(range,image));
                }
                result.add(old.put(positionInGroup[ordinal],updated));
            }
            return result;
        }
        private Set<Content> replacements(Plan plan,Store captured,Map<ObjectId,Set<LogicalValue>> logicalInputs) {
            var target=plan.target.location();
            if(!plan.target.sourceApplicable())return Set.of(unknown(target,"UNPROVEN_WRITE_DESTINATION",plan.event));
            var source=plan.write.source();
            if(source instanceof StatementEffects.CapturedBytes copy) {
                var c=copy.source().candidates().getFirst();int ordinal=ordinals.get(c.location().base().id());
                var sourceRange=c.location().range().orElseThrow();
                return Set.of(new Bytes(((Bytes)capture(content(captured,ordinal),c.location())).image().slice(sourceRange).copied(plan.event,sourceRange.start())));
            }
            if(source instanceof StatementEffects.UnknownSource u)return Set.of(unknown(target,u.reason(),plan.event));
            var expression=((StatementEffects.ExpressionSource)source).value();
            var logicalRead=expression instanceof Expressions.Read r?r:expression instanceof Expressions.FitText f&&f.value() instanceof Expressions.Read r?r:null;
            if(target.range().isPresent()&&logicalRead!=null) {
                var reads=readSource(plan.write);var result=new HashSet<Content>();
                var codecs=plan.write.destination().candidates().stream().filter(c->c.location().equals(target)).map(StorageIndex.Candidate::codec).distinct().toList();
                var extent=target.range().get().end().map(e->e.subtract(target.range().get().start()));
                if(!(reads.remainder() instanceof Scopes.NoMemory))result.add(unknown(target,"READ_LOCATION_REMAINDER",plan.event));
                // Keep each admitted physical alternative and its selected source range. A Choice
                // remainder is not evidence against bytes already supported by another alternative.
                for(int i=0;i<reads.candidates().size();i++) {
                    var candidate=reads.candidates().get(i);var range=candidate.location().range();
                    if(expression instanceof Expressions.FitText fit&&extent.filter(fit.length()::equals).isPresent()
                            &&codecs.size()==1&&codecs.getFirst().filter(MemoryCodecs::isIbm1047).isPresent()
                            &&range.isPresent()&&range.get().end().isPresent()&&candidate.codec().filter(MemoryCodecs::isIbm1047).isPresent()) {
                        var pad=MemoryCodecs.encodeText(codecs.getFirst().orElseThrow(),new Values.TextValue(fit.pad()),BigInteger.ONE);
                        if(pad.status()==MemoryCodecs.Status.EXACT) {
                            int ordinal=ordinals.get(candidate.location().base().id());
                            var image=((Bytes)capture(content(captured,ordinal),candidate.location())).image().slice(range.get()).copied(plan.event,range.get().start(),i);
                            result.add(new Bytes(image.fit(fit.length(),pad.value().orElseThrow().octets().getFirst(),plan.event)));continue;
                        }
                    }
                    result.add(unknown(target,"UNINTERPRETED_VALUE_EXPRESSION",plan.event));
                }
                for(var object:effects.storage().explicitObjects(logicalRead.place()))for(var value:logicalInputs.getOrDefault(object,Set.of())) {
                    result.add(unknown(target,"LOGICAL_READ_REMAINDER",plan.event));
                    if(codecs.size()!=1||codecs.getFirst().isEmpty()||extent.isEmpty())continue;
                    var text=value.text();
                    if(expression instanceof Expressions.FitText fit) {
                        int length=fit.length().intValueExact();var raw=text.value();int count=raw.codePointCount(0,raw.length());
                        text=new Values.TextValue(count>length?raw.substring(0,raw.offsetByCodePoints(0,length)):raw+fit.pad().repeat(length-count));
                    }
                    var encoded=MemoryCodecs.encodeText(codecs.getFirst().get(),text,extent.get());
                    if(encoded.status()==MemoryCodecs.Status.EXACT)result.add(new Bytes(ByteImage.literal(encoded.value().orElseThrow(),plan.event)
                        .withLogicalSupport(Set.of(new ByteImage.LogicalSupport(object,value.event())))));
                }
                if(!result.isEmpty())return Set.copyOf(result);
            }
            if(expression instanceof Expressions.Literal literal) {
                var v=literal.value();
                if(target.range().isEmpty())return Set.of(v instanceof Values.TextValue t
                    ?new Scalar(Optional.of(t),Set.of(plan.event),Set.of(),Set.of(),List.of(new Trace(target,Optional.empty(),plan.event,Optional.of(target),Map.of(),Set.of(),Set.of())))
                    :unknown(target,"NON_TEXT_LOGICAL_VALUE",plan.event));
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
                return Set.of(unknown(target,"UNINTERPRETED_LITERAL_REPRESENTATION",plan.event));
            }
            if(target.range().isEmpty()&&expression instanceof Expressions.Read read) {
                var resolution=effects.storage().resolve(read.place());var result=new HashSet<Content>();
                if(!(resolution.remainder() instanceof Scopes.NoMemory))result.add(unknown(target,"READ_LOCATION_REMAINDER",plan.event));
                for(var object:effects.storage().explicitObjects(read.place()))for(var value:logicalInputs.getOrDefault(object,Set.of()))
                    result.add(new Scalar(Optional.of(value.text()),Set.copyOf(List.of(value.event(),plan.event)),Set.of(),Set.of(),
                        List.of(new Trace(target,Optional.empty(),plan.event,Optional.of(target),Map.of(),Set.of(),Set.of(),Set.of(new ByteImage.LogicalSupport(object,value.event()))))));

                for(var candidate:resolution.candidates()) {
                    int ordinal=ordinals.get(candidate.location().base().id());
                    result.add(project(capture(content(captured,ordinal),candidate.location()),candidate).captured(plan.event,candidate.location(),target));
                }
                if(!result.isEmpty())return Set.copyOf(result);
            }
            return Set.of(unknown(target,"UNINTERPRETED_VALUE_EXPRESSION",plan.event));
        }
        State operation(State state,Operation operation) {
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
    private List<CapturedGap> captureGaps(StorageIndex.Location selected) {
        var result=new ArrayList<CapturedGap>();
        for(int i=0;i<sourceGaps.size();i++) {
            var gap=sourceGaps.get(i);if(effects.storage().disjoint(selected,gap.location()))continue;
            var affected=selected.range();
            if(affected.isPresent()&&selected.base().id().equals(gap.location().base().id()))affected=affected.get().intersect(gap.location().range().orElseThrow());
            result.add(new CapturedGap(i,affected));
        }
        return List.copyOf(result);
    }
    private Content capture(Content content,StorageIndex.Location selected) {
        // Prepared intersections: transfer/replay do not scan source inventories.
        for(var gap:Objects.requireNonNull(capturedGaps.get(selected),"unprepared captured read")) {
            if(content instanceof Bytes bytes) {
                content=new Bytes(bytes.image().withSourceGap(gap.range().orElseThrow(),gap.gap()));
            } else {
                var scalar=(Scalar)content;var gaps=new HashSet<>(scalar.sourceGaps());gaps.add(gap.gap());
                content=new Scalar(scalar.text(),scalar.producers(),scalar.reasons(),gaps,scalar.traces().stream().map(t->t.withGap(gap.gap())).toList());
            }
        }
        return content;
    }
    private Scalar project(Content content,StorageIndex.Candidate candidate) {
        if(content instanceof Scalar scalar)return scalar;
        var range=candidate.location().range().orElseThrow();var read=((Bytes)content).image().read(range);
        var traces=read.parts().stream().flatMap(p->traces(p,candidate.location()).stream()).toList();
        var gaps=new HashSet<Integer>();read.parts().forEach(p->gaps.addAll(p.sourceGaps()));
        if(read.bytes().isEmpty())return new Scalar(Optional.empty(),Set.of(),read.reasons(),gaps,traces);
        if(candidate.codec().isEmpty()||range.end().isEmpty())return new Scalar(Optional.empty(),Set.of(),Set.of("UNINTERPRETED_VIEW"),gaps,traces);
        var decoded=MemoryCodecs.decodeText(candidate.codec().get(),read.bytes().get(),range.end().get().subtract(range.start()));
        if(decoded.value().isEmpty())return new Scalar(Optional.empty(),Set.of(),Set.of(decoded.status().name()),gaps,traces);
        var producers=new HashSet<Integer>();for(var part:read.parts()){producers.addAll(part.contributors().keySet());part.logicalSupports().forEach(support->producers.add(support.event()));}
        return new Scalar(decoded.value(),producers,Set.of(),gaps,traces);
    }
    private List<Trace> traces(ByteImage.Part part,StorageIndex.Location selected) {
        if(part.payload().isEmpty()||part.coInitial().isEmpty())return List.of(trace(part,selected));
        return part.contributors().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->trace(part.contributor(e.getKey(),e.getValue()),selected)).toList();
    }
    private Trace trace(ByteImage.Part part,StorageIndex.Location selected) {
        var readStart=selected.range().orElseThrow().start();
        var observed=new StorageIndex.Location(selected.base(),Optional.of(new StorageRange(readStart.add(part.range().start()),part.range().end().map(e->e.add(readStart)))));
        var length=part.range().end().map(e->e.subtract(part.range().start()));
        var bytes=part.materialize();
        Optional<StorageIndex.Location> original=Optional.empty();
        if(bytes.isPresent()) {
            var written=eventDetails.get(part.producer()).target().location();
            original=Optional.of(new StorageIndex.Location(written.base(),Optional.of(StorageRange.exact(written.range().orElseThrow().start().add(part.producerOffset()),length.orElseThrow()))));
        }
        var captures=new HashMap<Integer,Set<CapturedRead>>();
        for(var entry:part.capturedOffsets().entrySet()) {
            var event=eventDetails.get(entry.getKey());var sources=readSource(event.write()).candidates();
            var destination=event.target().location();var portions=new HashSet<CapturedRead>();
            for(var position:entry.getValue()) {
                var source=sources.get(position.alternative()).location();var offset=position.offset();
                var src=new StorageIndex.Location(source.base(),Optional.of(StorageRange.exact(offset,length.orElseThrow())));
                var dst=new StorageIndex.Location(destination.base(),Optional.of(StorageRange.exact(destination.range().orElseThrow().start().add(offset.subtract(source.range().orElseThrow().start())),length.orElseThrow())));
                portions.add(new CapturedRead(source,src,dst));
            }
            captures.put(entry.getKey(),Set.copyOf(portions));
        }
        return new Trace(observed,bytes,part.producer(),original,captures,part.sourceGaps(),part.reasons(),part.logicalSupports());
    }
    public Execution execute(){var engine=new Engine();return new Execution(engine,DataflowSolver.solve(session,engine));}
    public final class Execution {
        private final Engine engine;private final DataflowResult<State> stable;private final Map<String,Long> solveMetrics;
        private Execution(Engine engine,DataflowResult<State> stable){this.engine=engine;this.stable=stable;solveMetrics=engine.metrics();}
        public DataflowResult<State> dataflow(){return stable;}
        public Map<String,Long> solveMetrics(){return solveMetrics;}
        /** Cumulative domain work, including observed replay; no semantic budget. */
        public Map<String,Long> metrics(){return engine.metrics();}
        public Map<String,Long> preparationMetrics(){var result=new TreeMap<>(effects.preparationMetrics());result.putAll(effects.storage().preparationMetrics());result.put("partitionSegments",(long)partition.segments().size());result.put("eventsPrepared",(long)events.size());result.put("correlationGroups",(long)groups.size());result.put("maxGroupBases",groups.stream().mapToLong(List::size).max().orElse(0));return Map.copyOf(result);}
        public ObservationBatch<ObjectId,RegionalValueFact> observe(Iterable<PointQuery<ObjectId>> requests) {
            return observe(requests,OBJECT_ORDER,StorageSubject.NamedObject::new,StorageValueFact::asObjectFact,true);
        }
        public ObservationBatch<StorageSubject,StorageValueFact> observeStorage(Iterable<PointQuery<StorageSubject>> requests) {
            return observe(requests,StorageSubject.ORDER,java.util.function.Function.identity(),java.util.function.Function.identity(),false);
        }
        private <T,V> ObservationBatch<T,V> observe(Iterable<PointQuery<T>> requests,Comparator<T> order,
                java.util.function.Function<T,StorageSubject> subject,java.util.function.Function<StorageValueFact,V> projection,boolean textOnly) {
            return BatchReplayer.materialize(session,stable,Direction.FORWARD,BOTTOM,requests,order,engine::operation,new BatchReplayer.Projection<State,T,V>() {
                @Override public boolean supports(PointQuery<T> query) {
                    var selected=subject.apply(query.subject());
                    if(!effects.storage().supports(selected,query.point().entry().unit()))return false;
                    return !textOnly||selected instanceof StorageSubject.NamedObject named&&session.index().object(named.object()).typeRef().equals(Types.known(Types.Builtin.TEXT));
                }
                @Override public V project(PointQuery<T> query,State state){return projection.apply(fact(new PointQuery<>(query.point(),subject.apply(query.subject())),state));}
                @Override public boolean supportsOutcome(PointQuery<T> query) {
                    var site=session.index().site(query.point().operation());return site!=null&&site.operation() instanceof Operations.Invoke invoke
                        &&query.point().outcome()==Control.NormalOutcome.INSTANCE&&invoke.outcomes().known().stream().anyMatch(Control.Normal.class::isInstance);
                }
                @Override public State transferOutcome(PointQuery<T> query,State before){return engine.apply(before,outcomes.get(session.index().site(query.point().operation()).operation()).get(query.point().outcome()),false);}
            });
        }
        private StorageValueFact fact(PointQuery<StorageSubject> query,State state) {
            var storage=effects.storage();var resolution=storage.resolve(query.subject());var reasons=new TreeSet<String>(resolution.reasons());
            boolean model=!(resolution.remainder() instanceof Scopes.NoMemory);if(model)reasons.add("READ_LOCATION_REMAINDER");
            var supports=new TreeMap<String,Set<Integer>>();var origins=new LinkedHashSet<>(storage.subjectOrigins(query.subject()));
            var alternatives=new HashSet<StorageValueFact.Alternative>();
            boolean source=sourceOpen(query);
            for(var gap:sourceGaps)if(resolution.candidates().stream().anyMatch(c->!storage.disjoint(c.location(),gap.location()))) {source=true;origins.add(gap.origin());}
            if(state.reached())for(var candidate:resolution.candidates()) {
                origins.addAll(candidate.origins());
                int ordinal=ordinals.get(candidate.location().base().id());
                for(var store:engine.value(state,groupOf[ordinal])) {
                    var value=RegionalValuesAnalysis.this.project(engine.content(store,ordinal),candidate);
                    for(var gap:value.sourceGaps()){source=true;origins.add(sourceGaps.get(gap).origin());}
                    if(value.text().isEmpty()){model=true;reasons.addAll(value.reasons());}
                    else supports.computeIfAbsent(value.text().get().value(),ignored->new HashSet<>()).addAll(value.producers());
                    var fragments=value.traces().stream().flatMap(t->fragments(t,query.point().entry(),value.text().isPresent()).stream()).distinct().sorted(StorageValueOrder.FRAGMENT).toList();
                    var interpretation=new RegionalValueFact.Interpretation(candidate.location().in(query.point().entry()),candidate.codec());
                    if(candidate.location().range().isEmpty())alternatives.add(new StorageValueFact.Alternative(interpretation,value.text(),fragments));
                    else {
                        // Co-initial contributors prove the same image, not alternative byte values.
                        // Keep the established nonoverlapping-fragment wire: a canonical complete
                        // cover plus one complete cover for each additional contribution suffices
                        // to retain every interval/provenance association without Cartesian products.
                        var byRange=new LinkedHashMap<StorageIndex.ContextualLocation,List<StorageValueFact.Fragment>>();
                        for(var fragment:fragments)byRange.computeIfAbsent(fragment.location(),ignored->new ArrayList<>()).add(fragment);
                        var groups=new ArrayList<>(byRange.values());var cover=groups.stream().map(List::getFirst).toList();
                        alternatives.add(new StorageValueFact.Alternative(interpretation,value.text(),cover));
                        for(int i=0;i<groups.size();i++)for(int j=1;j<groups.get(i).size();j++) {
                            var variant=new ArrayList<>(cover);variant.set(i,groups.get(i).get(j));
                            alternatives.add(new StorageValueFact.Alternative(interpretation,value.text(),variant));
                        }
                    }
                }
            }
            var logicalAlternatives=new ArrayList<StorageValueFact.LogicalAlternative>();
            if(state.reached())for(var object:storage.explicitObjects(query.subject())) {
                var logicalSupport=new TreeMap<String,Set<Integer>>();
                for(var value:state.logical.getOrDefault(object,Set.of()))logicalSupport.computeIfAbsent(value.text().value(),ignored->new HashSet<>()).add(value.event());
                logicalSupport.forEach((text,producers)->{
                    supports.computeIfAbsent(text,ignored->new HashSet<>()).addAll(producers);
                    logicalAlternatives.add(new StorageValueFact.LogicalAlternative(object,new Values.TextValue(text),producers.stream().map(events::get).distinct()
                        .sorted(Comparator.comparing(ValueFact.Support::evidence,StorageValueOrder.ID).thenComparing(ValueFact.Support::origin,StorageValueOrder.ID)).toList()));
                });
            }
            if(state.reached()&&supports.isEmpty()){model=true;reasons.add("NO_KNOWN_TEXT_PROJECTION");}
            var candidates=new ArrayList<Values.TextValue>();var associations=new ArrayList<ValueFact.CandidateSupport>();
            var premises=new LinkedHashSet<PremiseId>();var evidence=new LinkedHashSet<Id>();
            for(var entry:supports.entrySet()) {
                var text=new Values.TextValue(entry.getKey());candidates.add(text);
                var producers=entry.getValue().stream().map(events::get).distinct().sorted(Comparator.comparing(ValueFact.Support::evidence,StorageValueOrder.ID).thenComparing(ValueFact.Support::origin,StorageValueOrder.ID)).toList();
                associations.add(new ValueFact.CandidateSupport(text,producers));
                for(var producer:producers){premises.addAll(producer.premises());evidence.add(producer.evidence());origins.add(producer.origin());}
            }
            // Detached provenance includes capture/unknown events as well as original literal supports.
            for(var alternative:alternatives)for(var fragment:alternative.fragments()) {
                var definitions=new ArrayList<DefinitionEvent>();fragment.producer().ifPresent(p->definitions.add(p.definition()));
                fragment.unknownWriter().ifPresent(definitions::add);fragment.captures().forEach(c->definitions.add(c.definition()));
                for(var definition:definitions) {
                    premises.addAll(definition.premises());origins.add(definition.origin());
                    if(definition.operation().isPresent())evidence.add(definition.operation().get());else definition.destination().ifPresent(evidence::add);
                }
                fragment.sourceGaps().forEach(g->origins.add(g.origin()));
            }
            return new StorageValueFact(query.point(),query.subject(),resolution.candidates().stream().map(c->new RegionalValueFact.Interpretation(c.location().in(query.point().entry()),c.codec())).distinct().sorted(StorageValueOrder.INTERPRETATION).toList(),
                state.reached()?ValueFact.Reachability.REACHABLE:ValueFact.Reachability.UNREACHABLE_IN_MODEL,state.reached()?candidates:null,state.reached()?model:null,
                source,state.reached()&&model||source,ordered(premises),ordered(evidence),ordered(origins),associations,state.reached()?List.copyOf(reasons):List.of(),alternatives.stream().sorted(StorageValueOrder.ALTERNATIVE).toList(),logicalAlternatives);
        }
        private List<StorageValueFact.Fragment> fragments(Trace trace,EntryId entry,boolean knownText) {
            if(trace.logicalSupports().isEmpty())return List.of(fragment(trace,entry,knownText));
            return trace.logicalSupports().stream().map(ByteImage.LogicalSupport::object).distinct().map(object->
                fragment(new Trace(trace.observed(),trace.bytes(),trace.producer(),trace.original(),trace.captures(),trace.gaps(),trace.reasons(),
                    trace.logicalSupports().stream().filter(s->s.object().equals(object)).collect(java.util.stream.Collectors.toSet())),entry,knownText)).toList();
        }
        private StorageValueFact.Fragment fragment(Trace trace,EntryId entry,boolean knownText) {
            var kind=trace.observed().range().isPresent()?(trace.bytes().isPresent()?StorageValueFact.FragmentKind.KNOWN_BYTES:StorageValueFact.FragmentKind.UNKNOWN_BYTES)
                :trace.bytes().isPresent()?StorageValueFact.FragmentKind.LOGICAL_CAPTURE:knownText?StorageValueFact.FragmentKind.LOGICAL_VALUE:StorageValueFact.FragmentKind.UNKNOWN_LOGICAL;
            var producer=trace.original().map(location->new StorageValueFact.Producer(eventDetails.get(trace.producer()).definition(entry),location.in(entry)));
            var unknownWriter=producer.isEmpty()&&trace.producer()>=0?Optional.of(eventDetails.get(trace.producer()).definition(entry)):Optional.<DefinitionEvent>empty();
            var captures=new ArrayList<StorageValueFact.Capture>();
            trace.captures().forEach((event,reads)->{
                var prepared=eventDetails.get(event);var definition=prepared.definition(entry);
                for(var read:reads)captures.add(new StorageValueFact.Capture(definition,ProgramPoint.before(entry,prepared.operation().header().id()),read.sourceRange().in(entry),prepared.target().location().in(entry),read.sourceContribution().in(entry),read.destinationContribution().in(entry)));
            });
            var gaps=new HashSet<Integer>(trace.gaps());
            for(int i=0;i<sourceGaps.size();i++)if(!effects.storage().disjoint(trace.observed(),sourceGaps.get(i).location()))gaps.add(i);
            var publicGaps=gaps.stream().map(sourceGaps::get).map(g->new StorageValueFact.SourceGap(g.location().in(entry),g.origin(),g.uncertainties())).distinct().sorted(StorageValueOrder.GAP).toList();
            Optional<StorageValueFact.LogicalCapture> logicalCapture=Optional.empty();
            if(!trace.logicalSupports().isEmpty()) {
                var assign=(Operations.Assign)eventDetails.get(trace.producer()).operation();
                var object=trace.logicalSupports().iterator().next().object();
                var supports=trace.logicalSupports().stream().map(s->events.get(s.event())).distinct().sorted(Comparator.comparing(ValueFact.Support::evidence,StorageValueOrder.ID).thenComparing(ValueFact.Support::origin,StorageValueOrder.ID)).toList();
                logicalCapture=Optional.of(new StorageValueFact.LogicalCapture(object,ProgramPoint.before(entry,assign.header().id()),supports));
            }
            return new StorageValueFact.Fragment(trace.observed().in(entry),kind,trace.bytes(),producer,unknownWriter,captures.stream().distinct().sorted(StorageValueOrder.CAPTURE).toList(),publicGaps,trace.reasons().stream().sorted().toList(),logicalCapture);
        }
    }
    private boolean sourceOpen(PointQuery<StorageSubject> query) {
        var p=session.index().publication();var unit=session.index().unit(query.point().entry().unit());
        return p.coverage().inventory()!=Evidence.InventoryStatus.COMPLETE||!p.coverage().uncertainties().isEmpty()
            ||unit.coverage().inventory()!=Evidence.InventoryStatus.COMPLETE||!unit.coverage().uncertainties().isEmpty()||controlOpen.contains(unit.id())
            ||!session.context(query.point().entry()).entry().state().uncertainties().isEmpty()
            ||effects.storage().explicitObjects(query.subject()).stream().map(session.index()::object)
                .anyMatch(object->object.coverage()!=Evidence.CoverageStatus.MODELED||open(object.precision().storage())||open(object.precision().values()));
    }
    private static boolean open(Evidence.Claim c){return c.status()!=Evidence.PrecisionStatus.EXACT&&c.status()!=Evidence.PrecisionStatus.NOT_APPLICABLE;}
    private static <T extends Id> List<T> ordered(Collection<T> ids){return ids.stream().distinct().sorted(StorageValueOrder.ID).toList();}
}
