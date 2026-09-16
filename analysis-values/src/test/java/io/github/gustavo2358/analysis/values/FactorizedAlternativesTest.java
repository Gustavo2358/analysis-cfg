package io.github.gustavo2358.analysis.values;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactorizedAlternativesTest {
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
}
