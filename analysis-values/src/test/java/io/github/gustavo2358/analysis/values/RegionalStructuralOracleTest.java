package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Values;
import io.github.gustavo2358.air.model.Memory;
import io.github.gustavo2358.air.model.Control;
import io.github.gustavo2358.analysis.rd.DefinitionEvent;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;

/** Positive-topology typed oracle and counterexamples to uncorrelated producer sets. */
class RegionalStructuralOracleTest {
    static List<StorageValueFact> facts(int producers,boolean disjoint) {
        var execution=run(RegionalExplosionFixturesTest.fixture(4,producers,disjoint));
        var result=new ArrayList<StorageValueFact>();
        for(int i=0;i<4;i++) {
            var query=new PointQuery<StorageSubject>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-s0")),
                new StorageSubject.NamedObject(RegionalExplosionFixturesTest.object(i)));
            var batch=execution.observeStorage(List.of(query));assertEquals(ObservationBatch.Status.COMPLETE,batch.status());
            result.add(batch.observations().getFirst().value());
        }
        return List.copyOf(result);
    }
    /** Independently specified oracle: one strong assignment on base zero, untouched
     * entry contents on every other base. No output-derived digest or solver helper.
     * Historical pre-positive hashes included compensating cross-base unknown writers
     * and redundant separation premises, both intentionally removed by this contract. */
    static List<StorageValueFact> expectedFacts(int regions,int producers) {
        var result=new ArrayList<StorageValueFact>();
        var entry=new EntryId(U,"entry");var point=ProgramPoint.before(entry,new OperationId(U,"return-s0"));
        for(int base=0;base<regions;base++) {
            var id=new StorageId(P,"synthetic-region-"+base);
            var header=new Memory.StorageHeader(id,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P));
            var location=new StorageIndex.ContextualLocation(new StorageIndex.Location(header,
                Optional.of(StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(8)))),Optional.empty());
            var interpretation=new RegionalValueFact.Interpretation(location,Optional.of(IBM));
            var candidate=base==0?Optional.of(new Values.TextValue("ABCDEFGH")):Optional.<Values.TextValue>empty();
            var op=new OperationId(U,"synthetic-producer-"+(producers-1));
            var definition=new DefinitionEvent(entry,Optional.of(op),Optional.of(new OperandId(new OperationOwner(op),"destination")),
                0,Optional.of(Control.NormalOutcome.INSTANCE),id,DefinitionEvent.Kind.ASSIGN,false,origin(P),List.of(),List.of(),List.of());
            var fragment=new StorageValueFact.Fragment(location,
                base==0?StorageValueFact.FragmentKind.KNOWN_BYTES:StorageValueFact.FragmentKind.UNKNOWN_BYTES,
                base==0?Optional.of(new Values.BytesValue(List.of(193,194,195,196,197,198,199,200))):Optional.empty(),
                base==0?Optional.of(new StorageValueFact.Producer(definition,location)):Optional.empty(),Optional.empty(),List.of(),List.of(),
                base==0?List.of():List.of("UNSPECIFIED_ENTRY_CONTENT"));
            var support=new ValueFact.Support(op,origin(P),List.of());
            result.add(new StorageValueFact(point,new StorageSubject.NamedObject(RegionalExplosionFixturesTest.object(base)),List.of(interpretation),
                ValueFact.Reachability.REACHABLE,candidate.stream().toList(),base!=0,false,base!=0,List.of(),base==0?List.of(op):List.of(),
                List.of(origin(P)),base==0?List.of(new ValueFact.CandidateSupport(candidate.orElseThrow(),List.of(support))):List.of(),
                base==0?List.of():List.of("NO_KNOWN_TEXT_PROJECTION","UNSPECIFIED_ENTRY_CONTENT"),
                List.of(new StorageValueFact.Alternative(interpretation,candidate,List.of(fragment))),List.of()));
        }
        return List.copyOf(result);
    }
    @Test void positiveFactsRetainAllTypedFieldsAndProducerIdentities() {
        var images=new ArrayList<ByteImage>();
        for(int i=0;i<10;i++)images.add(ByteImage.unknown(Optional.of(BigInteger.valueOf(8)),"UNPROVEN_WRITE_DESTINATION",i));
        assertEquals(new TreeSet<>(java.util.stream.IntStream.range(0,10).boxed().toList()),
            images.stream().flatMap(i->i.parts().stream()).map(ByteImage.Part::producer).collect(java.util.stream.Collectors.toCollection(TreeSet::new)));
        // Standalone ByteImage provenance semantics are unchanged by the producer policy.
        assertEquals("e98852005f7f4d49d1246c3d1c1ae860acab3b001d17c197067342a42132577e",RegionalSemanticSnapshot.digest(images));
        for(boolean disjoint:List.of(false,true))for(int producers:List.of(1,5)) {
            var observed=facts(producers,disjoint);
            assertEquals(expectedFacts(4,producers),observed);
            assertEquals(observed,facts(producers,disjoint),"typed repeatability, independently executed solver");
        }
    }
    @Test void snapshotRetainsEveryPartFieldAndRejectsUnsupportedTypes() throws Exception {
        var part=ByteImage.unknown(Optional.of(BigInteger.valueOf(8)),"reason",1).parts().getFirst();
        var fields=ByteImage.Part.class.getRecordComponents();
        Object[] replacements={StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(4)),
            Optional.of(new ByteImage.Payload(new Values.BytesValue(List.of(1)),true)),1,2,BigInteger.ONE,
            Map.of(3,Set.of(new ByteImage.CaptureOffset(0,BigInteger.ONE))),Set.of("other"),Set.of(4),
            Map.of(5,BigInteger.ONE),Set.of(new ByteImage.LogicalSupport(RegionalExplosionFixturesTest.object(0),6))};
        assertEquals(fields.length,replacements.length,"new fields need an adversarial value");
        var types=Arrays.stream(fields).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
        for(int changed=0;changed<fields.length;changed++) {
            var values=new Object[fields.length];
            for(int i=0;i<fields.length;i++)values[i]=i==changed?replacements[i]:fields[i].getAccessor().invoke(part);
            var other=ByteImage.Part.class.getDeclaredConstructor(types).newInstance(values);
            assertNotEquals(RegionalSemanticSnapshot.encode(part),RegionalSemanticSnapshot.encode(other),fields[changed].getName());
        }
        assertEquals(RegionalSemanticSnapshot.encode(new LinkedHashSet<>(List.of(1,2))),RegionalSemanticSnapshot.encode(new LinkedHashSet<>(List.of(2,1))));
        assertEquals(RegionalSemanticSnapshot.encode(Map.of(1,"a",2,"b")),RegionalSemanticSnapshot.encode(new TreeMap<>(Map.of(2,"b",1,"a"))));
        assertNotEquals(RegionalSemanticSnapshot.encode(List.of(1,2)),RegionalSemanticSnapshot.encode(List.of(2,1)));
        assertNotEquals(RegionalSemanticSnapshot.encode(Optional.empty()),RegionalSemanticSnapshot.encode(null));
        assertThrows(IllegalArgumentException.class,()->RegionalSemanticSnapshot.encode(new Object()));
    }
    @Test void producerSetsLoseRangeAssociationsAndIndependentSetsInventCorrelations() {
        var bytes=new Values.BytesValue(List.of(1,1,1,1));
        var a=ByteImage.literal(bytes,1);var b=ByteImage.literal(bytes,2);
        var tail=StorageRange.exact(BigInteger.valueOf(4),BigInteger.valueOf(4));
        var blank=ByteImage.unknown(Optional.of(BigInteger.valueOf(8)),"entry");
        var head=StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(4));
        var ab=blank.write(head,a).write(tail,b);var ba=blank.write(head,b).write(tail,a);
        assertEquals(ab.read(StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(8))).bytes(),ba.read(StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(8))).bytes());
        assertEquals(ab.parts().stream().map(ByteImage.Part::producer).collect(java.util.stream.Collectors.toSet()),
            ba.parts().stream().map(ByteImage.Part::producer).collect(java.util.stream.Collectors.toSet()));
        assertNotEquals(RegionalSemanticSnapshot.encode(ab),RegionalSemanticSnapshot.encode(ba),"same producers and bytes, different contributed ranges");
        var relation=new FactorizedAlternatives<ByteImage>();
        var first=new TreeMap<Integer,ByteImage>(Map.of(0,a,1,b));var second=new TreeMap<Integer,ByteImage>(Map.of(0,b,1,a));
        var exact=relation.union(relation.singleton(first),relation.singleton(second));
        assertEquals(Set.of(first,second),new HashSet<>(relation.selections(exact)));
        var uncorrelated=relation.node(0,Map.of(a,relation.node(1,Map.of(a,relation.terminal,b,relation.terminal)),
            b,relation.node(1,Map.of(a,relation.terminal,b,relation.terminal))));
        assertEquals(4,relation.selections(uncorrelated).size());
        assertTrue(relation.selections(uncorrelated).contains(Map.of(0,a,1,a)));
        assertFalse(relation.selections(exact).contains(Map.of(0,a,1,a)),"cannot factor across unequal child relations");
    }
}
