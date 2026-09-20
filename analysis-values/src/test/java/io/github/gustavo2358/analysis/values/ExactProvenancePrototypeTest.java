package io.github.gustavo2358.analysis.values;

import java.math.BigInteger;
import java.util.*;
import java.util.function.UnaryOperator;
import io.github.gustavo2358.air.model.Values;
import io.github.gustavo2358.analysis.storage.StorageRange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExactProvenancePrototypeTest {
    static ByteImage unknown(int event){return ByteImage.unknown(Optional.of(BigInteger.valueOf(8)),"UNPROVEN_WRITE_DESTINATION",event);}
    static ExactProvenancePrototype<ByteImage> prototype(FactorizedAlternatives<ByteImage> concrete) {
        return new ExactProvenancePrototype<>(concrete,new ExactProvenancePrototype.Codec<>() {
            public ByteImage image(ByteImage label){return label;}
            public ByteImage label(ByteImage image){return image;}
        });
    }
    @Test void fixtureBRoundTripCanonicalIdempotenceAndDuplicateProducer() {
        var concrete=new FactorizedAlternatives<ByteImage>();var p=prototype(concrete);
        var edges=new HashMap<ByteImage,FactorizedAlternatives.Node<ByteImage>>();
        for(int i=0;i<10;i++)edges.put(unknown(i),concrete.terminal);
        var original=concrete.node(0,edges);var compact=p.factor(original);
        assertEquals(1,p.size(List.of(compact)).structuralEdges());
        assertEquals(10,p.size(List.of(compact)).provenanceRows());
        assertSame(original,p.expand(compact));
        assertSame(compact,p.factor(p.expand(compact)));
        assertEquals(RegionalSemanticSnapshot.encode(edges.keySet()),RegionalSemanticSnapshot.encode(p.expand(compact).edges.keySet()));
        assertSame(compact,p.union(compact,p.factor(concrete.node(0,Map.of(unknown(3),concrete.terminal)))));
    }

    static Set<Map<Integer,ByteImage>> rows(FactorizedAlternatives<ByteImage> d,FactorizedAlternatives.Node<ByteImage> n) {
        return new HashSet<>(d.selections(n));
    }
    static FactorizedAlternatives.Node<ByteImage> relation(FactorizedAlternatives<ByteImage> d,Collection<Map<Integer,ByteImage>> rows) {
        FactorizedAlternatives.Node<ByteImage> result=null;
        for(var row:rows)result=d.union(result,d.singleton(new TreeMap<>(row)));
        return result;
    }
    @Test void exhaustiveTwoLevelUnionProjectRestrictAndAssociativity() {
        var d=new FactorizedAlternatives<ByteImage>();var p=prototype(d);
        var universe=List.of(Map.of(0,unknown(1),1,unknown(1)),Map.of(0,unknown(1),1,unknown(2)),
            Map.of(0,unknown(2),1,unknown(1)),Map.of(0,unknown(2),1,unknown(2)));
        var relations=new ArrayList<FactorizedAlternatives.Node<ByteImage>>();
        var compact=new ArrayList<ExactProvenancePrototype.Node<ByteImage>>();
        var expected=new ArrayList<Set<Map<Integer,ByteImage>>>();
        for(int mask=0;mask<16;mask++) {
            var tuples=new HashSet<Map<Integer,ByteImage>>();for(int bit=0;bit<4;bit++)if((mask&(1<<bit))!=0)tuples.add(universe.get(bit));
            expected.add(tuples);var original=relation(d,tuples);relations.add(original);compact.add(p.factor(original));
            assertEquals(tuples,rows(d,p.expand(compact.getLast())));
            assertSame(compact.getLast(),p.factor(p.expand(compact.getLast())));
            for(var levels:List.of(Set.<Integer>of(),Set.of(0),Set.of(1),Set.of(0,1))) {
                var projected=new HashSet<Map<Integer,ByteImage>>();
                for(var row:tuples){var kept=new HashMap<>(row);kept.keySet().retainAll(levels);projected.add(Map.copyOf(kept));}
                assertEquals(projected,rows(d,p.expand(p.project(compact.getLast(),levels))));
                assertSame(d.project(original,levels),p.expand(p.project(compact.getLast(),levels)));
            }
            for(var selection:List.of(Map.<Integer,ByteImage>of(),Map.of(0,unknown(1)),Map.of(1,unknown(2)),
                    Map.of(0,unknown(1),1,unknown(2)),Map.of(0,unknown(99)))) {
                var kept=new HashSet<Map<Integer,ByteImage>>();
                for(var row:tuples)if(selection.entrySet().stream().allMatch(e->e.getValue().equals(row.get(e.getKey()))))kept.add(row);
                assertEquals(kept,rows(d,p.expand(p.restrict(compact.getLast(),selection))));
                assertSame(d.restrict(original,selection),p.expand(p.restrict(compact.getLast(),selection)));
            }
        }
        for(int a=0;a<16;a++)for(int b=0;b<16;b++) {
            var union=new HashSet<>(expected.get(a));union.addAll(expected.get(b));
            var left=compact.get(a);var right=compact.get(b);
            assertEquals(union,rows(d,p.expand(p.union(left,right))));
            assertSame(p.union(left,right),p.union(right,left));assertSame(left,p.union(left,left));
            for(var third:compact)assertSame(p.union(p.union(left,right),third),p.union(left,p.union(right,third)));
        }
        var anti=relation(d,List.of(universe.get(1),universe.get(2)));var factored=p.factor(anti);
        assertEquals(2,factored.edges.size(),"unequal children MUST prevent grouping at root");
        assertEquals(Set.of(universe.get(1),universe.get(2)),rows(d,p.expand(factored)));
        assertTrue(p.fallbackCalls>0,"overlapping producers with unequal children need exact subtree union");
        assertSame(p.terminal,p.project(p.terminal,Set.of()));assertNull(p.expand(null));
    }
    @Test void differentShapesHashCollisionsAndLevelsNeverConflate() {
        var d=new FactorizedAlternatives<ByteImage>();var p=prototype(d);
        var a=ByteImage.unknown(Optional.of(BigInteger.valueOf(8)),"Aa",1);
        var b=ByteImage.unknown(a.extent(),"BB",1);
        assertEquals(a.hashCode(),b.hashCode(),"intentional collision input");assertNotEquals(a,b);
        var ca=d.node(1,Map.of(a,d.terminal));var cb=d.node(1,Map.of(b,d.terminal));
        assertFalse(ExactProvenancePrototype.canFactor(a,b,0,0,d.terminal,d.terminal));
        assertFalse(ExactProvenancePrototype.canFactor(a,a,0,1,d.terminal,d.terminal));
        assertFalse(ExactProvenancePrototype.canFactor(a,a,0,0,ca,cb));
        assertTrue(ExactProvenancePrototype.canFactor(unknown(1),unknown(2),0,0,ca,ca));
        assertEquals(Set.of(Map.of(1,a),Map.of(1,b)),rows(d,p.expand(p.union(p.factor(ca),p.factor(cb)))));
        assertEquals(2,p.size(List.of(p.union(p.factor(ca),p.factor(cb)))).structuralEdges());
        var shorter=ByteImage.unknown(Optional.of(BigInteger.valueOf(4)),"Aa",1);
        assertFalse(ExactProvenancePrototype.canFactor(a,shorter,0,0,d.terminal,d.terminal));
    }
    @Test void admissionRejectsEveryEnrichedProvenanceDimension() throws Exception {
        var base=unknown(1);var part=base.parts().getFirst();
        var invalid=new ArrayList<ByteImage>();
        invalid.add(unknown(-1));invalid.add(ByteImage.unknown(Optional.empty(),"reason",1));
        invalid.add(ByteImage.literal(new Values.BytesValue(Collections.nCopies(8,1)),1));
        invalid.add(base.copied(3));
        invalid.add(base.withSourceGap(StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(8)),4));
        invalid.add(base.withLogicalSupport(Set.of(new ByteImage.LogicalSupport(RegionalExplosionFixturesTest.object(0),5))));
        invalid.add(base.write(StorageRange.exact(BigInteger.valueOf(4),BigInteger.valueOf(4)),ByteImage.literal(new Values.BytesValue(List.of(1,2,3,4)),6)));
        var ctor=ByteImage.class.getDeclaredConstructor(Optional.class,List.class);ctor.setAccessible(true);
        invalid.add(ctor.newInstance(base.extent(),List.of(new ByteImage.Part(part.range(),part.payload(),0,1,BigInteger.ONE,Map.of(),part.reasons()))));
        invalid.add(ctor.newInstance(base.extent(),List.of(new ByteImage.Part(part.range(),part.payload(),1,1,BigInteger.ZERO,Map.of(),part.reasons()))));
        invalid.add(ctor.newInstance(base.extent(),List.of(new ByteImage.Part(part.range(),part.payload(),0,1,BigInteger.ZERO,Map.of(),part.reasons(),Set.of(),Map.of(7,BigInteger.ZERO)))));
        invalid.add(ctor.newInstance(base.extent(),List.of(new ByteImage.Part(part.range(),part.payload(),0,1,BigInteger.ZERO,Map.of(),Set.of("a","b")))));
        var d=new FactorizedAlternatives<ByteImage>();var p=prototype(d);
        for(var image:invalid) {
            assertNull(ExactProvenancePrototype.shape(image),RegionalSemanticSnapshot.encode(image));
            var original=d.node(0,Map.of(image,d.terminal));var compact=p.factor(original);
            assertSame(original,p.expand(compact));assertEquals(0,p.size(List.of(compact)).provenanceRows());
        }
    }
    @Test void wholeWeakPartialCopyAndConnectedUpdateUseExactFallback() {
        var d=new FactorizedAlternatives<ByteImage>();var p=prototype(d);
        var original=relation(d,List.of(Map.of(0,unknown(1)),Map.of(0,unknown(2))));var compact=p.factor(original);
        var literal=ByteImage.literal(new Values.BytesValue(List.of(1,2,3,4)),3);
        var updates=List.<UnaryOperator<ByteImage>>of(ignored->unknown(9),
            image->image.write(StorageRange.exact(BigInteger.valueOf(4),BigInteger.valueOf(4)),literal),
            image->image.copied(10,BigInteger.valueOf(2),1));
        for(var update:updates) {
            var result=p.update(compact,Map.of(0,update));
            assertSame(d.update(original,Map.of(0,update)),p.expand(result));
            assertSame(result,p.factor(p.expand(result)));
        }
        var whole=p.update(compact,Map.of(0,updates.getFirst()));
        assertEquals(Set.of(Map.of(0,unknown(9))),rows(d,p.expand(whole)),"whole overwrite removes old producers");
        var partial=p.update(compact,Map.of(0,updates.get(1)));
        assertEquals(2,p.size(List.of(partial)).structuralEdges());assertEquals(0,p.size(List.of(partial)).provenanceRows());
        var weak=p.union(compact,p.factor(d.node(0,Map.of(unknown(3),d.terminal))));
        assertEquals(3,p.size(List.of(weak)).provenanceRows());assertEquals(1,p.size(List.of(weak)).structuralEdges());
        var connected=relation(d,List.of(Map.of(0,unknown(1),1,unknown(2)),Map.of(0,unknown(2),1,unknown(1))));
        var update=Map.<Integer,UnaryOperator<ByteImage>>of(0,updates.get(1),1,updates.get(2));
        assertSame(d.update(connected,update),p.expand(p.update(p.factor(connected),update)));
        assertEquals(2,rows(d,p.expand(p.update(p.factor(connected),update))).size());
        assertTrue(p.fallbackCalls>=6);
    }

    @Test void positiveFixtureAndLargerScalesHaveNoCompensatingProvenance() throws Exception {
        for(int regions:List.of(4,16,32))for(int producers:regions==4?List.of(1,5):List.of(regions==16?50:100)) {
            for(boolean disjoint:List.of(false,true)) {
                var publication=RegionalExplosionFixturesTest.fixture(regions,producers,disjoint);
                var result=ExactProvenanceFixtureBridge.check(publication,regions);
                assertEquals(RegionalStructuralOracleTest.expectedFacts(regions,producers),result.after());
                assertEquals(producers,result.targets());assertEquals(0,result.unproven());
                assertEquals(result.concreteEdges(),result.compact().expandedEdges());
                assertEquals(1,result.compact().structuralEdges());assertEquals(0,result.compact().provenanceRows());
                assertEquals(result.compact().structuralEdges(),result.solveMetrics().get("maxStateAlternatives"));
                assertEquals(0L,result.solveMetrics().get("maxProvenanceRows"));
                // Each published write targets only the same precise base. This replaces
                // obsolete target digests with an independently constructed typed target.
                var id=new io.github.gustavo2358.air.model.Ids.StorageId(RegionalValuesTest.P,"synthetic-region-0");
                var header=new io.github.gustavo2358.air.model.Memory.StorageHeader(id,Optional.of(RegionalValuesTest.U),
                    io.github.gustavo2358.air.model.Memory.Lifetime.PERSISTENT,io.github.gustavo2358.air.model.Memory.Visibility.PRIVATE,
                    ValuesFixtures.origin(RegionalValuesTest.P));
                var target=new io.github.gustavo2358.analysis.storage.StatementEffects.Target(
                    new io.github.gustavo2358.analysis.storage.StorageIndex.Location(header,Optional.of(StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(8)))),
                    io.github.gustavo2358.analysis.storage.StatementEffects.Strength.MUST,true,List.of(),List.of());
                assertEquals(Collections.nCopies(producers,target),RegionalExplosionFixturesTest.targets(publication));
                System.out.printf("PMT_SCALE regions=%d producers=%d redundantPremise=%s targets=%d structural=%d provenance=%d%n",
                    regions,producers,disjoint,result.targets(),result.compact().structuralEdges(),result.compact().provenanceRows());
            }
        }
    }
    @Test void realConnectedCopyAndPartialCopyReplayKeepCompleteObservations() throws Exception {
        var publication=RegionalExplosionFixturesTest.fixture(4,5,false);var unit=publication.units().getFirst();
        var instructions=new ArrayList<>(unit.sequences().getFirst().instructions());
        instructions.add(RegionalCompositionTest.copy("partial-copy",publication.storage().get(1).header().id(),1,
            publication.storage().get(2).header().id(),3,4));
        instructions.add(RegionalCompositionTest.copy("copy-again",publication.storage().get(2).header().id(),2,
            publication.storage().get(3).header().id(),0,5));
        publication=ValuesFixtures.replace(publication,List.of(ValuesFixtures.unit(RegionalValuesTest.U,unit.entries(),
            List.of(ValuesFixtures.returning(RegionalValuesTest.U,"s0",instructions)),unit.objects())),publication.coverage(),publication.uncertainties(),publication.premises());
        var result=ExactProvenanceFixtureBridge.check(publication,4);
        assertEquals(result.before(),result.after());
        assertTrue(result.after().stream().flatMap(f->f.alternatives().stream()).flatMap(a->a.fragments().stream()).anyMatch(f->!f.captures().isEmpty()));
        assertEquals(result.concreteEdges(),result.compact().expandedEdges());
    }
}
