package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.storage.StorageFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageIndexTest {
    @Test void disjointPremisesAreNotTransitiveAndCodecDoesNotChangePhysicalOverlap() {
        var bases=List.of(region("a",8L,Memory.Lifetime.ACTIVATION),region("b",8L,Memory.Lifetime.ACTIVATION),region("c",8L,Memory.Lifetime.ACTIVATION));
        var ab=disjoint("a","b");var bc=new Proofs.Premise(new PremiseId(P,"bc"),"manual","b apart from c",O,new Proofs.DisjointStorage(List.of(base("b"),base("c"))));
        var text=new Memory.ObjectDeclaration(object("text"),Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.ViewBinding(base("a"),java.math.BigInteger.ZERO,java.math.BigInteger.valueOf(8),Memory.AsciiText.INSTANCE),Memory.Visibility.PRIVATE,O,Evidence.CoverageStatus.MODELED,header("m").precision());
        var index=new StorageIndex(session(publication(bases,List.of(view("a","a",0,8),view("b","b",0,8),view("c","c",0,8),text),List.of(sequence("s",List.of())),List.of(ab,bc))));
        var a=index.object(object("a")).candidates().getFirst();var b=index.object(object("b")).candidates().getFirst();var c=index.object(object("c")).candidates().getFirst();var t=index.object(object("text")).candidates().getFirst();
        assertTrue(index.disjoint(a.location(),b.location()));assertTrue(index.disjoint(b.location(),c.location()));assertFalse(index.disjoint(a.location(),c.location()));
        assertEquals(a.location(),t.location());assertNotEquals(a.codec(),t.codec());
    }
    @Test void cyclicAndDanglingAssociationsFailBeforeAnalysis() {
        for(var binding:List.of(new Memory.AliasBinding(object("cycle")),new Memory.AliasBinding(object("missing")))) {
            var p=publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(declaration("cycle",binding)),List.of(sequence("s",List.of())),List.of());
            var build=new io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinator(io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry.empty()).build(p,io.github.gustavo2358.analysis.cfg.application.BuildOptions.defaults());
            assertEquals(io.github.gustavo2358.analysis.cfg.application.CfgBuildResult.Status.INVALID_IR,build.status());
        }
    }
    @Test void snapshotIsolationAndPreparationCountFollowObjectsNotPairs() {
        for(int count:List.of(1,2,5,40,1000,4000)) {
            var objects=new ArrayList<Memory.ObjectDeclaration>();
            for(int i=0;i<count;i++)objects.add(view("v"+i,"r",i,1));
            var p=publication(List.of(region("r",(long)count,Memory.Lifetime.ACTIVATION)),objects,List.of(sequence("s",List.of())),List.of());
            var index=new StorageIndex(session(p));var metrics=index.preparationMetrics();
            assertEquals((long)count,metrics.get("objectsResolved"));assertEquals(2L*count,metrics.get("bindingVisits"));assertEquals(0L,metrics.get("objectPairsMaterialized"));
            assertEquals(Optional.of(StorageRangeTest.range(count-1,count)),index.object(object("v"+(count-1))).candidates().getFirst().location().range());
            System.out.println("ST_STORAGE_PREPARE objects="+count+" bindingVisits="+metrics.get("bindingVisits")+" objectPairs=0");
        }
        var oldIndex=new StorageIndex(session(publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(view("same","r",0,4)),List.of(sequence("s",List.of())),List.of())));
        var newIndex=new StorageIndex(session(publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(view("same","r",4,4)),List.of(sequence("s",List.of())),List.of())));
        assertNotEquals(oldIndex.object(object("same")),newIndex.object(object("same")));
        assertEquals(Optional.of(StorageRangeTest.range(0,4)),oldIndex.object(object("same")).candidates().getFirst().location().range());
    }
    @Test void physicalAliasesAndAdjacentRangesDoNotDependOnNames() {
        var p=publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(view("all","r",0,8),view("left","r",0,4),view("right","r",4,4),declaration("alias",new Memory.AliasBinding(object("left")))),List.of(sequence("s",List.of())),List.of());
        var index=new StorageIndex(session(p));
        var a=index.object(object("left"));var alias=index.object(object("alias"));var b=index.object(object("right"));
        assertTrue(a.exact());assertEquals(a.candidates().getFirst().location(),alias.candidates().getFirst().location());
        assertTrue(index.disjoint(a.candidates().getFirst().location(),b.candidates().getFirst().location()));
        assertFalse(index.disjoint(a.candidates().getFirst().location(),index.object(object("all")).candidates().getFirst().location()));
        assertEquals(a,index.resolve(place("unused","left")));
    }
    @Test void distinctBasesRequirePremiseAndLifetimeQualifiesActivationOnly() {
        var bases=List.of(region("r",8L,Memory.Lifetime.ACTIVATION),region("p",8L,Memory.Lifetime.PERSISTENT));
        var objects=List.of(view("a","r",0,8),view("b","p",0,8));
        for(boolean proof:List.of(false,true)) {
            var index=new StorageIndex(session(publication(bases,objects,List.of(sequence("s",List.of())),proof?List.of(disjoint("r","p")):List.of())));
            var a=index.object(object("a")).candidates().getFirst().location();var b=index.object(object("b")).candidates().getFirst().location();
            assertEquals(proof,index.disjoint(a,b));
            assertNotEquals(a.in(new EntryId(U,"one")),a.in(new EntryId(U,"two")));
            assertEquals(b.in(new EntryId(U,"one")),b.in(new EntryId(U,"two")));
        }
    }
    @Test void alternativesAndUnknownExtentRetainIndependentRemainder() {
        var alternative=new Memory.AlternativesBinding(List.of(new Memory.ViewBinding(base("r"),java.math.BigInteger.ZERO,java.math.BigInteger.valueOf(4),Memory.IdentityBytes.INSTANCE),new Memory.ViewBinding(base("r"),java.math.BigInteger.valueOf(4),java.math.BigInteger.valueOf(4),Memory.IdentityBytes.INSTANCE)),Scopes.NoMemory.INSTANCE);
        var unknown=new Memory.UnknownBinding(new Scopes.StorageMemory(List.of(base("r"))),UNKNOWN);
        var p=publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION),region("u",null,Memory.Lifetime.EXTERNAL)),List.of(declaration("choice",alternative),declaration("unknown",unknown),view("tail","u",0,4)),List.of(sequence("s",List.of())),List.of());
        var index=new StorageIndex(session(p));
        assertEquals(2,index.object(object("choice")).candidates().size());assertFalse(index.object(object("choice")).exact());
        assertInstanceOf(Scopes.NoMemory.class,index.object(object("choice")).remainder());
        assertFalse(index.object(object("unknown")).exact());assertInstanceOf(Scopes.WithinMemory.class,index.object(object("unknown")).remainder());
        assertFalse(index.object(object("tail")).exact());assertEquals(1,index.object(object("tail")).candidates().size());
        assertTrue(index.object(object("tail")).reasons().contains("UNKNOWN_EXTENT"));
    }
}
