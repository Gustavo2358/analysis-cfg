package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.storage.StorageFixtures.*;
import static io.github.gustavo2358.analysis.storage.ReachingDefinitionsTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RegionalDefinitionAdversarialTest {
    @Test void differentBasesAndAlternativeDestinationsKeepPossibleOldDefinitions() {
        var bases=List.of(region("a",8L,Memory.Lifetime.ACTIVATION),region("b",8L,Memory.Lifetime.ACTIVATION));
        for(boolean proof:List.of(false,true)) {
            var p=publication(bases,List.of(view("a","a",0,8),view("b","b",0,8)),List.of(sequence("s",List.of(assign("d1","a",65,66,67,68,69,70,71,72),assign("d2","b",49,50,51,52,53,54,55,56)))),proof?List.of(disjoint("a","b")):List.of());
            var run=new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p)))).execute();var result=fact(run,after("d2","a"));
            assertEquals(Set.of("d1:0..8"),contributions(result));assertFalse(result.unknownRemainder());
            assertTrue(result.premises().isEmpty());
        }
        var choice=new Memory.AlternativesBinding(List.of(new Memory.AliasBinding(object("left")),new Memory.AliasBinding(object("right"))),Scopes.NoMemory.INSTANCE);
        var effect=new Operations.HavocMust(header("effect"),place("effect","choice"),UNKNOWN);
        var p=publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(view("all","r",0,8),view("left","r",0,4),view("right","r",4,4),declaration("choice",choice)),List.of(sequence("s",List.of(assign("d1","all",65,66,67,68,69,70,71,72),effect))),List.of());
        var run=new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p)))).execute();assertEquals(Set.of("d1:0..8","effect:0..8"),contributions(fact(run,after("effect","all"))));
    }
    @Test void opaqueEffectIsNotNopAndBeforePointStaysIndependent() {
        var opaque=new Operations.Opaque(uncertainHeader("opaque"),"unknown",List.of(),List.of(),envelope(new Scopes.WithinMemory(new Scopes.ObjectsMemory(List.of(object("all")))),new Control.JumpAlternative(new LabelId(U,"done"))));
        var p=publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(view("all","r",0,8)),List.of(new Sequence(new LabelId(U,"s"),List.of(assign("d1","all",65,66,67,68,69,70,71,72)),opaque,O),sequence("done",List.of())),List.of());
        var run=new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p)))).execute();
        var before=fact(run,before("opaque","all"));assertEquals(Set.of("d1:0..8"),contributions(before));assertFalse(before.unknownRemainder());
        var after=fact(run,before("return-done","all"));assertEquals(Set.of("d1:0..8","opaque:0..8"),contributions(after));assertTrue(after.unknownRemainder());assertTrue(after.uncertainties().isEmpty(),"header diagnostics are not definition identity");
    }
    @Test void sourceGapIsDiagnosticOnDeclaredSubject() {
        var original=view("left","r",0,4);var precision=original.precision();
        var gap=new Evidence.Claim(new Scopes.EntityScope(List.of(original.id())),Evidence.PrecisionStatus.OPEN,List.of(UNKNOWN));
        var partial=new Memory.ObjectDeclaration(original.id(),original.displayName(),original.typeRef(),original.storage(),original.visibility(),original.origin(),Evidence.CoverageStatus.MODELED,new Evidence.Precision(precision.control(),gap,precision.effects(),precision.values(),precision.dependencies()));
        var p=publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(view("all","r",0,8),partial,view("right","r",4,4),declaration("alias",new Memory.AliasBinding(object("left")))),List.of(sequence("s",List.of(assign("d1","all",65,66,67,68,69,70,71,72)))),List.of());
        var run=new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p)))).execute();
        var alias=fact(run,after("d1","alias"));assertFalse(alias.unknownRemainder());assertFalse(alias.sourceUnknownRemainder());
        assertTrue(fact(run,after("d1","left")).sourceUnknownRemainder());
        assertFalse(fact(run,after("d1","right")).sourceUnknownRemainder());
    }
    @Test void seededCellIsNotReinitializedOnLoopOrNextSequence() {
        var cell=new Memory.Cell(new Memory.StorageHeader(base("r"),Optional.of(U),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,O),BYTES);
        var jump=new Operations.Jump(header("to-loop"),new LabelId(U,"loop"));
        var predicate=new Expressions.Unknown(operand("branch","condition",Operand.Role.PREDICATE),Types.known(Types.Builtin.BOOL),List.of(),Scopes.NoMemory.INSTANCE,UNKNOWN);
        var branch=new Operations.Branch(header("branch"),predicate,new LabelId(U,"loop"),new LabelId(U,"done"));
        var p=publication(List.of(cell),List.of(declaration("all",new Memory.CellBinding(base("r")))),List.of(new Sequence(new LabelId(U,"s"),List.of(assign("d1","all",87,88,89,90)),jump,O),new Sequence(new LabelId(U,"loop"),List.of(),branch,O),sequence("done",List.of())),List.of());
        p=withEntries(p,List.of(seeded("main","s","all",65,66,67,68)));
        var run=new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p)))).execute();
        for(String operation:List.of("branch","return-done")) {
            var result=fact(run,before(operation,"all"));assertEquals(1,result.definitions().size());assertEquals("d1",result.definitions().getFirst().definition().operation().orElseThrow().localId());assertFalse(result.unknownRemainder());
        }
    }
    @Test void hugeBoundsUnknownTailAndZeroLengthNeverAllocatePerByte() {
        var huge=new BigInteger("184467440737095516170");
        var base=new Memory.Region(new Memory.StorageHeader(base("r"),Optional.of(U),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,O),Optional.of(huge.add(BigInteger.TEN)),Optional.empty());
        var object=declaration("large",new Memory.ViewBinding(base("r"),huge,BigInteger.valueOf(4),Memory.IdentityBytes.INSTANCE));
        var p=publication(List.of(base),List.of(object),List.of(sequence("s",List.of(assign("d1","large",1,2,3,4)))),List.of());
        var analysis=new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p))));assertEquals(3,analysis.partition().segments().size());
        var known=fact(analysis.execute(),after("d1","large"));assertFalse(known.unknownRemainder());assertEquals(huge,known.definitions().getFirst().contributedRanges().getFirst().location().range().orElseThrow().start());
        var tail=publication(List.of(region("u",null,Memory.Lifetime.EXTERNAL)),List.of(view("tail","u",0,4)),List.of(sequence("s",List.of())),List.of());
        var unknown=fact(new ReachingDefinitions(new StatementEffects(new StorageIndex(session(tail)))).execute(),before("return-s","tail"));assertTrue(unknown.unknownRemainder());assertTrue(unknown.resolutionRemainder());assertTrue(unknown.uncertainties().contains(UNKNOWN));
        var zero=publication(List.of(region("z",0L,Memory.Lifetime.ACTIVATION)),List.of(view("zero","z",0,0)),List.of(sequence("s",List.of(assign("empty","zero")))),List.of());
        var emptyAnalysis=new ReachingDefinitions(new StatementEffects(new StorageIndex(session(zero))));assertTrue(emptyAnalysis.partition().segments().isEmpty());
        var empty=fact(emptyAnalysis.execute(),after("empty","zero"));assertEquals(DefinitionFact.Reachability.REACHABLE,empty.reachability());assertFalse(empty.unknownRemainder());assertTrue(empty.definitions().isEmpty());
    }
    @Test void wideDefinitionsAndDuplicateQueriesHaveNoCandidateCapOrRepeatedReplay() {
        for(int size:List.of(1,2,5,40,256,1000)) {
            var objects=new ArrayList<Memory.ObjectDeclaration>();objects.add(view("all","r",0,size));var instructions=new ArrayList<Instruction>();
            var requests=new ArrayList<PointQuery<ObjectId>>();
            for(int i=0;i<size;i++) {objects.add(view("v"+i,"r",i,1));instructions.add(assign("d"+i,"v"+i,65));requests.add(before("return-s","v"+i));requests.add(before("return-s","v"+i));}
            requests.add(before("return-s","all"));
            var p=publication(List.of(region("r",(long)size,Memory.Lifetime.ACTIVATION)),objects,List.of(sequence("s",instructions)),List.of());
            var session=session(p);var analysis=new ReachingDefinitions(new StatementEffects(new StorageIndex(session)));var run=analysis.execute();
            var batch=run.observe(requests);assertEquals(size+1,batch.observations().size());assertEquals(size,batch.metrics().operationsReplayed());assertEquals(1,batch.metrics().sequencesReplayed());
            var all=batch.observations().stream().filter(o->o.query().subject().equals(object("all"))).findFirst().orElseThrow().value();assertEquals(size,all.definitions().size());assertFalse(all.unknownRemainder());
            assertEquals(size,run.dataflow().out(session.context(new EntryId(U,"main")),session.index().sequence(new LabelId(U,"s"))).explicitSegments());
            assertEquals(size,analysis.partition().segments().size());
            System.out.println("ST_RD_SCALE definitions="+size+" segments="+analysis.partition().segments().size()+" replay="+batch.metrics().operationsReplayed()+" queries="+batch.metrics().uniqueQueries());
        }
    }

    @Test void openEnvironmentDefinitionsDeduplicateRedundantSeparation() {
        for(int attempt=0;attempt<32;attempt++) {
            var effect=new Operations.HavocMay(header("effect"),new Scopes.AllMemory(P,true),UNKNOWN);
            var p=publication(List.of(region("a",8L,Memory.Lifetime.ACTIVATION),region("b",8L,Memory.Lifetime.ACTIVATION)),
                List.of(view("a","a",0,8),view("b","b",0,8)),List.of(sequence("s",List.of(effect))),List.of(disjoint("a","b")));
            var run=new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p)))).execute();
            var result=fact(run,after("effect","a"));
            var events=result.definitions().stream().map(DefinitionFact.Contribution::definition).filter(e->e.operation().isPresent()).toList();
            assertEquals(1,events.size(),"the same semantic target is not duplicated by a redundant separation assertion");
            assertEquals(List.of(List.of()),events.stream().map(DefinitionEvent::premises).toList(),"full event metadata has canonical order");
            assertTrue(result.unknownRemainder());
            assertEquals(Set.of("ENTRY:0..8","effect:0..8"),contributions(result));
        }
    }
}
