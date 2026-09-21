package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.storage.StorageFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class StatementEffectsTest {
    @Test void boundedOpenObjectDoesNotReceiveUnrelatedLogicalWrite() {
        var write=assign("write-q","q",1,2,3,4,5,6,7,8);
        var bases=List.of(region("r1",8L,Memory.Lifetime.PERSISTENT),region("r2",8L,Memory.Lifetime.PERSISTENT));
        var objects=List.of(declaration("u",new Memory.UnknownBinding(new Scopes.StorageMemory(List.of(base("r1"))),UNKNOWN)),view("q","r2",0,8));
        var effects=new StatementEffects(new StorageIndex(session(publication(bases,objects,List.of(sequence("s",List.of(write))),List.of()))));
        var result=effects.statement(write.header().id()).writes().getFirst();
        assertEquals(1,result.targets().size());
        assertTrue(result.logicalTargets().stream().noneMatch(t->t.object().equals(object("u"))),
            "R1-bounded U cannot receive a logical Event from an R2-only write");
    }
    @Test void boundedOpenObjectReceivesIntersectingWriteOnly() {
        var write=assign("write-r1","q",1,2,3,4,5,6,7,8);
        var bases=List.of(region("r1",8L,Memory.Lifetime.PERSISTENT),region("r2",8L,Memory.Lifetime.PERSISTENT));
        var objects=List.of(declaration("u",new Memory.UnknownBinding(new Scopes.StorageMemory(List.of(base("r1"))),UNKNOWN)),view("q","r1",0,8));
        var effects=new StatementEffects(new StorageIndex(session(publication(bases,objects,List.of(sequence("s",List.of(write))),List.of()))));
        assertTrue(effects.statement(write.header().id()).writes().getFirst().logicalTargets().stream().anyMatch(t->t.object().equals(object("u"))));
        assertEquals(1,effects.statement(write.header().id()).writes().getFirst().targets().size());
    }
    @Test void oneWriteAmongManyExactCellsHasOneTarget() {
        var cells=new ArrayList<Memory.Storage>();var objects=new ArrayList<Memory.ObjectDeclaration>();
        for(int i=0;i<100;i++) {
            var id=base("c"+i);cells.add(new Memory.Cell(new Memory.StorageHeader(id,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,O),BYTES));
            objects.add(declaration("o"+i,new Memory.CellBinding(id)));
        }
        var write=assign("write-one","o42",1,2,3,4);
        var effects=new StatementEffects(new StorageIndex(session(publication(cells,objects,List.of(sequence("s",List.of(write))),List.of()))));
        var result=effects.statement(write.header().id()).writes().getFirst();
        assertEquals(1,result.targets().size());assertEquals(base("c42"),result.targets().getFirst().location().base().id());
        assertTrue(result.logicalTargets().isEmpty());assertEquals(1L,effects.preparationMetrics().get("targetsPrepared"));
        assertEquals(1L,effects.preparationMetrics().get("directTargetsPrepared"));
    }
    @Test void boundedDestinationExpansionIsCountedSeparately() {
        var write=assign("write-u","u",1,2,3,4,5,6,7,8);
        var bases=List.of(region("r1",8L,Memory.Lifetime.PERSISTENT),region("r2",8L,Memory.Lifetime.PERSISTENT));
        var objects=List.of(declaration("u",new Memory.UnknownBinding(new Scopes.StorageMemory(List.of(base("r1"))),UNKNOWN)));
        var effects=new StatementEffects(new StorageIndex(session(publication(bases,objects,List.of(sequence("s",List.of(write))),List.of()))));
        assertEquals(List.of(base("r1")),effects.statement(write.header().id()).writes().getFirst().targets().stream().map(t->t.location().base().id()).toList());
        assertEquals(1L,effects.preparationMetrics().get("boundTargetsPrepared"));
        assertEquals(0L,effects.preparationMetrics().get("directTargetsPrepared"));
    }
    @Test void boundedOpenObjectsFollowLocalizedRelations() {
        var bases=new ArrayList<Memory.Storage>();var objects=new ArrayList<Memory.ObjectDeclaration>();var writes=new ArrayList<Instruction>();
        for(int i=0;i<160;i++) {
            String id="r"+i;
            bases.add(region(id,8L,Memory.Lifetime.PERSISTENT));
            objects.add(declaration("u"+i,new Memory.UnknownBinding(new Scopes.StorageMemory(List.of(base(id))),UNKNOWN)));
            objects.add(view("q"+i,id,0,8));writes.add(assign("write"+i,"q"+i,1,2,3,4,5,6,7,8));
        }
        var effects=new StatementEffects(new StorageIndex(session(publication(bases,objects,List.of(sequence("s",writes)),List.of()))));
        long logical=effects.statements().stream().flatMap(s->s.writes().stream()).mapToLong(w->w.logicalTargets().size()).sum();
        assertEquals(160L,logical);
        assertEquals(160L,effects.preparationMetrics().get("targetsPrepared"));
        System.out.println("W3_R1_BOUNDED_OPEN objects=160 writes=160 logicalTargets="+logical);
    }
    @Test void explicitBroadScopeStillPaysForAllPublishedBases() {
        var bases=new ArrayList<Memory.Storage>();
        for(int i=0;i<32;i++)bases.add(region("b"+i,8L,Memory.Lifetime.PERSISTENT));
        var may=new Operations.HavocMay(header("explicit-broad"),new Scopes.AllMemory(P,false),UNKNOWN);
        var effects=new StatementEffects(new StorageIndex(session(publication(bases,List.of(),List.of(sequence("s",List.of(may))),List.of()))));
        assertEquals(32,effects.statement(may.header().id()).writes().getFirst().targets().size());
        assertEquals(32L,effects.preparationMetrics().get("targetsPrepared"));
        assertEquals(32L,effects.preparationMetrics().get("explicitBroadTargetsPrepared"));
    }
    @Test void explicitVisibleMemoryStillSelectsPublishedVisibleObjects() {
        var bases=List.of(region("r1",8L,Memory.Lifetime.PERSISTENT),region("r2",8L,Memory.Lifetime.PERSISTENT),region("unreferenced",8L,Memory.Lifetime.PERSISTENT));
        var objects=List.of(view("q1","r1",0,8),view("q2","r2",0,8));
        var may=new Operations.HavocMay(header("visible"),new Scopes.VisibleMemory(U,false),UNKNOWN);
        var effects=new StatementEffects(new StorageIndex(session(publication(bases,objects,List.of(sequence("s",List.of(may))),List.of()))));
        var targets=effects.statement(may.header().id()).writes().getFirst().targets();
        assertEquals(Set.of(base("r1"),base("r2")),targets.stream().map(t->t.location().base().id()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2L,effects.preparationMetrics().get("targetsPrepared"));
        assertEquals(2L,effects.preparationMetrics().get("explicitBroadTargetsPrepared"));
    }
    @Test void partialMustWriteDoesNotTouchIndependentBase() {
        var assignment=assign("d1","left",1,2,3,4);
        var bases=List.of(region("r",8L,Memory.Lifetime.ACTIVATION),region("other",8L,Memory.Lifetime.ACTIVATION));
        var objects=List.of(view("left","r",0,4),view("right","r",4,4),view("foreign","other",0,8));
        for(boolean proof:List.of(false,true)) {
            var index=new StorageIndex(session(publication(bases,objects,List.of(sequence("s",List.of(assignment))),proof?List.of(disjoint("r","other")):List.of())));
            var effects=new StatementEffects(index).statement(assignment.header().id());
            var write=effects.writes().getFirst();assertEquals(1,write.targets().size());
            var exact=write.targets().stream().filter(t->t.location().base().id().equals(base("r"))).findFirst().orElseThrow();
            assertEquals(StatementEffects.Strength.MUST,exact.strength());assertEquals(Optional.of(StorageRangeTest.range(0,4)),exact.location().range());
            assertTrue(write.targets().stream().allMatch(StatementEffects.Target::sourceApplicable));
        }
    }
    @Test void ambiguousMustAndScopedMayNeverBecomeSimultaneousStrongWrites() {
        var alternatives=new Memory.AlternativesBinding(List.of(new Memory.AliasBinding(object("left")),new Memory.AliasBinding(object("right"))),Scopes.NoMemory.INSTANCE);
        var must=new Operations.HavocMust(header("must"),place("must","choice"),UNKNOWN);
        var may=new Operations.HavocMay(header("may"),new Scopes.ObjectsMemory(List.of(object("left"))),UNKNOWN);
        var index=new StorageIndex(session(publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(view("left","r",0,4),view("right","r",4,4),declaration("choice",alternatives)),List.of(sequence("s",List.of(must,may))),List.of())));
        var effects=new StatementEffects(index);
        assertEquals(2,effects.statement(must.header().id()).writes().getFirst().targets().size());
        assertTrue(effects.statement(must.header().id()).writes().getFirst().targets().stream().allMatch(t->t.strength()==StatementEffects.Strength.MAY));
        assertEquals(StatementEffects.Selection.SINGLE_DESTINATION,effects.statement(must.header().id()).writes().getFirst().selection());
        assertEquals(StatementEffects.Strength.MUST,effects.statement(must.header().id()).writes().getFirst().occurrenceStrength());
        var scoped=effects.statement(may.header().id()).writes().getFirst();assertEquals(1,scoped.targets().size());
        assertEquals(StatementEffects.Selection.MAY_SET,scoped.selection());assertEquals(StatementEffects.Strength.MAY,scoped.occurrenceStrength());
        assertEquals(Optional.of(StorageRangeTest.range(0,4)),scoped.targets().getFirst().location().range());assertEquals(StatementEffects.Strength.MAY,scoped.targets().getFirst().strength());
    }
    @Test void copyCapturesSourceAndOpaqueUnknownWritesAreExplicit() {
        var id=header("copy");
        java.util.function.BiFunction<String,Long,Expressions.Literal> integer=(name,value)->new Expressions.Literal(operand("copy",name,Operand.Role.ADDRESS_READ),new Values.IntValue(java.math.BigInteger.valueOf(value)));
        var copy=new Operations.CopyBytes(id,new Memory.ByteRange(base("r"),integer.apply("d-offset",2L),integer.apply("d-size",4L)),new Memory.ByteRange(base("r"),integer.apply("s-offset",0L),integer.apply("s-size",4L)),java.math.BigInteger.valueOf(4),envelope(new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(base("r")))),Control.ContinueAlternative.INSTANCE));
        var opaque=new Operations.Opaque(uncertainHeader("opaque"),"uninterpreted",List.of(),List.of(),envelope(new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(base("r")))),Control.ReturnAlternative.INSTANCE));
        var seq=new Sequence(new LabelId(U,"s"),List.of(copy),opaque,O);
        var effects=new StatementEffects(new StorageIndex(session(publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(view("all","r",0,8)),List.of(seq),List.of()))));
        var s=effects.statement(copy.header().id());assertEquals(1,s.reads().size());assertInstanceOf(StatementEffects.CapturedBytes.class,s.writes().getFirst().source());
        assertEquals(Optional.of(StorageRangeTest.range(0,4)),s.reads().getFirst().location().candidates().getFirst().location().range());
        assertEquals(Optional.of(StorageRangeTest.range(2,6)),s.writes().getFirst().targets().getFirst().location().range());
        assertEquals(1,effects.statement(opaque.header().id()).writes().size());
        assertEquals(StatementEffects.Strength.MAY,effects.statement(opaque.header().id()).writes().getFirst().targets().getFirst().strength());
    }
}
