package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import io.github.gustavo2358.analysis.structure.ProgramIndex;
import java.math.BigInteger;
import java.util.*;

/** Canonical AIR memory semantics, prepared outside the solver and shared by RD and values. */
public final class StatementEffects {
    public enum Strength { MUST, MAY }
    /** One evaluated destination is distinct from an effect that can touch a set of locations. */
    public enum Selection { SINGLE_DESTINATION, MAY_SET }
    public enum ReadKind { VALUE, ADDRESS, TARGET, FOREIGN }
    public sealed interface Source permits ExpressionSource, CapturedBytes, UnknownSource { }
    /** Explicit resident values or a borrowed canonical assignment occurrence.
     * Access reconstructs one expression; it never installs a decoded-value cache. */
    public static final class ExpressionSource implements Source {
        private final Expression resident;
        private final OperationRef occurrence;
        public ExpressionSource(Expression value){resident=Objects.requireNonNull(value);occurrence=null;}
        private ExpressionSource(OperationRef occurrence){this.occurrence=Objects.requireNonNull(occurrence);resident=null;}
        public Expression value(){return occurrence==null?resident:((Operations.Assign)occurrence.value()).value();}
        @Override public boolean equals(Object other){return this==other||other instanceof ExpressionSource source&&value().equals(source.value());}
        @Override public int hashCode(){return value().hashCode();}
        @Override public String toString(){return "ExpressionSource[value="+value()+"]";}
    }
    /** Owner-local typed code address, shared by effect and domain preparation.
     * Resident compatibility keeps the caller's operation explicitly. */
    public static final class OperationRef {
        private final ProgramIndex.Site site;
        private final Operation resident;
        private OperationRef(Operation operation){resident=Objects.requireNonNull(operation);site=null;}
        private OperationRef(ProgramIndex.Site site){this.site=Objects.requireNonNull(site);resident=null;}
        public Operation value(){return site==null?resident:site.operation();}
    }
    public record CapturedBytes(StorageIndex.Resolution source, BigInteger length) implements Source { }
    public record UnknownSource(String reason) implements Source { }
    public record Read(Optional<OperandId> occurrence,ReadKind kind,StorageIndex.Resolution location) { }
    public record Target(StorageIndex.Location location,Strength strength,boolean sourceApplicable,List<PremiseId> premises,List<String> reasons) {
        public Target { premises=List.copyOf(premises);reasons=List.copyOf(reasons); }
    }
    /** Logical identity is not a physical allocation or a disjointness proof. */
    public record LogicalTarget(ObjectId object,boolean sourceApplicable) { }
    public record Write(int slot,Optional<OperandId> occurrence,StorageIndex.Resolution destination,Source source,List<Target> targets,
                        Selection selection,Strength occurrenceStrength,List<LogicalTarget> logicalTargets) {
        public Write { logicalTargets=List.copyOf(logicalTargets);if(!(targets instanceof WholeTargets))targets=List.copyOf(targets);Objects.requireNonNull(selection);Objects.requireNonNull(occurrenceStrength); }
        public Write(int slot,Optional<OperandId> occurrence,StorageIndex.Resolution destination,Source source,List<Target> targets,Selection selection,Strength occurrenceStrength) {
            this(slot,occurrence,destination,source,targets,selection,occurrenceStrength,List.of());
        }
        public Write(int slot,Optional<OperandId> occurrence,StorageIndex.Resolution destination,Source source,List<Target> targets) {
            this(slot,occurrence,destination,source,targets,Selection.SINGLE_DESTINATION,Strength.MUST);
        }
    }
    public record Statement(OperationRef reference,List<Read> reads,List<Write> writes,
                            Map<Control.OutcomeKey,List<Write>> outcomes,List<Write> otherwise) {
        public Statement { Objects.requireNonNull(reference);reads=List.copyOf(reads);writes=List.copyOf(writes);outcomes=Map.copyOf(outcomes);otherwise=List.copyOf(otherwise); }
        public Statement(Operation operation,List<Read> reads,List<Write> writes,Map<Control.OutcomeKey,List<Write>> outcomes,List<Write> otherwise){
            this(new OperationRef(operation),reads,writes,outcomes,otherwise);
        }
        public Operation operation(){return reference.value();}
        @Override public boolean equals(Object other){return this==other||other instanceof Statement statement&&operation().equals(statement.operation())&&reads.equals(statement.reads)&&writes.equals(statement.writes)&&outcomes.equals(statement.outcomes)&&otherwise.equals(statement.otherwise);}
        @Override public int hashCode(){return Objects.hash(operation(),reads,writes,outcomes,otherwise);}
    }
    private final StorageIndex storage;
    private record OpenLocation(int declarationOrdinal,ObjectId object,StorageIndex.Location location) { }
    private final Map<StorageId,List<OpenLocation>> openByBase;
    private final WholeAliases wholeAliases;
    private final Map<OperationId,Statement> statements=new LinkedHashMap<>();
    private long operandVisits,targetsPrepared,directTargetsPrepared,boundTargetsPrepared,explicitBroadTargetsPrepared,baseComparisons;
    public StatementEffects(StorageIndex storage) {
        this.storage=Objects.requireNonNull(storage);
        var byBase=new HashMap<StorageId,List<OpenLocation>>();
        var store=storage.session().index().store();
        var declarations=store.declarationInventory();var inventory=store.storageInventory();
        WholeAliases broad=null;int declarationOrdinal=0;
        try {
        for(var object:storage.declarations()) {
            int ordinal=declarationOrdinal++;
            var resolution=storage.object(object.id());if(resolution.exact())continue;
            if(declarations.isPresent()&&inventory.isPresent()&&resolution.candidates().isEmpty()
                    &&resolution.remainder() instanceof Scopes.WithinMemory within&&within.scope() instanceof Scopes.AllMemory){
                if(broad==null)broad=new WholeAliases(declarations.orElseThrow(),inventory.orElseThrow());
                broad.add(ordinal);continue;
            }
            var locations=new LinkedHashSet<StorageIndex.Location>();
            resolution.candidates().forEach(c->locations.add(c.location()));
            if(resolution.remainder() instanceof Scopes.WithinMemory w) {
                try { storage.select(w.scope()).candidates().forEach(c->locations.add(c.location())); }
                catch(StorageIndex.UngroundedBound unusedNominalBound) {
                    // Admission checks executable uses; a nominal, unused cycle has no indexable scope.
                }
            }
            for(var location:locations)byBase.computeIfAbsent(location.base().id(),ignored->new ArrayList<>()).add(new OpenLocation(ordinal,object.id(),location));
        }
        byBase.replaceAll((id,values)->List.copyOf(values));openByBase=Map.copyOf(byBase);
        wholeAliases=broad;
        for(var unit:storage.session().index().store().units())for(var sequence:unit.sequences()) {
            for(var operation:sequence.instructions())statements.put(operation.header().id(),prepare(operation));
            var operation=sequence.terminator();statements.put(operation.header().id(),prepare(operation));
        }
        }catch(RuntimeException|Error failure){
            if(broad!=null)try{broad.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    /** One shared whole-catalogue relation, not one header for each object/base pair. */
    private static final class WholeAliases implements AutoCloseable {
        private final ProgramStore.DeclarationInventory declarations;
        private final ProgramStore.OrdinalColumn positions;
        private int count;
        WholeAliases(ProgramStore.DeclarationInventory declarations,ProgramStore.StorageInventory inventory){
            this.declarations=declarations;positions=inventory.column(declarations.size());
        }
        void add(int ordinal){positions.set(count,ordinal+1L);count=Math.incrementExact(count);}
        int size(){declarations.size();return count;}
        int ordinal(int at){Objects.checkIndex(at,size());return Math.toIntExact(positions.get(at)-1);}
        ObjectId identity(int at){return declarations.identity(ordinal(at));}
        @Override public void close(){positions.close();}
    }
    public StorageIndex storage() { return storage; }
    public Collection<Statement> statements() { return Collections.unmodifiableCollection(statements.values()); }
    public Map<String,Long> preparationMetrics() { return Map.of("statementsPrepared",(long)statements.size(),"operandVisits",operandVisits,
        "targetsPrepared",targetsPrepared,"directTargetsPrepared",directTargetsPrepared,"boundTargetsPrepared",boundTargetsPrepared,
        "explicitBroadTargetsPrepared",explicitBroadTargetsPrepared,"baseComparisons",baseComparisons); }
    public Statement statement(OperationId operation) {
        var result=statements.get(operation);if(result==null)throw new IllegalArgumentException("operation outside prepared snapshot");return result;
    }
    private Statement prepare(Operation operation) {
        var reference=storage.session().index().store().storageInventory().isPresent()
            ?new OperationRef(storage.session().index().site(operation.header().id())):new OperationRef(operation);
        var b=new Builder(operation);
        if(operation instanceof Operations.Assign a) {
            b.visit(a.value(),ReadKind.VALUE);b.visit(a.destination(),ReadKind.ADDRESS);
            b.write(b.writes,Optional.of(a.destination().header().id()),storage.resolve(a.destination()),Strength.MUST,reference.site==null?new ExpressionSource(a.value()):new ExpressionSource(reference));
        } else if(operation instanceof Operations.HavocMust h) {
            b.visit(h.destination(),ReadKind.ADDRESS);b.write(b.writes,Optional.of(h.destination().header().id()),storage.resolve(h.destination()),Strength.MUST,new UnknownSource("HAVOC_MUST"));
        } else if(operation instanceof Operations.HavocMay h)b.scopeWrite(b.writes,h.scope(),"HAVOC_MAY");
        else if(operation instanceof Operations.CopyBytes c) {
            b.visit(c.source().offset(),ReadKind.ADDRESS);b.visit(c.source().extent(),ReadKind.ADDRESS);
            b.visit(c.destination().offset(),ReadKind.ADDRESS);b.visit(c.destination().extent(),ReadKind.ADDRESS);
            var source=storage.byteRange(c.source(),c.length(),c.header().origin());
            var destination=storage.byteRange(c.destination(),c.length(),c.header().origin());
            if(source.exact()&&destination.exact()) {
                b.reads.add(new Read(Optional.empty(),ReadKind.VALUE,source));
                b.write(b.writes,Optional.empty(),destination,Strength.MUST,new CapturedBytes(source,c.length()));
            } else b.envelope(c.fallback().memory(),b.writes);
        } else if(operation instanceof Operations.Opaque o) {
            for(var operand:o.knownOperands())b.visit(operand,operand instanceof Expression&&operand.header().role()==Operand.Role.VALUE_READ?ReadKind.VALUE:ReadKind.ADDRESS);
            b.envelope(o.envelope().memory(),b.writes);
        } else if(operation instanceof Operations.Invoke invoke) {
            if(invoke.target() instanceof Interactions.ComputedTarget target)b.visit(target.name(),ReadKind.TARGET);
            for(var argument:invoke.arguments()) {
                if(argument instanceof Interactions.ValueArgument a)b.visit(a.value(),ReadKind.VALUE);
                else if(argument instanceof Interactions.CopyArgument a)b.visit(a.value(),ReadKind.VALUE);
                else b.visit(((Interactions.ReferenceArgument)argument).place(),ReadKind.ADDRESS);
            }
            for(var place:invoke.results())b.visit(place,ReadKind.ADDRESS);
            for(var place:invoke.effectOperands())b.visit(place,ReadKind.ADDRESS);
            b.foreign(invoke.effectBound().otherwise(),b.otherwise);
            for(var e:invoke.effectBound().perOutcome()) {
                var writes=new ArrayList<Write>();b.foreign(e.effects(),writes);b.outcomes.put(e.outcome(),writes);
            }
            // Result destinations are evaluated above; returned contents are assigned only on normal return.
            var normal=new ArrayList<>(b.outcomes.getOrDefault(Control.NormalOutcome.INSTANCE,b.otherwise));
            for(var place:invoke.results())b.write(normal,Optional.of(place.header().id()),storage.resolve(place),Strength.MUST,new UnknownSource("INVOKE_RESULT"));
            b.outcomes.put(Control.NormalOutcome.INSTANCE,normal);
        } else if(operation instanceof Operations.Branch branch)b.visit(branch.predicate(),ReadKind.VALUE);
        else if(operation instanceof Operations.Dispatch dispatch)b.visit(dispatch.selector(),ReadKind.VALUE);
        else if(operation instanceof Operations.Return r)for(var value:r.values())b.visit(value,ReadKind.VALUE);
        else if(operation instanceof Operations.Raise r)for(var value:r.values())b.visit(value,ReadKind.VALUE);
        else if(operation instanceof Operations.IndirectJump j){b.visit(j.target(),ReadKind.VALUE);b.envelope(j.fallback().memory(),b.writes);}
        // Interpreted local control preserves memory; body operations account for its effects.
        else if(!(operation instanceof Operations.LocalInvoke||operation instanceof Operations.LocalBoundary||operation instanceof Operations.LocalResume||operation instanceof Operations.LocalUnwind||operation instanceof Operations.Nop||operation instanceof Operations.Jump||operation instanceof Operations.Halt))
            throw new IllegalArgumentException("unsupported operation effect");
        b.outcomes.replaceAll((key,value)->List.copyOf(value));
        return new Statement(reference,b.reads,b.writes,b.outcomes,b.otherwise);
    }
    /** Normalized finite targets; only a proved singleton can replace old contributors. */
    public List<Target> targets(StorageIndex.Resolution destination,Strength requested) {
        return targets(destination,requested,false,false);
    }
    /** Each admitted catalogue has unique bases. Source applicability separates
     * direct targets from remainder targets, even for equal whole locations. */
    private static final class WholeTargets extends AbstractList<Target> implements RandomAccess {
        private final List<StorageIndex.Candidate> direct,remainder;
        private final Strength strength;
        private final List<String> reasons;
        WholeTargets(List<StorageIndex.Candidate> direct,List<StorageIndex.Candidate> remainder,Strength strength,List<String> reasons){
            this.direct=direct;this.remainder=remainder;this.strength=strength;this.reasons=List.copyOf(reasons);
        }
        @Override public int size(){return Math.addExact(direct.size(),remainder.size());}
        @Override public Target get(int ordinal){
            Objects.checkIndex(ordinal,size());int count=direct.size();boolean source=ordinal<count;
            var candidate=source?direct.get(ordinal):remainder.get(ordinal-count);
            return new Target(candidate.location(),source?strength:Strength.MAY,source,List.of(),reasons);
        }
        @Override public void clear(){throw new UnsupportedOperationException("immutable borrowed whole storage targets");}
    }
    static boolean wholeTargets(List<Target> targets){return targets instanceof WholeTargets;}
    public static Optional<StorageIndex.BaseAddress> address(Write write,int ordinal){
        Objects.checkIndex(ordinal,write.targets().size());
        if(!(write.targets() instanceof WholeTargets whole))return Optional.empty();
        int count=whole.direct.size();return StorageIndex.address(ordinal<count?whole.direct:whole.remainder,ordinal<count?ordinal:ordinal-count);
    }
    public static boolean sourceApplicable(Write write,int ordinal){
        Objects.checkIndex(ordinal,write.targets().size());
        return write.targets() instanceof WholeTargets whole?ordinal<whole.direct.size():write.targets().get(ordinal).sourceApplicable();
    }
    public static List<PremiseId> targetPremises(Write write,int ordinal){
        Objects.checkIndex(ordinal,write.targets().size());return write.targets() instanceof WholeTargets?List.of():write.targets().get(ordinal).premises();
    }
    private List<Target> targets(StorageIndex.Resolution destination,Strength requested,boolean scoped,boolean broad) {
        if(destination.remainder() instanceof Scopes.NoMemory
                ||destination.remainder() instanceof Scopes.WithinMemory within&&within.scope() instanceof Scopes.AllMemory){
            var direct=StorageIndex.nonEmptyWholeCandidates(destination);
            if(direct==null&&destination.candidates().isEmpty()
                    &&destination.remainder() instanceof Scopes.WithinMemory within&&within.scope() instanceof Scopes.AllMemory
                    &&storage.session().index().store().storageInventory().isPresent())direct=List.of();
            if(direct!=null){
                var remainder=destination.remainder() instanceof Scopes.WithinMemory within
                    ?storage.select(within.scope()).candidates():List.<StorageIndex.Candidate>of();
                var result=new WholeTargets(direct,remainder,requested==Strength.MUST&&destination.exact()?Strength.MUST:Strength.MAY,destination.reasons());
                int count=direct.size();
                if(broad)explicitBroadTargetsPrepared+=count;else if(scoped)boundTargetsPrepared+=count;else directTargetsPrepared+=count;
                explicitBroadTargetsPrepared+=remainder.size();targetsPrepared+=result.size();return result;
            }
        }
        var result=new LinkedHashSet<Target>();var direct=new ArrayList<StorageIndex.Candidate>(destination.candidates());
        for(var candidate:direct) {
            var location=candidate.location();if(location.range().isPresent()&&location.range().get().empty())continue;
            if(result.add(new Target(location,requested==Strength.MUST&&destination.exact()?Strength.MUST:Strength.MAY,true,List.of(),destination.reasons()))) {
                if(broad)explicitBroadTargetsPrepared++;else if(scoped)boundTargetsPrepared++;else directTargetsPrepared++;
            }
        }
        if(destination.remainder() instanceof Scopes.WithinMemory remainder) {
            var selected=storage.select(remainder.scope());
            boolean remainderBroad=broad||explicitBroad(remainder.scope());
            for(var candidate:selected.candidates())if(result.add(new Target(candidate.location(),Strength.MAY,false,List.of(),destination.reasons()))) {
                if(remainderBroad)explicitBroadTargetsPrepared++;else boundTargetsPrepared++;
            }
        }
        targetsPrepared+=result.size();return List.copyOf(result);
    }
    private static boolean explicitBroad(Scopes.MemoryScope scope) {
        if(scope instanceof Scopes.AllMemory||scope instanceof Scopes.VisibleMemory)return true;
        return scope instanceof Scopes.MemoryUnion union&&union.members().stream().anyMatch(StatementEffects::explicitBroad);
    }
    private final class Builder {
        final Operation operation;
        final List<Read> reads=new ArrayList<>();final List<Write> writes=new ArrayList<>(),otherwise=new ArrayList<>();
        final Map<Control.OutcomeKey,List<Write>> outcomes=new HashMap<>();
        final Map<OperandId,Place> places=new HashMap<>();int nextSlot;
        Builder(Operation operation){this.operation=operation;}
        void write(List<Write> out,Optional<OperandId> occurrence,StorageIndex.Resolution destination,Strength strength,Source source) {
            if(storage.session().index().unprovedPreconditions(operation.header().id()))strength=Strength.MAY;
            var place=occurrence.map(places::get).orElse(null);
            var targets=targets(destination,strength);
            out.add(new Write(nextSlot++,occurrence,destination,source,targets,Selection.SINGLE_DESTINATION,strength,logicalTargets(place,targets)));
        }
        List<LogicalTarget> logicalTargets(Place place,List<Target> targets) {
            // Index once by positive storage scope; no write scans unrelated open objects.
            var explicit=place==null?List.<ObjectId>of():storage.explicitObjects(place);
            var result=new LinkedHashMap<ObjectId,Boolean>();
            for(var id:explicit)if(!storage.object(id).exact())result.put(id,true);
            if(openByBase.isEmpty()){
                if(wholeAliases!=null&&nonEmpty(targets))for(int at=0;at<wholeAliases.size();at++)result.putIfAbsent(wholeAliases.identity(at),false);
            }else{
                boolean pendingBroad=wholeAliases!=null;
                for(var target:targets){
                    var location=target.location();var narrow=openByBase.getOrDefault(location.base().id(),List.of());
                    if(pendingBroad&&location.range().filter(StorageRange::empty).isEmpty()){
                        int at=0;
                        for(int broad=0;broad<wholeAliases.size();broad++){
                            int ordinal=wholeAliases.ordinal(broad);
                            while(at<narrow.size()&&narrow.get(at).declarationOrdinal()<ordinal){
                                var open=narrow.get(at++);if(!storage.disjoint(location,open.location()))result.putIfAbsent(open.object(),false);
                            }
                            result.putIfAbsent(wholeAliases.identity(broad),false);
                        }
                        while(at<narrow.size()){var open=narrow.get(at++);if(!storage.disjoint(location,open.location()))result.putIfAbsent(open.object(),false);}
                        pendingBroad=false;
                    }else for(var open:narrow)if(!storage.disjoint(location,open.location()))result.putIfAbsent(open.object(),false);
                }
            }
            return result.entrySet().stream().map(e->new LogicalTarget(e.getKey(),e.getValue())).toList();
        }
        boolean nonEmpty(List<Target> targets){
            if(targets instanceof WholeTargets whole){
                if(!whole.direct.isEmpty())return true;
                if(whole.remainder.isEmpty())return false;
                var owner=StorageIndex.address(whole.remainder,0).orElseThrow().owner();
                return !owner.nonEmptyStorage().isEmpty();
            }
            for(var target:targets)if(target.location().range().filter(StorageRange::empty).isEmpty())return true;
            return false;
        }
        void scopeWrite(List<Write> out,Scopes.MemoryScope scope,String reason) {
            var destination=storage.select(scope);
            var targets=targets(destination,Strength.MAY,true,explicitBroad(scope));
            out.add(new Write(nextSlot++,Optional.empty(),destination,new UnknownSource(reason),targets,Selection.MAY_SET,Strength.MAY,logicalTargets(null,targets)));
        }
        void boundRead(Scopes.MemoryBound bound,ReadKind kind) { if(bound instanceof Scopes.WithinMemory w)reads.add(new Read(Optional.empty(),kind,storage.select(w.scope()))); }
        void foreign(Interactions.ForeignEffects e,List<Write> out) {
            boundRead(e.reads(),ReadKind.FOREIGN);
            if(e.writes() instanceof Scopes.WithinMemory w)scopeWrite(out,w.scope(),"FOREIGN_MAY_WRITE");
            for(var id:e.mustOverwrite())write(out,Optional.of(id),occurrence(id),Strength.MUST,new UnknownSource("FOREIGN_MUST_WRITE"));
        }
        StorageIndex.Resolution occurrence(OperandId id) {
            var place=places.get(id);if(place==null)throw new IllegalArgumentException("effect references a non-place occurrence");return storage.resolve(place);
        }
        void envelope(Envelopes.MemoryEnvelope e,List<Write> out) {
            for(var id:e.knownReads())reads.add(new Read(Optional.of(id),ReadKind.VALUE,occurrence(id)));
            boundRead(e.otherReads(),ReadKind.FOREIGN);
            if(e.otherWrites() instanceof Scopes.WithinMemory w)scopeWrite(out,w.scope(),"ENVELOPE_MAY_WRITE");
            for(var id:e.knownWrites())write(out,Optional.of(id),occurrence(id),Strength.MAY,new UnknownSource("ENVELOPE_KNOWN_WRITE"));
            for(var id:e.mustOverwrite())write(out,Optional.of(id),occurrence(id),Strength.MUST,new UnknownSource("ENVELOPE_MUST_WRITE"));
        }
        void visit(Operand root,ReadKind kind) {
            record Visit(Operand operand,ReadKind kind) { }
            var pending=new ArrayDeque<Visit>();pending.push(new Visit(root,kind));
            while(!pending.isEmpty()) {
                var item=pending.pop();var operand=item.operand();var role=operand.header().role()==Operand.Role.ADDRESS_READ?ReadKind.ADDRESS:
                    item.kind();operandVisits++;
                if(operand instanceof Place p) {
                    places.put(p.header().id(),p);
                    if(p instanceof Places.RegionSlice s){pending.push(new Visit(s.length(),ReadKind.ADDRESS));pending.push(new Visit(s.offset(),ReadKind.ADDRESS));}
                    else if(p instanceof Places.Choice c)for(int i=c.candidates().size()-1;i>=0;i--)pending.push(new Visit(c.candidates().get(i),ReadKind.ADDRESS));
                } else if(operand instanceof Expressions.Read r) {
                    reads.add(new Read(Optional.of(r.header().id()),role,storage.resolve(r.place())));pending.push(new Visit(r.place(),ReadKind.ADDRESS));
                } else if(operand instanceof Expressions.Unknown u) {
                    boundRead(u.remainingReads(),role);for(int i=u.dependencies().size()-1;i>=0;i--)pending.push(new Visit(u.dependencies().get(i),role));
                } else {
                    var children=Operands.children(operand);
                    for(int i=children.size()-1;i>=0;i--)pending.push(new Visit(children.get(i),role));
                }
            }
        }
    }
}
