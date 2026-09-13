package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.math.BigInteger;
import java.util.*;

/** Canonical AIR memory semantics, prepared outside the solver and shared by RD and values. */
public final class StatementEffects {
    public enum Strength { MUST, MAY }
    public enum ReadKind { VALUE, ADDRESS, TARGET, FOREIGN }
    public sealed interface Source permits ExpressionSource, CapturedBytes, UnknownSource { }
    public record ExpressionSource(Expression value) implements Source { }
    public record CapturedBytes(StorageIndex.Resolution source, BigInteger length) implements Source { }
    public record UnknownSource(String reason) implements Source { }
    public record Read(Optional<OperandId> occurrence,ReadKind kind,StorageIndex.Resolution location) { }
    public record Target(StorageIndex.Location location,Strength strength,boolean sourceApplicable,List<PremiseId> premises,List<String> reasons) {
        public Target { premises=List.copyOf(premises);reasons=List.copyOf(reasons); }
    }
    public record Write(int slot,Optional<OperandId> occurrence,StorageIndex.Resolution destination,Source source,List<Target> targets) {
        public Write { targets=List.copyOf(targets); }
    }
    public record Statement(Operation operation,List<Read> reads,List<Write> writes,
                            Map<Control.OutcomeKey,List<Write>> outcomes,List<Write> otherwise) {
        public Statement { reads=List.copyOf(reads);writes=List.copyOf(writes);outcomes=Map.copyOf(outcomes);otherwise=List.copyOf(otherwise); }
    }
    private final StorageIndex storage;
    private final Map<OperationId,Statement> statements=new LinkedHashMap<>();
    private long operandVisits,targetsPrepared,baseComparisons;
    public StatementEffects(StorageIndex storage) {
        this.storage=Objects.requireNonNull(storage);
        for(var unit:storage.session().index().publication().units())for(var sequence:unit.sequences()) {
            for(var operation:sequence.instructions())statements.put(operation.header().id(),prepare(operation));
            var operation=sequence.terminator();statements.put(operation.header().id(),prepare(operation));
        }
    }
    public StorageIndex storage() { return storage; }
    public Collection<Statement> statements() { return Collections.unmodifiableCollection(statements.values()); }
    public Map<String,Long> preparationMetrics() { return Map.of("statementsPrepared",(long)statements.size(),"operandVisits",operandVisits,"targetsPrepared",targetsPrepared,"baseComparisons",baseComparisons); }
    public Statement statement(OperationId operation) {
        var result=statements.get(operation);if(result==null)throw new IllegalArgumentException("operation outside prepared snapshot");return result;
    }
    private Statement prepare(Operation operation) {
        var b=new Builder(operation);
        if(operation instanceof Operations.Assign a) {
            b.visit(a.value(),ReadKind.VALUE);b.visit(a.destination(),ReadKind.ADDRESS);
            b.write(b.writes,Optional.of(a.destination().header().id()),storage.resolve(a.destination()),Strength.MUST,new ExpressionSource(a.value()));
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
            for(var operand:o.knownOperands())b.visit(operand,ReadKind.ADDRESS);
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
        else if(operation instanceof Operations.LocalInvoke l)b.envelope(l.fallback().memory(),b.writes);
        else if(operation instanceof Operations.LocalBoundary l)b.envelope(l.fallback().memory(),b.writes);
        else if(operation instanceof Operations.LocalResume l)b.envelope(l.fallback().memory(),b.writes);
        else if(operation instanceof Operations.LocalUnwind l)b.envelope(l.fallback().memory(),b.writes);
        else if(!(operation instanceof Operations.Nop||operation instanceof Operations.Jump||operation instanceof Operations.Halt))
            throw new IllegalArgumentException("unsupported operation effect");
        b.outcomes.replaceAll((key,value)->List.copyOf(value));
        return new Statement(operation,b.reads,b.writes,b.outcomes,b.otherwise);
    }
    /** Normalized finite targets; only a proved singleton can replace old contributors. */
    public List<Target> targets(StorageIndex.Resolution destination,Strength requested) {
        var result=new LinkedHashSet<Target>();var direct=new ArrayList<StorageIndex.Candidate>(destination.candidates());
        for(var candidate:direct) {
            var location=candidate.location();if(location.range().isPresent()&&location.range().get().empty())continue;
            var proof=new LinkedHashSet<PremiseId>();
            for(var base:storage.bases()) {
                var other=storage.whole(base.header().id());
                if(other.base().id().equals(location.base().id()))continue;
                baseComparisons++;
                if(storage.disjoint(location,other))proof.addAll(storage.separationPremises(location,other));
                else result.add(new Target(other,Strength.MAY,false,List.of(),List.of("UNPROVEN_BASE_SEPARATION")));
            }
            result.add(new Target(location,requested==Strength.MUST&&destination.exact()?Strength.MUST:Strength.MAY,true,List.copyOf(proof),destination.reasons()));
        }
        if(destination.remainder() instanceof Scopes.WithinMemory remainder) {
            var scopes=new ArrayDeque<Scopes.MemoryScope>();var visited=new HashSet<Scopes.MemoryScope>();scopes.add(remainder.scope());
            while(!scopes.isEmpty()) {
                var scope=scopes.remove();
                if(!visited.add(scope)) {
                    // Self-referential safe scopes carry no usable base bound. Retain an all-base MAY effect.
                    for(var base:storage.bases())result.add(new Target(storage.whole(base.header().id()),Strength.MAY,false,List.of(),List.of("UNRESOLVED_SCOPE")));
                    continue;
                }
                var selected=storage.select(scope);
                for(var candidate:selected.candidates()) {
                    var location=candidate.location();result.add(new Target(location,Strength.MAY,false,List.of(),destination.reasons()));
                    for(var base:storage.bases()) {
                        var other=storage.whole(base.header().id());
                        if(!other.base().id().equals(location.base().id())&&!storage.disjoint(location,other))
                            result.add(new Target(other,Strength.MAY,false,List.of(),List.of("UNPROVEN_BASE_SEPARATION")));
                    }
                }
                if(selected.remainder() instanceof Scopes.WithinMemory w && !(scope instanceof Scopes.AllMemory))scopes.add(w.scope());
            }
        }
        targetsPrepared+=result.size();return List.copyOf(result);
    }
    private final class Builder {
        final Operation operation;
        final List<Read> reads=new ArrayList<>();final List<Write> writes=new ArrayList<>(),otherwise=new ArrayList<>();
        final Map<Control.OutcomeKey,List<Write>> outcomes=new HashMap<>();
        final Map<OperandId,Place> places=new HashMap<>();int nextSlot;
        Builder(Operation operation){this.operation=operation;}
        void write(List<Write> out,Optional<OperandId> occurrence,StorageIndex.Resolution destination,Strength strength,Source source) {
            out.add(new Write(nextSlot++,occurrence,destination,source,targets(destination,strength)));
        }
        void scopeWrite(List<Write> out,Scopes.MemoryScope scope,String reason) { write(out,Optional.empty(),storage.select(scope),Strength.MAY,new UnknownSource(reason)); }
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
                var item=pending.pop();var operand=item.operand();var role=item.kind();operandVisits++;
                if(operand instanceof Place p) {
                    places.put(p.header().id(),p);
                    if(p instanceof Places.RegionSlice s){pending.push(new Visit(s.length(),ReadKind.ADDRESS));pending.push(new Visit(s.offset(),ReadKind.ADDRESS));}
                    else if(p instanceof Places.Choice c)for(int i=c.candidates().size()-1;i>=0;i--)pending.push(new Visit(c.candidates().get(i),ReadKind.ADDRESS));
                } else if(operand instanceof Expressions.Read r) {
                    reads.add(new Read(Optional.of(r.header().id()),role,storage.resolve(r.place())));pending.push(new Visit(r.place(),ReadKind.ADDRESS));
                } else if(operand instanceof Expressions.Unknown u) {
                    boundRead(u.remainingReads(),role);for(int i=u.dependencies().size()-1;i>=0;i--)pending.push(new Visit(u.dependencies().get(i),role));
                } else if(operand instanceof Expressions.Binary b) {pending.push(new Visit(b.right(),role));pending.push(new Visit(b.left(),role));}
                else if(operand instanceof Expressions.Unary u)pending.push(new Visit(u.argument(),role));
                else if(operand instanceof Expressions.Quantize q)pending.push(new Visit(q.value(),role));
                else if(operand instanceof Expressions.FitText f)pending.push(new Visit(f.value(),role));
                else if(operand instanceof Expressions.SliceText s){pending.push(new Visit(s.count(),role));pending.push(new Visit(s.start(),role));pending.push(new Visit(s.value(),role));}
                else if(operand instanceof Expressions.TrimRight t)pending.push(new Visit(t.value(),role));
                else if(!(operand instanceof Expressions.Literal))throw new IllegalArgumentException("unsupported operand effect");
            }
        }
    }
}
