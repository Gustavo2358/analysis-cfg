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

    @Test void fixtureCAndLargerScalesKeepFrozenFactsWithFewerStructuralEdges() throws Exception {
        var expected=Map.of("1/false","083a6aa0ff2826958698b32b42ffb6fafacad171b989d339f2e1d408c471e206",
            "5/false","3bf6ed5144cc53452781cca4809aee08a6eea32727697a092159400c9c5fcbbe",
            "1/true","59eae5b3d2f41a5180c411c47ddc689ab4457517c361b79577d0f3e5ff8ea6e1",
            "5/true","22d1216224174d58fb77291cf18e21ae45874c43032917c6bdc0153512072583");
        for(boolean disjoint:List.of(false,true))for(int producers:List.of(1,5)) {
            var result=ExactProvenanceFixtureBridge.check(RegionalExplosionFixturesTest.fixture(4,producers,disjoint),4);
            assertEquals(expected.get(producers+"/"+disjoint),RegionalSemanticSnapshot.digest(result.after()));
            assertEquals(result.concreteEdges(),result.compact().expandedEdges());
            if(!disjoint&&producers==5)assertTrue(result.compact().structuralEdges()<result.concreteEdges());
            System.out.printf("W32_C producers=%d disjoint=%s concrete=%d factored=%s%n",producers,disjoint,result.concreteEdges(),result.compact());
        }
        for(int regions:List.of(16,32)) {
            int producers=regions==16?50:100;
            var publication=RegionalExplosionFixturesTest.fixture(regions,producers,false);
            var result=ExactProvenanceFixtureBridge.check(publication,regions);
            String expectedHash=regions==16?"e9b3792a76ffbd96d302075402c341701adfb9cbcb26c634af88cf8ea0656302":"9e8437da4f81de7deb3b717f2fe78fe0c5244943c09b7e18e594f44c491ca496";
            assertEquals(expectedHash,RegionalSemanticSnapshot.digest(result.after()));
            assertEquals(regions==16?"f6a26180cba8e0090c652faa8e3619889a1e3bcdbd64775c0b5b262151c538b5":"027c14c2ea4b40906741282c18ecc736a057078ec6b1b882f7d8434b5d613983",
                RegionalSemanticSnapshot.digest(RegionalExplosionFixturesTest.targets(publication)));
            assertEquals(result.concreteEdges(),result.compact().expandedEdges());
            assertEquals(result.unproven(),result.compact().provenanceRows());
            assertTrue(result.compact().structuralEdges()<result.concreteEdges()/2);
            System.out.printf("W32_SCALE regions=%d producers=%d targets=%d unproven=%d concrete=%d factored=%s facts=%s%n",
                regions,producers,result.targets(),result.unproven(),result.concreteEdges(),result.compact(),expectedHash);
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
