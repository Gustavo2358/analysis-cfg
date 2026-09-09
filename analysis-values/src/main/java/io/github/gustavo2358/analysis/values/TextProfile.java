package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Admission/preparation for scalar-text-direct@1. No effect is inferred from operation spelling. */
final class TextProfile {
    record Location(int ordinal,Memory.Cell cell) { }
    record Write(Location location,Candidates value) { }
    final AnalysisSession session;
    final Map<ObjectId,Location> subjects=new HashMap<>();
    final IdentityHashMap<Operation,Write> writes=new IdentityHashMap<>();
    final IdentityHashMap<ContextView,PossibleValuesState> boundaries=new IdentityHashMap<>();
    private final Map<UnitId,Set<ObjectId>> visible=new HashMap<>();
    final Map<UnitId,Boolean> sourceOpen=new HashMap<>();
    final ValueUniverse universe=new ValueUniverse();
    final List<PremiseId> premises=new ArrayList<>();
    final ValuesWork preparation=new ValuesWork();
    private final Set<Operation> admitted=Collections.newSetFromMap(new IdentityHashMap<>());
    TextProfile(AnalysisSession session) {
        this.session=Objects.requireNonNull(session);
        var index=session.index();var publication=index.publication();
        var cells=new HashMap<StorageId,Location>();
        for(var unit:publication.units())for(var object:unit.objects()) {
            var cell=index.directCell(object.id());
            if(!(object.storage() instanceof Memory.CellBinding)||cell==null||!text(object.typeRef())||!text(cell.typeRef()))
                throw new Refusal(false,"UNSUPPORTED_STORAGE_PROFILE");
            var location=cells.get(cell.header().id());
            if(location==null){int ordinal=cells.size();Math.incrementExact(ordinal);location=new Location(ordinal,cell);cells.put(cell.header().id(),location);}
            subjects.put(object.id(),location);
        }
        // A single premise must cover all admitted bases. Scan premise members once, not pairs.
        if(cells.size()>1) {
            boolean covered=false;
            for(var premise:publication.premises())if(premise.assertion() instanceof Proofs.DisjointStorage disjoint) {
                var members=new HashSet<StorageId>();
                for(var id:disjoint.storage())if(cells.containsKey(id))members.add(id);
                if(members.size()==cells.size()){premises.add(premise.id());covered=true;break;}
            }
            if(!covered)throw new Refusal(false,"UNSUPPORTED_STORAGE_DISJOINTNESS");
        }
        for(var unit:publication.units()) {
            boolean open=open(publication.coverage())||open(unit.coverage());
            for(var sequence:unit.sequences()) {
                for(var instruction:sequence.instructions()){prepare(instruction);open|=open(instruction.header());}
                prepare(sequence.terminator());open|=open(sequence.terminator().header());
            }
            sourceOpen.put(unit.id(),open);visible.put(unit.id(),Set.copyOf(unit.visibleObjects()));
        }
        for(var context:session.contexts()) {
            var seed=PossibleValuesState.reached();var initial=new HashMap<Integer,Entries.InitialValue>();
            for(var condition:context.entry().state().conditions()) {
                if(!(condition.place() instanceof Places.ObjectPlace object))throw new Refusal(false,"UNSUPPORTED_INITIAL_PLACE");
                var location=subjects.get(object.object());
                if(location==null)throw new Refusal(false,"UNSUPPORTED_INITIAL_STORAGE");
                var previous=initial.putIfAbsent(location.ordinal(),condition.value());
                if(previous!=null&&!previous.equals(condition.value())) {
                    if(previous instanceof Entries.LiteralInitial a&&condition.value() instanceof Entries.LiteralInitial b) {
                        if(!a.value().value().equals(b.value().value()))throw new Refusal(true,"CONTRADICTORY_INITIAL_VALUES");
                    } else throw new Refusal(false,"UNSUPPORTED_OVERLAPPING_INITIAL_CONDITIONS");
                }
                if(condition.value() instanceof Entries.LiteralInitial literal) {
                    if(!(literal.value().value() instanceof Values.TextValue text))throw new Refusal(false,"UNSUPPORTED_INITIAL_VALUE");
                    seed=seed.assign(location.ordinal(),universe.intern(text,preparation),preparation);
                }
            }
            boundaries.put(context,seed);
        }
    }
    private static boolean text(Types.TypeRef type) { return type instanceof Types.Known k&&k.type()==Types.Builtin.TEXT; }
    private void prepare(Operation operation) {
        if(operation instanceof Operations.Assign assign) {
            if(!(assign.destination() instanceof Places.ObjectPlace destination)||!(assign.value() instanceof Expressions.Literal literal)||!(literal.value() instanceof Values.TextValue text))
                throw new Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");
            var location=subjects.get(destination.object());
            if(location==null)throw new Refusal(false,"UNSUPPORTED_STORAGE_PROFILE");
            writes.put(operation,new Write(location,universe.intern(text,preparation)));
        } else if(!(operation instanceof Operations.Nop||operation instanceof Operations.Return||operation instanceof Operations.Jump||operation instanceof Operations.Branch||operation instanceof Operations.Halt))
            throw new Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");
        admitted.add(operation);
    }
    PossibleValuesState transferOperation(PossibleValuesState state,Operation operation,ValuesWork work) {
        if(!admitted.contains(operation))throw new IllegalArgumentException("operation outside prepared snapshot");
        if(!state.isReached())return state;
        var write=writes.get(operation);
        if(write==null)return state;
        work.strongAssignments=Math.incrementExact(work.strongAssignments);
        return state.assign(write.location().ordinal(),write.value(),work);
    }
    boolean supports(ObjectId subject,EntryId entry) {
        if(!subjects.containsKey(subject))return false;
        return subject.unit().equals(entry.unit())||visible.get(entry.unit()).contains(subject);
    }
    boolean sourceOpen(ObjectId subject,EntryId entry) {
        var object=session.index().object(subject);
        return sourceOpen.get(entry.unit())||object.coverage()!=Evidence.CoverageStatus.MODELED
                ||open(object.precision().storage())||open(object.precision().values());
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
