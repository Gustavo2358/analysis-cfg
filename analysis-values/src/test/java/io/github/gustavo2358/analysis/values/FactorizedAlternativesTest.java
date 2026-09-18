package io.github.gustavo2358.analysis.values;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactorizedAlternativesTest {
    /** Independent collecting relation oracle, with empty relations and skipped levels. */
    @Test void thousandFiniteRelationsPreserveAlgebraAndCanonicality() {
        var random=new Random(20260916);
        for(int trial=0;trial<1000;trial++) {
            var domain=new FactorizedAlternatives<Integer>();int dimensions=random.nextInt(6);
            Set<Map<Integer,Integer>> a=randomRelation(random,dimensions),b=randomRelation(random,dimensions);
            var left=relation(domain,a);var right=relation(domain,b);var joined=domain.union(left,right);
            var union=new HashSet<>(a);union.addAll(b);assertEquals(union,tuples(domain,joined));
            assertSame(joined,domain.union(right,left));assertSame(left,domain.union(left,left));
            int changed=dimensions==0?0:2*random.nextInt(dimensions);
            var image=new HashSet<Map<Integer,Integer>>();
            for(var row:a) {var copy=new HashMap<>(row);if(copy.containsKey(changed))copy.put(changed,0);image.add(Map.copyOf(copy));}
            var updated=domain.update(left,Map.of(changed,ignored->0));assertEquals(image,tuples(domain,updated));
            var selected=new HashSet<Integer>();var restriction=new HashMap<Integer,Integer>();
            for(int i=0;i<dimensions;i++)if(random.nextBoolean()){selected.add(2*i);restriction.put(2*i,random.nextInt(4));}
            var projected=new HashSet<Map<Integer,Integer>>();var restricted=new HashSet<Map<Integer,Integer>>();
            for(var row:a) {
                var copy=new HashMap<Integer,Integer>();selected.forEach(i->copy.put(i,row.get(i)));projected.add(Map.copyOf(copy));
                if(restriction.entrySet().stream().allMatch(e->e.getValue().equals(row.get(e.getKey()))))restricted.add(row);
            }
            assertEquals(projected,tuples(domain,domain.project(left,selected)));
            assertEquals(restricted,tuples(domain,domain.restrict(left,restriction)));
            image.addAll(a);assertEquals(image,tuples(domain,domain.union(left,updated)));
        }
        System.out.println("EP_R2_RELATION_ORACLE scenarios=1000 checks=7000 seed=20260916 PASS");
    }
    private static Set<Map<Integer,Integer>> randomRelation(Random random,int dimensions) {
        var rows=new HashSet<Map<Integer,Integer>>();int count=random.nextInt(7);
        for(int i=0;i<count;i++) {var row=new HashMap<Integer,Integer>();for(int j=0;j<dimensions;j++)row.put(2*j,random.nextInt(3));rows.add(Map.copyOf(row));}
        return rows;
    }
    private static FactorizedAlternatives.Node<Integer> relation(FactorizedAlternatives<Integer> domain,Set<Map<Integer,Integer>> rows) {
        FactorizedAlternatives.Node<Integer> root=null;
        for(var row:rows)root=domain.union(root,domain.singleton(new TreeMap<>(row)));return root;
    }
    private static Set<Map<Integer,Integer>> tuples(FactorizedAlternatives<Integer> domain,FactorizedAlternatives.Node<Integer> root) {
        return new HashSet<>(domain.selections(root));
    }
    @Test void independentWeakChoicesShareSuffixesWithoutWorlds() {
        var domain=new FactorizedAlternatives<Integer>();var initial=new TreeMap<Integer,Integer>();
        for(int i=0;i<32;i++)initial.put(i,0);
        var state=domain.singleton(initial);
        for(int i=0;i<32;i++)for(int j=1;j<5;j++) {
            int value=j;state=domain.union(state,domain.update(state,Map.of(i,ignored->value)));
        }
        var size=FactorizedAlternatives.size(List.of(state));
        assertEquals(32,size.nodes());assertEquals(160,size.alternatives());assertEquals(5,size.maxComponent());
        assertEquals(5,domain.selections(domain.project(state,Set.of(7))).size());
    }
    @Test void localImagesRestrictionsAndUnionMatchIndependentFiniteRelations() {
        var domain=new FactorizedAlternatives<String>();
        var a=new TreeMap<>(Map.of(0,"A",1,"B",2,"C"));var b=new TreeMap<>(Map.of(0,"X",1,"Y",2,"Z"));
        var left=domain.singleton(a);var right=domain.singleton(b);var join=domain.union(left,right);
        assertSame(join,domain.union(right,left));assertSame(join,domain.union(join,left));
        assertEquals(Set.of(a,b),new HashSet<>(domain.selections(join)));
        var changed=domain.update(join,Map.of(1,ignored->"Q"));
        assertEquals(Set.of(Map.of(0,"A",1,"Q",2,"C"),Map.of(0,"X",1,"Q",2,"Z")),new HashSet<>(domain.selections(changed)));
        assertEquals(List.of(a),domain.selections(domain.restrict(join,Map.of(1,"B"))));
        assertEquals(Set.of(Map.of(0,"A",2,"C"),Map.of(0,"X",2,"Z")),new HashSet<>(domain.selections(domain.project(join,Set.of(0,2)))));
        assertNull(domain.restrict(join,Map.of(0,"absent")));
    }
    @Test void cachedComponentSizesMatchFullDagTraversalWithoutLosingSharedSuffixes() {
        var random=new Random(20260917);
        var domain=new FactorizedAlternatives<Integer>();
        assertEquals(new FactorizedAlternatives.Size(0,0,0),domain.componentSize(null));
        assertEquals(new FactorizedAlternatives.Size(0,0,0),domain.componentSize(domain.terminal));
        for(int trial=0;trial<1000;trial++) {
            var root=relation(domain,randomRelation(random,1+random.nextInt(6)));
            if(root==null)continue;
            var expected=FactorizedAlternatives.size(List.of(root));
            var measured=domain.componentSize(root);assertEquals(expected,measured);
            if(!root.terminal())assertSame(measured,domain.componentSize(root));
            var changed=domain.update(root,Map.of(0,ignored->99));
            assertEquals(FactorizedAlternatives.size(List.of(changed)),domain.componentSize(changed));
            assertEquals(expected,domain.componentSize(root),"old immutable snapshot remains exact");
        }
        var suffix=domain.node(3,Map.of(10,domain.terminal,20,domain.terminal));
        var diamond=domain.node(1,Map.of(1,suffix,2,suffix));
        assertEquals(new FactorizedAlternatives.Size(2,4,2),domain.componentSize(diamond));
    }

}
