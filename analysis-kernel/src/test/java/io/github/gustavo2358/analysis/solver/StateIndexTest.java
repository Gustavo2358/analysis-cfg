package io.github.gustavo2358.analysis.solver;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StateIndexTest {
    static AnalysisResources resources(long heap) {
        return new AnalysisResources(new AnalysisResources.Limits(heap,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE));
    }
    @Test void distinctInputsDoNotRequirePairwiseSemanticComparisons() {
        var budget=resources(Long.MAX_VALUE);long[] comparisons={0},hashes={0};int n=4096;
        try(var index=new StateIndex<Integer,Integer>(budget,AnalysisResources.Phase.DOMAIN,a->{hashes[0]++;return a.longValue();},(a,b)->{comparisons[0]++;return a.equals(b);})) {
            for(int i=0;i<n;i++)assertEquals(i,index.putIfAbsent(i,i));
            for(int i=n-1;i>=0;i--)assertEquals(i,index.get(i));
            assertEquals(2L*n,hashes[0],"growth reuses cached keys");assertEquals(n,index.size());assertTrue(comparisons[0]<=4L*n,"comparisons="+comparisons[0]);
            assertTrue(index.probes()<20L*n,"probes="+index.probes());
        }
        assertEquals(0,budget.heapUsed());
    }
    @Test void liveFilteringCompactsCapacityAndNeverRecomputesFingerprints() {
        var budget=resources(Long.MAX_VALUE);long[] hashes={0};int n=32768;
        try(var index=new StateIndex<Integer,Integer>(budget,AnalysisResources.Phase.DOMAIN,a->{hashes[0]++;return a.longValue();},Integer::equals)) {
            for(int i=0;i<n;i++)index.putIfAbsent(i,i);
            assertEquals(n-3,index.retainEntries(a->a>=n-3));assertEquals(n,hashes[0]);assertEquals(3,index.size());
            assertTrue(budget.heapUsed()<512,"old capacity retained="+budget.heapUsed());
            for(int i=n-3;i<n;i++)assertEquals(i,index.get(i));assertNull(index.get(n-4));
            assertEquals(3,index.retainEntries(a->false));assertEquals(0,index.size());
            assertEquals(7,index.putIfAbsent(7,7));assertEquals(7,index.get(7));
        }
        assertEquals(0,budget.heapUsed());
    }
    @Test void rejectedLiveFilteringDoesNotLoseAnyOldInput() {
        var budget=resources(500);
        try(var index=new StateIndex<Integer,Integer>(budget,AnalysisResources.Phase.DOMAIN,Integer::longValue,Integer::equals)) {
            index.putIfAbsent(1,1);index.putIfAbsent(2,2);
            assertThrows(AnalysisResources.Exhausted.class,()->index.retainEntries(a->a==1));
            assertEquals(2,index.size());assertEquals(1,index.get(1));assertEquals(2,index.get(2));
        }
        assertEquals(0,budget.heapUsed());
    }
    record Input(int value,int presentation) { }
    @Test void fingerprintCollisionsAndDifferentObjectEqualityNeverMergeSemanticInputs() {
        var budget=resources(Long.MAX_VALUE);
        try(var index=new StateIndex<Input,Integer>(budget,AnalysisResources.Phase.DOMAIN,a->a.value()%7,(a,b)->a.value()==b.value())) {
            var random=new Random(91172);var keys=new ArrayList<Integer>();for(int i=0;i<300;i++)keys.add(i);Collections.shuffle(keys,random);
            for(int key:keys)assertEquals(key,index.putIfAbsent(new Input(key,0),key));
            for(int key:keys)assertEquals(key,index.putIfAbsent(new Input(key,999),-1));
            for(int key:keys)assertEquals(key,index.get(new Input(key,77)));
            assertNull(index.get(new Input(999,0)));assertEquals(300,index.size());
        }
        assertEquals(0,budget.heapUsed());
    }
    @Test void unregisteredConstantFingerprintRemainsSemanticallyExact() {
        var budget=resources(Long.MAX_VALUE);
        try(var index=new StateIndex<String,Integer>(budget,AnalysisResources.Phase.CONTROL,a->0,String::equals)) {
            assertEquals(1,index.putIfAbsent("a",1));assertEquals(2,index.putIfAbsent("b",2));
            assertEquals(1,index.get(new String(new char[]{'a'})));assertEquals(2,index.get("b"));assertNull(index.get("c"));
        }
    }
    @Test void rejectedCapacityGrowthPreservesExistingInputsAndClosesEveryLease() {
        var budget=resources(500);
        var index=new StateIndex<Integer,Integer>(budget,AnalysisResources.Phase.DOMAIN,Integer::longValue,Integer::equals);
        assertEquals(1,index.putIfAbsent(1,1));assertEquals(2,index.putIfAbsent(2,2));
        assertThrows(AnalysisResources.Exhausted.class,()->index.putIfAbsent(3,3));
        assertEquals(1,index.get(1));assertEquals(2,index.get(2));assertEquals(2,index.size());
        index.close();index.close();assertEquals(0,budget.heapUsed());
        assertThrows(IllegalStateException.class,()->index.get(1));
    }
}
