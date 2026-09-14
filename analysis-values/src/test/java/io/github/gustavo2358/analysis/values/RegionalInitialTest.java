package io.github.gustavo2358.analysis.values;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;
import static io.github.gustavo2358.analysis.values.RegionalCompositionTest.with;
class RegionalInitialTest {
    static Entries.InitialCondition seed(String id,int start,String text) {
        var owner=new EntryOwner(new EntryId(U,"entry"));var o=origin(P);
        var place=new Places.RegionSlice(new Operand.Header(new OperandId(owner,id),Operand.Role.VALUE_WRITE,o),R,
            new Expressions.Literal(new Operand.Header(new OperandId(owner,id+"-offset"),Operand.Role.VALUE_READ,o),new Values.IntValue(BigInteger.valueOf(start))),
            new Expressions.Literal(new Operand.Header(new OperandId(owner,id+"-length"),Operand.Role.VALUE_READ,o),new Values.IntValue(BigInteger.valueOf(text.length()))),IBM,Types.known(Types.Builtin.TEXT));
        return new Entries.InitialCondition(place,new Entries.LiteralInitial(new Expressions.Literal(new Operand.Header(new OperandId(owner,id+"-value"),Operand.Role.VALUE_READ,o),new Values.TextValue(text))),o,List.of());
    }
    static Publication seeded(List<Sequence> sequences,List<Entries.InitialCondition> conditions) {
        var p=regional(sequences);var u=p.units().getFirst();var e=u.entries().getFirst();
        var entry=new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(conditions,List.of()),e.origin());
        return replace(p,List.of(unit(U,List.of(entry),u.sequences(),u.objects())),p.coverage(),p.uncertainties(),p.premises());
    }
    @Test void partialOverlappingInitialConditionsAreSimultaneousAndRetainAllSupports() {
        for(boolean reverse:List.of(false,true)) {
            var seeds=new ArrayList<>(List.of(seed("left",0,"ABCDEF"),seed("right",2,"CDEFGH"),seed("middle",3,"DE")));if(reverse)Collections.reverse(seeds);
            var value=at(run(seeded(List.of(returning(U,"s0",List.of())),seeds)),"return-s0",WHOLE);
            assertEquals(List.of("ABCDEFGH"),texts(value));assertFalse(value.modelValueRemainder());
            assertEquals(Set.of("left","right","middle"),new HashSet<>(value.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList()));
        }
    }
    @Test void partialKillDropsOnlyCoveredInitialSupportsAndCopyRetainsTheRest() {
        var seeds=List.of(seed("whole",0,"ABCDEFGH"),seed("prefix",0,"ABCD"));
        var p=seeded(List.of(returning(U,"s0",List.of(assign(U,"overwrite",PREFIX,"WXYZ")))),seeds);
        var value=at(run(p),"return-s0",WHOLE);assertEquals(List.of("WXYZEFGH"),texts(value));
        assertEquals(Set.of("whole","overwrite"),new HashSet<>(value.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList()));
        var twice=seeded(List.of(returning(U,"s0",List.of(RegionalCompositionTest.copy("shift",R,0,R,4,4)))),seeds);
        var copied=at(run(twice),"return-s0",SUFFIX);assertEquals(List.of("ABCD"),texts(copied));
        assertEquals(Set.of("whole","prefix"),new HashSet<>(copied.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList()));
    }
    @Test void detachedAlternativesPreserveNonoverlappingWireFragmentsAndEveryInitialOrigin() {
        var p=seeded(List.of(returning(U,"s0",List.of())),List.of(seed("left",0,"ABCDEF"),seed("right",2,"CDEFGH"),seed("middle",3,"DE")));
        var q=new PointQuery<io.github.gustavo2358.analysis.storage.StorageSubject>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-s0")),new io.github.gustavo2358.analysis.storage.StorageSubject.NamedObject(WHOLE));
        var fact=run(p).observeStorage(List.of(q)).observations().getFirst().value();var producers=new HashSet<String>();
        for(var alternative:fact.alternatives()) {
            var cursor=BigInteger.ZERO;
            for(var fragment:alternative.fragments()) {
                var range=fragment.location().location().range().orElseThrow();assertEquals(cursor,range.start(),"wire alternative must have no overlap or hole");cursor=range.end().orElseThrow();
                producers.add(fragment.producer().orElseThrow().definition().destination().orElseThrow().localId());
            }
            assertEquals(BigInteger.valueOf(8),cursor);
        }
        assertEquals(Set.of("left","right","middle"),producers);assertTrue(fact.alternatives().size()<=5);
    }
    @Test void unspecifiedBytesRemainUnknownWhileKnownChildCanBeRead() {
        var execution=run(seeded(List.of(returning(U,"s0",List.of())),List.of(seed("prefix",0,"ABCD"))));
        assertEquals(List.of("ABCD"),texts(at(execution,"return-s0",PREFIX)));assertFalse(at(execution,"return-s0",PREFIX).modelValueRemainder());
        assertTrue(at(execution,"return-s0",WHOLE).modelValueRemainder());assertTrue(at(execution,"return-s0",SUFFIX).modelValueRemainder());
    }
    @Test void overwriteKillsInitialDefinitionAcrossLoopAndInitialLabelBackedge() {
        var p=seeded(List.of(with(jump(U,"s0","head"),assign(U,"overwrite",WHOLE,"OTHERPGM")),branch(U,"head","s0","exit"),returning(U,"exit",List.of())),List.of(seed("value",0,"PGM00001")));
        var execution=run(p);var value=at(execution,"return-exit",WHOLE);assertEquals(List.of("OTHERPGM"),texts(value));assertFalse(value.modelValueRemainder());
        assertEquals(List.of("overwrite"),value.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList());
        var initial=execution.observe(List.of(new PointQuery<>(ProgramPoint.entry(new EntryId(U,"entry")),WHOLE))).observations().getFirst().value();assertEquals(List.of("PGM00001"),texts(initial));
    }
}
