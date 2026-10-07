package io.github.gustavo2358.analysis.solver;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersistentCallerSupportTest {
    static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE));}
    @Test void condensedPersistentSupportsMatchIndependentCallerWalks() {
        var random=new Random(31951);
        for(int round=0;round<80;round++) {
            int n=1+random.nextInt(30);int[] variables=new int[n];int[][] parents=new int[n][];
            for(int i=0;i<n;i++) {
                variables[i]=random.nextInt(12);var list=new ArrayList<Integer>();
                for(int j=0;j<n;j++)if(random.nextInt(8)==0)list.add(j);
                parents[i]=list.stream().mapToInt(Integer::intValue).toArray();
            }
            var resources=resources();
            try(var store=new ResidentPageStore(4096,resources);var support=new PersistentCallerSupport(store,resources,variables,parents)) {
                for(int from=0;from<n;from++) {
                    var expected=new BitSet();var seen=new BitSet();var queue=new ArrayDeque<Integer>();queue.add(from);
                    while(!queue.isEmpty()){int v=queue.removeFirst();if(seen.get(v))continue;seen.set(v);expected.set(variables[v]);for(int p:parents[v])queue.addLast(p);}
                    for(int key=0;key<16;key++)assertEquals(expected.get(key),support.contains(from,key),"round="+round+" frame="+from+" key="+key);
                }
            }
            assertEquals(0,resources.heapUsed());
        }
    }
    @Test void chainSupportsShareSearchPathsInsteadOfCopyingAllAncestors() {
        int n=2048;int[] variables=new int[n];int[][] parents=new int[n][];
        for(int i=0;i<n;i++){variables[i]=i;parents[i]=i==0?new int[0]:new int[]{i-1};}
        var resources=resources();
        try(var store=new ResidentPageStore(4096,resources);var support=new PersistentCallerSupport(store,resources,variables,parents)) {
            assertTrue(support.records()<32L*n,"persistent record count="+support.records());
            for(int i=0;i<n;i++){assertTrue(support.contains(i,0));assertTrue(support.contains(i,i));assertFalse(support.contains(i,i+1));}
        }
        assertEquals(0,resources.heapUsed());
    }
}
