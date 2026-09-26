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

/** Factorized relations over finite storage segments; canonical effects and the shared solver/replayer. */
public final class RegionalValuesAnalysis {
    public static final String PROFILE="regional-text-images@2";
    static final Comparator<ObjectId> OBJECT_ORDER=Comparator.comparing((ObjectId id)->id.unit().publication().localId()).thenComparing(id->id.unit().localId()).thenComparing(ObjectId::localId);
    private final AnalysisSession session;
    private final StorageAnalysisMode mode;
    private final Map<StorageId,List<ObjectId>> logicalCellAliases;
    private final StatementEffects effects;
    private final StoragePartition partition;
    private final List<StorageIndex.Location> bases;
    private final Map<StorageId,Integer> ordinals=new HashMap<>();
    private final List<List<Integer>> groups;
    private final int[] groupOf,groupSizes;
    private final List<List<StoragePartition.Segment>> groupSegments;
    private final Map<StoragePartition.Segment,Integer> levels=new HashMap<>();
    private final Map<StatementEffects.Write,StorageIndex.Resolution> preparedReads=new IdentityHashMap<>();
    private final Map<Operation,List<Plan>> operations=new IdentityHashMap<>();
    private final Map<Operation,Map<Control.OutcomeKey,List<Plan>>> outcomes=new IdentityHashMap<>();
    private final Map<Operation,List<Plan>> otherwise=new IdentityHashMap<>();
    private final Map<EntryId,List<Plan>> initial=new HashMap<>();
    private final List<ValueFact.Support> events=new ArrayList<>();
    private final List<PreparedEvent> eventDetails=new ArrayList<>();
    private final Set<UnitId> controlOpen=new HashSet<>();
    private record Plan(StatementEffects.Write write,StatementEffects.Target target,int event,StatementEffects.LogicalTarget logical) { }
    private record LogicalValue(Values.TextValue text,int event) { }
    private record PreparedEvent(Operation operation,Entries.InitialCondition initial,StatementEffects.Write write,StatementEffects.Target target,Optional<Control.OutcomeKey> outcome,StatementEffects.LogicalTarget logical) {
        DefinitionEvent definition(EntryId entry) {
            if(logical!=null)return operation==null?DefinitionEvent.logicalInitial(entry,initial,write.slot(),logical.object(),write.destination()):DefinitionEvent.logicalWrite(entry,operation,write,logical,outcome);
            return operation==null?DefinitionEvent.initial(entry,initial,write.slot(),target,write.destination()):DefinitionEvent.write(entry,operation,write,target,outcome);
        }
    }
    private sealed interface Content permits Bytes,Scalar { }
    private record Bytes(ByteImage image) implements Content { }
    private record ReadCapture(Map<Integer,Content> contents,Map<StatementEffects.Write,Integer> choices) { }
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
    private record Scalar(Optional<Values.TextValue> text,Set<Integer> producers,Set<String> reasons,Set<Integer> sourceGaps,List<Trace> traces) implements Content {
        Scalar { producers=Set.copyOf(producers);reasons=Set.copyOf(reasons);sourceGaps=Set.copyOf(sourceGaps);traces=List.copyOf(traces); }
        Scalar captured(int event,StorageIndex.Location source,StorageIndex.Location destination) {
            return new Scalar(text,producers,reasons,sourceGaps,traces.stream().map(t->t.captured(event,source,destination)).toList());
        }
    }
    public enum Status { ACCEPTED, UNSUPPORTED, INVALID_INPUT }
    public record Admission(Status status,String reason,Optional<RegionalValuesAnalysis> analysis) { }
    public static Admission prepare(AnalysisSession session) { return prepare(session,StorageAnalysisMode.LOGICAL_ONLY); }
    public static Admission prepare(AnalysisSession session,StorageAnalysisMode mode) {
        Objects.requireNonNull(mode);
        var effects=new StatementEffects(new StorageIndex(session));
        // Share validated original entry facts; operational alias impacts never decide admission.
        var admission=ReachingDefinitions.prepare(effects);
        if(admission.status()!=ReachingDefinitions.Status.ACCEPTED)
            return new Admission(admission.status()==ReachingDefinitions.Status.INVALID_INPUT?Status.INVALID_INPUT:Status.UNSUPPORTED,admission.reason(),Optional.empty());
        return new Admission(Status.ACCEPTED,null,Optional.of(new RegionalValuesAnalysis(effects,admission.analysis().orElseThrow().partition(),mode)));
    }
    private RegionalValuesAnalysis(StatementEffects effects,StoragePartition partition,StorageAnalysisMode mode) {
        this.mode=mode;
        this.effects=effects;this.partition=partition;session=effects.storage().session();
        logicalCellAliases=effects.storage().declarations().stream().filter(o->o.storage() instanceof Memory.CellBinding)
            .collect(java.util.stream.Collectors.groupingBy(o->((Memory.CellBinding)o.storage()).storage(),
                java.util.stream.Collectors.mapping(Memory.ObjectDeclaration::id,java.util.stream.Collectors.toList())));
        bases=mode.physical()?effects.storage().bases().stream().map(b->effects.storage().whole(b.header().id()))
            .sorted(Comparator.comparing((StorageIndex.Location l)->l.base().id().localId())).toList():List.of();
        for(int i=0;i<bases.size();i++)ordinals.put(bases.get(i).base().id(),i);
        // Partition ordinals follow the AIR storage inventory. DAG variable order must
        // instead be canonical, including allocation/work metrics in the public wire.
        for(var base:bases)for(var segment:partition.intersecting(base))levels.put(segment,levels.size());
        for(var statement:effects.statements()) {
            var op=statement.operation();operations.put(op,compile(statement.writes(),op.header().id(),op.header().origin(),List.of(),op,null,Optional.of(Control.NormalOutcome.INSTANCE)));
            otherwise.put(op,compile(statement.otherwise(),op.header().id(),op.header().origin(),List.of(),op,null,Optional.empty()));
            var choices=new HashMap<Control.OutcomeKey,List<Plan>>();
            statement.outcomes().forEach((key,writes)->choices.put(key,compile(writes,op.header().id(),op.header().origin(),List.of(),op,null,Optional.of(key))));
            outcomes.put(op,Map.copyOf(choices));
            if(op instanceof Operations.Invoke i&&i.outcomes().remainder() instanceof Scopes.WithinControl)
                controlOpen.add(op.header().id().unit());
        }
        // Coverage describes the projection, never a write, target, or propagated image.
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
                int a=representative(parent,ordinals.get(source.location().base().id()));
                int b=representative(parent,ordinals.get(plan.target.location().base().id()));
                parent[Math.max(a,b)]=Math.min(a,b);
            }
        }
        var components=new TreeMap<Integer,List<Integer>>();
        for(int i=0;i<bases.size();i++)components.computeIfAbsent(representative(parent,i),ignored->new ArrayList<>()).add(i);
        groups=components.values().stream().map(List::copyOf).toList();groupOf=new int[bases.size()];groupSizes=groups.stream().mapToInt(List::size).toArray();
        for(int g=0;g<groups.size();g++)for(int ordinal:groups.get(g))groupOf[ordinal]=g;
        groupSegments=groups.stream().map(group->group.stream().flatMap(i->partition.intersecting(bases.get(i)).stream())
            .sorted(Comparator.comparingInt(levels::get)).toList()).toList();
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
        if(mode.physical())for(var write:writes)for(var target:write.targets()) {
            var premises=new LinkedHashSet<>(initialPremises);premises.addAll(target.premises());
            var event=events.size();events.add(new ValueFact.Support(evidence,origin,ordered(premises)));
            eventDetails.add(new PreparedEvent(operation,initial,write,target,outcome,null));
            result.add(new Plan(write,target,event,null));
        }
        for(var write:writes)for(var logical:logicalTargets(write)) {
            int event=events.size();events.add(new ValueFact.Support(evidence,origin,ordered(initialPremises)));
            eventDetails.add(new PreparedEvent(operation,initial,write,null,outcome,logical));
            result.add(new Plan(write,null,event,logical));
        }
        return List.copyOf(result);
    }
    private List<StatementEffects.LogicalTarget> logicalTargets(StatementEffects.Write write) {
        if(mode.physical())return write.logicalTargets();
        // Only explicit named writes supply logical candidates; alias scopes never invent text.
        var place=write.occurrence().map(session.index()::place).orElse(null);
        if(place==null)return List.of();
        var ids=new TreeSet<ObjectId>(OBJECT_ORDER);
        for(var id:effects.storage().explicitObjects(place)) {
            var binding=session.index().object(id).storage();
            if(binding instanceof Memory.CellBinding cell)ids.addAll(logicalCellAliases.get(cell.storage()));else ids.add(id);
        }
        return ids.stream().map(id->new StatementEffects.LogicalTarget(id,true)).toList();
    }
    public static final class State {
        private final EntryId entry;
        private final SegmentMap<RegionalAlternatives.Node<Content>> bindings;
        private final Map<ObjectId,Set<LogicalValue>> logical;
        private final int[] groupSizes;
        private State(EntryId entry,SegmentMap<RegionalAlternatives.Node<Content>> bindings,Map<ObjectId,Set<LogicalValue>> logical,int[] groupSizes){this.entry=entry;this.bindings=bindings;this.logical=Map.copyOf(logical);this.groupSizes=groupSizes;}
        private RegionalAlternatives.Size size(){var roots=new ArrayList<RegionalAlternatives.Node<Content>>();bindings.forEach((g,node)->roots.add(node));return RegionalAlternatives.size(roots);}
        /** Encoded structural edges (one compact edge may carry multiple events), not worlds. */
        public long materializedAlternatives(){return size().alternatives();}
        public long decisionNodes(){return size().nodes();}
        public long maxComponentCardinality(){return size().maxComponent();}
        public boolean reached(){return entry!=null;}
        public int explicitBases(){int[] count={0};bindings.forEach((g,node)->count[0]+=groupSizes[g]);return count[0];}
    }
    private static final State BOTTOM=new State(null,new SegmentMap<>(),Map.of(),new int[0]);
    private Content unknown(StorageIndex.Location location,String reason) {return unknown(location,reason,-1);}
    private Content unknown(StorageIndex.Location location,String reason,int event) {
        return location.range().isPresent()?new Bytes(ByteImage.unknown(location.range().get().end().map(e->e.subtract(location.range().get().start())),reason,event))
            :new Scalar(Optional.empty(),Set.of(),Set.of(reason),Set.of(),List.of(new Trace(location,Optional.empty(),event,Optional.empty(),Map.of(),Set.of(),Set.of(reason))));
    }
    final class Engine implements AnalysisDefinition<State> {
        long contentReads,contentUpdates,alternativeVisits,physicalGroupsApplied,physicalWritesApplied,maxLogicalCells,maxLogicalValues;
        @Override public Direction direction(){return Direction.FORWARD;}
        @Override public State bottom(){return BOTTOM;}
        private final RegionalAlternatives<Content> relations=new RegionalAlternatives<>(content -> content instanceof Bytes bytes ? bytes.image() : null,Bytes::new);
        private final Map<Integer,RegionalAlternatives.Node<Content>> defaults=new HashMap<>();
        private long maxStateAlternatives,maxDecisionNodes,maxComponentCardinality,maxProvenanceRows,maxExpandedAlternatives,boundaryAlternatives;
        private State track(State state) {
            maxLogicalCells=Math.max(maxLogicalCells,state.logical.size());
            maxLogicalValues=Math.max(maxLogicalValues,state.logical.values().stream().mapToLong(Set::size).sum());
            // Correlation groups partition segment levels: nodes/edges cannot overlap
            // across groups and each component's distinct labels belong to one group.
            var totals=new long[5];
            state.bindings.forEach((group,root)->{
                var size=relations.componentSize(root);
                totals[0]+=size.nodes();totals[1]+=size.alternatives();totals[2]=Math.max(totals[2],size.maxComponent());totals[3]+=size.provenanceRows();totals[4]+=size.expandedAlternatives();
            });
            maxStateAlternatives=Math.max(maxStateAlternatives,totals[1]);maxProvenanceRows=Math.max(maxProvenanceRows,totals[3]);maxExpandedAlternatives=Math.max(maxExpandedAlternatives,totals[4]);
            maxDecisionNodes=Math.max(maxDecisionNodes,totals[0]);maxComponentCardinality=Math.max(maxComponentCardinality,totals[2]);return state;
        }
        private RegionalAlternatives.Node<Content> value(State state,int group) {
            contentReads++;var present=state.bindings.get(group);
            return present!=null?present:defaults.computeIfAbsent(group,g->{
                var values=new TreeMap<Integer,Content>();for(var segment:groupSegments.get(g))values.put(levels.get(segment),unknown(segment.location(),"UNSPECIFIED_ENTRY_CONTENT"));
                return relations.singleton(values);
            });
        }
        /** Recompose only the captured/requested pieces; unrelated bytes stay unspecified. */
        private Content content(Map<Integer,Content> selected,int ordinal) {
            var location=bases.get(ordinal);Content result=unknown(location,"UNSPECIFIED_ENTRY_CONTENT");
            for(var segment:partition.intersecting(location)) {
                var piece=selected.get(levels.get(segment));if(piece==null)continue;
                result=result instanceof Bytes bytes?new Bytes(bytes.image().write(segment.location().range().orElseThrow(),((Bytes)piece).image())):piece;
            }
            return result;
        }
        private List<Map<StatementEffects.Write,Integer>> sourceSelections(List<Plan> plans) {
            // An alternative place reads ONE candidate. Combining all candidates' components
            // would enumerate unrelated worlds even though the expression is a choice.
            var writes=new LinkedHashSet<StatementEffects.Write>();
            for(var plan:plans)if(plan.logical==null&&plan.target.sourceApplicable()&&readSource(plan.write)!=null)writes.add(plan.write);
            List<Map<StatementEffects.Write,Integer>> selections=List.of(Map.of());
            for(var write:writes) {
                var next=new ArrayList<Map<StatementEffects.Write,Integer>>();int count=readSource(write).candidates().size();
                for(var prior:selections)for(int i=0;i<Math.max(1,count);i++) {
                    var choice=new HashMap<>(prior);choice.put(write,count==0?-1:i);next.add(Map.copyOf(choice));
                }
                selections=List.copyOf(next);
            }
            return selections;
        }
        private Set<Integer> readSegments(Map<StatementEffects.Write,Integer> choices) {
            var selected=new HashSet<Integer>();
            choices.forEach((write,i)->{if(i>=0)for(var segment:partition.intersecting(readSource(write).candidates().get(i).location()))selected.add(levels.get(segment));});
            return Set.copyOf(selected);
        }
        private List<Content> contents(State state,StorageIndex.Location location) {
            int ordinal=ordinals.get(location.base().id());var selected=new HashSet<Integer>();
            for(var segment:partition.intersecting(location))selected.add(levels.get(segment));
            return relations.selections(relations.project(value(state,groupOf[ordinal]),selected)).stream().map(c->content(c,ordinal)).toList();
        }
        @Override public Iterable<Boundary<State>> boundaries(AnalysisSession selected) {
            if(session!=selected)throw new IllegalArgumentException("foreign session");
            var result=new ArrayList<Boundary<State>>();
            for(var context:session.contexts()) {
                var seed=new State(context.entry().id(),new SegmentMap<>(),Map.of(),groupSizes);
                var boundary=track(apply(seed,initial.get(seed.entry),false));boundaryAlternatives=Math.max(boundaryAlternatives,boundary.materializedAlternatives());
                result.add(new Boundary<>(context,context.entryNode(),boundary));
            }
            return result;
        }
        @Override public Join<State> joinInto(State a,State b,DomainWork work) {
            if(!b.reached())return new Join<>(a,false);if(!a.reached())return new Join<>(b,true);
            if(!a.entry.equals(b.entry))throw new IllegalArgumentException("different entries");
            final class Accumulator { SegmentMap<RegionalAlternatives.Node<Content>> root=a.bindings; }
            var acc=new Accumulator();
            b.bindings.forEach((key,v)->{work.joinEntryVisited();acc.root=acc.root.put(key,relations.union(value(a,key),v));});
            a.bindings.forEach((key,v)->{if(b.bindings.get(key)==null){work.joinEntryVisited();acc.root=acc.root.put(key,relations.union(v,value(b,key)));}});
            var logical=new HashMap<>(a.logical);b.logical.forEach((key,v)->{work.joinEntryVisited();logical.merge(key,v,Engine::unionLogical);});
            return acc.root==a.bindings&&logical.equals(a.logical)?new Join<>(a,false):new Join<>(track(new State(a.entry,acc.root,logical,groupSizes)),true);
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
            if(mode.physical())for(var entry:grouped.entrySet()) {
                physicalGroupsApplied++;physicalWritesApplied+=entry.getValue().values().stream().mapToLong(List::size).sum();
                int group=entry.getKey();RegionalAlternatives.Node<Content> all=null;
                var original=value(before,group);
                for(var choices:sourceSelections(entry.getValue().values().stream().flatMap(List::stream).toList())) {
                    var selected=readSegments(choices);
                    // Enumerate only the chosen local read, retaining other factors symbolically.
                    for(var parts:relations.selections(relations.project(original,selected))) {
                        var captured=new ReadCapture(parts,choices);var current=relations.restrict(original,parts);
                        for(var occurrence:entry.getValue().entrySet())current=write(current,captured,occurrence.getKey(),occurrence.getValue(),forceMay,before.logical);
                        all=relations.union(all,current);
                    }
                }
                var updated=root.put(group,all);if(updated!=root)contentUpdates++;root=updated;
            }
            var logical=new HashMap<>(before.logical);
            for(var plan:plans)if(plan.logical!=null&&plan.logical.sourceApplicable()) {
                var supported=logicalReplacements(before,plan);
                boolean strong=!forceMay&&plan.write.destination().exact()
                    &&plan.write.occurrenceStrength()==StatementEffects.Strength.MUST
                    &&plan.write.selection()==StatementEffects.Selection.SINGLE_DESTINATION
                    &&plan.write.occurrence().map(session.index()::place).orElse(null) instanceof Places.ObjectPlace;
                if(!mode.physical()&&strong) {
                    if(supported.isEmpty())logical.remove(plan.logical.object());else logical.put(plan.logical.object(),supported);
                } else if(!supported.isEmpty())logical.merge(plan.logical.object(),supported,KillAuthority::weakUpdate);
            }
            return root==before.bindings&&logical.equals(before.logical)?before:track(new State(before.entry,root,logical,groupSizes));
        }
        private static Set<LogicalValue> unionLogical(Set<LogicalValue> a,Set<LogicalValue> b) {
            if(a.containsAll(b))return a;var result=new HashSet<>(a);result.addAll(b);return Set.copyOf(result);
        }
        private Set<LogicalValue> logicalReplacements(State captured,Plan plan) {
            // Open physical binding gives no authority to kill. Unknown widens the already-open
            // domain; only explicit supported expression values add logical candidates.
            if(!(plan.write.source() instanceof StatementEffects.ExpressionSource expression))return Set.of();
            return logicalExpression(captured,plan,expression.value());
        }
        private Set<LogicalValue> logicalExpression(State captured,Plan plan,Expression expression) {
            if(expression instanceof Expressions.FitText fit) {
                var result=new HashSet<LogicalValue>();
                for(var value:logicalExpression(captured,plan,fit.value())) {
                    int length=fit.length().intValueExact();String raw=value.text().value();int count=raw.codePointCount(0,raw.length());
                    String fitted=count>length?raw.substring(0,raw.offsetByCodePoints(0,length)):raw+fit.pad().repeat(length-count);
                    result.add(new LogicalValue(new Values.TextValue(fitted),value.event()));
                }
                return Set.copyOf(result);
            }
            if(expression instanceof Expressions.Literal literal&&literal.value() instanceof Values.TextValue text)
                return Set.of(new LogicalValue(text,plan.event));
            if(expression instanceof Expressions.Read read) {
                var result=new HashSet<LogicalValue>();
                for(var object:effects.storage().explicitObjects(read.place()))result.addAll(captured.logical.getOrDefault(object,Set.of()));
                if(mode.physical())for(var candidate:effects.storage().resolve(read.place()).candidates()) {
                    int ordinal=ordinals.get(candidate.location().base().id());
                    for(var contents:contents(captured,candidate.location())) {
                        var value=RegionalValuesAnalysis.this.project(contents,candidate);
                        if(value.text().isPresent())for(int event:value.producers())result.add(new LogicalValue(value.text().get(),event));
                    }
                }
                for(var value:List.copyOf(result))result.add(new LogicalValue(value.text(),plan.event));
                return Set.copyOf(result);
            }
            return Set.of();
        }
        private RegionalAlternatives.Node<Content> write(RegionalAlternatives.Node<Content> current,ReadCapture captured,StatementEffects.Write write,List<Plan> plans,boolean forceMay,Map<ObjectId,Set<LogicalValue>> logicalInputs) {
            if(write.selection()==StatementEffects.Selection.MAY_SET) {
                for(var plan:plans)current=weak(current,captured,plan,logicalInputs);return current;
            }
            RegionalAlternatives.Node<Content> next=null;
            var execution=forceMay?KillAuthority.Execution.POSSIBLE:KillAuthority.Execution.REQUIRED;
            if(!KillAuthority.exhaustive(write,plans.stream().map(Plan::target).toList(),execution))next=current;
            for(var plan:plans)if(plan.target.sourceApplicable()) {
                var replacement=replace(current,captured,plan,logicalInputs);var authority=KillAuthority.selected(write,plan.target,execution);
                next=relations.union(next,authority.isPresent()?KillAuthority.strongOverwrite(authority.get(),replacement):weakUnion(current,replacement));
            }
            if(next==null)next=current;
            current=next;
            for(var plan:plans)if(!plan.target.sourceApplicable())current=weak(current,captured,plan,logicalInputs);
            return current;
        }
        private RegionalAlternatives.Node<Content> weakUnion(RegionalAlternatives.Node<Content> old,RegionalAlternatives.Node<Content> supplied) {
            // Symbolic counterpart of KillAuthority.weakUpdate: both relations survive.
            return relations.union(old,supplied);
        }
        private RegionalAlternatives.Node<Content> weak(RegionalAlternatives.Node<Content> current,ReadCapture captured,Plan plan,Map<ObjectId,Set<LogicalValue>> logicalInputs) {
            return weakUnion(current,replace(current,captured,plan,logicalInputs));
        }
        private RegionalAlternatives.Node<Content> replace(RegionalAlternatives.Node<Content> old,ReadCapture captured,Plan plan,Map<ObjectId,Set<LogicalValue>> logicalInputs) {
            RegionalAlternatives.Node<Content> result=null;
            for(var replacement:replacements(plan,captured,logicalInputs)) {
                alternativeVisits++;var suppliedValues=new HashMap<Integer,Content>();var updates=new HashMap<Integer,java.util.function.UnaryOperator<Content>>();
                for(var segment:partition.intersecting(plan.target.location())) {
                    Content piece=replacement;
                    if(replacement instanceof Bytes bytes) {
                        var range=segment.location().range().orElseThrow();var destination=plan.target.location().range().orElseThrow();
                        var relative=new StorageRange(range.start().subtract(destination.start()),range.end().map(e->e.subtract(destination.start())));
                        piece=new Bytes(bytes.image().slice(relative));
                    }
                    var supplied=piece;suppliedValues.put(levels.get(segment),supplied);
                    updates.put(levels.get(segment),prior->{
                        if(prior instanceof Bytes bytes&&eventDetails.get(plan.event).initial()!=null&&eventDetails.get(plan.event).initial().value() instanceof Entries.LiteralInitial)
                            return new Bytes(bytes.image().initialize(new StorageRange(BigInteger.ZERO,bytes.image().extent()),((Bytes)supplied).image()));
                        return supplied;
                    });
                }
                boolean initialLiteral=eventDetails.get(plan.event).initial()!=null&&eventDetails.get(plan.event).initial().value() instanceof Entries.LiteralInitial;
                result=relations.union(result,initialLiteral?relations.update(old,updates):relations.overwrite(old,suppliedValues));
            }
            return result;
        }
        private Set<Content> replacements(Plan plan,ReadCapture captured,Map<ObjectId,Set<LogicalValue>> logicalInputs) {
            var target=plan.target.location();
            if(!plan.target.sourceApplicable())return Set.of(unknown(target,"UNPROVEN_WRITE_DESTINATION",plan.event));
            var source=plan.write.source();
            if(source instanceof StatementEffects.CapturedBytes copy) {
                var c=copy.source().candidates().getFirst();int ordinal=ordinals.get(c.location().base().id());
                var sourceRange=c.location().range().orElseThrow();
                return Set.of(new Bytes(((Bytes)capture(content(captured.contents(),ordinal),c.location())).image().slice(sourceRange).copied(plan.event,sourceRange.start())));
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
                    if(captured.choices().get(plan.write)!=i)continue;
                    var candidate=reads.candidates().get(i);var range=candidate.location().range();
                    if(expression instanceof Expressions.FitText fit&&extent.filter(fit.length()::equals).isPresent()
                            &&codecs.size()==1&&codecs.getFirst().filter(MemoryCodecs::isIbm1047).isPresent()
                            &&range.isPresent()&&range.get().end().isPresent()&&candidate.codec().filter(MemoryCodecs::isIbm1047).isPresent()) {
                        var pad=MemoryCodecs.encodeText(codecs.getFirst().orElseThrow(),new Values.TextValue(fit.pad()),BigInteger.ONE);
                        if(pad.status()==MemoryCodecs.Status.EXACT) {
                            int ordinal=ordinals.get(candidate.location().base().id());
                            var image=((Bytes)capture(content(captured.contents(),ordinal),candidate.location())).image().slice(range.get()).copied(plan.event,range.get().start(),i);
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

                for(int i=0;i<resolution.candidates().size();i++) {
                    if(captured.choices().get(plan.write)!=i)continue;
                    var candidate=resolution.candidates().get(i);int ordinal=ordinals.get(candidate.location().base().id());
                    result.add(project(capture(content(captured.contents(),ordinal),candidate.location()),candidate).captured(plan.event,candidate.location(),target));
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
        private Map<String,Long> metrics(){var result=new TreeMap<>(relations.metrics());result.put("maxLogicalCells",maxLogicalCells);result.put("maxLogicalValues",maxLogicalValues);result.put("physicalGroupsApplied",physicalGroupsApplied);result.put("physicalWritesApplied",physicalWritesApplied);result.put("logicalOnlyMode",mode.physical()?0L:1L);result.put("contentReads",contentReads);result.put("contentUpdates",contentUpdates);result.put("alternativeVisits",alternativeVisits);result.put("maxStateAlternatives",maxStateAlternatives);result.put("maxDecisionNodes",maxDecisionNodes);result.put("maxComponentCardinality",maxComponentCardinality);result.put("boundaryAlternatives",boundaryAlternatives);result.put("maxProvenanceRows",maxProvenanceRows);result.put("maxExpandedAlternatives",maxExpandedAlternatives);return Map.copyOf(result);}
    }
    private Content capture(Content content,StorageIndex.Location selected) {
        return content; // Only executable writes and copies contribute to content.
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
        public Map<String,Long> preparationMetrics(){var result=new TreeMap<>(effects.preparationMetrics());result.putAll(effects.storage().preparationMetrics());result.put("partitionSegments",(long)partition.segments().size());result.put("eventsPrepared",(long)events.size());result.put("physicalPlansPrepared",eventDetails.stream().filter(e->e.logical()==null).count());result.put("logicalPlansPrepared",eventDetails.stream().filter(e->e.logical()!=null).count());result.put("correlationGroups",(long)groups.size());result.put("maxGroupBases",groups.stream().mapToLong(List::size).max().orElse(0));return Map.copyOf(result);}
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
            if(!mode.physical()){model=true;reasons.add("PHYSICAL_PROPAGATION_DISABLED");}
            if(mode.physical()&&state.reached())for(var candidate:resolution.candidates()) {
                origins.addAll(candidate.origins());
                int ordinal=ordinals.get(candidate.location().base().id());
                for(var contents:engine.contents(state,candidate.location())) {
                    var value=RegionalValuesAnalysis.this.project(contents,candidate);
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
                source,state.reached()&&model,ordered(premises),ordered(evidence),ordered(origins),associations,state.reached()?List.copyOf(reasons):List.of(),alternatives.stream().sorted(StorageValueOrder.ALTERNATIVE).toList(),logicalAlternatives);
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
            var publicGaps=List.<StorageValueFact.SourceGap>of();
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
