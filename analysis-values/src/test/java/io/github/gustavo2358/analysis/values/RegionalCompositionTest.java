package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;

/** Independently specified collecting semantics; no expected value is read from the domain. */
class RegionalCompositionTest {
    static final StorageId Y=new StorageId(P,"destination");
    static final ObjectId YWHOLE=new ObjectId(U,"destination-whole");
    @Test void extendedDomainHasAnExplicitProfileAndRejectsTheOldKey() {
        var provider=new RegionalValuesProvider();var entry=new EntryId(U,"entry");
        assertTrue(provider.supports(RegionalValuesProvider.key(entry)));
        var old=new io.github.gustavo2358.analysis.plan.AnalysisKey("RegionalValues","1","regional-text-images@1",io.github.gustavo2358.analysis.solver.Direction.FORWARD,"FINITE_REGIONAL_IMAGES",Map.of(),entry);
        assertFalse(provider.supports(old));assertEquals("RegionalValueFact@1",provider.projection());
    }
    static Sequence with(Sequence control,Instruction... instructions) {
        return new Sequence(control.label(),List.of(instructions),control.terminator(),origin(P));
    }
    static Operations.CopyBytes copy(String id,StorageId source,int sourceOffset,StorageId destination,int destinationOffset,int length) {
        var h=header(U,id);
        java.util.function.BiFunction<String,Integer,Expressions.Literal> integer=(name,value)->new Expressions.Literal(operand(h.id(),name,Operand.Role.ADDRESS_READ),new Values.IntValue(BigInteger.valueOf(value)));
        var src=new Memory.ByteRange(source,integer.apply("source-offset",sourceOffset),integer.apply("source-length",length));
        var dst=new Memory.ByteRange(destination,integer.apply("destination-offset",destinationOffset),integer.apply("destination-length",length));
        var reads=new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(source)));
        var writes=new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(destination)));
        var fallback=new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(),reads,List.of(),writes,List.of()),new Control.ControlEnvelope(List.of(Control.ContinueAlternative.INSTANCE),Scopes.NoControl.INSTANCE),new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE));
        return new Operations.CopyBytes(h,dst,src,BigInteger.valueOf(length),fallback);
    }
    static Publication twoBases(List<Sequence> sequences) {
        var p=regional(sequences);var u=p.units().getFirst();var objects=new ArrayList<>(u.objects());
        objects.add(new Memory.ObjectDeclaration(YWHOLE,Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.ViewBinding(Y,BigInteger.ZERO,BigInteger.valueOf(8),IBM),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,header(U,"metadata").precision()));
        var storage=new ArrayList<>(p.storage());storage.add(new Memory.Region(new Memory.StorageHeader(Y,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Optional.of(BigInteger.valueOf(8)),Optional.empty()));
        return new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,u.entries(),u.sequences(),objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),List.of(new Proofs.Premise(new PremiseId(P,"separate"),"manual contract","independent allocations",origin(P),new Proofs.DisjointStorage(List.of(R,Y)))));
    }
    @Test void partialLiteralComposesTwoProducers() {
        var value=at(run(regional(List.of(returning(U,"s0",List.of(assign(U,"whole-write",WHOLE,"ABCDEFGH"),assign(U,"prefix-write",PREFIX,"WXYZ")))))),"return-s0",WHOLE);
        assertEquals(List.of("WXYZEFGH"),texts(value));assertFalse(value.modelValueRemainder());
        assertEquals(Set.of("whole-write","prefix-write"),new HashSet<>(value.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList()));
    }
    @Test void branchHalvesStayInTheirOriginalImage() {
        var p=regional(List.of(branch(U,"s0","a","b"),
            with(jump(U,"a","join"),assign(U,"a-prefix",PREFIX,"AAAA"),assign(U,"a-suffix",SUFFIX,"BBBB")),
            with(jump(U,"b","join"),assign(U,"b-prefix",PREFIX,"CCCC"),assign(U,"b-suffix",SUFFIX,"DDDD")),returning(U,"join",List.of())));
        var value=at(run(p),"return-join",WHOLE);
        assertEquals(List.of("AAAABBBB","CCCCDDDD"),texts(value));assertFalse(value.modelValueRemainder());
    }
    @Test void crossBasePartialCopyPreservesBranchPairs() {
        var p=twoBases(List.of(branch(U,"s0","a","b"),
            with(jump(U,"a","join"),assign(U,"a-x",WHOLE,"AAAABBBB"),assign(U,"a-y",YWHOLE,"CCCCDDDD")),
            with(jump(U,"b","join"),assign(U,"b-x",WHOLE,"WWWWXXXX"),assign(U,"b-y",YWHOLE,"YYYYZZZZ")),
            returning(U,"join",List.of(copy("copy",R,0,Y,4,4)))));
        var value=at(run(p),"return-join",YWHOLE);
        assertEquals(List.of("CCCCAAAA","YYYYWWWW"),texts(value));assertFalse(value.modelValueRemainder());
    }
    @Test void oneDestinationChoiceCannotWriteBothHalves() {
        var h=header(U,"choose-half");
        var choice=new Places.Choice(operand(h.id(),"choice",Operand.Role.VALUE_WRITE),List.of(
            new Places.ObjectPlace(operand(h.id(),"left",Operand.Role.VALUE_WRITE),PREFIX),
            new Places.ObjectPlace(operand(h.id(),"right",Operand.Role.VALUE_WRITE),SUFFIX)),Scopes.NoMemory.INSTANCE,Types.known(Types.Builtin.TEXT));
        var write=new Operations.Assign(h,choice,new Expressions.Literal(operand(h.id(),"value",Operand.Role.VALUE_READ),new Values.TextValue("WXYZ")));
        var value=at(run(regional(List.of(returning(U,"s0",List.of(assign(U,"whole-write",WHOLE,"ABCDEFGH"),write))))),"return-s0",WHOLE);
        assertEquals(List.of("ABCDWXYZ","WXYZEFGH"),texts(value));assertFalse(value.modelValueRemainder());
    }
    @Test void overlappingCopyLoopHasExactlyTheFiniteConcreteImages() {
        var p=regional(List.of(with(jump(U,"s0","head"),assign(U,"initial",WHOLE,"ABCDEFGH")),branch(U,"head","body","exit"),
            with(jump(U,"body","head"),copy("shift",R,0,R,1,7)),returning(U,"exit",List.of())));
        var expected=new TreeSet<String>();String concrete="ABCDEFGH";
        while(expected.add(concrete))concrete=concrete.substring(0,1)+concrete.substring(0,7);
        assertEquals(8,expected.size());
        var execution=run(p);var value=at(execution,"return-exit",WHOLE);
        assertEquals(List.copyOf(expected),texts(value));assertFalse(value.modelValueRemainder());
        assertEquals(List.of("initial"),value.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList());
        assertTrue(execution.dataflow().metrics().nodesTransferred()<100);
    }
    @Test void jointStoreJoinLawsAndTransfersIncludeUnknownAndSupportOnlyChanges() {
        var a=assign(U,"producer-a",WHOLE,"ABCDEFGH");var b=assign(U,"producer-b",WHOLE,"ABCDEFGH");
        var y=assign(U,"producer-y",YWHOLE,"XXXXXXXX");var prefix=assign(U,"prefix",PREFIX,"WXYZ");var copy=copy("copy",R,0,Y,4,4);
        var p=twoBases(List.of(returning(U,"s0",List.of(a,b,y,prefix,copy))));var session=session(p);
        var analysis=RegionalValuesAnalysis.prepare(session,StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).analysis().orElseThrow();var engine=analysis.new Engine();
        var work=new io.github.gustavo2358.analysis.solver.DomainWork();var seed=engine.boundaries(session).iterator().next().state();
        var left=engine.operation(engine.operation(seed,y),a);var right=engine.operation(engine.operation(seed,y),b);
        assertFalse(engine.equivalent(left,right,work),"equal bytes with different supports are different states");
        var states=List.of(engine.bottom(),seed,left,right,engine.operation(left,prefix));
        java.util.function.BinaryOperator<RegionalValuesAnalysis.State> join=(x,z)->engine.joinInto(x,z,work).state();
        for(var x:states) {
            assertFalse(engine.joinInto(x,x,work).changed());
            assertTrue(engine.equivalent(x,join.apply(x,engine.bottom()),work));
            for(var z:states) {
                var union=join.apply(x,z);assertTrue(engine.equivalent(union,join.apply(z,x),work));
                assertFalse(engine.joinInto(union,x,work).changed());assertFalse(engine.joinInto(union,z,work).changed());
                for(var v:states)assertTrue(engine.equivalent(join.apply(union,v),join.apply(x,join.apply(z,v)),work));
                for(var operation:List.of(a,prefix,copy)) {
                    var whole=engine.operation(union,operation);
                    var separate=join.apply(engine.operation(x,operation),engine.operation(z,operation));
                    assertTrue(engine.equivalent(whole,separate,work),"transfer distributes over joint alternatives");
                    assertFalse(engine.joinInto(whole,engine.operation(x,operation),work).changed());
                }
            }
        }
        assertTrue(engine.joinInto(left,right,work).changed(),"support-only change propagates");
        assertFalse(engine.equivalent(seed,engine.bottom(),work),"reached unknown is not bottom");
    }
    @Test void copyConnectedInventoryPermutationPreservesFactsAndSupports() {
        var p=twoBases(List.of(branch(U,"s0","a","b"),
            with(jump(U,"a","join"),assign(U,"a-x",WHOLE,"AAAABBBB"),assign(U,"a-y",YWHOLE,"CCCCDDDD")),
            with(jump(U,"b","join"),assign(U,"b-x",WHOLE,"WWWWXXXX"),assign(U,"b-y",YWHOLE,"YYYYZZZZ")),
            returning(U,"join",List.of(copy("copy",R,0,Y,4,4)))));
        var expected=at(run(p),"return-join",YWHOLE);var u=p.units().getFirst();
        var sequences=new ArrayList<>(u.sequences());Collections.reverse(sequences);var objects=new ArrayList<>(u.objects());Collections.reverse(objects);
        var storage=new ArrayList<>(p.storage());Collections.reverse(storage);
        var permuted=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,u.entries(),sequences,objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        assertEquals(expected,at(run(permuted),"return-join",YWHOLE));
    }
}
