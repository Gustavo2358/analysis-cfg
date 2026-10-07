package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.adapters.FilePageStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PagedBooleanCircuitStorageTest {
    @TempDir Path directory;
    @Test void exactMixedCircuitQueriesAndCollectionSurviveOnePageEviction() {
        var memory=new AnalysisResources(new AnalysisResources.Limits(2000000,100000,0,64000000,2,500000000,0));
        try(var resident=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var spill=new FilePageStore(directory,128,1,memory,AnalysisResources.Phase.CONTROL)) {
            try(var a=new PagedBooleanCircuit(resident,memory);var b=new PagedBooleanCircuit(spill,memory)) {
                long left=0,right=0;
                for(int key=0;key<12;key++) {
                    left=a.or(left,a.and(a.variable(key),a.variable(key+12)));
                    right=b.or(right,b.and(b.variable(key),b.variable(key+12)));
                }
                long ta=a.retain(left),tb=b.retain(right);a.collect();b.collect();
                assertEquals(a.retainedNodes(),b.retainedNodes());assertTrue(b.retainedNodes()<=48);
                for(int bits=0;bits<64;bits++) {
                    int assignment=bits;
                    assertEquals(a.test(left,key->(assignment&(1<<(key%6)))!=0),b.test(right,key->(assignment&(1<<(key%6)))!=0));
                }
                assertTrue(a.satisfiable(left));assertTrue(b.satisfiable(right));
                long fixedA=a.restrict(left,key->key<12?1:-1),fixedB=b.restrict(right,key->key<12?1:-1);
                long expectedA=0,expectedB=0;
                for(int key=12;key<24;key++){expectedA=a.or(expectedA,a.variable(key));expectedB=b.or(expectedB,b.variable(key));}
                assertTrue(a.equivalent(fixedA,expectedA));assertTrue(b.equivalent(fixedB,expectedB));
                assertFalse(b.equivalent(fixedB,expectedB^1));
                assertTrue(spill.statistics().evictions()>100);
                a.release(ta);b.release(tb);a.collect();b.collect();assertEquals(0,b.retainedNodes());
            }
            assertEquals(0,resident.statistics().livePages());assertEquals(0,spill.statistics().livePages());
            long page=spill.allocate();spill.release(page);
        }
        assertEquals(0,memory.heapUsed());assertEquals(0,memory.used(AnalysisResources.Pool.TEMPORARY));
        assertEquals(0,memory.used(AnalysisResources.Pool.OPEN_FILES));
    }
    @Test void CircuitAndDecisionCardinalitySpillWithConstantResidentCapacity() {
        long capacity=-1,previousTemporary=0;
        for(int count:new int[]{16,64,256}) {
            var memory=new AnalysisResources(new AnalysisResources.Limits(60000,0,0,64000000,1,500000000,0));
            try(var spill=new FilePageStore(directory,128,1,memory,AnalysisResources.Phase.CONTROL)) {
                try(var b=new PagedBooleanCircuit(spill,memory)) {
                    long root=1;for(int key=0;key<count;key++)root=b.and(root,b.variable(key));
                    assertTrue(b.satisfiable(root));assertEquals(2*count-1,b.lastDecisionVariables());
                    assertEquals(1,b.restrict(root,key->1));assertTrue(b.test(root,key->true));
                    if(capacity<0)capacity=memory.heapPeak();else assertEquals(capacity,memory.heapPeak());
                    long temporary=memory.used(AnalysisResources.Pool.TEMPORARY);
                    assertTrue(temporary>previousTemporary);previousTemporary=temporary;
                }
                assertEquals(0,spill.statistics().livePages());
            }
            assertEquals(0,memory.heapUsed());assertEquals(0,memory.used(AnalysisResources.Pool.TEMPORARY));
            assertEquals(0,memory.used(AnalysisResources.Pool.OPEN_FILES));
        }
    }
}
