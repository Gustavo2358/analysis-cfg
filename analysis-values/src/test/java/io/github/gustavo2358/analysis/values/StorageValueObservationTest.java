package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.*;
import io.github.gustavo2358.analysis.storage.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;
import static io.github.gustavo2358.analysis.values.RegionalCompositionTest.*;

class StorageValueObservationTest {
    static StorageRange range(int start,int length){return StorageRange.exact(BigInteger.valueOf(start),BigInteger.valueOf(length));}
    static PointQuery<StorageSubject> query(String operation,StorageId storage,int start,int length) {
        return new PointQuery<>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,operation)),new StorageSubject.PhysicalRange(storage,range(start,length),IBM));
    }
    @Test void composedCandidateExposesBothSurvivingProducerIntervalsAndMatchingRdEvents() {
        var p=regional(List.of(returning(U,"s0",List.of(assign(U,"old",WHOLE,"ABCDEFGH"),assign(U,"new",PREFIX,"WXYZ")))));
        var session=session(p);var values=RegionalValuesAnalysis.prepare(session,StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).analysis().orElseThrow().execute();var q=query("return-s0",R,0,8);
        var batch=values.observeStorage(List.of(q,q));assertEquals(ObservationBatch.Status.COMPLETE,batch.status());assertEquals(1,batch.metrics().uniqueQueries());
        var fact=batch.observations().getFirst().value();assertEquals(List.of(new Values.TextValue("WXYZEFGH")),fact.candidates());assertFalse(fact.modelValueRemainder());
        assertEquals(1,fact.alternatives().size());var fragments=fact.alternatives().getFirst().fragments();assertEquals(2,fragments.size());
        var expected=Map.of("new",range(0,4),"old",range(4,4));
        var rd=new ReachingDefinitions(new StatementEffects(new StorageIndex(session))).execute().observeStorage(List.of(q)).observations().getFirst().value();
        for(var fragment:fragments) {
            var producer=fragment.producer().orElseThrow();var operation=producer.definition().operation().orElseThrow().localId();
            assertEquals(expected.get(operation),fragment.location().location().range().orElseThrow());
            assertEquals(expected.get(operation),producer.contributedRange().location().range().orElseThrow());
            assertEquals(StorageValueFact.FragmentKind.KNOWN_BYTES,fragment.kind());assertTrue(fragment.captures().isEmpty());
            assertTrue(rd.definitions().stream().anyMatch(c->c.definition().equals(producer.definition())&&c.contributedRanges().contains(fragment.location())));
        }
        var partial=values.observeStorage(List.of(query("return-s0",R,2,4))).observations().getFirst().value();
        assertEquals(List.of(new Values.TextValue("YZEF")),partial.candidates());
    }
    @Test void copiedFragmentHasOriginalProducerAndSeparateCaptureWithExactRanges() {
        var p=twoBases(List.of(returning(U,"s0",List.of(assign(U,"source",WHOLE,"ABCDEFGH"),assign(U,"destination",YWHOLE,"12345678"),
            copy("capture",R,2,Y,3,4),assign(U,"later-source",WHOLE,"XXXXXXXX")))));
        var fact=run(p).observeStorage(List.of(query("return-s0",Y,0,8))).observations().getFirst().value();
        assertEquals(List.of(new Values.TextValue("123CDEF8")),fact.candidates());assertFalse(fact.modelValueRemainder());
        var copied=fact.alternatives().getFirst().fragments().stream().filter(f->!f.captures().isEmpty()).findFirst().orElseThrow();
        assertEquals(range(3,4),copied.location().location().range().orElseThrow());
        var original=copied.producer().orElseThrow();assertEquals("source",original.definition().operation().orElseThrow().localId());
        assertEquals(R,original.contributedRange().location().base().id());assertEquals(range(2,4),original.contributedRange().location().range().orElseThrow());
        var capture=copied.captures().getFirst();assertEquals("capture",capture.definition().operation().orElseThrow().localId());assertEquals(DefinitionEvent.Kind.COPY,capture.definition().kind());
        assertEquals(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"capture")),capture.before());
        assertEquals(R,capture.sourceRange().location().base().id());assertEquals(range(2,4),capture.sourceRange().location().range().orElseThrow());
        assertEquals(Y,capture.destinationRange().location().base().id());assertEquals(range(3,4),capture.destinationRange().location().range().orElseThrow());
        assertEquals(capture.sourceRange(),capture.sourceContribution());assertEquals(capture.destinationRange(),capture.destinationContribution());
        assertFalse(fact.evidence().contains(new OperationId(U,"later-source")));
    }

    @Test void copyOfCopyRetainsCroppedIntermediateAndOriginalContributions() {
        var p=twoBases(List.of(returning(U,"s0",List.of(assign(U,"source",WHOLE,"ABCDEFGH"),assign(U,"destination",YWHOLE,"12345678"),
            copy("first",R,1,Y,2,5),copy("second",Y,3,R,0,3)))));
        var fact=run(p).observeStorage(List.of(query("return-s0",R,0,3))).observations().getFirst().value();
        assertEquals(List.of(new Values.TextValue("CDE")),fact.candidates());var fragment=fact.alternatives().getFirst().fragments().getFirst();
        assertEquals(range(2,3),fragment.producer().orElseThrow().contributedRange().location().range().orElseThrow());
        assertEquals(2,fragment.captures().size());
        var first=fragment.captures().stream().filter(c->c.definition().operation().orElseThrow().localId().equals("first")).findFirst().orElseThrow();
        assertEquals(range(1,5),first.sourceRange().location().range().orElseThrow());assertEquals(range(2,5),first.destinationRange().location().range().orElseThrow());
        assertEquals(range(2,3),first.sourceContribution().location().range().orElseThrow());assertEquals(range(3,3),first.destinationContribution().location().range().orElseThrow());
        var second=fragment.captures().stream().filter(c->c.definition().operation().orElseThrow().localId().equals("second")).findFirst().orElseThrow();
        assertEquals(range(3,3),second.sourceContribution().location().range().orElseThrow());assertEquals(range(0,3),second.destinationContribution().location().range().orElseThrow());
    }
    @Test void unknownPrefixExposesItsWriterAndKeepsOnlyTheSurvivingLiteralSuffix() {
        var p=RegionalTransferTest.uncertainty(regional(List.of(returning(U,"s0",List.of(assign(U,"old",WHOLE,"ABCDEFGH"),RegionalTransferTest.havoc("unknown-prefix",PREFIX))))));
        var fact=run(p).observeStorage(List.of(query("return-s0",R,0,8))).observations().getFirst().value();
        assertEquals(List.of(),fact.candidates());assertTrue(fact.modelValueRemainder());var fragments=fact.alternatives().getFirst().fragments();assertEquals(2,fragments.size());
        var prefix=fragments.getFirst();assertEquals(StorageValueFact.FragmentKind.UNKNOWN_BYTES,prefix.kind());assertTrue(prefix.producer().isEmpty());assertTrue(prefix.bytes().isEmpty());
        assertEquals("unknown-prefix",prefix.unknownWriter().orElseThrow().operation().orElseThrow().localId());assertFalse(prefix.modelReasons().isEmpty());
        var suffix=fragments.getLast();assertEquals(range(4,4),suffix.location().location().range().orElseThrow());assertEquals(range(4,4),suffix.producer().orElseThrow().contributedRange().location().range().orElseThrow());
    }
    @Test void branchSupportsRemainSeparateAlternativesAndInventoryOrderIsIrrelevant() {
        var p=twoBases(List.of(branch(U,"s0","a","b"),
            with(jump(U,"a","join"),assign(U,"a-x",WHOLE,"AAAABBBB"),assign(U,"a-y",YWHOLE,"CCCCDDDD")),
            with(jump(U,"b","join"),assign(U,"b-x",WHOLE,"WWWWXXXX"),assign(U,"b-y",YWHOLE,"YYYYZZZZ")),
            returning(U,"join",List.of(copy("copy",R,0,Y,4,4)))));
        var q=query("return-join",Y,0,8);var expected=run(p).observeStorage(List.of(q)).observations().getFirst().value();
        assertEquals(List.of(new Values.TextValue("CCCCAAAA"),new Values.TextValue("YYYYWWWW")),expected.candidates());assertEquals(2,expected.alternatives().size());
        for(var alternative:expected.alternatives()) {
            var names=alternative.fragments().stream().map(f->f.producer().orElseThrow().definition().operation().orElseThrow().localId()).collect(java.util.stream.Collectors.toSet());
            assertEquals(alternative.candidate().orElseThrow().value().equals("CCCCAAAA")?Set.of("a-x","a-y"):Set.of("b-x","b-y"),names);
        }
        var u=p.units().getFirst();var sequences=new ArrayList<>(u.sequences());Collections.reverse(sequences);var objects=new ArrayList<>(u.objects());Collections.reverse(objects);var storage=new ArrayList<>(p.storage());Collections.reverse(storage);
        var permuted=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,u.entries(),sequences,objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        assertEquals(expected,run(permuted).observeStorage(List.of(q)).observations().getFirst().value());
    }
    @Test void sourceGapMetadataDoesNotCreateCapturedFragments() {
        var baseline=twoBases(List.of(returning(U,"s0",List.of(assign(U,"source",WHOLE,"ABCDEFGH"),copy("copy",R,0,Y,0,8)))));
        var diagnostic=RegionalProvenanceTest.sourceGap(baseline,PREFIX);
        var query=query("return-s0",Y,0,8);
        var expected=run(baseline).observeStorage(List.of(query)).observations().getFirst().value();
        var fact=run(diagnostic).observeStorage(List.of(query)).observations().getFirst().value();
        assertEquals(expected,fact,"entire detached result including capture ranges and producer evidence is unchanged");
        assertFalse(fact.sourceUnknownRemainder());assertFalse(fact.modelValueRemainder());
        var fragments=fact.alternatives().getFirst().fragments();assertEquals(1,fragments.size());
        assertTrue(fragments.getFirst().sourceGaps().isEmpty());
        assertEquals(1,fragments.getFirst().captures().size());
        assertEquals("source",fragments.getFirst().producer().orElseThrow().definition().operation().orElseThrow().localId());
    }
    @Test void zeroLengthUnreachableAndUninterpretedBytesHaveDistinctProducts() {
        var p=regional(List.of(returning(U,"s0",List.of(assign(U,"write",WHOLE,"ABCDEFGH"))),returning(U,"dead",List.of())));var execution=run(p);
        var zero=execution.observeStorage(List.of(query("return-s0",R,8,0))).observations().getFirst().value();
        assertEquals(List.of(new Values.TextValue("")),zero.candidates());assertFalse(zero.modelValueRemainder());assertTrue(zero.alternatives().getFirst().fragments().isEmpty());
        var dead=execution.observeStorage(List.of(query("return-dead",R,0,8))).observations().getFirst().value();assertEquals(ValueFact.Reachability.UNREACHABLE_IN_MODEL,dead.reachability());assertNull(dead.candidates());assertNull(dead.modelValueRemainder());assertTrue(dead.alternatives().isEmpty());
        var q=new PointQuery<StorageSubject>(query("return-s0",R,0,8).point(),new StorageSubject.PhysicalRange(R,range(0,8),Memory.IdentityBytes.INSTANCE));
        var raw=execution.observeStorage(List.of(q)).observations().getFirst().value();assertEquals(List.of(),raw.candidates());assertTrue(raw.modelValueRemainder());assertEquals(StorageValueFact.FragmentKind.KNOWN_BYTES,raw.alternatives().getFirst().fragments().getFirst().kind());
        assertFalse(raw.alternatives().getFirst().fragments().getFirst().bytes().orElseThrow().octets().isEmpty());
    }
    @Test void logicalCaptureReportsWholeCellDestinationWithoutInventedByteOffsets() {
        var cell=new StorageId(P,"logical-copy");var object=new ObjectId(U,"logical-copy");var h=header(U,"read-copy");
        var read=new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand(h.id(),"source",Operand.Role.VALUE_READ),WHOLE));
        var capture=new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),object),read);
        var p=regional(List.of(returning(U,"s0",List.of(assign(U,"old",WHOLE,"ABCDEFGH"),assign(U,"prefix",PREFIX,"WXYZ"),capture,assign(U,"later",WHOLE,"XXXXXXXX")))));
        var u=p.units().getFirst();var objects=new ArrayList<>(u.objects());objects.add(new Memory.ObjectDeclaration(object,Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.CellBinding(cell),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,h.precision()));
        var storage=new ArrayList<>(p.storage());storage.add(new Memory.Cell(new Memory.StorageHeader(cell,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Types.known(Types.Builtin.TEXT)));
        p=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,u.entries(),u.sequences(),objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),List.of(new Proofs.Premise(new PremiseId(P,"separate"),"manual contract","independent allocations",origin(P),new Proofs.DisjointStorage(List.of(R,cell)))));
        var q=new PointQuery<StorageSubject>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-s0")),new StorageSubject.NamedObject(object));
        var fact=run(p).observeStorage(List.of(q)).observations().getFirst().value();assertEquals(List.of(new Values.TextValue("WXYZEFGH")),fact.candidates());assertEquals(2,fact.alternatives().getFirst().fragments().size());
        for(var fragment:fact.alternatives().getFirst().fragments()) {
            assertEquals(StorageValueFact.FragmentKind.LOGICAL_CAPTURE,fragment.kind());assertTrue(fragment.location().location().range().isEmpty());assertEquals(cell,fragment.location().location().base().id());
            var c=fragment.captures().getFirst();assertTrue(c.destinationContribution().location().range().isEmpty());assertTrue(c.destinationRange().location().range().isEmpty());
            assertEquals(R,c.sourceContribution().location().base().id());assertEquals(fragment.producer().orElseThrow().contributedRange(),c.sourceContribution());
        }
        assertTrue(fact.evidence().contains(new OperationId(U,"read-copy")));assertFalse(fact.evidence().contains(new OperationId(U,"later")));
    }
}
