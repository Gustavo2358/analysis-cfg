package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.structure.*;
import io.github.gustavo2358.analysis.storage.KillAuthority;
import java.util.*;

/** Admission/preparation for scalar-text-direct@1. No effect is inferred from operation spelling. */
final class TextProfile {
    record Location(int ordinal,Memory.Cell cell) { }
    sealed interface Write permits LiteralWrite, CopyWrite, ExpressionWrite { Location location(); }
    record LiteralWrite(Location location,Candidates value) implements Write { }
    record CopyWrite(Location location,Location source) implements Write { }
    record ExpressionWrite(Location location,Operations.Assign operation) implements Write { }
    final AnalysisSession session;
    final Map<ObjectId,Location> subjects=new HashMap<>();
    private final Set<ObjectId> textSubjects=new HashSet<>();
    final IdentityHashMap<Operation,Write> writes=new IdentityHashMap<>();
    private final IdentityHashMap<Operation,KillAuthority.Permit> overwrites=new IdentityHashMap<>();
    private final IdentityHashMap<Operation,ForeignEffectTransfer> effects=new IdentityHashMap<>();
    private final IdentityHashMap<Operation,ConservativeEffectTransfer> conservative=new IdentityHashMap<>();
    private final boolean effectAware;
    private final List<Location> modeledCells;
    private final Set<Location> selected;
    final long requestedObjects;
    boolean selected(Location location){return selected.contains(location);}
    int preparedCellCount(){return selected.size();}
    final IdentityHashMap<ContextView,PossibleValuesState> boundaries=new IdentityHashMap<>();
    private final Map<UnitId,Set<ObjectId>> visible=new HashMap<>();
    final Map<UnitId,Boolean> sourceOpen=new HashMap<>();
    private final Set<Integer> sourceOpenCells=new HashSet<>();
    private final Set<EntryId> sourceOpenEntries=new HashSet<>();
    final ValueUniverse universe=new ValueUniverse();
    final List<PremiseId> premises=new ArrayList<>();
    final ValuesWork preparation=new ValuesWork();
    private final Set<Operation> admitted=Collections.newSetFromMap(new IdentityHashMap<>());
    TextProfile(AnalysisSession session,boolean effectAware) {this(session,effectAware,null);}
    TextProfile(AnalysisSession session,boolean effectAware,Set<ObjectId> demand) {
        this.effectAware=effectAware;
        this.session=Objects.requireNonNull(session);
        var index=session.index();var publication=index.publication();
        var cells=new HashMap<StorageId,Location>();
        for(var unit:publication.units())for(var object:unit.objects()) {
            var cell=index.directCell(object.id());
            if(!(object.storage() instanceof Memory.CellBinding)||cell==null||!cellDomain(object.typeRef())||!cellDomain(cell.typeRef()))
                throw new Refusal(false,"UNSUPPORTED_STORAGE_PROFILE");
            var location=cells.get(cell.header().id());
            if(location==null){int ordinal=cells.size();Math.incrementExact(ordinal);location=new Location(ordinal,cell);cells.put(cell.header().id(),location);}
            subjects.put(object.id(),location);
            if(text(object.typeRef()))textSubjects.add(object.id());
            if(object.coverage()!=Evidence.CoverageStatus.MODELED||open(object.precision().storage())||open(object.precision().values()))
                sourceOpenCells.add(location.ordinal());
        }

        // Each Cell StorageId is an independent slot; aliases share a CellBinding.
        var selected=new HashSet<Location>();
        if(demand==null)selected.addAll(cells.values());
        else {
            for(var object:demand) {
                var location=subjects.get(object);if(location==null)throw new Refusal(false,"UNSUPPORTED_DEMAND_STORAGE");selected.add(location);
            }
            // Backwards closure of possible reaching copies. No control/path pruning;
            // every write to a selected cell is retained, including MAY/unknown effects.
            var sources=new HashMap<Location,Set<Location>>();
            for(var site:index.sites(Operations.Assign.class)) {
                var assign=(Operations.Assign)site.operation();
                if(assign.destination() instanceof Places.ObjectPlace to) {
                    // Only relevant expressions require admission; unrelated unsupported effects still use existing guards.
                    try {for(var from:TextExpressions.reads(assign.value()))sources.computeIfAbsent(subjects.get(to.object()),k->new HashSet<>()).add(subjects.get(from));}
                    catch(Refusal ignored) { }
                }
            }
            var pending=new ArrayDeque<Location>(selected);
            while(!pending.isEmpty())for(var source:sources.getOrDefault(pending.removeFirst(),Set.of())) {
                if(source==null)throw new Refusal(false,"UNSUPPORTED_DEMAND_SOURCE");
                if(selected.add(source))pending.addLast(source);
            }
        }
        requestedObjects=demand==null?subjects.size():demand.size();
        this.selected=Set.copyOf(selected);
        modeledCells=selected.stream().sorted(Comparator.comparingInt(Location::ordinal)).toList();
        for(var unit:publication.units()) {
            boolean open=open(publication.coverage())||open(unit.coverage());
            for(var sequence:unit.sequences()) {
                for(var instruction:sequence.instructions()){prepare(instruction);open|=!(instruction instanceof Operations.HavocMust||instruction instanceof Operations.HavocMay)&&open(instruction.header());}
                prepare(sequence.terminator());open|=!(sequence.terminator() instanceof Operations.Opaque)&&open(sequence.terminator().header());
                if(effectAware && sequence.terminator() instanceof Operations.Invoke invoke)
                    open|=invoke.outcomes().remainder() instanceof Scopes.WithinControl;
            }
            sourceOpen.put(unit.id(),open);visible.put(unit.id(),Set.copyOf(unit.visibleObjects()));
        }
        for(var context:session.contexts()) {
            if(!context.entry().state().uncertainties().isEmpty())sourceOpenEntries.add(context.entry().id());
            var seed=PossibleValuesState.reached();var initial=new HashMap<Integer,Values.TextValue>();var initialized=new HashSet<Integer>();
            for(var condition:context.entry().state().conditions().stream().sorted(Comparator.comparingInt(c->c.value() instanceof Entries.LiteralInitial?0:1)).toList()) {
                if(!(condition.place() instanceof Places.ObjectPlace object))throw new Refusal(false,"UNSUPPORTED_INITIAL_PLACE");
                var location=subjects.get(object.object());
                if(location==null)throw new Refusal(false,"UNSUPPORTED_INITIAL_STORAGE");
                if(condition.value() instanceof Entries.LiteralInitial literal) {
                    if(!(literal.value().value() instanceof Values.TextValue text))throw new Refusal(false,"UNSUPPORTED_INITIAL_VALUE");
                    var previous=initial.putIfAbsent(location.ordinal(),text);
                    if(previous!=null&&!previous.equals(text))throw new Refusal(true,"CONTRADICTORY_INITIAL_VALUES");
                    if(!selected(location))continue;
                    var value=universe.supported(text,condition.place().header().id(),condition.origin(),condition.premises(),preparation);
                    // Simultaneous support is unioned; only the first strong fact can
                    // replace unspecified default content at the invocation boundary.
                    if(!initialized.add(location.ordinal()))value=seed.value(location.ordinal(),preparation).join(value,preparation);
                    seed=seed.initialize(location.ordinal(),value,preparation);
                } else if(condition.value() instanceof Entries.PossibleLiterals possible) {
                    if(!selected(location))continue;
                    var value=Candidates.UNKNOWN;
                    for(var literal:possible.candidates()) {
                        if(!(literal.value() instanceof Values.TextValue text))throw new Refusal(false,"UNSUPPORTED_INITIAL_VALUE");
                        value=value.join(universe.supported(text,literal.header().id(),condition.origin(),condition.premises(),preparation),preparation);
                    }
                    seed=seed.weakUpdate(location.ordinal(),value,preparation);initialized.add(location.ordinal());
                } else if(selected(location))seed=seed.widenUnknown(location.ordinal(),preparation);
            }
            boundaries.put(context,seed);
        }
    }
    private static boolean text(Types.TypeRef type) { return type instanceof Types.Known k&&k.type()==Types.Builtin.TEXT; }
    // Auxiliary integer cells participate in alias/effect bounds, without numeric
    // evaluation or candidate queries. Their open value uses the existing top.
    private static boolean cellDomain(Types.TypeRef type) {
        return type instanceof Types.Known k&&(k.type()==Types.Builtin.TEXT||k.type()==Types.Builtin.INT);
    }
    private void prepare(Operation operation) {
        if(operation instanceof Operations.Assign assign) {
            if(!(assign.destination() instanceof Places.ObjectPlace destination))
                throw new Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");
            var location=subjects.get(destination.object());
            if(location==null)throw new Refusal(false,"UNSUPPORTED_STORAGE_PROFILE");
            if(!selected(location)){admitted.add(operation);return;}
            if(assign.value() instanceof Expressions.Literal literal && literal.value() instanceof Values.TextValue text)
                writes.put(operation,new LiteralWrite(location,universe.supported(text,assign.header().id(),assign.header().origin(),List.of(),preparation)));
            else if(assign.value() instanceof Expressions.Read read && read.place() instanceof Places.ObjectPlace source) {
                var sourceLocation=subjects.get(source.object());
                if(sourceLocation==null)throw new Refusal(false,"UNSUPPORTED_STORAGE_PROFILE");
                writes.put(operation,new CopyWrite(location,sourceLocation));
            } else if(assign.value() instanceof Expressions.FitText||assign.value() instanceof Expressions.SliceText) {
                for(var source:TextExpressions.reads(assign.value()))if(!subjects.containsKey(source))throw new Refusal(false,"UNSUPPORTED_STORAGE_PROFILE");
                writes.put(operation,new ExpressionWrite(location,assign));
            } else throw new Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");
        } else if(operation instanceof Operations.HavocMust || operation instanceof Operations.HavocMay || operation instanceof Operations.Opaque) {
            conservative.put(operation,ConservativeEffectTransfer.prepare(operation,this));
        } else if(effectAware && operation instanceof Operations.Invoke invoke) {
            if(!invoke.results().isEmpty())throw new Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");
            effects.put(operation,ForeignEffectTransfer.prepare(invoke.effectBound(),modeledCells,subjects));
        } else if(!(operation instanceof Operations.Nop||operation instanceof Operations.Return||operation instanceof Operations.Jump||operation instanceof Operations.Branch||operation instanceof Operations.Halt))
            throw new Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");
        var write=writes.get(operation);
        if(write!=null)overwrites.put(operation,KillAuthority.exactCell(session,operation,write.location().cell()).orElseThrow(()->new Refusal(false,"UNPROVED_STRONG_OVERWRITE")));
        admitted.add(operation);
    }
    PossibleValuesState transferOperation(PossibleValuesState state,Operation operation,ValuesWork work) {
        if(!admitted.contains(operation))throw new IllegalArgumentException("operation outside prepared snapshot");
        if(!state.isReached())return state;
        var partial=conservative.get(operation);
        if(partial!=null)return partial.apply(state,work);
        var effect=effects.get(operation);
        if(effect!=null)return effect.apply(state,work);
        var write=writes.get(operation);
        if(write==null)return state;
        work.strongAssignments=Math.incrementExact(work.strongAssignments);
        // Capture the immutable source value before the strong update, preserving
        // its open remainder and candidate supports without creating an alias.
        var value=write instanceof LiteralWrite literal ? literal.value()
            : write instanceof CopyWrite copy ? state.value(copy.source().ordinal(),work)
            : TextExpressions.evaluate(((ExpressionWrite)write).operation(),state,subjects,universe,work);
        return state.strongOverwrite(write.location().ordinal(),value,overwrites.get(operation),work);
    }
    boolean supports(ObjectId subject,EntryId entry) {
        if(!textSubjects.contains(subject)||!selected(subjects.get(subject)))return false;
        return subject.unit().equals(entry.unit())||visible.get(entry.unit()).contains(subject);
    }
    boolean sourceOpen(ObjectId subject,EntryId entry) {
        return sourceOpen.get(entry.unit())||sourceOpenEntries.contains(entry)
                ||sourceOpenCells.contains(subjects.get(subject).ordinal());
    }
    private static boolean open(Evidence.Coverage coverage) { return coverage.inventory()!=Evidence.InventoryStatus.COMPLETE||!coverage.uncertainties().isEmpty(); }
    private static boolean open(Evidence.Claim claim) { return claim.status()!=Evidence.PrecisionStatus.EXACT&&claim.status()!=Evidence.PrecisionStatus.NOT_APPLICABLE; }
    private static boolean open(Operations.Header header) {
        return header.coverage()!=Evidence.CoverageStatus.MODELED||open(header.precision().control())||open(header.precision().storage())||open(header.precision().effects())||open(header.precision().values());
    }
    static final class Refusal extends RuntimeException {
        private static final long serialVersionUID=1L;
        final boolean invalid;
        Refusal(boolean invalid,String reason){super(reason);this.invalid=invalid;}
    }
}
