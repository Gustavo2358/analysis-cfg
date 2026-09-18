package io.github.gustavo2358.analysis.values;

import java.math.BigInteger;
import java.util.*;
import java.util.function.UnaryOperator;
import io.github.gustavo2358.air.model.Values;
import io.github.gustavo2358.analysis.storage.StorageRange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegionalAlternativesTest {
    static ByteImage unknown(int event){return ByteImage.unknown(Optional.of(BigInteger.valueOf(8)),"UNPROVEN_WRITE_DESTINATION",event);}
    static RegionalAlternatives<ByteImage> domain() {
        return new RegionalAlternatives<>(image->image,image->image);
    }
    @Test void fixtureBRoundTripCanonicalIdempotenceAndDuplicateProducer() {
        var d=new FactorizedAlternatives<ByteImage>();var p=domain();
        var edges=new HashMap<ByteImage,FactorizedAlternatives.Node<ByteImage>>();
        for(int i=0;i<10;i++)edges.put(unknown(i),d.terminal);
        var original=d.node(0,edges);var compact=p.factor(original);
        assertEquals(1,RegionalAlternatives.size(List.of(compact)).structuralEdges());
        assertEquals(10,RegionalAlternatives.size(List.of(compact)).provenanceRows());
        assertSame(original,p.expand(compact,d));
        assertSame(compact,p.factor(p.expand(compact,d)));
        assertEquals(RegionalSemanticSnapshot.encode(edges.keySet()),RegionalSemanticSnapshot.encode(p.expand(compact,d).edges.keySet()));
        assertSame(compact,p.union(compact,p.factor(d.node(0,Map.of(unknown(3),d.terminal)))));
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
        var d=new FactorizedAlternatives<ByteImage>();var p=domain();
        var universe=List.of(Map.of(0,unknown(1),1,unknown(1)),Map.of(0,unknown(1),1,unknown(2)),
            Map.of(0,unknown(2),1,unknown(1)),Map.of(0,unknown(2),1,unknown(2)));
        var relations=new ArrayList<FactorizedAlternatives.Node<ByteImage>>();
        var compact=new ArrayList<RegionalAlternatives.Node<ByteImage>>();
        var expected=new ArrayList<Set<Map<Integer,ByteImage>>>();
        for(int mask=0;mask<16;mask++) {
            var tuples=new HashSet<Map<Integer,ByteImage>>();for(int bit=0;bit<4;bit++)if((mask&(1<<bit))!=0)tuples.add(universe.get(bit));
            expected.add(tuples);var original=relation(d,tuples);relations.add(original);compact.add(p.factor(original));
            assertEquals(tuples,rows(d,p.expand(compact.getLast(),d)));
            assertSame(compact.getLast(),p.factor(p.expand(compact.getLast(),d)));
            for(var levels:List.of(Set.<Integer>of(),Set.of(0),Set.of(1),Set.of(0,1))) {
                var projected=new HashSet<Map<Integer,ByteImage>>();
                for(var row:tuples){var kept=new HashMap<>(row);kept.keySet().retainAll(levels);projected.add(Map.copyOf(kept));}
                assertEquals(projected,rows(d,p.expand(p.project(compact.getLast(),levels),d)));
                assertSame(d.project(original,levels),p.expand(p.project(compact.getLast(),levels),d));
            }
            for(var selection:List.of(Map.<Integer,ByteImage>of(),Map.of(0,unknown(1)),Map.of(1,unknown(2)),
                    Map.of(0,unknown(1),1,unknown(2)),Map.of(0,unknown(99)))) {
                var kept=new HashSet<Map<Integer,ByteImage>>();
                for(var row:tuples)if(selection.entrySet().stream().allMatch(e->e.getValue().equals(row.get(e.getKey()))))kept.add(row);
                assertEquals(kept,rows(d,p.expand(p.restrict(compact.getLast(),selection),d)));
                assertSame(d.restrict(original,selection),p.expand(p.restrict(compact.getLast(),selection),d));
            }
        }
        for(int a=0;a<16;a++)for(int b=0;b<16;b++) {
            var union=new HashSet<>(expected.get(a));union.addAll(expected.get(b));
            var left=compact.get(a);var right=compact.get(b);
            assertEquals(union,rows(d,p.expand(p.union(left,right),d)));
            assertSame(p.union(left,right),p.union(right,left));assertSame(left,p.union(left,left));
            for(var third:compact)assertSame(p.union(p.union(left,right),third),p.union(left,p.union(right,third)));
        }
        var anti=relation(d,List.of(universe.get(1),universe.get(2)));var factored=p.factor(anti);
        assertEquals(2,factored.structuralEdges(),"unequal children MUST prevent grouping at root");
        assertEquals(Set.of(universe.get(1),universe.get(2)),rows(d,p.expand(factored,d)));
        assertTrue(p.metrics().get("concreteFallbacks")>0,"overlapping producers with unequal children need exact subtree union");
        assertSame(p.terminal,p.project(p.terminal,Set.of()));assertNull(p.expand(null,d));
    }
    @Test void differentShapesHashCollisionsAndLevelsNeverConflate() {
        var d=new FactorizedAlternatives<ByteImage>();var p=domain();
        var a=ByteImage.unknown(Optional.of(BigInteger.valueOf(8)),"Aa",1);
        var b=ByteImage.unknown(a.extent(),"BB",1);
        assertEquals(a.hashCode(),b.hashCode(),"intentional collision input");assertNotEquals(a,b);
        var ca=d.node(1,Map.of(a,d.terminal));var cb=d.node(1,Map.of(b,d.terminal));
        assertFalse(RegionalAlternatives.canFactor(a,b,0,0,d.terminal,d.terminal));
        assertFalse(RegionalAlternatives.canFactor(a,a,0,1,d.terminal,d.terminal));
        assertFalse(RegionalAlternatives.canFactor(a,a,0,0,ca,cb));
        assertTrue(RegionalAlternatives.canFactor(unknown(1),unknown(2),0,0,ca,ca));
        assertEquals(Set.of(Map.of(1,a),Map.of(1,b)),rows(d,p.expand(p.union(p.factor(ca),p.factor(cb)),d)));
        assertEquals(2,RegionalAlternatives.size(List.of(p.union(p.factor(ca),p.factor(cb)))).structuralEdges());
        var shorter=ByteImage.unknown(Optional.of(BigInteger.valueOf(4)),"Aa",1);
        assertFalse(RegionalAlternatives.canFactor(a,shorter,0,0,d.terminal,d.terminal));
        var literal=ByteImage.literal(new Values.BytesValue(List.of(1,2,3,4)),5);
        var mixed=relation(d,List.of(Map.of(0,a,1,literal),Map.of(0,literal,1,b),Map.of(0,b,1,a)));
        var compact=p.factor(mixed);
        for(var levels:List.of(Set.<Integer>of(),Set.of(0),Set.of(1),Set.of(0,1)))
            assertSame(d.project(mixed,levels),p.expand(p.project(compact,levels),d));
        for(var selected:List.of(Map.of(0,literal),Map.of(1,a),Map.of(0,a,1,literal)))
            assertSame(d.restrict(mixed,selected),p.expand(p.restrict(compact,selected),d));
        assertThrows(IllegalArgumentException.class,()->p.union(p.factor(ca),p.factor(d.node(0,Map.of(a,d.terminal)))));
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
        invalid.add(ByteImage.unknown(Optional.of(BigInteger.ZERO),"empty",1));
        invalid.add(base.write(StorageRange.exact(BigInteger.valueOf(4),BigInteger.valueOf(4)),ByteImage.unknown(Optional.of(BigInteger.valueOf(4)),"partial",2)));
        invalid.add(ctor.newInstance(base.extent(),List.of(new ByteImage.Part(part.range(),part.payload(),0,1,BigInteger.ONE,Map.of(),part.reasons()))));
        invalid.add(ctor.newInstance(base.extent(),List.of(new ByteImage.Part(part.range(),part.payload(),1,1,BigInteger.ZERO,Map.of(),part.reasons()))));
        invalid.add(ctor.newInstance(base.extent(),List.of(new ByteImage.Part(part.range(),part.payload(),0,1,BigInteger.ZERO,Map.of(),part.reasons(),Set.of(),Map.of(7,BigInteger.ZERO)))));
        invalid.add(ctor.newInstance(base.extent(),List.of(new ByteImage.Part(part.range(),part.payload(),0,1,BigInteger.ZERO,Map.of(),Set.of("a","b")))));
        var d=new FactorizedAlternatives<ByteImage>();var p=domain();
        for(var image:invalid) {
            assertNull(RegionalAlternatives.shape(image),RegionalSemanticSnapshot.encode(image));
            var original=d.node(0,Map.of(image,d.terminal));var compact=p.factor(original);
            assertSame(original,p.expand(compact,d));assertEquals(0,RegionalAlternatives.size(List.of(compact)).provenanceRows());
        }
    }
    @Test void wholeWeakPartialCopyAndConnectedUpdateUseExactFallback() {
        var d=new FactorizedAlternatives<ByteImage>();var p=domain();
        var original=relation(d,List.of(Map.of(0,unknown(1)),Map.of(0,unknown(2))));var compact=p.factor(original);
        var literal=ByteImage.literal(new Values.BytesValue(List.of(1,2,3,4)),3);
        var updates=List.<UnaryOperator<ByteImage>>of(ignored->unknown(9),
            image->image.write(StorageRange.exact(BigInteger.valueOf(4),BigInteger.valueOf(4)),literal),
            image->image.copied(10,BigInteger.valueOf(2),1));
        for(var update:updates) {
            var result=p.update(compact,Map.of(0,update));
            assertSame(d.update(original,Map.of(0,update)),p.expand(result,d));
            assertSame(result,p.factor(p.expand(result,d)));
        }
        var whole=p.update(compact,Map.of(0,updates.getFirst()));
        assertEquals(Set.of(Map.of(0,unknown(9))),rows(d,p.expand(whole,d)),"whole overwrite removes old producers");
        var partial=p.update(compact,Map.of(0,updates.get(1)));
        assertEquals(2,RegionalAlternatives.size(List.of(partial)).structuralEdges());assertEquals(0,RegionalAlternatives.size(List.of(partial)).provenanceRows());
        var weak=p.union(compact,p.factor(d.node(0,Map.of(unknown(3),d.terminal))));
        assertEquals(3,RegionalAlternatives.size(List.of(weak)).provenanceRows());assertEquals(1,RegionalAlternatives.size(List.of(weak)).structuralEdges());
        var connected=relation(d,List.of(Map.of(0,unknown(1),1,unknown(2)),Map.of(0,unknown(2),1,unknown(1))));
        var update=Map.<Integer,UnaryOperator<ByteImage>>of(0,updates.get(1),1,updates.get(2));
        assertSame(d.update(connected,update),p.expand(p.update(p.factor(connected),update),d));
        assertEquals(2,rows(d,p.expand(p.update(p.factor(connected),update),d)).size());
        assertTrue(p.metrics().get("concreteFallbacks")>=6);
    }

    @Test void constantOverwriteMatchesConcreteAndEventsHandleCollisionExactly() {
        var d=new FactorizedAlternatives<ByteImage>();var p=domain();
        var original=relation(d,List.of(Map.of(0,unknown(1),1,unknown(2)),Map.of(0,unknown(2),1,unknown(1))));
        var compact=p.factor(original);
        for(var values:List.of(Map.of(0,unknown(9)),Map.of(1,unknown(9)),Map.of(0,unknown(9),1,unknown(10)))) {
            var updates=new HashMap<Integer,UnaryOperator<ByteImage>>();values.forEach((level,value)->updates.put(level,ignored->value));
            assertSame(d.update(original,updates),p.expand(p.overwrite(compact,values),d));
        }
        var a=RegionalAlternatives.Events.of(0).union(RegionalAlternatives.Events.of(33));
        var b=RegionalAlternatives.Events.of(1).union(RegionalAlternatives.Events.of(2));
        assertEquals(a.hashCode(),b.hashCode());assertNotEquals(a,b);
        var all=a.union(b);assertEquals(4,all.size());for(int i:List.of(0,1,2,33))assertTrue(all.contains(i));
        assertFalse(all.contains(3));assertSame(all,all.union(a));assertEquals(all,b.union(a));
        assertFalse(a.intersects(b));assertTrue(all.intersects(b));
        assertThrows(IllegalArgumentException.class,()->p.union(compact,domain().factor(original)));
    }
    @Test void deepGraphOperationsAndTemporaryFallbackKeepCanonicalIdentity() {
        var d=new FactorizedAlternatives<ByteImage>();var p=domain();var values=new TreeMap<Integer,ByteImage>();
        for(int i=0;i<12000;i++)values.put(i,unknown(1));
        var original=d.singleton(values);var compact=p.factor(original);assertSame(original,p.expand(compact,d));
        assertSame(compact,p.factor(p.expand(compact,new FactorizedAlternatives<>())));
        assertSame(compact,p.restrict(compact,Map.of(11999,unknown(1))));
        assertSame(d.project(original,Set.of(0,6000,11999)),p.expand(p.project(compact,Set.of(0,6000,11999)),d));
        var updates=Map.<Integer,UnaryOperator<ByteImage>>of(11999,image->image.copied(3));
        var changed=p.update(compact,updates);assertSame(d.update(original,updates),p.expand(changed,d));
        assertSame(d.union(original,p.expand(changed,d)),p.expand(p.union(compact,changed),d));
        assertEquals(12000,p.componentSize(compact).nodes());
        assertEquals(List.of(Map.copyOf(values)),p.selections(compact));
        assertSame(compact,p.update(compact,Map.of(0,image->image)));
    }
}
